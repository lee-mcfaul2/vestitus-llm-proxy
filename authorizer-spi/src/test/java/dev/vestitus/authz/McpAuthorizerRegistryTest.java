package dev.vestitus.authz;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class McpAuthorizerRegistryTest {
    private static AuthorizationRequest req() {
        return new AuthorizationRequest(
            new Principal("u", Set.of(), Map.of()),
            "read",
            new ResourceRef("mcp-a", "t", "f", Map.of()),
            Map.of());
    }

    @Test
    void boundAuthorizerDecisionPassesThrough() {
        var reg = McpAuthorizerRegistry.of(Map.of("mcp-a", r -> AuthorizationDecision.allow()));
        assertTrue(reg.authorize("mcp-a", req()).allowed());
    }

    @Test
    void unknownMcpIsDeniedFailClosed() {
        var reg = McpAuthorizerRegistry.of(Map.of("mcp-a", new DenyAllAuthorizer()));
        assertFalse(reg.authorize("mcp-unknown", req()).allowed());
        assertFalse(reg.authorize(" ", req()).allowed());
        assertFalse(reg.authorize(null, req()).allowed());
    }

    @Test
    void throwingAuthorizerIsDeniedFailClosed() {
        Authorizer boom = r -> { throw new RuntimeException("kaboom"); };
        var reg = McpAuthorizerRegistry.of(Map.of("mcp-a", boom));
        AuthorizationDecision d = reg.authorize("mcp-a", req());
        assertFalse(d.allowed());
        assertTrue(((AuthorizationDecision.Deny) d).reason().contains("fail-closed"));
    }

    @Test
    void nullDecisionFromAuthorizerIsDeniedFailClosed() {
        Authorizer nuller = r -> null;
        var reg = McpAuthorizerRegistry.of(Map.of("mcp-a", nuller));
        assertFalse(reg.authorize("mcp-a", req()).allowed());
    }

    @Test
    void nullRequestIsDeniedFailClosed() {
        var reg = McpAuthorizerRegistry.of(Map.of("mcp-a", r -> AuthorizationDecision.allow()));
        assertFalse(reg.authorize("mcp-a", null).allowed());
    }

    @Test
    void registryValidatesAndCopiesCells() {
        Map<String, Authorizer> cells = new HashMap<>();
        cells.put("mcp-a", new DenyAllAuthorizer());
        var reg = McpAuthorizerRegistry.of(cells);
        cells.put("mcp-b", new DenyAllAuthorizer());     // mutate original after build
        assertFalse(reg.authorize("mcp-b", req()).allowed()); // not visible -> deny
        assertThrows(IllegalArgumentException.class, () -> McpAuthorizerRegistry.of(null));
        var bad = new HashMap<String, Authorizer>();
        bad.put(" ", new DenyAllAuthorizer());
        assertThrows(IllegalArgumentException.class, () -> McpAuthorizerRegistry.of(bad));
        var nullVal = new HashMap<String, Authorizer>();
        nullVal.put("mcp-a", null);
        assertThrows(IllegalArgumentException.class, () -> McpAuthorizerRegistry.of(nullVal));
    }

    /** In-test allow-everything authorizer (proves a bound cell's Allow flows through). */
    static final class AllowAuthorizer implements Authorizer {
        @Override
        public AuthorizationDecision authorize(AuthorizationRequest request) {
            return AuthorizationDecision.allow();
        }
    }

    @Test
    void ofEntriesBuildsAWorkingRegistry() {
        var reg = McpAuthorizerRegistry.ofEntries(java.util.List.of(
            new RegistryEntry("mcp-a", new AllowAuthorizer()),
            new RegistryEntry("mcp-b", new DenyAllAuthorizer())));
        assertTrue(reg.authorize("mcp-a", req()).allowed());   // bound Allow
        assertFalse(reg.authorize("mcp-b", req()).allowed());  // bound DenyAll
        assertFalse(reg.authorize("mcp-x", req()).allowed());  // unbound -> deny
    }

    @Test
    void ofEntriesRejectsDuplicateMcpIdFailClosed() {
        var dup = java.util.List.of(
            new RegistryEntry("mcp-a", new AllowAuthorizer()),
            new RegistryEntry("mcp-a", new DenyAllAuthorizer()));
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> McpAuthorizerRegistry.ofEntries(dup));
        assertTrue(ex.getMessage().contains("duplicate mcpId"),
            "fail-closed message must name the duplicate mcpId: " + ex.getMessage());
    }

    @Test
    void ofEntriesEmptyListYieldsDenyAllNotAnError() {
        var reg = McpAuthorizerRegistry.ofEntries(java.util.List.of());
        // set-policy (is empty admissible) is the CALLER's per ADR-003 D6;
        // here an empty generation is a valid fail-closed deny-all, NOT an exception.
        assertFalse(reg.authorize("mcp-a", req()).allowed());
        assertFalse(reg.authorize("anything", req()).allowed());
    }

    @Test
    void ofEntriesNullListIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> McpAuthorizerRegistry.ofEntries(null));
    }

    @Test
    void ofEntriesNullElementIsRejectedFailClosed() {
        var withNull = new java.util.ArrayList<RegistryEntry>();
        withNull.add(new RegistryEntry("mcp-a", new AllowAuthorizer()));
        withNull.add(null);
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> McpAuthorizerRegistry.ofEntries(withNull));
        assertTrue(ex.getMessage().contains("null entry")
                && ex.getMessage().contains("fail-closed"),
            "null element must be a fail-closed reject: " + ex.getMessage());
    }

    @Test
    void perCellValidationStillAppliesViaRegistryEntry() {
        // RegistryEntry's own compact-ctor blocks blank/null upstream of the
        // map collapse; per-cell validation is preserved (defence in depth).
        assertThrows(IllegalArgumentException.class,
            () -> new RegistryEntry(" ", new AllowAuthorizer()));
        assertThrows(IllegalArgumentException.class,
            () -> new RegistryEntry("mcp-a", null));
    }
}
