//! Session logger for Antigravity Mesh daemon.
//!
//! Appends one JSONL line per agent event to `~/.gemini/mesh_sessions.jsonl`
//! (macOS/Linux) or `%APPDATA%\AntigravityMesh\mesh_sessions.jsonl` (Windows).
//! Each write is atomic at the line level — safe to call from concurrent tasks.
//! The file survives connection drops, so tool-call history is never lost.

use serde::Serialize;
use std::{
    fs,
    io::Write,
    path::{Path, PathBuf},
    time::{SystemTime, UNIX_EPOCH},
};

// ─── Public Types ────────────────────────────────────────────────────────────

/// A single event record written to the session log.
///
/// Event types:
/// - `session_start`   — user question received, agent starting
/// - `tool_call`       — agent invoked a tool (view_file, run_command, …)
/// - `tool_result`     — tool execution completed
/// - `response_delta`  — fragment of generated text (capped at 500 chars)
/// - `session_end`     — agent finished, full response available
/// - `disconnected`    — client dropped the SSE stream mid-session
/// - `error`           — unrecoverable error (spawn failure, etc.)
#[derive(Serialize, Default, Clone)]
pub struct SessionLogEntry {
    /// ISO 8601 timestamp (UTC), e.g. "2026-09-06T19:20:43Z"
    pub ts: String,
    /// Unix timestamp in seconds
    pub ts_unix: u64,
    /// Daemon node hostname
    pub node: String,
    /// Agent conversation ID (set from stream "result" event or initial payload)
    pub conversation_id: Option<String>,
    /// Event type (see above)
    pub event: String,

    /// User's original question — only on `session_start`
    #[serde(skip_serializing_if = "Option::is_none")]
    pub question: Option<String>,

    /// Tool name — on `tool_call` / `tool_result`
    #[serde(skip_serializing_if = "Option::is_none")]
    pub tool_name: Option<String>,

    /// Tool parameters JSON — on `tool_call`
    #[serde(skip_serializing_if = "Option::is_none")]
    pub tool_args: Option<serde_json::Value>,

    /// Short preview of generated text — on `response_delta` (max 500 chars)
    #[serde(skip_serializing_if = "Option::is_none")]
    pub response_delta: Option<String>,

    /// Full agent response text — on `session_end`
    #[serde(skip_serializing_if = "Option::is_none")]
    pub final_response: Option<String>,

    /// Outcome: `ok` | `disconnected` | `timeout` | `error`
    #[serde(skip_serializing_if = "Option::is_none")]
    pub status: Option<String>,

    /// CLI process exit code — on `session_end`
    #[serde(skip_serializing_if = "Option::is_none")]
    pub returncode: Option<i32>,
}

// ─── Public API ──────────────────────────────────────────────────────────────

/// Returns the path to `mesh_sessions.jsonl`, creating parent dirs if needed.
pub fn get_session_log_path() -> PathBuf {
    #[cfg(target_os = "windows")]
    let base = std::env::var_os("APPDATA")
        .map(|p| PathBuf::from(p).join("AntigravityMesh"))
        .unwrap_or_else(|| PathBuf::from("."));

    #[cfg(not(target_os = "windows"))]
    let base = std::env::var_os("HOME")
        .map(|p| PathBuf::from(p).join(".gemini"))
        .unwrap_or_else(|| PathBuf::from("."));

    let _ = fs::create_dir_all(&base);
    base.join("mesh_sessions.jsonl")
}

/// Appends one event as a JSONL line to the default session log path.
/// Timestamps are filled in automatically; caller only sets the semantic fields.
pub fn log_session_event(entry: SessionLogEntry) {
    log_session_event_to(&get_session_log_path(), entry);
}

