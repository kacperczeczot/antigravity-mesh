mod tray;

use axum::{
    extract::{ConnectInfo, Json, State},
    http::{HeaderMap, StatusCode},
    response::{Html, IntoResponse},
    routing::{get, post},
    Router,
};
use clap::Parser;
use rand::{distributions::Alphanumeric, Rng};
use serde::Deserialize;
use serde_json::json;

use std::{
    collections::HashMap,
    fs,
    net::{IpAddr, SocketAddr},
    path::PathBuf,
    process::Stdio,
    sync::Arc,
    time::Duration,
};
use sysinfo::{Disks, System};
use tokio::{process::Command, sync::RwLock, time::timeout};
use tower_http::cors::CorsLayer;
use walkdir::WalkDir;

#[derive(Parser, Debug)]
#[command(name = "agy-mesh-daemon", about = "Antigravity Mesh Native Node Daemon")]
struct Cli {
    #[arg(long, default_value = "0.0.0.0")]
    host: String,

    #[arg(long, default_value_t = 8888)]
    port: u16,

    #[arg(long)]
    token: Option<String>,

    #[arg(long)]
    no_tray: bool,

    /// Path to AI CLI binary (agy, gemini, claude, etc.). Auto-detected if not specified.
    #[arg(long)]
    agy_path: Option<String>,
}

