package dev.vestitus.mcpschema;

public record McpSchemaVersion(String value) {
    public static final McpSchemaVersion CURRENT = new McpSchemaVersion("1.0.0");

    public McpSchemaVersion {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("mcp schema version must be non-blank");
        }
    }

    public static McpSchemaVersion parse(String s) {
        return new McpSchemaVersion(s == null ? null : s.trim());
    }
}
