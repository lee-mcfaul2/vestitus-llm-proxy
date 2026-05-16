package dev.vestitus.authz;

/**
 * Safe default reference implementation: denies everything. This is the
 * fail-closed baseline used when no policy-backed Authorizer (e.g. the
 * Cedar engine, Plan 03) is bound for an MCP.
 */
public final class DenyAllAuthorizer implements Authorizer {
    @Override
    public AuthorizationDecision authorize(AuthorizationRequest request) {
        if (request == null)
            return AuthorizationDecision.deny("null request");
        return AuthorizationDecision.deny("default-deny: no policy authorizer bound");
    }
}
