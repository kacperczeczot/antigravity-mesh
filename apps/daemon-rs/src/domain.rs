use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct CapabilitySet {
    pub filesystem: bool,
    pub process_exec: bool,
    pub agent: bool,
    pub agent_stream: bool,
    pub gpu: bool,
    pub tasks: bool,
    pub multi_session: bool,
}

impl Default for CapabilitySet {
    fn default() -> Self {
        Self {
            filesystem: true,
            process_exec: true,
            agent: true,
            agent_stream: true,
            gpu: false,
            tasks: true,
            multi_session: true,
        }
    }
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq, Default)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum ExecutionPolicy {
    Safe,
    #[default]
    Normal,
    Unrestricted,
}

impl ExecutionPolicy {
    pub fn is_command_allowed(&self, cmd: &str) -> Result<(), String> {
        let trimmed = cmd.trim();
        if trimmed.is_empty() {
            return Err("Polecenie nie może być puste".to_string());
        }

        match self {
            ExecutionPolicy::Unrestricted => Ok(()),
            ExecutionPolicy::Normal => {
                // Reject obvious destructive commands
                let destructive = [
                    "rm -rf /",
                    "rm -rf /*",
                    "format",
                    "mkfs",
                    "dd if=",
                    ":(){ :|:& };:",
                    "shutdown",
                    "reboot",
                    "init 0",
                ];
                let lower = trimmed.to_lowercase();
                for d in destructive {
                    if lower.contains(d) {
                        return Err(format!("Zablokowano polecenie destrukcyjne: '{}'", d));
                    }
                }
                Ok(())
            }
            ExecutionPolicy::Safe => {
                // Only allow standard development and query tools
                // Extract first token (binary name)
                let first_token = trimmed
                    .split_whitespace()
                    .next()
                    .unwrap_or("")
                    .trim_start_matches("./")
                    .trim_start_matches(".\\");
                
                let clean_bin = std::path::Path::new(first_token)
                    .file_name()
                    .and_then(|f| f.to_str())
                    .unwrap_or(first_token)
                    .to_lowercase();

                let clean_bin = clean_bin.trim_end_matches(".exe").trim_end_matches(".cmd").trim_end_matches(".bat");

                let allowed_bins = [
                    "git", "cargo", "rustc", "npm", "node", "npx", "yarn", "pnpm",
                    "gradle", "gradlew", "mvn", "python", "python3", "pytest",
                    "agy", "gemini", "claude", "ls", "dir", "echo", "cat", "pwd",
                    "find", "grep", "rg", "head", "tail", "curl"
                ];

                if !allowed_bins.contains(&clean_bin) {
                    return Err(format!(
                        "Narzędzie '{}' nie znajduje się na białej liście trybu SAFE. Dozwolone: {:?}",
                        clean_bin, allowed_bins
                    ));
                }

                // Disallow shell chaining operators in SAFE mode to prevent evasion
                let chaining = [";", "&&", "||", "|", "`", "$("];
                for ch in chaining {
                    if trimmed.contains(ch) {
                        return Err(format!(
                            "Operatory łączenia poleceń ('{}') są zablokowane w trybie SAFE",
                            ch
                        ));
                    }
                }

                Ok(())
            }
        }
    }
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum TaskType {
    AgentQuery,
    ProcessExec,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum TaskStatus {
    Queued,
    Running,
    Completed,
    Failed,
    Cancelled,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Task {
    pub id: String,
    pub client_task_id: Option<String>,
    pub task_type: TaskType,
    pub conversation_id: Option<String>,
    pub command: Option<String>,
    pub question: Option<String>,
    pub cwd: Option<String>,
    pub execution_policy: ExecutionPolicy,
    pub auto_approve: bool,
    pub status: TaskStatus,
    pub progress: Option<String>,
    pub returncode: Option<i32>,
    pub result: Option<String>,
    pub error: Option<String>,
    pub created_at: u64,
    pub started_at: Option<u64>,
    pub completed_at: Option<u64>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct NodeInfo {
    pub node_id: String,
    pub name: String,
    pub platform: String,
    pub os_version: String,
    pub arch: String,
    pub version: String,
    pub capabilities: CapabilitySet,
    pub execution_policy: ExecutionPolicy,
    pub uptime_secs: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ChatSessionMetadata {
    pub id: String,
    pub title: String,
    pub node_id: String,
    pub created_at: u64,
    pub updated_at: u64,
    pub message_count: usize,
}
