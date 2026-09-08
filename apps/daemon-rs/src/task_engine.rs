use crate::domain::{ExecutionPolicy, Task, TaskStatus, TaskType};
use crate::state_store::StateStore;
use std::collections::HashMap;
use std::path::PathBuf;
use std::process::Stdio;
use std::sync::Arc;
use std::time::{SystemTime, UNIX_EPOCH};
use tokio::io::AsyncBufReadExt;
use tokio::process::Command;
use tokio::sync::RwLock;
use uuid::Uuid;

pub struct TaskEngine {
    store: Arc<StateStore>,
    active_tasks: Arc<RwLock<HashMap<String, Arc<RwLock<ActiveTaskEntry>>>>>,
    agy_cli_path: Option<String>,
}

struct ActiveTaskEntry {
    task: Task,
    log_buffer: String,
    cancel_tx: Option<tokio::sync::oneshot::Sender<()>>,
    #[cfg(unix)]
    pgid: Option<i32>,
}

impl TaskEngine {
    pub fn new(store: Arc<StateStore>, agy_cli_path: Option<String>) -> Self {
        Self {
            store,
            active_tasks: Arc::new(RwLock::new(HashMap::new())),
            agy_cli_path,
        }
    }

    pub fn store(&self) -> &Arc<StateStore> {
        &self.store
    }

    pub fn now_secs() -> u64 {
        SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs()
    }

    pub async fn submit_task(
        &self,
        client_task_id: Option<String>,
        task_type: TaskType,
        conversation_id: Option<String>,
        command: Option<String>,
        question: Option<String>,
        cwd: Option<String>,
        policy: Option<ExecutionPolicy>,
        auto_approve: bool,
    ) -> Result<Task, String> {
        // 1. Check idempotency: if client_task_id is provided and already exists, return existing task
        if let Some(ref cid) = client_task_id {
            if let Ok(Some(existing)) = self.store.find_task_by_client_id(cid) {
                return Ok(existing);
            }
        }

        let exec_policy = policy.unwrap_or_default();

        // 2. Validate policy for ProcessExec
        if task_type == TaskType::ProcessExec {
            if let Some(ref cmd) = command {
                exec_policy.is_command_allowed(cmd)?;
            } else {
                return Err("Brak polecenia dla zadania process_exec".to_string());
            }
        }

        let task_id = format!("task_{}", Uuid::new_v4().simple());
        let task = Task {
            id: task_id.clone(),
            client_task_id,
            task_type,
            conversation_id,
            command,
            question,
            cwd,
            execution_policy: exec_policy,
            auto_approve,
            status: TaskStatus::Queued,
            progress: Some("Zadanie zakolejkowane".to_string()),
            returncode: None,
            result: None,
            error: None,
            created_at: Self::now_secs(),
            started_at: None,
            completed_at: None,
        };

        // Persist initial state
        self.store.save_task(&task)?;

        let (cancel_tx, cancel_rx) = tokio::sync::oneshot::channel();
        let entry = Arc::new(RwLock::new(ActiveTaskEntry {
            task: task.clone(),
            log_buffer: String::new(),
            cancel_tx: Some(cancel_tx),
            #[cfg(unix)]
            pgid: None,
        }));

        self.active_tasks
            .write()
            .await
            .insert(task_id.clone(), entry.clone());

        // Spawn async execution
        let store_clone = self.store.clone();
        let active_tasks_clone = self.active_tasks.clone();
        let agy_cli = self.agy_cli_path.clone();

        tokio::spawn(async move {
            Self::execute_task_lifecycle(
                entry,
                store_clone,
                active_tasks_clone,
                cancel_rx,
                agy_cli,
            )
            .await;
        });

        Ok(task)
    }

