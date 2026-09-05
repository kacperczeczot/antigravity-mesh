use copypasta::{ClipboardContext, ClipboardProvider};
use tray_icon::{
    menu::{Menu, MenuEvent, MenuItem, PredefinedMenuItem},
    Icon, TrayIcon, TrayIconBuilder, TrayIconEvent,
};
use winit::{
    application::ApplicationHandler,
    event::WindowEvent,
    event_loop::{ActiveEventLoop, ControlFlow, EventLoop},
    window::WindowId,
};

#[derive(Debug)]
pub enum UserEvent {
    #[allow(dead_code)]
    Tray(TrayIconEvent),
    Menu(MenuEvent),
}

pub struct TrayApp {
    pub port: u16,
    pub token: String,
    pub node_name: String,
    pub tray_icon: Option<TrayIcon>,
    pub copy_token_id: muda::MenuId,
    pub open_web_id: muda::MenuId,
    pub quit_id: muda::MenuId,
}

impl ApplicationHandler<UserEvent> for TrayApp {
    fn resumed(&mut self, _event_loop: &ActiveEventLoop) {
        if self.tray_icon.is_some() {
            return;
        }

        let tray_menu = Menu::new();

        let title_item = MenuItem::new(
            format!("🟢 Antigravity Mesh ({})", self.node_name),
            false,
            None,
        );
        let port_item = MenuItem::new(
            format!("📡 Port: {}", self.port),
            false,
            None,
        );
        let token_preview = if self.token.len() > 10 {
            format!("🔑 Token: {}...", &self.token[..8])
        } else {
            format!("🔑 Token: {}", self.token)
        };
        let token_item = MenuItem::new(token_preview, false, None);

        let copy_token_btn = MenuItem::new("📋 Copy Token to Clipboard", true, None);
        self.copy_token_id = copy_token_btn.id().clone();

        let open_web_btn = MenuItem::new("🌐 Open Web Dashboard", true, None);
        self.open_web_id = open_web_btn.id().clone();

        let quit_btn = MenuItem::new("❌ Quit Antigravity Mesh", true, None);
        self.quit_id = quit_btn.id().clone();

        let _ = tray_menu.append(&title_item);
        let _ = tray_menu.append(&port_item);
        let _ = tray_menu.append(&token_item);
        let _ = tray_menu.append(&PredefinedMenuItem::separator());
        let _ = tray_menu.append(&copy_token_btn);
        let _ = tray_menu.append(&open_web_btn);
        let _ = tray_menu.append(&PredefinedMenuItem::separator());
        let _ = tray_menu.append(&quit_btn);

        let icon = create_tray_icon();

        let mut builder = TrayIconBuilder::new()
            .with_menu(Box::new(tray_menu))
            .with_tooltip(format!("Antigravity Mesh ({})", self.node_name));

        if let Some(ic) = icon {
            builder = builder.with_icon(ic);
        }

        match builder.build() {
            Ok(icon) => {
                println!("✅ System Tray icon created successfully!");
                self.tray_icon = Some(icon);
            }
            Err(e) => {
                eprintln!("❌ Failed to create System Tray icon: {}", e);
            }
        }
    }

    fn user_event(&mut self, event_loop: &ActiveEventLoop, event: UserEvent) {
        match event {
            UserEvent::Menu(menu_event) => {
                if menu_event.id == self.copy_token_id {
                    if let Ok(mut ctx) = ClipboardContext::new() {
                        let _ = ctx.set_contents(self.token.clone());
                    }
                } else if menu_event.id == self.open_web_id {
                    let url = format!("http://localhost:{}", self.port);
                    let _ = webbrowser::open(&url);
                } else if menu_event.id == self.quit_id {
                    event_loop.exit();
                }
            }
            UserEvent::Tray(_) => {}
        }
    }

    fn window_event(&mut self, _event_loop: &ActiveEventLoop, _id: WindowId, _event: WindowEvent) {}
}

pub fn run_tray(port: u16, token: String, node_name: String) -> Result<(), Box<dyn std::error::Error>> {
    let event_loop = EventLoop::<UserEvent>::with_user_event().build()?;
    event_loop.set_control_flow(ControlFlow::Wait);

    let proxy = event_loop.create_proxy();
    let proxy_tray = proxy.clone();
    TrayIconEvent::set_event_handler(Some(move |event| {
        let _ = proxy_tray.send_event(UserEvent::Tray(event));
    }));

    MenuEvent::set_event_handler(Some(move |event| {
        let _ = proxy.send_event(UserEvent::Menu(event));
    }));

    let mut app = TrayApp {
        port,
        token,
        node_name,
        tray_icon: None,
        copy_token_id: muda::MenuId::new("copy"),
        open_web_id: muda::MenuId::new("web"),
        quit_id: muda::MenuId::new("quit"),
    };

    event_loop.run_app(&mut app)?;
    Ok(())
}

fn create_tray_icon() -> Option<Icon> {
    const ICON_RGBA: &[u8] = include_bytes!("../assets/icon_32.rgba");
    match Icon::from_rgba(ICON_RGBA.to_vec(), 32, 32) {
        Ok(icon) => {
            println!("🎨 Tray icon decoded successfully (32x32 RGBA)");
            Some(icon)
        }
        Err(e) => {
            eprintln!("❌ Failed to decode tray icon RGBA: {}", e);
            None
        }
    }
}
