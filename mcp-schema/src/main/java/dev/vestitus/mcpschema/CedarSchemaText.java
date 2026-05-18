package dev.vestitus.mcpschema;

/**
 * The per-MCP Cedar schema text (spec §5.4 target state). Opaque holder;
 * non-blank validated only — semantic checking is the Plan 04b gate. Named
 * distinctly from McpSchemaVersion / McpSchema to avoid confusion.
 */
public record CedarSchemaText(String text) {
    public CedarSchemaText {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Cedar schema text must be non-blank");
        }
    }
}
