package dev.vestitus.mcpschema;

/**
 * One declared MCP field. The default-deny linchpin (spec §5.3 step 2,
 * non-Cedar half): a field missing its {@code pii} OR {@code iam} annotation
 * is rejected at construction. Under Jackson FAIL_ON_UNKNOWN a missing JSON
 * property arrives null here, this constructor throws, and McpSchemaJson
 * wraps it fail-closed.
 */
public record FieldDecl(String name, PiiType pii, IamBinding iam) {
    public FieldDecl {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("field name must be non-blank");
        }
        if (pii == null) {
            throw new IllegalArgumentException(
                "field '" + name + "' missing PII classification (default-deny)");
        }
        if (iam == null) {
            throw new IllegalArgumentException(
                "field '" + name + "' missing IAM binding (default-deny)");
        }
    }
}
