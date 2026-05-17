//! cedar-cabi: vestitus self-built shim over a commit-pinned cedar-policy.
//! 03a scope = probe only: prove the pinned Cedar API round-trips.
//! The extern "C" ABI is added in a later sub-plan.

#[cfg(test)]
mod tests {
    use cedar_policy::{Authorizer, Context, Decision, Entities, PolicySet, Request};
    use std::str::FromStr;

    fn uid(s: &str) -> cedar_policy::EntityUid {
        cedar_policy::EntityUid::from_str(s).unwrap()
    }

    #[test]
    fn hello_world_permit_allows() {
        let policies = PolicySet::from_str(
            r#"permit(principal == User::"alice", action == Action::"view", resource == Resource::"doc1");"#
        ).expect("policy parses");
        let req = Request::new(
            uid(r#"User::"alice""#), uid(r#"Action::"view""#), uid(r#"Resource::"doc1""#),
            Context::empty(), None,
        ).expect("request builds");
        let ans = Authorizer::new().is_authorized(&req, &policies, &Entities::empty());
        assert_eq!(ans.decision(), Decision::Allow);
    }

    #[test]
    fn no_matching_policy_denies_fail_closed() {
        let policies = PolicySet::from_str(
            r#"permit(principal == User::"bob", action == Action::"view", resource == Resource::"doc1");"#
        ).expect("policy parses");
        let req = Request::new(
            uid(r#"User::"alice""#), uid(r#"Action::"view""#), uid(r#"Resource::"doc1""#),
            Context::empty(), None,
        ).expect("request builds");
        let ans = Authorizer::new().is_authorized(&req, &policies, &Entities::empty());
        assert_eq!(ans.decision(), Decision::Deny);
    }
}

#[cfg(test)]
mod header_tests {
    #[test]
    fn generated_header_exists_and_has_guard() {
        let h = include_str!("../include/cedar_cabi.h");
        assert!(h.contains("CEDAR_CABI_H"), "header missing include guard");
        assert!(h.contains("cedar_string_free"), "header missing cedar_string_free decl");
    }
}
