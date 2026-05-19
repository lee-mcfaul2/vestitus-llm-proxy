package dev.vestitus.tokenizer;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.security.KeyStore;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class TokenizerEndpointConfigTest {

    private static KeyStore emptyKs() throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        return ks;
    }

    private static TokenizerEndpointConfig valid(URI ep) throws Exception {
        return new TokenizerEndpointConfig(
            ep, emptyKs(), emptyKs(), "p".toCharArray(),
            Duration.ofSeconds(2), Duration.ofSeconds(5),
            Duration.ofSeconds(900), 2, Duration.ofMillis(750));
    }

    @Test
    void httpsConfigAccepted() throws Exception {
        var c = valid(URI.create("https://tok.internal:8443"));
        assertEquals(2, c.maxRetries());
        assertEquals(Duration.ofMillis(750), c.latencyBudget());
    }

    @Test
    void nonHttpsEndpointRejected() throws Exception {
        assertThrows(IllegalArgumentException.class,
            () -> valid(URI.create("http://tok.internal:8443")));
    }

    @Test
    void nullTrustRejected() throws Exception {
        assertThrows(NullPointerException.class, () -> new TokenizerEndpointConfig(
            URI.create("https://t"), null, emptyKs(), "p".toCharArray(),
            Duration.ofSeconds(2), Duration.ofSeconds(5),
            Duration.ofSeconds(900), 2, Duration.ofMillis(750)));
    }

    @Test
    void negativeMaxRetriesRejected() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new TokenizerEndpointConfig(
            URI.create("https://t"), emptyKs(), emptyKs(), "p".toCharArray(),
            Duration.ofSeconds(2), Duration.ofSeconds(5),
            Duration.ofSeconds(900), -1, Duration.ofMillis(750)));
    }

    @Test
    void ttlOutOfRangeRejected() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new TokenizerEndpointConfig(
            URI.create("https://t"), emptyKs(), emptyKs(), "p".toCharArray(),
            Duration.ofSeconds(2), Duration.ofSeconds(5),
            Duration.ofSeconds(0), 2, Duration.ofMillis(750)));
        assertThrows(IllegalArgumentException.class, () -> new TokenizerEndpointConfig(
            URI.create("https://t"), emptyKs(), emptyKs(), "p".toCharArray(),
            Duration.ofSeconds(2), Duration.ofSeconds(5),
            Duration.ofSeconds(86401), 2, Duration.ofMillis(750)));
    }

    @Test
    void nonPositiveTimeoutsAndBudgetRejected() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new TokenizerEndpointConfig(
            URI.create("https://t"), emptyKs(), emptyKs(), "p".toCharArray(),
            Duration.ZERO, Duration.ofSeconds(5),
            Duration.ofSeconds(900), 2, Duration.ofMillis(750)));
        assertThrows(IllegalArgumentException.class, () -> new TokenizerEndpointConfig(
            URI.create("https://t"), emptyKs(), emptyKs(), "p".toCharArray(),
            Duration.ofSeconds(2), Duration.ofSeconds(5),
            Duration.ofSeconds(900), 2, Duration.ZERO));
    }
}
