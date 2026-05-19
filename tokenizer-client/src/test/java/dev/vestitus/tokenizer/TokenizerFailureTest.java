package dev.vestitus.tokenizer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TokenizerFailureTest {
    @Test
    void carriesKindAndDetail() {
        TokenizerFailure f =
            new TokenizerFailure(TokenizerFailure.FailureKind.UNREACHABLE, "conn refused");
        assertEquals(TokenizerFailure.FailureKind.UNREACHABLE, f.kind());
        assertEquals("conn refused", f.detail());
    }

    @Test
    void nullKindRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new TokenizerFailure(null, "x"));
    }

    @Test
    void blankDetailRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new TokenizerFailure(TokenizerFailure.FailureKind.TIMEOUT, " "));
    }

    @Test
    void allFiveKindsExist() {
        assertEquals(5, TokenizerFailure.FailureKind.values().length);
        assertNotNull(TokenizerFailure.FailureKind.valueOf("RETRIABLE_EXHAUSTED"));
        assertNotNull(TokenizerFailure.FailureKind.valueOf("TERMINAL_ERROR"));
        assertNotNull(TokenizerFailure.FailureKind.valueOf("MALFORMED_RESPONSE"));
    }
}
