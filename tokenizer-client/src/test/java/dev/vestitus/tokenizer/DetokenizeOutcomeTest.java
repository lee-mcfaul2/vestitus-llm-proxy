package dev.vestitus.tokenizer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DetokenizeOutcomeTest {
    @Test
    void detokenizedCarriesPlaintextAndType() {
        DetokenizeOutcome o =
            new DetokenizeOutcome.Detokenized("alice@example.com", PiiType.EMAIL);
        assertTrue(o.ok());
        var d = (DetokenizeOutcome.Detokenized) o;
        assertEquals("alice@example.com", d.plaintext());
        assertEquals(PiiType.EMAIL, d.type());
    }

    @Test
    void emptyPlaintextAllowed() {
        assertTrue(new DetokenizeOutcome.Detokenized("", PiiType.NAME).ok());
    }

    @Test
    void nullPlaintextRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new DetokenizeOutcome.Detokenized(null, PiiType.NAME));
    }

    @Test
    void nullTypeRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new DetokenizeOutcome.Detokenized("x", null));
    }

    @Test
    void failureIsNotOk() {
        DetokenizeOutcome o =
            new TokenizerFailure(TokenizerFailure.FailureKind.MALFORMED_RESPONSE, "x");
        assertFalse(o.ok());
    }
}
