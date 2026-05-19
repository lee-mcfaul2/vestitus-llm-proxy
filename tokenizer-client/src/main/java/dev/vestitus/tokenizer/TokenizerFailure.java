package dev.vestitus.tokenizer;

/**
 * The shared fail-closed variant of every {@link Tokenizer} outcome. An
 * implementation MUST NOT throw; any failure is one of these.
 *
 * <p><b>Inv. 13 (no secret/PII in audit or trace):</b> {@code detail} carries
 * ONLY a service {@code error_type}, HTTP status, and/or a short static
 * reason. It MUST NEVER contain {@code target}, {@code token}, {@code
 * plaintext}, request/response headers, or any response-body substring that
 * could echo a secret or PII. This failure value flows into audit and trace.
 */
public record TokenizerFailure(FailureKind kind, String detail)
        implements SessionOutcome, TokenizeOutcome, DetokenizeOutcome {

    public enum FailureKind {
        UNREACHABLE, TIMEOUT, RETRIABLE_EXHAUSTED, TERMINAL_ERROR, MALFORMED_RESPONSE
    }

    public TokenizerFailure {
        if (kind == null)
            throw new IllegalArgumentException("failure kind required");
        if (detail == null || detail.isBlank())
            throw new IllegalArgumentException("failure detail required");
    }

    @Override
    public boolean ok() { return false; }
}
