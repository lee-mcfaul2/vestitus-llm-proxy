//! cedar-cabi C ABI: fail-closed, panic-safe runtime shim over pinned cedar-policy.
//! Strings/JSON cross the boundary; Rust owns returned strings (free via
//! `cedar_string_free`). Any null/parse/eval error or panic => non-allow/invalid,
//! never ALLOW, never UB (ADR-001; spec §7 "engine error => deny").

use std::ffi::{c_char, CStr, CString};
use std::ptr;

/// Result codes for the C ABI. Fail-closed: only a clean Cedar `Decision::Allow`
/// (resp. a passing validation) yields `Allow`/`Valid`.
#[repr(C)]
#[derive(Debug, PartialEq, Eq, Clone, Copy)]
pub enum CedarResult {
    Deny = 0,
    Allow = 1,
    Valid = 2,
    Invalid = 3,
    Error = -1,
}

/// Allocate a C string owned by Rust. Returned pointer must be freed with
/// `cedar_string_free`. Returns null only on interior-NUL (then the message is lost).
pub(crate) fn into_c_string(s: &str) -> *mut c_char {
    match CString::new(s) {
        Ok(c) => c.into_raw(),
        Err(_) => ptr::null_mut(),
    }
}

/// Borrow a C string as `&str`. Error (never panic) on null or invalid UTF-8.
pub(crate) fn cstr_in<'a>(p: *const c_char) -> Result<&'a str, String> {
    if p.is_null() {
        return Err("null pointer argument".to_string());
    }
    unsafe { CStr::from_ptr(p) }
        .to_str()
        .map_err(|e| format!("invalid utf-8 in argument: {e}"))
}

/// Run `f` with panic protection. On `Ok(code)` -> that code, no diagnostics.
/// On `Err(msg)` or a panic -> `CedarResult::Error` and `*out_diag` set to an
/// owned C string (caller frees). Fail-closed by construction.
pub(crate) fn ffi_guard<F>(out_diag: &mut *mut c_char, f: F) -> CedarResult
where
    F: FnOnce() -> Result<CedarResult, String> + std::panic::UnwindSafe,
{
    match std::panic::catch_unwind(f) {
        Ok(Ok(code)) => code,
        Ok(Err(msg)) => {
            *out_diag = into_c_string(&msg);
            CedarResult::Error
        }
        Err(panic) => {
            let msg = panic
                .downcast_ref::<&str>()
                .map(|s| s.to_string())
                .or_else(|| panic.downcast_ref::<String>().cloned())
                .unwrap_or_else(|| "unknown".to_string());
            *out_diag = into_c_string(&format!("panic in cedar-cabi: {msg}"));
            CedarResult::Error
        }
    }
}

/// Free a string previously returned by this library. Null is a safe no-op.
///
/// # Safety
/// `s` must be a pointer returned by a `cedar-cabi` function and not freed before.
#[no_mangle]
pub unsafe extern "C" fn cedar_string_free(s: *mut c_char) {
    if s.is_null() {
        return;
    }
    drop(CString::from_raw(s));
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn string_free_handles_null_and_owned() {
        unsafe { cedar_string_free(ptr::null_mut()) };
        let s = into_c_string("hello");
        assert!(!s.is_null());
        unsafe { cedar_string_free(s) };
    }

    #[test]
    fn guard_catches_panic_as_error() {
        let mut diag: *mut c_char = ptr::null_mut();
        let code = ffi_guard(&mut diag, || panic!("boom"));
        assert_eq!(code, CedarResult::Error);
        assert!(!diag.is_null());
        let msg = unsafe { CStr::from_ptr(diag) }.to_string_lossy().into_owned();
        assert!(msg.contains("panic"), "diag should mention panic, got: {msg}");
        unsafe { cedar_string_free(diag) };
    }

    #[test]
    fn guard_passes_through_ok_and_sets_no_diag() {
        let mut diag: *mut c_char = ptr::null_mut();
        let code = ffi_guard(&mut diag, || Ok(CedarResult::Allow));
        assert_eq!(code, CedarResult::Allow);
        assert!(diag.is_null(), "no diagnostics on success");
    }

    #[test]
    fn cstr_in_rejects_null() {
        assert!(cstr_in(ptr::null()).is_err());
        let s = into_c_string("x");
        assert_eq!(cstr_in(s).unwrap(), "x");
        unsafe { cedar_string_free(s) };
    }
}
