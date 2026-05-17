//! cedar-cabi C ABI: fail-closed, panic-safe runtime shim over pinned cedar-policy.
//! Strings/JSON cross the boundary; Rust owns returned strings (free via
//! `cedar_string_free`). Any null/parse/eval error or panic => non-allow/invalid,
//! never ALLOW, never UB (ADR-001; spec §7 "engine error => deny").

use std::ffi::{c_char, CStr, CString};
use std::ptr;
use cedar_policy::{Authorizer, Context, Decision, Entities, EntityUid, PolicySet, Request, Schema, ValidationMode, Validator};
use std::str::FromStr;

// The C entrypoints rely on `catch_unwind` as the fail-closed boundary, which
// is a silent no-op under `panic = "abort"`. A future build profile that sets
// abort would invisibly break that guarantee, so refuse to compile in it.
#[cfg(panic = "abort")]
compile_error!("cedar-cabi requires panic=unwind: catch_unwind is the fail-closed boundary");

/// Result codes for the C ABI. Fail-closed: only a clean Cedar `Decision::Allow`
/// (resp. a passing validation) yields `Allow`/`Valid`.
#[repr(i32)]
#[derive(Debug, PartialEq, Eq, Clone, Copy)]
pub enum CedarResult {
    Deny = 0,
    Allow = 1,
    Valid = 2,
    Invalid = 3,
    Error = -1,
}

/// Allocate a C string owned by Rust. Returned pointer must be freed with
/// `cedar_string_free`. Never returns null for a real message: interior NUL
/// bytes are scrubbed to `?` so a fail-closed diagnostic is never silently
/// dropped (spec §6 inv.7 — every fail-closed branch must stay observable).
pub(crate) fn into_c_string(s: &str) -> *mut c_char {
    let sanitized = s.replace('\0', "?");
    match CString::new(sanitized) {
        Ok(c) => c.into_raw(),
        // Unreachable in practice (NUL scrubbed above); stay fail-safe regardless.
        Err(_) => ptr::null_mut(),
    }
}

