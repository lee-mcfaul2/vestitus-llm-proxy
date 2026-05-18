package dev.vestitus.trust;

/**
 * Fail-closed signal a {@link BundleDigester} (or a {@link BundleVerifier}
 * internal failure) raises on malformed/oversized/ambiguous input or an
 * internal error. The core treats it as a fail-closed reload abort (keep
 * last-good); it is never a grant. Mirrors {@code McpSchemaParseException}.
 */
public final class TrustException extends RuntimeException {
    public TrustException(String message, Throwable cause) { super(message, cause); }
}
