package dev.vestitus.tokenizer;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class SessionOutcomeTest {
    @Test
    void sessionOpenedCarriesExpiry() {
        Instant exp = Instant.parse("2026-05-19T12:00:00Z");
        SessionOutcome o = new SessionOutcome.SessionOpened(exp);
        assertTrue(o.ok());
        assertEquals(exp, ((SessionOutcome.SessionOpened) o).expiresAt());
    }

    @Test
    void sessionEndedIsOk() {
        assertTrue(new SessionOutcome.SessionEnded().ok());
    }

    @Test
    void nullExpiryRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new SessionOutcome.SessionOpened(null));
    }

    @Test
    void failureIsNotOk() {
        SessionOutcome o =
            new TokenizerFailure(TokenizerFailure.FailureKind.UNREACHABLE, "x");
        assertFalse(o.ok());
    }
}
