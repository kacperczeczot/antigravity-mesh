//! Power management module for Antigravity Mesh
//!
//! Prevents system idle sleep while the node daemon is running, ensuring the machine
//! remains reachable over the network (LAN/Tailscale/VPN) even when the display is off.

#[cfg(target_os = "macos")]
pub mod platform {
    use std::ffi::{c_char, c_void, CString};

    #[repr(C)]
    struct __CFString(c_void);
    type CFStringRef = *const __CFString;

    #[link(name = "CoreFoundation", kind = "framework")]
    unsafe extern "C" {
        fn CFStringCreateWithCString(
            alloc: *const c_void,
            c_str: *const c_char,
            encoding: u32,
        ) -> CFStringRef;
        fn CFRelease(cf: *const c_void);
    }

    #[link(name = "IOKit", kind = "framework")]
    unsafe extern "C" {
        fn IOPMAssertionCreateWithName(
            assertion_type: CFStringRef,
            assertion_level: u32,
            assertion_name: CFStringRef,
            assertion_id: *mut u32,
        ) -> i32;
        fn IOPMAssertionRelease(assertion_id: u32) -> i32;
    }

    const K_CF_STRING_ENCODING_UTF8: u32 = 0x08000100;
    const K_IOPM_ASSERTION_LEVEL_ON: u32 = 255;

    pub struct SleepAssertion {
        id: u32,
    }

    impl SleepAssertion {
        pub fn acquire(reason: &str) -> Option<Self> {
            unsafe {
                let type_cstr = CString::new("PreventUserIdleSystemSleep").ok()?;
                let name_cstr = CString::new(reason).ok()?;

                let type_cf = CFStringCreateWithCString(
                    std::ptr::null(),
                    type_cstr.as_ptr(),
                    K_CF_STRING_ENCODING_UTF8,
                );
                let name_cf = CFStringCreateWithCString(
                    std::ptr::null(),
                    name_cstr.as_ptr(),
                    K_CF_STRING_ENCODING_UTF8,
                );

                if type_cf.is_null() || name_cf.is_null() {
                    if !type_cf.is_null() {
                        CFRelease(type_cf as _);
                    }
                    if !name_cf.is_null() {
                        CFRelease(name_cf as _);
                    }
                    return None;
                }

                let mut assertion_id: u32 = 0;
                let ret = IOPMAssertionCreateWithName(
                    type_cf,
                    K_IOPM_ASSERTION_LEVEL_ON,
                    name_cf,
                    &mut assertion_id,
                );

                CFRelease(type_cf as _);
                CFRelease(name_cf as _);

                if ret == 0 {
                    println!(
                        "🔋 [macOS Power] Zarejestrowano asercję: PreventUserIdleSystemSleep (ID={}) - Mac nie zaśnie w tle!",
                        assertion_id
                    );
                    Some(SleepAssertion { id: assertion_id })
                } else {
                    eprintln!("⚠️ [macOS Power] Nie udało się zarejestrować asercji zasilania (ret={})", ret);
                    None
                }
            }
        }
    }

    impl Drop for SleepAssertion {
        fn drop(&mut self) {
            unsafe {
                let ret = IOPMAssertionRelease(self.id);
                if ret == 0 {
                    println!("🔋 [macOS Power] Pomyślnie zwolniono asercję zasilania (ID={})", self.id);
                }
            }
        }
    }
}

#[cfg(target_os = "windows")]
pub mod platform {
    unsafe extern "system" {
        fn SetThreadExecutionState(es_flags: u32) -> u32;
    }

    const ES_CONTINUOUS: u32 = 0x80000000;
    const ES_SYSTEM_REQUIRED: u32 = 0x00000001;

    pub struct SleepAssertion;

    impl SleepAssertion {
        pub fn acquire(_reason: &str) -> Option<Self> {
            unsafe {
                let prev = SetThreadExecutionState(ES_CONTINUOUS | ES_SYSTEM_REQUIRED);
                if prev != 0 {
                    println!("🔋 [Windows Power] Aktywowano ES_SYSTEM_REQUIRED - system nie zaśnie przy bezczynności!");
                    Some(SleepAssertion)
                } else {
                    eprintln!("⚠️ [Windows Power] Nie udało się ustawić stanu SetThreadExecutionState");
                    None
                }
            }
        }
    }

    impl Drop for SleepAssertion {
        fn drop(&mut self) {
            unsafe {
                SetThreadExecutionState(ES_CONTINUOUS);
                println!("🔋 [Windows Power] Przywrócono domyślny stan zasilania (ES_CONTINUOUS)");
            }
        }
    }
}

#[cfg(not(any(target_os = "macos", target_os = "windows")))]
pub mod platform {
    pub struct SleepAssertion;

    impl SleepAssertion {
        pub fn acquire(_reason: &str) -> Option<Self> {
            // On Linux systems, sleep prevention is typically managed by systemd-inhibit or desktop managers
            None
        }
    }
}

pub use platform::SleepAssertion;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_sleep_assertion_lifecycle() {
        let assertion = SleepAssertion::acquire("Antigravity Mesh Unit Test");
        #[cfg(any(target_os = "macos", target_os = "windows"))]
        assert!(assertion.is_some(), "Powinno udać się zarejestrować asercję zasilania na macOS/Windows");
        drop(assertion);
    }
}
