package dev.vestitus.tokenizer;

/** The outcome of {@code tokenize}. Sealed, never thrown. */
public sealed interface TokenizeOutcome
        permits TokenizeOutcome.Tokenized, TokenizerFailure {

    record Tokenized(String token) implements TokenizeOutcome {
        public Tokenized {
            if (token == null || token.isBlank())
                throw new IllegalArgumentException("token required");
        }
    }

    default boolean ok() {
        return switch (this) {
            case Tokenized t -> true;
            case TokenizerFailure f -> false;
        };
    }
}
