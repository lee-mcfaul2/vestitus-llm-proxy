package dev.vestitus.tokenizer;

import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class HttpPiiTokenizerClientRetryTest {

    private HttpsServer server;

    @AfterEach
    void stop() { if (server != null) server.stop(0); }

    private static void send(com.sun.net.httpserver.HttpExchange ex,
                             int status, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, b.length == 0 ? -1 : b.length);
        try (var os = ex.getResponseBody()) { os.write(b); }
    }

    @Test
    void retriable503ThenSuccess() throws Exception {
        AtomicInteger n = new AtomicInteger();
        server = TlsTestSupport.startServer(
            TlsTestSupport.load("server.p12"), "server", ex -> {
                if (n.getAndIncrement() == 0)
                    send(ex, 503, "{\"error_type\":\"REDIS_UNAVAILABLE\","
                        + "\"retriable\":true,\"message\":\"x\"}");
                else
                    send(ex, 200, "{\"token\":\"tok_ok\"}");
            });
        var cfg = TlsTestSupport.pinnedConfig(
            TlsTestSupport.baseUri(server), 2, Duration.ofSeconds(3));
        Tokenizer t = HttpPiiTokenizerClient.create(cfg);
        TokenizeOutcome o = t.tokenize(
            "00000000-0000-0000-0000-000000000001", PiiType.SSN, "x");
        assertEquals("tok_ok", ((TokenizeOutcome.Tokenized) o).token());
        assertTrue(n.get() >= 2);
    }

    @Test
    void retriableExhaustedFailsClosedWithinBudget() throws Exception {
        server = TlsTestSupport.startServer(
            TlsTestSupport.load("server.p12"), "server", ex ->
                send(ex, 503, "{\"error_type\":\"KMASTER_UNAVAILABLE\","
                    + "\"retriable\":true,\"message\":\"x\"}"));
        var cfg = TlsTestSupport.pinnedConfig(
            TlsTestSupport.baseUri(server), 2, Duration.ofMillis(750));
        Tokenizer t = HttpPiiTokenizerClient.create(cfg);
        long start = System.nanoTime();
        TokenizeOutcome o = t.tokenize(
            "00000000-0000-0000-0000-000000000001", PiiType.SSN, "x");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        TokenizerFailure f = (TokenizerFailure) o;
        assertEquals(TokenizerFailure.FailureKind.RETRIABLE_EXHAUSTED, f.kind());
        assertTrue(elapsedMs < 3000, "should respect the 750ms budget, was " + elapsedMs);
    }
}
