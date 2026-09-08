use crate::domain::{ChatSessionMetadata, Task};
use redb::{Database, ReadableDatabase, ReadableTable, TableDefinition};
use std::path::{Path, PathBuf};
use std::sync::Arc;

const TASKS_TABLE: TableDefinition<&str, &[u8]> = TableDefinition::new("tasks");
const TASK_LOGS_TABLE: TableDefinition<&str, &[u8]> = TableDefinition::new("task_logs");
const SESSIONS_TABLE: TableDefinition<&str, &[u8]> = TableDefinition::new("sessions");

pub struct StateStore {
    db: Arc<Database>,
}

impl StateStore {
    pub fn open<P: AsRef<Path>>(path: P) -> Result<Self, String> {
        let p = path.as_ref();
        if let Some(parent) = p.parent() {
            let _ = std::fs::create_dir_all(parent);
        }

        let db = Database::create(p).map_err(|e| format!("Błąd otwarcia bazy redb: {}", e))?;

        // Initialize tables in a write transaction
        {
            let write_txn = db
                .begin_write()
                .map_err(|e| format!("Błąd transakcji zapisu redb: {}", e))?;
            {
                let _ = write_txn
                    .open_table(TASKS_TABLE)
                    .map_err(|e| format!("Błąd inicjalizacji tabeli tasks: {}", e))?;
                let _ = write_txn
                    .open_table(TASK_LOGS_TABLE)
                    .map_err(|e| format!("Błąd inicjalizacji tabeli task_logs: {}", e))?;
                let _ = write_txn
                    .open_table(SESSIONS_TABLE)
                    .map_err(|e| format!("Błąd inicjalizacji tabeli sessions: {}", e))?;
            }
            write_txn
                .commit()
                .map_err(|e| format!("Błąd zatwierdzenia tabel redb: {}", e))?;
        }

        Ok(Self { db: Arc::new(db) })
    }

    pub fn default_path() -> PathBuf {
        if let Some(home) = dirs_home() {
            home.join(".antigravity").join("mesh").join("state.redb")
        } else {
            PathBuf::from("state.redb")
        }
    }

    pub fn save_task(&self, task: &Task) -> Result<(), String> {
        let serialized = serde_json::to_vec(task).map_err(|e| e.to_string())?;
        let write_txn = self
            .db
            .begin_write()
            .map_err(|e| format!("Błąd transakcji zapisu: {}", e))?;
        {
            let mut table = write_txn
                .open_table(TASKS_TABLE)
                .map_err(|e| format!("Błąd otwarcia tabeli: {}", e))?;
            table
                .insert(task.id.as_str(), serialized.as_slice())
                .map_err(|e| format!("Błąd zapisu zadania: {}", e))?;
        }
        write_txn.commit().map_err(|e| format!("Błąd commit: {}", e))?;
        Ok(())
    }

    pub fn get_task(&self, task_id: &str) -> Result<Option<Task>, String> {
        let read_txn = self
            .db
            .begin_read()
            .map_err(|e| format!("Błąd transakcji odczytu: {}", e))?;
        let table = read_txn
            .open_table(TASKS_TABLE)
            .map_err(|e| format!("Błąd otwarcia tabeli: {}", e))?;
        if let Some(val) = table.get(task_id).map_err(|e| e.to_string())? {
            let task: Task = serde_json::from_slice(val.value()).map_err(|e| e.to_string())?;
            Ok(Some(task))
        } else {
            Ok(None)
        }
    }

    pub fn find_task_by_client_id(&self, client_task_id: &str) -> Result<Option<Task>, String> {
        let tasks = self.list_tasks(500)?;
        for task in tasks {
            if let Some(ref cid) = task.client_task_id {
                if cid == client_task_id {
                    return Ok(Some(task));
                }
            }
        }
        Ok(None)
    }

    pub fn list_tasks(&self, limit: usize) -> Result<Vec<Task>, String> {
        let read_txn = self
            .db
            .begin_read()
            .map_err(|e| format!("Błąd transakcji odczytu: {}", e))?;
        let table = read_txn
            .open_table(TASKS_TABLE)
            .map_err(|e| format!("Błąd otwarcia tabeli: {}", e))?;
        let iter = table.iter().map_err(|e| e.to_string())?;

        let mut tasks = Vec::new();
        for item in iter {
            let (_key, val) = item.map_err(|e| e.to_string())?;
            if let Ok(task) = serde_json::from_slice::<Task>(val.value()) {
                tasks.push(task);
            }
        }

        // Sort by created_at descending (newest first)
        tasks.sort_by(|a, b| b.created_at.cmp(&a.created_at));
        tasks.truncate(limit);
        Ok(tasks)
    }

