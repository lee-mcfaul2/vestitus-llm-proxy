package dev.vestitus.tokenizer;

import java.time.Instant;

/**
 * The outcome of {@code beginSession}/{@code endSession}. Sealed, never thrown:
 * a failure is a {@link TokenizerFailure}, not an exception.
 */
public sealed interface SessionOutcome
        permits SessionOutcome.SessionOpened, SessionOutcome.SessionEnded,
                TokenizerFailure {

    /** Session opened; {@code expiresAt} is the tokenizer-owned TTL deadline. */
    record SessionOpened(Instant expiresAt) implements SessionOutcome {
        public SessionOpened {
            if (expiresAt == null)
                throw new IllegalArgumentException("expiresAt required");
        }
    }

    /** Session-end acknowledged (best-effort by contract; TTL is the backstop). */
    record SessionEnded() implements SessionOutcome {}

    default boolean ok() {
        return switch (this) {
            case SessionOpened o -> true;
            case SessionEnded e -> true;
            case TokenizerFailure f -> false;
        };
    }
}