    async fn execute_task_lifecycle(
        entry: Arc<RwLock<ActiveTaskEntry>>,
        store: Arc<StateStore>,
        active_tasks: Arc<RwLock<HashMap<String, Arc<RwLock<ActiveTaskEntry>>>>>,
        mut cancel_rx: tokio::sync::oneshot::Receiver<()>,
        agy_cli_path: Option<String>,
    ) {
        // Transition to RUNNING
        let (task_id, task_type, question, command, cwd, conv_id, auto_approve) = {
            let mut guard = entry.write().await;
            guard.task.status = TaskStatus::Running;
            guard.task.started_at = Some(Self::now_secs());
            guard.task.progress = Some("Uruchamianie procesu...".to_string());
            let _ = store.save_task(&guard.task);
            (
                guard.task.id.clone(),
                guard.task.task_type,
                guard.task.question.clone(),
                guard.task.command.clone(),
                guard.task.cwd.clone(),
                guard.task.conversation_id.clone(),
                guard.task.auto_approve,
            )
        };

        let work_dir = cwd
            .map(PathBuf::from)
            .unwrap_or_else(|| std::env::current_dir().unwrap_or_else(|_| PathBuf::from(".")));

        let mut cmd = match task_type {
            TaskType::AgentQuery => {
                let cli = match agy_cli_path {
                    Some(c) => c,
                    None => {
                        let mut guard = entry.write().await;
                        guard.task.status = TaskStatus::Failed;
                        guard.task.error = Some("Brak dostępnego CLI asystenta AI (agy)".to_string());
                        guard.task.completed_at = Some(Self::now_secs());
                        let _ = store.save_task(&guard.task);
                        active_tasks.write().await.remove(&task_id);
                        return;
                    }
                };

                let mut c = Command::new(cli);
                c.current_dir(&work_dir);
                c.arg("--output-format").arg("stream-json");
                if let Some(ref cid) = conv_id {
                    let trimmed = cid.trim();
                    if !trimmed.is_empty() {
                        c.arg("--conversation").arg(trimmed);
                    }
                }
                if auto_approve {
                    c.arg("--dangerously-skip-permissions");
                }
                c.arg("--print-timeout").arg("60m");
                c.arg("--print");
                c.arg(question.unwrap_or_default());
                c
            }
            TaskType::ProcessExec => {
                let cmd_str = command.unwrap_or_default();
                #[cfg(windows)]
                {
                    let mut c = Command::new("cmd.exe");
                    c.current_dir(&work_dir);
                    c.arg("/C").arg(&cmd_str);
                    c
                }
                #[cfg(not(windows))]
                {
                    let mut c = Command::new("/bin/sh");
                    c.current_dir(&work_dir);
                    c.arg("-c").arg(&cmd_str);
                    c
                }
            }
        };

        cmd.stdout(Stdio::piped()).stderr(Stdio::piped());

        // Process group configuration on Unix to prevent orphaned child processes
        #[cfg(unix)]
        {
            cmd.process_group(0);
        }

        let mut child = match cmd.spawn() {
            Ok(c) => c,
            Err(e) => {
                let mut guard = entry.write().await;
                guard.task.status = TaskStatus::Failed;
                guard.task.error = Some(format!("Błąd uruchomienia procesu: {}", e));
                guard.task.completed_at = Some(Self::now_secs());
                let _ = store.save_task(&guard.task);
                active_tasks.write().await.remove(&task_id);
                return;
            }
        };

        #[cfg(unix)]
        {
            if let Some(pid) = child.id() {
                let mut guard = entry.write().await;
                guard.pgid = Some(pid as i32);
            }
        }

        let stdout = child.stdout.take();
        let stderr = child.stderr.take();

        // Async log collectors
        let entry_stdout = entry.clone();
        let store_stdout = store.clone();
        let task_id_stdout = task_id.clone();
        let stdout_handle = tokio::spawn(async move {
            if let Some(out) = stdout {
                let mut reader = tokio::io::BufReader::new(out).lines();
                while let Ok(Some(line)) = reader.next_line().await {
                    let mut guard = entry_stdout.write().await;
                    guard.log_buffer.push_str(&line);
                    guard.log_buffer.push('\n');

                    // If JSON stream from agy, parse progress / status updates
                    if let Ok(val) = serde_json::from_str::<serde_json::Value>(&line) {
                        if let Some(status) = val.get("status").and_then(|s| s.as_str()) {
                            guard.task.progress = Some(status.to_string());
                        } else if let Some(thought) = val.get("thought").and_then(|t| t.as_str()) {
                            guard.task.progress = Some(format!("Myśli: {}", thought));
                        }
                    }
                    let _ = store_stdout.append_task_log(&task_id_stdout, &format!("{}\n", line));
                }
            }
        });

        let entry_stderr = entry.clone();
        let store_stderr = store.clone();
        let task_id_stderr = task_id.clone();
        let stderr_handle = tokio::spawn(async move {
            if let Some(err) = stderr {
                let mut reader = tokio::io::BufReader::new(err).lines();
                while let Ok(Some(line)) = reader.next_line().await {
                    let mut guard = entry_stderr.write().await;
                    guard.log_buffer.push_str("[stderr] ");
                    guard.log_buffer.push_str(&line);
                    guard.log_buffer.push('\n');
                    let _ = store_stderr.append_task_log(&task_id_stderr, &format!("[stderr] {}\n", line));
                }
            }
        });

        // Wait for either child exit OR cancellation
        tokio::select! {
            _ = &mut cancel_rx => {
                // Kill process tree
                #[cfg(unix)]
                {
                    let pgid_opt = { entry.read().await.pgid };
                    if let Some(pgid) = pgid_opt {
                        unsafe {
                            libc::kill(-pgid, libc::SIGKILL);
                        }
                    }
                }
                let _ = child.kill().await;

                let mut guard = entry.write().await;
                guard.task.status = TaskStatus::Cancelled;
                guard.task.progress = Some("Zadanie zostało anulowane przez użytkownika".to_string());
                guard.task.completed_at = Some(Self::now_secs());
                let _ = store.save_task(&guard.task);
            }
            status_res = child.wait() => {
                let _ = stdout_handle.await;
                let _ = stderr_handle.await;

                let mut guard = entry.write().await;
                guard.task.completed_at = Some(Self::now_secs());

                match status_res {
                    Ok(exit_status) => {
                        let code = exit_status.code().unwrap_or(-1);
                        guard.task.returncode = Some(code);
                        if exit_status.success() {
                            guard.task.status = TaskStatus::Completed;
                            guard.task.progress = Some("Zakończono pomyślnie".to_string());
                            guard.task.result = Some(guard.log_buffer.clone());
                        } else {
                            guard.task.status = TaskStatus::Failed;
                            guard.task.error = Some(format!("Proces zakończył się kodem błędu {}", code));
                            guard.task.progress = Some(format!("Błąd wykonania (kod {})", code));
                        }
                    }
                    Err(e) => {
                        guard.task.status = TaskStatus::Failed;
                        guard.task.error = Some(format!("Błąd oczekiwania na proces: {}", e));
                    }
                }
                let _ = store.save_task(&guard.task);
            }
        }

        // Cleanup active map
        active_tasks.write().await.remove(&task_id);
    }

