package dev.vestitus.tokenizer;

/** The outcome of {@code detokenize}. Sealed, never thrown. */
public sealed interface DetokenizeOutcome
        permits DetokenizeOutcome.Detokenized, TokenizerFailure {

    /** {@code plaintext} is the original value; {@code type} is service-echoed. */
    record Detokenized(String plaintext, PiiType type) implements DetokenizeOutcome {
        public Detokenized {
            if (plaintext == null)
                throw new IllegalArgumentException("plaintext required");
            if (type == null)
                throw new IllegalArgumentException("type required");
        }
    }

    default boolean ok() {
        return switch (this) {
            case Detokenized d -> true;
            case TokenizerFailure f -> false;
        };
    }
}
