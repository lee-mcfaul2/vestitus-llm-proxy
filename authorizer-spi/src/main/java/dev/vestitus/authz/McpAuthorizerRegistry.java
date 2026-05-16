package dev.vestitus.authz;

import java.util.Map;

/**
 * MCP-keyed registry of isolated authorizer cells. The operator binds an
 * {@link Authorizer} per {@code mcpId} out of band (e.g. at startup, after
 * verifying the signed MCP schema). Each cell is independent: a bad/unbound
 * cell never affects another.
 *
 * Fail-closed: a missing/blank {@code mcpId}, no bound authorizer, a {@code
 * null} request, a {@code null} returned decision, or any {@link Throwable}
 * from a bound authorizer all yield {@code Deny}.
 */
public final class McpAuthorizerRegistry {
    private final Map<String, Authorizer> cells;

    private McpAuthorizerRegistry(Map<String, Authorizer> cells) {
        this.cells = Map.copyOf(cells);
    }

    public static McpAuthorizerRegistry of(Map<String, Authorizer> cells) {
        if (cells == null)
            throw new IllegalArgumentException("cells required");
        for (var e : cells.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank())
                throw new IllegalArgumentException("blank mcpId in cells");
            if (e.getValue() == null)
                throw new IllegalArgumentException("null authorizer for mcpId " + e.getKey());
        }
        return new McpAuthorizerRegistry(cells);
    }

    public AuthorizationDecision authorize(String mcpId, AuthorizationRequest request) {
        if (mcpId == null || mcpId.isBlank())
            return AuthorizationDecision.deny("missing mcpId (fail-closed)");
        if (request == null)
            return AuthorizationDecision.deny("null request (fail-closed)");
        Authorizer a = cells.get(mcpId);
        if (a == null)
            return AuthorizationDecision.deny("no authorizer bound for mcp " + mcpId + " (fail-closed)");
        try {
            AuthorizationDecision d = a.authorize(request);
            return d != null ? d
                : AuthorizationDecision.deny("authorizer returned null (fail-closed)");
        } catch (Throwable t) {
            return AuthorizationDecision.deny(
                "authorizer error (fail-closed): " + t.getClass().getSimpleName());
        }
    }
}