/// Appends one event to a specific path (used in tests).
pub fn log_session_event_to(path: &Path, mut entry: SessionLogEntry) {
    let (ts, ts_unix) = now_ts();
    if entry.ts.is_empty() {
        entry.ts = ts;
        entry.ts_unix = ts_unix;
    }
    if let Ok(line) = serde_json::to_string(&entry) {
        if let Ok(mut f) = fs::OpenOptions::new()
            .create(true)
            .append(true)
            .open(path)
        {
            let _ = writeln!(f, "{}", line);
        }
    }
}

/// Returns the last `limit` entries from the default session log (oldest → newest).
/// Silently returns an empty vec if the file is missing or unreadable.
pub fn read_recent_entries(limit: usize) -> Vec<serde_json::Value> {
    read_recent_from_path(&get_session_log_path(), limit)
}

/// Returns the last `limit` entries from a specific path (used in tests).
pub fn read_recent_from_path(path: &Path, limit: usize) -> Vec<serde_json::Value> {
    let content = match fs::read_to_string(path) {
        Ok(c) => c,
        Err(_) => return vec![],
    };
    let all: Vec<serde_json::Value> = content
        .lines()
        .filter(|l| !l.trim().is_empty())
        .filter_map(|l| serde_json::from_str(l).ok())
        .collect();
    let skip = all.len().saturating_sub(limit);
    all.into_iter().skip(skip).collect()
}

// ─── Private helpers ─────────────────────────────────────────────────────────

fn now_ts() -> (String, u64) {
    let unix = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0);
    (unix_to_iso(unix), unix)
}

/// Formats a Unix timestamp (seconds since 1970-01-01) as ISO 8601 UTC.
/// No external dependencies — pure arithmetic.
pub(crate) fn unix_to_iso(secs: u64) -> String {
    let s = secs % 60;
    let m = (secs / 60) % 60;
    let h = (secs / 3600) % 24;
    let days = secs / 86400;
    let (y, mo, d) = days_to_date(days);
    format!("{:04}-{:02}-{:02}T{:02}:{:02}:{:02}Z", y, mo, d, h, m, s)
}

/// Converts days since 1970-01-01 to (year, month, day).
/// Uses Howard Hinnant's algorithm (public domain, integer-only).
fn days_to_date(z: u64) -> (u64, u64, u64) {
    let z = z as i64 + 719_468;
    let era: i64 = if z >= 0 { z } else { z - 146_096 } / 146_097;
    let doe = (z - era * 146_097) as u64;
    let yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let d = doy - (153 * mp + 2) / 5 + 1;
    let mo = if mp < 10 { mp + 3 } else { mp - 9 };
    let y = yoe + (era * 400) as u64 + u64::from(mo <= 2);
    (y, mo, d)
}

// ─── Tests ───────────────────────────────────────────────────────────────────

#[cfg(test)]
mod tests {
    use super::*;

    // ── Timestamp formatting ──────────────────────────────────────────────

    #[test]
    fn test_unix_to_iso_epoch() {
        assert_eq!(unix_to_iso(0), "1970-01-01T00:00:00Z");
    }

    #[test]
    fn test_unix_to_iso_known_date() {
        // 2024-01-01 00:00:00 UTC
        assert_eq!(unix_to_iso(1_704_067_200), "2024-01-01T00:00:00Z");
    }

    #[test]
    fn test_unix_to_iso_leap_day() {
        // 2024-02-29 00:00:00 UTC (leap year)
        assert_eq!(unix_to_iso(1_709_164_800), "2024-02-29T00:00:00Z");
    }

    #[test]
    fn test_unix_to_iso_end_of_year() {
        // 2023-12-31 23:59:59 UTC
        assert_eq!(unix_to_iso(1_704_067_199), "2023-12-31T23:59:59Z");
    }

    #[test]
    fn test_unix_to_iso_time_components() {
        // 2024-01-01 13:45:30 UTC
        assert_eq!(
            unix_to_iso(1_704_067_200 + 13 * 3600 + 45 * 60 + 30),
            "2024-01-01T13:45:30Z"
        );
    }

    // ── Serialization ─────────────────────────────────────────────────────