    pub async fn get_task(&self, task_id: &str) -> Result<Option<Task>, String> {
        // First check in-memory active tasks for freshest progress/status
        if let Some(entry) = self.active_tasks.read().await.get(task_id) {
            let guard = entry.read().await;
            return Ok(Some(guard.task.clone()));
        }
        // Otherwise load from persistent redb store
        self.store.get_task(task_id)
    }

    pub async fn list_tasks(&self, limit: usize) -> Result<Vec<Task>, String> {
        let mut tasks = self.store.list_tasks(limit)?;
        let active = self.active_tasks.read().await;
        for t in tasks.iter_mut() {
            if let Some(entry) = active.get(&t.id) {
                let guard = entry.read().await;
                t.status = guard.task.status;
                t.progress = guard.task.progress.clone();
            }
        }
        Ok(tasks)
    }

    pub async fn get_task_logs(&self, task_id: &str, offset: usize) -> Result<(String, usize), String> {
        // Check active in-memory buffer first
        if let Some(entry) = self.active_tasks.read().await.get(task_id) {
            let guard = entry.read().await;
            let full = &guard.log_buffer;
            if offset >= full.len() {
                return Ok((String::new(), full.len()));
            }
            let slice = &full[offset..];
            return Ok((slice.to_string(), full.len()));
        }

        // Otherwise get from persistent store
        let stored = self.store.get_task_logs(task_id)?;
        if offset >= stored.len() {
            return Ok((String::new(), stored.len()));
        }
        let slice = &stored[offset..];
        Ok((slice.to_string(), stored.len()))
    }

