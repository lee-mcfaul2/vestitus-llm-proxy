package dev.vestitus.mcpschema;

/**
 * Opaque, typed holder naming the external-IAM entitlement a field requires.
 * vestitus does not interpret {@code entitlement} here; the IAM resolver is
 * Plan 04e. A typed record (not a bare String) per the project's
 * typed-attribute discipline.
 */
public record IamBinding(String entitlement) {
    public IamBinding {
        if (entitlement == null || entitlement.isBlank()) {
            throw new IllegalArgumentException("IAM binding entitlement must be non-blank");
        }
    }
}
