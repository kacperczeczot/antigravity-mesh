use axum::{
    extract::{ConnectInfo, Json, State},
    http::{HeaderMap, StatusCode},
    response::IntoResponse,
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
}

#[derive(Clone)]
struct AppState {
    auth_token: Arc<RwLock<String>>,
    node_name: String,
    port: u16,
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
        std::env::var_os("HOME").map(PathBuf::from)
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

#[tokio::main]
async fn main() {
    let cli = Cli::parse();
    let token = load_or_create_token(cli.token, cli.port);

    let node_name = sysinfo::System::host_name().unwrap_or_else(|| "unknown-node".to_string());

    let state = AppState {
        auth_token: Arc::new(RwLock::new(token.clone())),
        node_name: node_name.clone(),
        port: cli.port,
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

    let listener = tokio::net::TcpListener::bind(addr)
        .await
        .expect("Failed to bind TCP listener");

    axum::serve(
        listener,
        app.into_make_service_with_connect_info::<SocketAddr>(),
    )
    .await
    .expect("Server error");
}

async fn handle_root() -> impl IntoResponse {
    Json(json!({
        "message": "Antigravity Mesh Native Daemon (Rust)",
        "version": env!("CARGO_PKG_VERSION"),
        "endpoints": ["GET /health", "GET /system", "POST /query", "POST /exec", "POST /ask", "POST /pair"]
    }))
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

    let flags = if payload.auto_approve {
        "--dangerously-skip-permissions"
    } else {
        ""
    };

    let escaped_q = payload.question.replace('"', "\\\"");
    let cmd = format!("agy {} -p \"{}\"", flags, escaped_q);

    let result = run_shell_command(&cmd, Duration::from_secs(120)).await;
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
        c.args(["-c", cmd]);
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
