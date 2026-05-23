package dev.vestitus.inspect.llmguard;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.security.KeyStore;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class LlmGuardEndpointConfigTest {

    private static KeyStore emptyKs() throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        return ks;
    }

    private static LlmGuardEndpointConfig good(URI uri) throws Exception {
        return new LlmGuardEndpointConfig(uri, emptyKs(), emptyKs(),
            "changeit".toCharArray(),
            Duration.ofSeconds(2), Duration.ofSeconds(5),
            2, Duration.ofMillis(750));
    }

    @Test
    void rejectsNonHttpsEndpoint() throws Exception {
        assertThrows(IllegalArgumentException.class, () ->
            good(URI.create("http://llm-guard.internal/analyze/prompt")));
    }

    @Test
    void rejectsNullsAndNonPositiveDurationsAndNegativeRetries() throws Exception {
        URI ok = URI.create("https://llm-guard.internal/analyze/prompt");
        assertThrows(NullPointerException.class, () -> new LlmGuardEndpointConfig(
            null, emptyKs(), emptyKs(), "x".toCharArray(),
            Duration.ofSeconds(1), Duration.ofSeconds(1), 0, Duration.ofMillis(1)));
        assertThrows(IllegalArgumentException.class, () -> new LlmGuardEndpointConfig(
            ok, emptyKs(), emptyKs(), "x".toCharArray(),
            Duration.ZERO, Duration.ofSeconds(1), 0, Duration.ofMillis(1)));
        assertThrows(IllegalArgumentException.class, () -> new LlmGuardEndpointConfig(
            ok, emptyKs(), emptyKs(), "x".toCharArray(),
            Duration.ofSeconds(1), Duration.ofSeconds(-1), 0, Duration.ofMillis(1)));
        assertThrows(IllegalArgumentException.class, () -> new LlmGuardEndpointConfig(
            ok, emptyKs(), emptyKs(), "x".toCharArray(),
            Duration.ofSeconds(1), Duration.ofSeconds(1), -1, Duration.ofMillis(1)));
        assertThrows(IllegalArgumentException.class, () -> new LlmGuardEndpointConfig(
            ok, emptyKs(), emptyKs(), "x".toCharArray(),
            Duration.ofSeconds(1), Duration.ofSeconds(1), 0, Duration.ZERO));
    }

    @Test
    void acceptsAWellFormedConfig() throws Exception {
        var cfg = good(URI.create("https://llm-guard.internal:8000/analyze/prompt"));
        assertEquals(URI.create("https://llm-guard.internal:8000/analyze/prompt"),
            cfg.endpoint());
        assertEquals(2, cfg.maxRetries());
        assertEquals(Duration.ofMillis(750), cfg.latencyBudget());
    }
}
