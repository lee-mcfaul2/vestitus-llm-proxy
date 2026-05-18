package dev.vestitus.mcpschema;

import java.util.List;

/**
 * The signed mcp-schema artifact's single aggregate root (spec §5.3 step 1 +
 * §5.4): version anchor, MCP id, the tool catalog, the MCP's Cedar ruleset,
 * and the per-MCP Cedar schema. NOT a sealed/discriminated family — one
 * document. {@code tools} must be non-empty: a zero-tool MCP has nothing to
 * authorize, so it is fail-closed rejected (default-deny posture).
 */
public record McpSchema(
        McpSchemaVersion schemaVersion,
        String mcpId,
        List<ToolDecl> tools,
        CedarRuleset ruleset,
        CedarSchemaText cedarSchema) {
    public McpSchema {
        if (schemaVersion == null) {
            throw new IllegalArgumentException("schemaVersion must be non-null");
        }
        if (mcpId == null || mcpId.isBlank()) {
            throw new IllegalArgumentException("mcpId must be non-blank");
        }
        if (tools == null) {
            throw new IllegalArgumentException("tools must be non-null");
        }
        if (tools.isEmpty()) {
            throw new IllegalArgumentException(
                "tools must be non-empty (zero-tool MCP rejected, default-deny)");
        }
        if (ruleset == null) {
            throw new IllegalArgumentException("ruleset must be non-null");
        }
        if (cedarSchema == null) {
            throw new IllegalArgumentException("cedarSchema must be non-null");
        }
        tools = List.copyOf(tools);
    }
}
