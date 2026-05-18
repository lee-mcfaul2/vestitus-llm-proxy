package dev.vestitus.bundle;

/**
 * Fail-closed signal that the persisted version floor is unreadable, corrupt,
 * or could not be written (ADR-003 D7). {@code bundle-runtime}-local on
 * purpose: the no-rollback gate (D6, verifier-independent) converts ANY store
 * throwable into a fail-closed {@code Reject}, so an untrustworthy floor store
 * can never permit acceptance. Mirrors {@code mcp-schema}'s
 * {@code McpSchemaParseException} shape; carries no verifier semantics (this
 * gate is downstream of and independent from the swappable verifier).
 */
public final class VersionStoreException extends RuntimeException {
    public VersionStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
