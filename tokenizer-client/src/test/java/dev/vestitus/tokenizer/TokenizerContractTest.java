package dev.vestitus.tokenizer;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class TokenizerContractTest {

    /** A trivial in-memory Tokenizer proving the interface composes. */
    static final class FakeTokenizer implements Tokenizer {
        @Override public SessionOutcome beginSession(String uuid, Duration ttl) {
            return new SessionOutcome.SessionOpened(Instant.EPOCH.plus(ttl));
        }
        @Override public TokenizeOutcome tokenize(String uuid, PiiType type, String target) {
            return new TokenizeOutcome.Tokenized("tok-" + type.name());
        }
        @Override public DetokenizeOutcome detokenize(String uuid, String token) {
            return new DetokenizeOutcome.Detokenized("plain", PiiType.EMAIL);
        }
        @Override public SessionOutcome endSession(String uuid) {
            return new SessionOutcome.SessionEnded();
        }
    }

    @Test
    void interfaceComposesWithSealedOutcomes() {
        Tokenizer t = new FakeTokenizer();
        assertTrue(t.beginSession("u", Duration.ofSeconds(60)).ok());
        TokenizeOutcome to = t.tokenize("u", PiiType.SSN, "123-45-6789");
        assertEquals("tok-SSN", ((TokenizeOutcome.Tokenized) to).token());
        assertTrue(t.detokenize("u", "tok-SSN").ok());
        assertTrue(t.endSession("u").ok());
    }
}
