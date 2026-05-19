package dev.vestitus.tokenizer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TokenizeOutcomeTest {
    @Test
    void tokenizedCarriesToken() {
        TokenizeOutcome o = new TokenizeOutcome.Tokenized("tok_abc");
        assertTrue(o.ok());
        assertEquals("tok_abc", ((TokenizeOutcome.Tokenized) o).token());
    }

    @Test
    void blankTokenRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new TokenizeOutcome.Tokenized(" "));
    }

    @Test
    void failureIsNotOk() {
        TokenizeOutcome o =
            new TokenizerFailure(TokenizerFailure.FailureKind.TERMINAL_ERROR, "x");
        assertFalse(o.ok());
    }
}