    pub async fn cancel_task(&self, task_id: &str) -> Result<bool, String> {
        if let Some(entry) = self.active_tasks.read().await.get(task_id) {
            let mut guard = entry.write().await;
            guard.task.status = TaskStatus::Cancelled;
            guard.task.progress = Some("Zadanie zostało anulowane".to_string());
            guard.task.completed_at = Some(Self::now_secs());
            let _ = self.store.save_task(&guard.task);

            #[cfg(unix)]
            if let Some(pgid) = guard.pgid {
                unsafe {
                    libc::kill(-pgid, libc::SIGTERM);
                }
            }

            if let Some(tx) = guard.cancel_tx.take() {
                let _ = tx.send(());
            }
            return Ok(true);
        }
        Ok(false)
    }

    pub async fn register_streaming_task(
        &self,
        conversation_id: Option<String>,
        question: String,
        cwd: Option<String>,
        cancel_tx: Option<tokio::sync::oneshot::Sender<()>>,
    ) -> Result<String, String> {
        let task_id = format!("task_{}", Uuid::new_v4().simple());
        let task = Task {
            id: task_id.clone(),
            client_task_id: None,
            task_type: TaskType::AgentQuery,
            conversation_id,
            command: None,
            question: Some(question),
            cwd,
            execution_policy: ExecutionPolicy::Normal,
            auto_approve: true,
            status: TaskStatus::Running,
            progress: Some("Agent analizuje zapytanie...".to_string()),
            returncode: None,
            result: None,
            error: None,
            created_at: Self::now_secs(),
            started_at: Some(Self::now_secs()),
            completed_at: None,
        };

        self.store.save_task(&task)?;

        let entry = Arc::new(RwLock::new(ActiveTaskEntry {
            task,
            log_buffer: String::new(),
            cancel_tx,
            #[cfg(unix)]
            pgid: None,
        }));

        self.active_tasks.write().await.insert(task_id.clone(), entry);
        Ok(task_id)
    }

    pub async fn update_task_pgid(&self, task_id: &str, pgid: i32) {
        #[cfg(unix)]
        if let Some(entry) = self.active_tasks.read().await.get(task_id) {
            let mut guard = entry.write().await;
            guard.pgid = Some(pgid);
        }
    }

    pub async fn update_task_progress(&self, task_id: &str, progress: String) {
        if let Some(entry) = self.active_tasks.read().await.get(task_id) {
            let mut guard = entry.write().await;
            guard.task.progress = Some(progress);
            let _ = self.store.save_task(&guard.task);
        }
    }

    pub async fn complete_task(
        &self,
        task_id: &str,
        returncode: i32,
        result: String,
        conversation_id: Option<String>,
    ) {
        let entry_opt = self.active_tasks.write().await.remove(task_id);
        if let Some(entry) = entry_opt {
            let mut guard = entry.write().await;
            guard.task.status = if returncode == 0 {
                TaskStatus::Completed
            } else {
                TaskStatus::Failed
            };
            guard.task.returncode = Some(returncode);
            guard.task.result = Some(result.clone());
            if returncode != 0 {
                guard.task.error = Some(result);
            }
            if conversation_id.is_some() {
                guard.task.conversation_id = conversation_id;
            }
            guard.task.completed_at = Some(Self::now_secs());
            guard.task.progress = Some(if returncode == 0 {
                "Zadanie ukończone".to_string()
            } else {
                "Zadanie zakończone błędem".to_string()
            });
            let _ = self.store.save_task(&guard.task);
        } else if let Ok(Some(mut task)) = self.store.get_task(task_id) {
            task.status = if returncode == 0 {
                TaskStatus::Completed
            } else {
                TaskStatus::Failed
            };
            task.returncode = Some(returncode);
            task.result = Some(result.clone());
            if returncode != 0 {
                task.error = Some(result);
            }
            if conversation_id.is_some() {
                task.conversation_id = conversation_id;
            }
            task.completed_at = Some(Self::now_secs());
            let _ = self.store.save_task(&task);
        }
    }

    pub async fn fail_task(&self, task_id: &str, error: String) {
        let entry_opt = self.active_tasks.write().await.remove(task_id);
        if let Some(entry) = entry_opt {
            let mut guard = entry.write().await;
            guard.task.status = TaskStatus::Failed;
            guard.task.error = Some(error.clone());
            guard.task.completed_at = Some(Self::now_secs());
            guard.task.progress = Some(format!("Błąd: {}", error));
            let _ = self.store.save_task(&guard.task);
        }
    }
}
