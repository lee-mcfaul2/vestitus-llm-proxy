package dev.vestitus.trust;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class VerificationConfigTest {
    @Test
    void anchoredRegexpAccepted() {
        var c = new VerificationConfig(
            "^https://github\\.com/acme/sec-repo@refs/tags/.*$",
            "https://token.actions.githubusercontent.com",
            Map.of());
        assertEquals("https://token.actions.githubusercontent.com", c.oidcIssuer());
    }

    @Test
    void blankRegexpRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new VerificationConfig("  ", "iss", Map.of()));
    }

    @Test
    void regexpMissingCaretRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new VerificationConfig("https://x$", "iss", Map.of()));
    }

    @Test
    void regexpMissingDollarRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new VerificationConfig("^https://x", "iss", Map.of()));
    }

    @Test
    void blankIssuerRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new VerificationConfig("^x$", " ", Map.of()));
    }

    @Test
    void nullIssuerRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new VerificationConfig("^x$", null, Map.of()));
    }

    @Test
    void nullExtraRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new VerificationConfig("^x$", "iss", null));
    }

    @Test
    void extraIsImmutableCopy() {
        var src = new HashMap<String, String>();
        src.put("k", "v");
        var c = new VerificationConfig("^x$", "iss", src);
        src.clear();
        assertEquals("v", c.extra().get("k"));
        assertThrows(UnsupportedOperationException.class, () -> c.extra().put("z", "z"));
    }

    @Test
    void emptyExtraAccepted() {
        var c = new VerificationConfig("^x$", "iss", Map.of());
        assertTrue(c.extra().isEmpty());
    }
}
