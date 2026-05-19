package dev.vestitus.tokenizer;

import java.time.Duration;

/**
 * Pluggable client for an external PII Tokenizer. vestitus owns no
 * tokenization key, logic, or guarantee and holds NO per-uuid state — the
 * caller drives the session lifecycle and the tokenizer owns its keys/TTL.
 *
 * <p><b>Contract:</b> an implementation MUST NOT throw. Every operation
 * returns a sealed outcome whose failure variant is {@link TokenizerFailure}
 * (fail-closed: the caller treats any non-success outcome as a reason to abort
 * the request, never as success-by-exception). {@code uuid} is a caller-supplied
 * canonical lowercase-hyphenated session UUID. A local or alternative
 * implementation MAY treat {@code beginSession}/{@code endSession} as no-ops
 * and MAY ignore {@link PiiType}.</p>
 */
public interface Tokenizer {

    /**
     * Opens the per-request session/key scope. Called once at the start of
     * every prompt, unconditionally (even when no PII is later detected), so
     * no per-uuid tracking is needed anywhere. Re-opening an existing uuid is
     * idempotent and is a success.
     */
    SessionOutcome beginSession(String uuid, Duration ttl);

    /** Tokenizes {@code target} within {@code uuid}; {@code type} is opaque pass-through. */
    TokenizeOutcome tokenize(String uuid, PiiType type, String target);

    /** Reverses a token within {@code uuid}; the outcome carries the echoed type. */
    DetokenizeOutcome detokenize(String uuid, String token);

    /**
     * Signals the session is over so the tokenizer destroys its per-session
     * key. Best-effort by contract (the tokenizer's TTL is the backstop).
     */
    SessionOutcome endSession(String uuid);
}
