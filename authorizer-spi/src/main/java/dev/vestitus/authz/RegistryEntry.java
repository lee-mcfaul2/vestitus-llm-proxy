package dev.vestitus.authz;

/**
 * One bundle's {@code mcpId -> Authorizer} binding, the input unit for {@link
 * McpAuthorizerRegistry#ofEntries}. A {@code List<RegistryEntry>} (not a
 * {@code Map}) is the input so a duplicate {@code mcpId} is detectable BEFORE
 * the entries collapse — a {@code Map} would silently last-wins, which
 * ADR-003 D8 / red-team CRITICAL-2 flag as exploitable (a buggy/hostile bundle
 * set could re-bind another MCP's authorizer cell). Compact-ctor validated,
 * matching the {@code IllegalArgumentException} house style of the other
 * {@code authorizer-spi} records and {@link McpAuthorizerRegistry#of}.
 */
public record RegistryEntry(String mcpId, Authorizer authorizer) {
    public RegistryEntry {
        if (mcpId == null || mcpId.isBlank())
            throw new IllegalArgumentException("mcpId must be non-blank");
        if (authorizer == null)
            throw new IllegalArgumentException("authorizer required for mcpId " + mcpId);
    }
}
