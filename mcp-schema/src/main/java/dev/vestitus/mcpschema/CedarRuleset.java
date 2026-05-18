package dev.vestitus.mcpschema;

/**
 * The MCP's declared Cedar policy text. Opaque-but-validated: this type layer
 * checks non-blank ONLY. Semantic Cedar validation ({@code cedar validate} vs the
 * per-MCP Cedar schema) is the Plan 04b gate; mcp-schema has no native/Cedar
 * dependency by design.
 */
public record CedarRuleset(String text) {
    public CedarRuleset {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Cedar ruleset text must be non-blank");
        }
    }
}
