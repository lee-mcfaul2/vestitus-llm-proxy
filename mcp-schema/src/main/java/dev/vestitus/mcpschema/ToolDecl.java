package dev.vestitus.mcpschema;

import java.util.List;

/**
 * One declared MCP tool: a name, a (possibly empty) description, and a
 * (possibly empty) list of declared fields. Empty {@code fields} is legal
 * (a field-less action tool). Description injection-lint is the Plan 04b
 * gate, not this type layer.
 */
public record ToolDecl(String name, String description, List<FieldDecl> fields) {
    public ToolDecl {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("tool name must be non-blank");
        }
        if (description == null) {
            throw new IllegalArgumentException("tool description must be non-null");
        }
        if (fields == null) {
            throw new IllegalArgumentException("tool fields must be non-null");
        }
        fields = List.copyOf(fields);
    }
}