    #[test]
    fn test_entry_serializes_correctly() {
        let entry = SessionLogEntry {
            ts: "2026-09-06T19:00:00Z".to_string(),
            ts_unix: 1_757_257_200,
            node: "test-node".to_string(),
            conversation_id: Some("conv-123".to_string()),
            event: "tool_call".to_string(),
            tool_name: Some("view_file".to_string()),
            tool_args: Some(serde_json::json!({"AbsolutePath": "/foo/bar.kt"})),
            ..Default::default()
        };
        let json = serde_json::to_string(&entry).expect("serialize");
        assert!(json.contains(r#""event":"tool_call""#));
        assert!(json.contains(r#""tool_name":"view_file""#));
        // Unset optional fields must be absent (skip_serializing_if)
        assert!(!json.contains("final_response"));
        assert!(!json.contains("response_delta"));
        assert!(!json.contains("returncode"));
    }

    #[test]
    fn test_optional_fields_omitted_when_none() {
        let entry = SessionLogEntry {
            event: "session_start".to_string(),
            node: "n".to_string(),
            question: Some("hello".to_string()),
            ..Default::default()
        };
        let json = serde_json::to_string(&entry).expect("serialize");
        assert!(!json.contains("tool_name"));
        assert!(!json.contains("tool_args"));
        assert!(!json.contains("final_response"));
        assert!(!json.contains("\"status\""));
    }

    // ── JSONL file I/O ────────────────────────────────────────────────────

    #[test]
    fn test_read_recent_missing_file() {
        let entries = read_recent_from_path(
            Path::new("/tmp/__mesh_nonexistent_99999.jsonl"),
            10,
        );
        assert!(entries.is_empty());
    }

    #[test]
    fn test_read_recent_limit() {
        let tmp = std::env::temp_dir().join("mesh_test_limit.jsonl");
        {
            let mut f = fs::File::create(&tmp).unwrap();
            for i in 0u32..20 {
                writeln!(f, r#"{{"ts_unix":{i},"event":"s"}}"#, i = i).unwrap();
            }
        }
        let entries = read_recent_from_path(&tmp, 5);
        assert_eq!(entries.len(), 5);
        assert_eq!(entries[0]["ts_unix"], 15);
        assert_eq!(entries[4]["ts_unix"], 19);
        let _ = fs::remove_file(&tmp);
    }

    #[test]
    fn test_log_and_read_back() {
        let tmp = std::env::temp_dir().join("mesh_test_write.jsonl");
        let _ = fs::remove_file(&tmp);

        log_session_event_to(
            &tmp,
            SessionLogEntry {
                event: "session_start".to_string(),
                node: "unit-test-node".to_string(),
                question: Some("test question".to_string()),
                ..Default::default()
            },
        );

        let entries = read_recent_from_path(&tmp, 10);
        assert_eq!(entries.len(), 1);
        assert_eq!(entries[0]["event"], "session_start");
        assert_eq!(entries[0]["node"], "unit-test-node");
        assert!(entries[0]["ts"].as_str().unwrap().ends_with('Z'));
        assert!(entries[0]["ts_unix"].as_u64().unwrap() > 0);

        let _ = fs::remove_file(&tmp);
    }

    #[test]
    fn test_multiple_events_appended() {
        let tmp = std::env::temp_dir().join("mesh_test_multi.jsonl");
        let _ = fs::remove_file(&tmp);

        for event in &["session_start", "tool_call", "session_end"] {
            log_session_event_to(
                &tmp,
                SessionLogEntry {
                    event: event.to_string(),
                    node: "n".to_string(),
                    ..Default::default()
                },
            );
        }

        let entries = read_recent_from_path(&tmp, 100);
        assert_eq!(entries.len(), 3);
        assert_eq!(entries[0]["event"], "session_start");
        assert_eq!(entries[1]["event"], "tool_call");
        assert_eq!(entries[2]["event"], "session_end");

        let _ = fs::remove_file(&tmp);
    }
}
