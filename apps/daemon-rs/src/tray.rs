use crate::autostart;
use copypasta::{ClipboardContext, ClipboardProvider};
use tray_icon::{
    Icon, TrayIcon, TrayIconBuilder, TrayIconEvent,
    menu::{CheckMenuItem, Menu, MenuEvent, MenuItem, PredefinedMenuItem},
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
    pub pairing_pin: String,
    pub node_name: String,
    pub update_available: Option<String>,
    pub tray_icon: Option<TrayIcon>,
    pub copy_token_id: muda::MenuId,
    pub copy_pin_id: muda::MenuId,
    pub open_web_id: muda::MenuId,
    pub update_id: muda::MenuId,
    pub autostart_id: muda::MenuId,
    pub autostart_item: Option<CheckMenuItem>,
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
        let port_item = MenuItem::new(format!("📡 Port: {}", self.port), false, None);
        let pin_item = MenuItem::new(format!("🔢 PIN parowania: {}", self.pairing_pin), false, None);
        let copy_pin_btn = MenuItem::new("📋 Kopiuj kod PIN", true, None);
        self.copy_pin_id = copy_pin_btn.id().clone();

        let token_preview = if self.token.len() > 10 {
            format!("🔑 Token: {}...", &self.token[..8])
        } else {
            format!("🔑 Token: {}", self.token)
        };
        let token_item = MenuItem::new(token_preview, false, None);

        let copy_token_btn = MenuItem::new("📋 Kopiuj pełny token", true, None);
        self.copy_token_id = copy_token_btn.id().clone();

        let open_web_btn = MenuItem::new("🌐 Open Web Dashboard", true, None);
        self.open_web_id = open_web_btn.id().clone();

        let is_autostart = autostart::is_autostart_enabled();
        let autostart_btn = CheckMenuItem::new(
            "🚀 Uruchamiaj przy starcie (Launch at Login)",
            true,
            is_autostart,
            None,
        );
        self.autostart_id = autostart_btn.id().clone();
        self.autostart_item = Some(autostart_btn.clone());

        let quit_btn = MenuItem::new("❌ Quit Antigravity Mesh", true, None);
        self.quit_id = quit_btn.id().clone();

        let _ = tray_menu.append(&title_item);
        let _ = tray_menu.append(&port_item);
        let _ = tray_menu.append(&pin_item);
        let _ = tray_menu.append(&copy_pin_btn);
        let _ = tray_menu.append(&token_item);
        let _ = tray_menu.append(&copy_token_btn);
        let _ = tray_menu.append(&PredefinedMenuItem::separator());

        if let Some(ref ver) = self.update_available {
            let update_btn = MenuItem::new(format!("✨ Update Available (v{})", ver), true, None);
            self.update_id = update_btn.id().clone();
            let _ = tray_menu.append(&update_btn);
            let _ = tray_menu.append(&PredefinedMenuItem::separator());
        }

        let _ = tray_menu.append(&copy_token_btn);
        let _ = tray_menu.append(&open_web_btn);
        let _ = tray_menu.append(&autostart_btn);
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
                if menu_event.id == self.copy_pin_id {
                    if let Ok(mut ctx) = ClipboardContext::new() {
                        let _ = ctx.set_contents(self.pairing_pin.clone());
                    }
                } else if menu_event.id == self.copy_token_id {
                    if let Ok(mut ctx) = ClipboardContext::new() {
                        let _ = ctx.set_contents(self.token.clone());
                    }
                } else if menu_event.id == self.open_web_id {
                    let url = format!("http://localhost:{}", self.port);
                    let _ = webbrowser::open(&url);
                } else if menu_event.id == self.update_id {
                    let url = "https://github.com/kacperczeczot/antigravity-mesh/releases/latest";
                    let _ = webbrowser::open(url);
                } else if menu_event.id == self.autostart_id {
                    let current = autostart::is_autostart_enabled();
                    let target = !current;
                    let res = autostart::set_autostart(target);
                    if let Some(ref item) = self.autostart_item {
                        if res.is_ok() {
                            item.set_checked(target);
                        } else {
                            item.set_checked(current);
                        }
                    }
                } else if menu_event.id == self.quit_id {
                    event_loop.exit();
                }
            }
            UserEvent::Tray(_) => {}
        }
    }

    fn window_event(&mut self, _event_loop: &ActiveEventLoop, _id: WindowId, _event: WindowEvent) {}
}

pub fn run_tray(
    port: u16,
    token: String,
    pairing_pin: String,
    node_name: String,
    update_available: Option<String>,
) -> Result<(), Box<dyn std::error::Error>> {
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
        pairing_pin,
        node_name,
        update_available,
        tray_icon: None,
        copy_token_id: muda::MenuId::new("copy"),
        copy_pin_id: muda::MenuId::new("copy_pin"),
        open_web_id: muda::MenuId::new("web"),
        update_id: muda::MenuId::new("update"),
        autostart_id: muda::MenuId::new("autostart"),
        autostart_item: None,
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