    pub fn append_task_log(&self, task_id: &str, text: &str) -> Result<(), String> {
        let write_txn = self
            .db
            .begin_write()
            .map_err(|e| format!("Błąd transakcji zapisu: {}", e))?;
        {
            let mut table = write_txn
                .open_table(TASK_LOGS_TABLE)
                .map_err(|e| format!("Błąd otwarcia tabeli logów: {}", e))?;

            let mut current = if let Some(val) = table.get(task_id).map_err(|e| e.to_string())? {
                String::from_utf8_lossy(val.value()).to_string()
            } else {
                String::new()
            };

            current.push_str(text);
            // Cap log size in storage at 1MB to prevent disk bloat
            const MAX_LOG_BYTES: usize = 1024 * 1024;
            if current.len() > MAX_LOG_BYTES {
                let excess = current.len() - MAX_LOG_BYTES;
                current = format!("[...wcześniejsze logi obcięte...]\n{}", &current[excess..]);
            }

            table
                .insert(task_id, current.as_bytes())
                .map_err(|e| format!("Błąd zapisu logów: {}", e))?;
        }
        write_txn.commit().map_err(|e| format!("Błąd commit: {}", e))?;
        Ok(())
    }

    pub fn get_task_logs(&self, task_id: &str) -> Result<String, String> {
        let read_txn = self
            .db
            .begin_read()
            .map_err(|e| format!("Błąd transakcji odczytu: {}", e))?;
        let table = read_txn
            .open_table(TASK_LOGS_TABLE)
            .map_err(|e| format!("Błąd otwarcia tabeli logów: {}", e))?;

        if let Some(val) = table.get(task_id).map_err(|e| e.to_string())? {
            Ok(String::from_utf8_lossy(val.value()).to_string())
        } else {
            Ok(String::new())
        }
    }

    pub fn save_session(&self, session: &ChatSessionMetadata) -> Result<(), String> {
        let serialized = serde_json::to_vec(session).map_err(|e| e.to_string())?;
        let write_txn = self
            .db
            .begin_write()
            .map_err(|e| format!("Błąd transakcji zapisu: {}", e))?;
        {
            let mut table = write_txn
                .open_table(SESSIONS_TABLE)
                .map_err(|e| format!("Błąd otwarcia tabeli: {}", e))?;
            table
                .insert(session.id.as_str(), serialized.as_slice())
                .map_err(|e| format!("Błąd zapisu sesji: {}", e))?;
        }
        write_txn.commit().map_err(|e| format!("Błąd commit: {}", e))?;
        Ok(())
    }

    pub fn list_sessions(&self) -> Result<Vec<ChatSessionMetadata>, String> {
        let read_txn = self
            .db
            .begin_read()
            .map_err(|e| format!("Błąd transakcji odczytu: {}", e))?;
        let table = read_txn
            .open_table(SESSIONS_TABLE)
            .map_err(|e| format!("Błąd otwarcia tabeli: {}", e))?;
        let iter = table.iter().map_err(|e| e.to_string())?;

        let mut sessions = Vec::new();
        for item in iter {
            let (_key, val) = item.map_err(|e| e.to_string())?;
            if let Ok(sess) = serde_json::from_slice::<ChatSessionMetadata>(val.value()) {
                sessions.push(sess);
            }
        }
        sessions.sort_by(|a, b| b.updated_at.cmp(&a.updated_at));
        Ok(sessions)
    }

    pub fn prune_old_tasks(&self, keep_latest: usize) -> Result<usize, String> {
        let mut all_tasks = self.list_tasks(1000)?;
        if all_tasks.len() <= keep_latest {
            return Ok(0);
        }

        // Tasks sorted descending, so items after keep_latest are old
        let to_remove: Vec<String> = all_tasks.drain(keep_latest..).map(|t| t.id).collect();
        let remove_count = to_remove.len();

        let write_txn = self
            .db
            .begin_write()
            .map_err(|e| format!("Błąd transakcji zapisu: {}", e))?;
        {
            let mut tasks_table = write_txn
                .open_table(TASKS_TABLE)
                .map_err(|e| format!("Błąd otwarcia tabeli tasks: {}", e))?;
            let mut logs_table = write_txn
                .open_table(TASK_LOGS_TABLE)
                .map_err(|e| format!("Błąd otwarcia tabeli logs: {}", e))?;

            for id in &to_remove {
                let _ = tasks_table.remove(id.as_str());
                let _ = logs_table.remove(id.as_str());
            }
        }
        write_txn.commit().map_err(|e| format!("Błąd commit: {}", e))?;
        Ok(remove_count)
    }
}

fn dirs_home() -> Option<PathBuf> {
    #[cfg(windows)]
    {
        std::env::var_os("USERPROFILE").map(PathBuf::from)
    }
    #[cfg(not(windows))]
    {
        if let Some(h) = std::env::var_os("HOME").map(PathBuf::from) {
            Some(h)
        } else if let Ok(user) = std::env::var("USER") {
            #[cfg(target_os = "macos")]
            {
                Some(PathBuf::from(format!("/Users/{}", user)))
            }
            #[cfg(not(target_os = "macos"))]
            {
                Some(PathBuf::from(format!("/home/{}", user)))
            }
        } else {
            None
        }
    }
}
