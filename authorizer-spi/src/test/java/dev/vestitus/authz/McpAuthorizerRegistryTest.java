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
    }
}