#[derive(Clone)]
struct AppState {
    auth_token: Arc<RwLock<String>>,
    node_name: String,
    port: u16,
    /// Resolved path to AI CLI binary (agy, gemini, claude, etc.), or None if not found.
    agy_cli_path: Option<String>,
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
    if let Ok(content) = fs::read_to_string(&config_path) {
        if let Ok(nodes) = serde_json::from_str::<HashMap<String, serde_json::Value>>(&content) {
            for key in ["local", "local-node", "local-mac", "local-win", "self"] {
                if let Some(node) = nodes.get(key) {
                    if let Some(token) = node.get("token").and_then(|v| v.as_str()) {
                        return token.to_string();
                    }
                }
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
    if let Ok(content) = fs::read_to_string(&config_path) {
        if let Ok(existing) = serde_json::from_str(&content) {
            nodes = existing;
        }
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
    let _ = fs::write(&config_path, serde_json::to_string_pretty(&nodes).unwrap_or_default());

    new_token
}

fn save_paired_node(node_name: &str, host: &str, port: u16, token: &str) {
    let config_path = get_config_path();
    let mut nodes: HashMap<String, serde_json::Value> = HashMap::new();
    if let Ok(content) = fs::read_to_string(&config_path) {
        if let Ok(existing) = serde_json::from_str(&content) {
            nodes = existing;
        }
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
    let _ = fs::write(&config_path, serde_json::to_string_pretty(&nodes).unwrap_or_default());
}

fn is_private_ip(ip: IpAddr) -> bool {
    match ip {
        IpAddr::V4(ipv4) => ipv4.is_loopback() || ipv4.is_private() || ipv4.is_link_local(),
        IpAddr::V6(ipv6) => ipv6.is_loopback(),
    }
}

async fn verify_auth(headers: &HeaderMap, state: &AppState) -> bool {
    let expected = state.auth_token.read().await;
    if expected.is_empty() {
        return true;
    }

    if let Some(token) = headers.get("X-Mesh-Token") {
        if let Ok(t) = token.to_str() {
            if t.trim() == *expected {
                return true;
            }
        }
    }

    if let Some(auth) = headers.get("Authorization") {
        if let Ok(a) = auth.to_str() {
            let stripped = a.replace("Bearer ", "");
            if stripped.trim() == *expected {
                return true;
            }
        }
    }

    false
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

        if let Ok(output) = std::process::Command::new(finder).arg(path).output() {
            if output.status.success() {
                let found = String::from_utf8_lossy(&output.stdout).lines().next().unwrap_or("").trim().to_string();
                if !found.is_empty() && std::path::Path::new(&found).exists() {
                    return Some(found);
                }
            }
        }
        eprintln!("⚠️  Specified --agy-path '{}' not found, falling back to auto-detection", path);
    }

    #[cfg(windows)]
    let finder = "where.exe";
    #[cfg(not(windows))]
    let finder = "which";

    for name in &["agy", "gemini", "claude"] {
        if let Ok(output) = std::process::Command::new(finder).arg(name).output() {
            if output.status.success() {
                let found = String::from_utf8_lossy(&output.stdout).lines().next().unwrap_or("").trim().to_string();
                if !found.is_empty() && std::path::Path::new(&found).exists() {
                    println!("🔍 Found AI CLI: {}", found);
                    return Some(found);
                }
            }
        }
    }

    #[cfg(windows)]
    let cli_names = [
        "agy.exe", "agy.cmd", "agy.bat", "agy",
        "gemini.exe", "gemini.cmd", "gemini.bat", "gemini",
        "claude.exe", "claude.cmd", "claude.bat", "claude"
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
        search_dirs.push(home.join(".antigravity-ide").join("antigravity-ide").join("bin"));
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

fn main() {
    let cli = Cli::parse();
    let token = load_or_create_token(cli.token, cli.port);
    let node_name = sysinfo::System::host_name().unwrap_or_else(|| "unknown-node".to_string());
    let agy_cli_path = discover_agy_cli(cli.agy_path);

    let state = AppState {
        auth_token: Arc::new(RwLock::new(token.clone())),
        node_name: node_name.clone(),
        port: cli.port,
        agy_cli_path: agy_cli_path.clone(),
    };

    let app = Router::new()
        .route("/", get(handle_root))
        .route("/health", get(handle_health))
        .route("/system", get(handle_system))
        .route("/query", post(handle_query))
        .route("/exec", post(handle_exec))
        .route("/ask", post(handle_ask))
        .route("/pair", post(handle_pair))
        .layer(CorsLayer::permissive())
        .with_state(state);

    let addr: SocketAddr = format!("{}:{}", cli.host, cli.port)
        .parse()
        .expect("Invalid host/port");

    println!("🚀 Antigravity Mesh Native Daemon (Rust) listening on {}", addr);
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
        if let Err(e) = tray::run_tray(cli.port, token, node_name) {
            eprintln!("Tray error: {}, keeping server thread running", e);
            let _ = server_handle.join();
        }
    } else {
        let _ = server_handle.join();
    }
}

async fn handle_root(headers: HeaderMap, State(state): State<AppState>) -> impl IntoResponse {
    if let Some(accept) = headers.get("Accept") {
        if let Ok(accept_str) = accept.to_str() {
            if accept_str.contains("text/html") {
                let token = state.auth_token.read().await.clone();
                let html = format!(
                    r#"<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Antigravity Mesh Node - {node}</title>
    <style>
        :root {{
            --bg: #0d1117;
            --card-bg: rgba(22, 27, 34, 0.85);
            --border: rgba(255, 255, 255, 0.1);
            --primary: #58a6ff;
            --success: #3fb950;
            --text: #c9d1d9;
            --text-bright: #ffffff;
            --code-bg: #161b22;
        }}
        body {{
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            background: linear-gradient(135deg, #090d16 0%, #161b22 100%);
            color: var(--text);
            margin: 0;
            padding: 40px 20px;
            display: flex;
            justify-content: center;
            align-items: center;
            min-height: 100vh;
            box-sizing: border-box;
        }}
        .card {{
            background: var(--card-bg);
            border: 1px solid var(--border);
            border-radius: 16px;
            box-shadow: 0 20px 40px rgba(0,0,0,0.5);
            backdrop-filter: blur(12px);
            padding: 36px;
            max-width: 580px;
            width: 100%;
        }}
        .header {{
            display: flex;
            align-items: center;
            gap: 16px;
            margin-bottom: 24px;
        }}
        .status-dot {{
            width: 16px;
            height: 16px;
            background-color: var(--success);
            border-radius: 50%;
            box-shadow: 0 0 12px var(--success);
            display: inline-block;
        }}
        h1 {{
            font-size: 24px;
            margin: 0;
            color: var(--text-bright);
        }}
        .badge {{
            background: rgba(88, 166, 255, 0.15);
            color: var(--primary);
            border: 1px solid rgba(88, 166, 255, 0.3);
            border-radius: 6px;
            padding: 2px 8px;
            font-size: 12px;
            font-weight: 600;
        }}
        .info-row {{
            margin-bottom: 18px;
        }}
        .info-label {{
            font-size: 12px;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            color: #8b949e;
            margin-bottom: 6px;
        }}
        .info-val {{
            font-size: 16px;
            font-weight: 500;
            color: var(--text-bright);
        }}
        .token-box {{
            background: var(--code-bg);
            border: 1px solid var(--border);
            border-radius: 8px;
            padding: 12px 14px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, monospace;
            font-size: 14px;
            color: #7ee787;
            word-break: break-all;
            margin-top: 6px;
        }}
        button {{
            background: #238636;
            color: #ffffff;
            border: none;
            border-radius: 6px;
            padding: 8px 14px;
            font-size: 13px;
            font-weight: 600;
            cursor: pointer;
            transition: background 0.2s;
            white-space: nowrap;
            margin-left: 10px;
        }}
        button:hover {{
            background: #2ea043;
        }}
        .endpoints {{
            margin-top: 24px;
            padding-top: 20px;
            border-top: 1px solid var(--border);
        }}
        .endpoint-tag {{
            display: inline-block;
            background: rgba(255, 255, 255, 0.05);
            border-radius: 4px;
            padding: 4px 8px;
            margin: 4px 4px 4px 0;
            font-size: 12px;
            font-family: monospace;
            color: var(--text);
        }}
    </style>
</head>
<body>
    <div class="card">
        <div class="header">
            <span class="status-dot"></span>
            <h1>Antigravity Mesh Node</h1>
            <span class="badge">v{ver}</span>
        </div>
        <div class="info-row">
            <div class="info-label">Node Name</div>
            <div class="info-val">{node}</div>
        </div>
        <div class="info-row">
            <div class="info-label">Listening Port</div>
            <div class="info-val">{port}</div>
        </div>
        <div class="info-row">
            <div class="info-label">Authentication Token</div>
            <div class="token-box">
                <span id="tok">{token}</span>
                <button onclick="navigator.clipboard.writeText('{token}'); this.innerText='Copied!'; setTimeout(()=>this.innerText='Copy', 2000);">Copy</button>
            </div>
        </div>
        <div class="endpoints">
            <div class="info-label">Available Endpoints</div>
            <span class="endpoint-tag">GET /health</span>
            <span class="endpoint-tag">GET /system</span>
            <span class="endpoint-tag">POST /query</span>
            <span class="endpoint-tag">POST /exec</span>
            <span class="endpoint-tag">POST /ask</span>
            <span class="endpoint-tag">POST /pair</span>
        </div>
    </div>
</body>
</html>"#,
                    node = state.node_name,
                    ver = env!("CARGO_PKG_VERSION"),
                    port = state.port,
                    token = token,
                );
                return Html(html).into_response();
            }
        }
    }

    Json(json!({
        "message": "Antigravity Mesh Native Daemon (Rust)",
        "version": env!("CARGO_PKG_VERSION"),
        "endpoints": ["GET /health", "GET /system", "POST /query", "POST /exec", "POST /ask", "POST /pair"]
    })).into_response()
}

async fn handle_health(
    headers: HeaderMap,
    State(state): State<AppState>,
) -> Result<impl IntoResponse, StatusCode> {
    if !verify_auth(&headers, &state).await {
        return Err(StatusCode::UNAUTHORIZED);
    }

    let os_name = sysinfo::System::name().unwrap_or_else(|| std::env::consts::OS.to_string());

    Ok(Json(json!({
        "status": "ok",
        "platform": os_name,
        "node": state.node_name,
        "port": state.port,
        "engine": "rust-native"
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
    let cpu_brand = cpus.first().map(|c| c.brand().to_string()).unwrap_or_default();
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

    Ok(Json(json!({
        "node_name": state.node_name,
        "os_name": System::name().unwrap_or_default(),
        "os_version": System::os_version().unwrap_or_default(),
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
        "disks": disk_list,
        "cwd": cwd,
        "engine": "rust-native"
    })))
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
    2
}

async fn handle_query(
    headers: HeaderMap,
    State(state): State<AppState>,
    Json(payload): Json<QueryRequest>,
) -> Result<impl IntoResponse, StatusCode> {
    if !verify_auth(&headers, &state).await {
        return Err(StatusCode::UNAUTHORIZED);
    }

    let target_path = PathBuf::from(&payload.path);
    if !target_path.exists() {
        return Ok(Json(json!({
            "error": format!("Path '{}' does not exist", payload.path)
        })));
    }

    let mut items = Vec::new();
    let walker = WalkDir::new(&target_path)
        .max_depth(payload.max_depth)
        .into_iter()
        .filter_map(|e| e.ok());

    for entry in walker {
        if entry.depth() == 0 {
            continue;
        }
        let file_type = if entry.file_type().is_dir() {
            "dir"
        } else {
            "file"
        };
        let size = entry.metadata().map(|m| m.len()).unwrap_or(0);
        let full_p = entry.path().to_string_lossy().to_string();
        let name = entry.file_name().to_string_lossy().to_string();

        items.push(json!({
            "name": name,
            "type": file_type,
            "size": size,
            "path": full_p
        }));

        if items.len() >= 300 {
            break;
        }
    }

    Ok(Json(json!({
        "path": payload.path,
        "count": items.len(),
        "items": items
    })))
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
    question: String,
    #[serde(default = "default_auto_approve")]
    auto_approve: bool,
}

fn default_auto_approve() -> bool {
    true
}

async fn handle_ask(
    headers: HeaderMap,
    State(state): State<AppState>,
    Json(payload): Json<AskRequest>,
) -> Result<impl IntoResponse, StatusCode> {
    if !verify_auth(&headers, &state).await {
        return Err(StatusCode::UNAUTHORIZED);
    }

    let cli_path = match &state.agy_cli_path {
        Some(p) => p.clone(),
        None => match discover_agy_cli(None) {
            Some(discovered) => discovered,
            None => {
                return Ok(Json(json!({
                    "error": "No AI CLI (agy, gemini, claude) is installed or found on this node. Install one and restart the daemon, or use --agy-path to specify the binary location.",
                    "returncode": -1,
                    "hint": "Install Google Antigravity CLI: curl -fsSL https://antigravity.google/cli/install.sh | bash"
                })));
            }
        },
    };

    let mut process = Command::new(&cli_path);
    if payload.auto_approve {
        process.arg("--dangerously-skip-permissions");
    }
    process.arg("--print");
    process.arg(&payload.question);
    process.stdout(Stdio::piped()).stderr(Stdio::piped());

    let dur = Duration::from_secs(120);
    let result = match timeout(dur, process.output()).await {
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
            "returncode": -1,
            "error": format!("Failed to execute process: {}", e)
        }),
        Err(_) => json!({
            "returncode": -1,
            "error": format!("Command timed out after {} seconds", dur.as_secs())
        }),
    };

    Ok(Json(result))
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
}

async fn handle_pair(
    ConnectInfo(addr): ConnectInfo<SocketAddr>,
    State(state): State<AppState>,
    Json(payload): Json<PairRequest>,
) -> Result<impl IntoResponse, (StatusCode, Json<serde_json::Value>)> {
    if !is_private_ip(addr.ip()) {
        return Err((
            StatusCode::FORBIDDEN,
            Json(json!({"error": "Pairing only allowed from private LAN"})),
        ));
    }

    let remote_ip = addr.ip().to_string();
    let remote_host = payload.host.unwrap_or(remote_ip.clone());
    let remote_port = payload.port.unwrap_or(8888);
    let remote_name = payload
        .node_name
        .unwrap_or_else(|| format!("node-{}", remote_ip.replace('.', "-")));

    save_paired_node(&remote_name, &remote_host, remote_port, &payload.token);

    let my_token = state.auth_token.read().await.clone();
    let os_name = sysinfo::System::name().unwrap_or_else(|| std::env::consts::OS.to_string());

    Ok(Json(json!({
        "status": "paired",
        "node_name": state.node_name,
        "token": my_token,
        "platform": os_name
    })))
}