/// Copy a C string into an owned `String`. Error (never panic) on null or
/// invalid UTF-8. Returns an owned value by design: the unsafe borrow is
/// confined here and never escapes with a caller-chosen lifetime.
pub(crate) fn cstr_in(p: *const c_char) -> Result<String, String> {
    if p.is_null() {
        return Err("null pointer argument".to_string());
    }
    unsafe { CStr::from_ptr(p) }
        .to_str()
        .map(|s| s.to_owned())
        .map_err(|e| format!("invalid utf-8 in argument: {e}"))
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

/// Authorize one request. Fail-closed: returns `Allow` (1) ONLY on a clean
/// Cedar `Decision::Allow`; `Deny` (0) on a clean deny (reasons/errors in
/// `*out_diag`); `Error` (-1) on any null arg, parse/validation/eval failure,
/// or panic (message in `*out_diag`). The non-Allow diagnostics string lets the
/// caller log + emit a reason-labeled metric (spec §6 inv.7; §7 "engine error
/// => deny, never pass-through"). `context_json` is a JSON object (`{}` = none);
/// `entities_json` is a JSON array (`[]` = none).
///
/// # Safety
/// All pointers must be valid NUL-terminated C strings (or null, which is a
/// fail-closed Error). `out_diag` must be a valid pointer to a writable pointer.
#[no_mangle]
pub unsafe extern "C" fn cedar_is_authorized(
    policies: *const c_char,
    principal: *const c_char,
    action: *const c_char,
    resource: *const c_char,
    context_json: *const c_char,
    entities_json: *const c_char,
    out_diag: *mut *mut c_char,
) -> CedarResult {
    if out_diag.is_null() {
        return CedarResult::Error;
    }
    let out = &mut *out_diag;
    *out = ptr::null_mut();

    // Copy borrowed inputs into owned Strings BEFORE catch_unwind
    // (raw ptrs are not UnwindSafe; owned String is).
    let inputs = (|| {
        Ok::<_, String>((
            cstr_in(policies)?,
            cstr_in(principal)?,
            cstr_in(action)?,
            cstr_in(resource)?,
            cstr_in(context_json)?,
            cstr_in(entities_json)?,
        ))
    })();
    let (pol, prin, act, res, ctx, ents) = match inputs {
        Ok(v) => v,
        Err(e) => {
            *out = into_c_string(&e);
            return CedarResult::Error;
        }
    };

    let result: Result<(CedarResult, Option<String>), String> =
        std::panic::catch_unwind(std::panic::AssertUnwindSafe(move || {
            let policy_set =
                PolicySet::from_str(&pol).map_err(|e| format!("policy parse error: {e}"))?;
            let principal_uid =
                EntityUid::from_str(&prin).map_err(|e| format!("principal uid error: {e}"))?;
            let action_uid =
                EntityUid::from_str(&act).map_err(|e| format!("action uid error: {e}"))?;
            let resource_uid =
                EntityUid::from_str(&res).map_err(|e| format!("resource uid error: {e}"))?;
            let context = Context::from_json_str(&ctx, None)
                .map_err(|e| format!("context json error: {e}"))?;
            let entities = Entities::from_json_str(&ents, None)
                .map_err(|e| format!("entities json error: {e}"))?;
            let request =
                Request::new(principal_uid, action_uid, resource_uid, context, None)
                    .map_err(|e| format!("request error: {e}"))?;
            let response =
                Authorizer::new().is_authorized(&request, &policy_set, &entities);
            match response.decision() {
                Decision::Allow => Ok((CedarResult::Allow, None)),
                Decision::Deny => {
                    let reasons: Vec<String> =
                        response.diagnostics().reason().map(|p| p.to_string()).collect();
                    let errs: Vec<String> =
                        response.diagnostics().errors().map(|e| e.to_string()).collect();
                    let mut msg = String::from("deny");
                    if !reasons.is_empty() {
                        msg.push_str(&format!("; reasons=[{}]", reasons.join(",")));
                    }
                    if !errs.is_empty() {
                        msg.push_str(&format!("; errors=[{}]", errs.join("; ")));
                    }
                    Ok((CedarResult::Deny, Some(msg)))
                }
            }
        }))
        .unwrap_or_else(|p| {
            let m = p
                .downcast_ref::<&str>()
                .map(|s| s.to_string())
                .or_else(|| p.downcast_ref::<String>().cloned())
                .unwrap_or_else(|| "unknown".to_string());
            Err(format!("panic in cedar-cabi: {m}"))
        });

    match result {
        Ok((CedarResult::Allow, _)) => CedarResult::Allow,
        Ok((CedarResult::Deny, diag)) => {
            if let Some(d) = diag {
                *out = into_c_string(&d);
            }
            CedarResult::Deny
        }
        Ok((other, _)) => {
            *out = into_c_string(&format!("unexpected code {other:?} (fail-closed)"));
            CedarResult::Error
        }
        Err(msg) => {
            *out = into_c_string(&msg);
            CedarResult::Error
        }
    }
}

/// Validate a Cedar policy set against a schema (registration-time gate).
/// Fail-closed: `Valid` (2) ONLY when validation passes cleanly; `Invalid` (3)
/// with the validation errors in `*out_diag`; `Error` (-1) on null args, an
/// unparseable schema/policy, or a panic (message in `*out_diag`). The non-Valid
/// diagnostics string lets the caller log + emit a reason-labeled metric
/// (spec §6 inv.7). `schema_src` is Cedar human schema syntax (`.cedarschema`).
///
/// # Safety
/// Pointers must be valid NUL-terminated C strings (or null => Error).
/// `out_diag` must point to a writable pointer.
#[no_mangle]
pub unsafe extern "C" fn cedar_validate(
    schema_src: *const c_char,
    policies_src: *const c_char,
    out_diag: *mut *mut c_char,
) -> CedarResult {
    if out_diag.is_null() {
        return CedarResult::Error;
    }
    let out = &mut *out_diag;
    *out = ptr::null_mut();

    let inputs = (|| {
        Ok::<_, String>((cstr_in(schema_src)?, cstr_in(policies_src)?))
    })();
    let (schema_s, pol_s) = match inputs {
        Ok(v) => v,
        Err(e) => {
            *out = into_c_string(&e);
            return CedarResult::Error;
        }
    };

    let result: Result<(CedarResult, Option<String>), String> =
        std::panic::catch_unwind(std::panic::AssertUnwindSafe(move || {
            let schema =
                Schema::from_str(&schema_s).map_err(|e| format!("schema parse error: {e}"))?;
            let pset =
                PolicySet::from_str(&pol_s).map_err(|e| format!("policy parse error: {e}"))?;
            let vr = Validator::new(schema).validate(&pset, ValidationMode::default());
            if vr.validation_passed() {
                Ok((CedarResult::Valid, None))
            } else {
                let msgs: Vec<String> =
                    vr.validation_errors().map(|e| e.to_string()).collect();
                Ok((CedarResult::Invalid, Some(msgs.join("; "))))
            }
        }))
        .unwrap_or_else(|p| {
            let m = p
                .downcast_ref::<&str>()
                .map(|s| s.to_string())
                .or_else(|| p.downcast_ref::<String>().cloned())
                .unwrap_or_else(|| "unknown".to_string());
            Err(format!("panic in cedar-cabi: {m}"))
        });

    match result {
        Ok((CedarResult::Valid, _)) => CedarResult::Valid,
        Ok((CedarResult::Invalid, diag)) => {
            if let Some(d) = diag {
                *out = into_c_string(&d);
            }
            CedarResult::Invalid
        }
        Ok((other, _)) => {
            *out = into_c_string(&format!("unexpected code {other:?} (fail-closed)"));
            CedarResult::Error
        }
        Err(msg) => {
            *out = into_c_string(&msg);
            CedarResult::Error
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::ffi::CString;

    fn cs(s: &str) -> CString { CString::new(s).unwrap() }

    fn call_authz(
        policies: &str, principal: &str, action: &str, resource: &str,
        context_json: &str, entities_json: &str,
    ) -> (CedarResult, Option<String>) {
        let (p, pr, a, r, c, e) =
            (cs(policies), cs(principal), cs(action), cs(resource), cs(context_json), cs(entities_json));
        let mut diag: *mut c_char = ptr::null_mut();
        let code = unsafe {
            cedar_is_authorized(p.as_ptr(), pr.as_ptr(), a.as_ptr(), r.as_ptr(),
                                c.as_ptr(), e.as_ptr(), &mut diag)
        };
        let d = if diag.is_null() { None } else {
            let s = unsafe { CStr::from_ptr(diag) }.to_string_lossy().into_owned();
            unsafe { cedar_string_free(diag) };
            Some(s)
        };
        (code, d)
    }

    #[test]
    fn matching_permit_allows() {
        let (code, _) = call_authz(
            r#"permit(principal == User::"alice", action == Action::"view", resource == Resource::"doc1");"#,
            r#"User::"alice""#, r#"Action::"view""#, r#"Resource::"doc1""#, "{}", "[]");
        assert_eq!(code, CedarResult::Allow);
    }

    #[test]
    fn no_matching_policy_denies() {
        let (code, _) = call_authz(
            r#"permit(principal == User::"bob", action == Action::"view", resource == Resource::"doc1");"#,
            r#"User::"alice""#, r#"Action::"view""#, r#"Resource::"doc1""#, "{}", "[]");
        assert_eq!(code, CedarResult::Deny);
    }

    #[test]
    fn malformed_policy_errors_never_allows() {
        let (code, diag) = call_authz(
            "this is not cedar", r#"User::"alice""#, r#"Action::"view""#,
            r#"Resource::"doc1""#, "{}", "[]");
        assert_eq!(code, CedarResult::Error);
        assert_ne!(code, CedarResult::Allow);
        assert!(diag.unwrap_or_default().to_lowercase().contains("polic"));
    }

    #[test]
    fn bad_entity_uid_errors_never_allows() {
        let (code, _) = call_authz(
            r#"permit(principal, action, resource);"#,
            "not a uid", r#"Action::"view""#, r#"Resource::"doc1""#, "{}", "[]");
        assert_eq!(code, CedarResult::Error);
    }

    #[test]
    fn null_pointer_arg_errors_never_allows() {
        let mut diag: *mut c_char = ptr::null_mut();
        let code = unsafe {
            cedar_is_authorized(ptr::null(), ptr::null(), ptr::null(),
                                ptr::null(), ptr::null(), ptr::null(), &mut diag)
        };
        assert_eq!(code, CedarResult::Error);
        if !diag.is_null() { unsafe { cedar_string_free(diag) }; }
    }

    #[test]
    fn string_free_handles_null_and_owned() {
        unsafe { cedar_string_free(ptr::null_mut()) };
        let s = into_c_string("hello");
        assert!(!s.is_null());
        unsafe { cedar_string_free(s) };
    }

    #[test]
    fn null_out_diag_pointer_errors() {
        // The one Error exit where no diagnostic can be written (out_diag itself null).
        let (p, pr, a, r, c, e) = (
            cs(r#"permit(principal, action, resource);"#),
            cs(r#"User::"alice""#), cs(r#"Action::"view""#), cs(r#"Resource::"doc1""#),
            cs("{}"), cs("[]"));
        let code = unsafe {
            cedar_is_authorized(p.as_ptr(), pr.as_ptr(), a.as_ptr(), r.as_ptr(),
                                c.as_ptr(), e.as_ptr(), ptr::null_mut())
        };
        assert_eq!(code, CedarResult::Error);
        assert_ne!(code, CedarResult::Allow);
    }

    #[test]
    fn malformed_context_json_errors_never_allows() {
        let (code, _) = call_authz(
            r#"permit(principal, action, resource);"#,
            r#"User::"alice""#, r#"Action::"view""#, r#"Resource::"doc1""#,
            "not json", "[]");
        assert_eq!(code, CedarResult::Error);
        assert_ne!(code, CedarResult::Allow);
    }

    #[test]
    fn malformed_entities_json_errors_never_allows() {
        let (code, _) = call_authz(
            r#"permit(principal, action, resource);"#,
            r#"User::"alice""#, r#"Action::"view""#, r#"Resource::"doc1""#,
            "{}", "not an array");
        assert_eq!(code, CedarResult::Error);
        assert_ne!(code, CedarResult::Allow);
    }

    #[test]
    fn forbid_overrides_permit_denies_with_diagnostic() {
        // Cedar: an applicable forbid always overrides permit => Deny, with the
        // Deny diagnostic string populated (proves the Deny out_diag plumbing).
        let (code, diag) = call_authz(
            r#"permit(principal, action, resource); forbid(principal == User::"alice", action, resource);"#,
            r#"User::"alice""#, r#"Action::"view""#, r#"Resource::"doc1""#, "{}", "[]");
        assert_eq!(code, CedarResult::Deny);
        let d = diag.expect("Deny must carry a diagnostic string");
        assert!(d.contains("deny"), "diagnostic should start with 'deny', got: {d}");
    }

    #[test]
    fn cstr_in_rejects_null() {
        assert!(cstr_in(ptr::null()).is_err());
        let s = into_c_string("x");
        assert_eq!(cstr_in(s).unwrap(), "x");
        unsafe { cedar_string_free(s) };
    }

    #[test]
    fn into_c_string_scrubs_interior_nul_never_null() {
        let p = into_c_string("bad\0msg");
        assert!(!p.is_null(), "interior NUL must be scrubbed, not dropped");
        let got = unsafe { CStr::from_ptr(p) }.to_string_lossy().into_owned();
        assert_eq!(got, "bad?msg");
        unsafe { cedar_string_free(p) };
    }

    fn call_validate(schema: &str, policies: &str) -> (CedarResult, Option<String>) {
        let (s, p) = (cs(schema), cs(policies));
        let mut diag: *mut c_char = ptr::null_mut();
        let code = unsafe { cedar_validate(s.as_ptr(), p.as_ptr(), &mut diag) };
        let d = if diag.is_null() { None } else {
            let m = unsafe { CStr::from_ptr(diag) }.to_string_lossy().into_owned();
            unsafe { cedar_string_free(diag) };
            Some(m)
        };
        (code, d)
    }

    const HELLO_SCHEMA: &str = r#"
        entity User;
        entity Resource;
        action "view" appliesTo { principal: User, resource: Resource };
    "#;

    #[test]
    fn well_typed_policy_is_valid() {
        let (code, _) = call_validate(
            HELLO_SCHEMA,
            r#"permit(principal == User::"alice", action == Action::"view", resource == Resource::"doc1");"#);
        assert_eq!(code, CedarResult::Valid);
    }

    #[test]
    fn ill_typed_policy_is_invalid_with_messages() {
        let (code, diag) = call_validate(
            HELLO_SCHEMA,
            r#"permit(principal == User::"alice", action == Action::"delete", resource == Resource::"doc1");"#);
        assert_eq!(code, CedarResult::Invalid);
        assert!(diag.unwrap_or_default().len() > 0, "invalid result must carry messages");
    }

    #[test]
    fn unparseable_schema_errors() {
        let (code, _) = call_validate("this is not a schema", r#"permit(principal, action, resource);"#);
        assert_eq!(code, CedarResult::Error);
    }

    #[test]
    fn unparseable_policy_errors() {
        let (code, _) = call_validate(HELLO_SCHEMA, "not a policy");
        assert_eq!(code, CedarResult::Error);
    }

    #[test]
    fn null_out_diag_validate_errors() {
        let (s, p) = (cs(HELLO_SCHEMA), cs(r#"permit(principal, action, resource);"#));
        let code = unsafe { cedar_validate(s.as_ptr(), p.as_ptr(), ptr::null_mut()) };
        assert_eq!(code, CedarResult::Error);
        assert_ne!(code, CedarResult::Valid);
    }
}
