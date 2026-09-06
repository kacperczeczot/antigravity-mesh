use std::process::Command;

#[cfg(target_os = "macos")]
pub fn is_autostart_enabled() -> bool {
    let output = Command::new("osascript")
        .arg("-e")
        .arg("tell application \"System Events\" to get name of every login item")
        .output();
    if let Ok(out) = output {
        let text = String::from_utf8_lossy(&out.stdout);
        text.contains("AntigravityMesh")
    } else {
        false
    }
}

#[cfg(target_os = "macos")]
pub fn set_autostart(enable: bool) -> Result<(), String> {
    if enable {
        let app_path = if let Ok(exe) = std::env::current_exe() {
            let s = exe.to_string_lossy().to_string();
            if let Some(pos) = s.find(".app") {
                s[..pos + 4].to_string()
            } else {
                "/Applications/AntigravityMesh.app".to_string()
            }
        } else {
            "/Applications/AntigravityMesh.app".to_string()
        };

        let script = format!(
            r#"tell application "System Events" to make login item at end with properties {{path:"{}", hidden:false}}"#,
            app_path
        );
        let res = Command::new("osascript").arg("-e").arg(&script).output();
        match res {
            Ok(out) if out.status.success() => {
                println!("✅ Registered login item for {}", app_path);
                Ok(())
            }
            Ok(out) => Err(String::from_utf8_lossy(&out.stderr).to_string()),
            Err(e) => Err(e.to_string()),
        }
    } else {
        let script = r#"tell application "System Events" to delete login item "AntigravityMesh""#;
        let res = Command::new("osascript").arg("-e").arg(script).output();
        match res {
            Ok(out) if out.status.success() => {
                println!("🗑️ Removed login item for AntigravityMesh");
                Ok(())
            }
            Ok(out) => Err(String::from_utf8_lossy(&out.stderr).to_string()),
            Err(e) => Err(e.to_string()),
        }
    }
}

#[cfg(target_os = "windows")]
pub fn is_autostart_enabled() -> bool {
    let output = Command::new("reg")
        .args(["query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run", "/v", "AntigravityMesh"])
        .output();
    if let Ok(out) = output {
        out.status.success()
    } else {
        false
    }
}

#[cfg(target_os = "windows")]
pub fn set_autostart(enable: bool) -> Result<(), String> {
    if enable {
        let exe_path = match std::env::current_exe() {
            Ok(p) => p.to_string_lossy().to_string(),
            Err(e) => return Err(e.to_string()),
        };
        let val = format!("\"{}\"", exe_path);
        let res = Command::new("reg")
            .args(["add", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run", "/v", "AntigravityMesh", "/t", "REG_SZ", "/d", &val, "/f"])
            .output();
        match res {
            Ok(out) if out.status.success() => {
                println!("✅ Registered Windows startup registry entry: {}", val);
                Ok(())
            }
            Ok(out) => Err(String::from_utf8_lossy(&out.stderr).to_string()),
            Err(e) => Err(e.to_string()),
        }
    } else {
        let res = Command::new("reg")
            .args(["delete", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run", "/v", "AntigravityMesh", "/f"])
            .output();
        match res {
            Ok(out) if out.status.success() => {
                println!("🗑️ Removed Windows startup registry entry");
                Ok(())
            }
            Ok(out) => Err(String::from_utf8_lossy(&out.stderr).to_string()),
            Err(e) => Err(e.to_string()),
        }
    }
}

#[cfg(not(any(target_os = "macos", target_os = "windows")))]
pub fn is_autostart_enabled() -> bool {
    false
}

#[cfg(not(any(target_os = "macos", target_os = "windows")))]
pub fn set_autostart(_enable: bool) -> Result<(), String> {
    Err("Autostart not supported on this OS".into())
}
