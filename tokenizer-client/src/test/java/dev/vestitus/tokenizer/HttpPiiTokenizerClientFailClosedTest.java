package dev.vestitus.tokenizer;

import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class HttpPiiTokenizerClientFailClosedTest {

    private static final String UUID = "00000000-0000-0000-0000-000000000001";
    private HttpsServer server;

    @AfterEach
    void stop() { if (server != null) server.stop(0); }

    private static void send(com.sun.net.httpserver.HttpExchange ex,
                             int status, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, b.length == 0 ? -1 : b.length);
        try (var os = ex.getResponseBody()) { os.write(b); }
    }

    private Tokenizer clientFor(com.sun.net.httpserver.HttpHandler h) throws Exception {
        server = TlsTestSupport.startServer(
            TlsTestSupport.load("server.p12"), "server", h);
        return HttpPiiTokenizerClient.create(TlsTestSupport.pinnedConfig(
            TlsTestSupport.baseUri(server), 0, Duration.ofMillis(750)));
    }

    @Test
    void nonRetriable404IsTerminalWithErrorType() throws Exception {
        Tokenizer t = clientFor(ex -> send(ex, 404,
            "{\"error_type\":\"SCOPE_NOT_FOUND\",\"retriable\":false,\"message\":\"x\"}"));
        TokenizerFailure f = (TokenizerFailure)
            t.tokenize(UUID, PiiType.SSN, "x");
        assertEquals(TokenizerFailure.FailureKind.TERMINAL_ERROR, f.kind());
        assertTrue(f.detail().contains("SCOPE_NOT_FOUND"));
    }

    @Test
    void malformedBodyIsMalformed() throws Exception {
        Tokenizer t = clientFor(ex -> send(ex, 200, "{not-json"));
        TokenizerFailure f = (TokenizerFailure) t.tokenize(UUID, PiiType.SSN, "x");
        assertEquals(TokenizerFailure.FailureKind.MALFORMED_RESPONSE, f.kind());
    }

    @Test
    void missingFieldIsMalformed() throws Exception {
        Tokenizer t = clientFor(ex -> send(ex, 200, "{\"nope\":1}"));
        TokenizerFailure f = (TokenizerFailure) t.tokenize(UUID, PiiType.SSN, "x");
        assertEquals(TokenizerFailure.FailureKind.MALFORMED_RESPONSE, f.kind());
    }

    @Test
    void unknownDetokenizeTypeIsMalformed() throws Exception {
        Tokenizer t = clientFor(ex -> send(ex, 200,
            "{\"plaintext\":\"v\",\"type\":\"NOT_A_TYPE\"}"));
        TokenizerFailure f = (TokenizerFailure) t.detokenize(UUID, "tok");
        assertEquals(TokenizerFailure.FailureKind.MALFORMED_RESPONSE, f.kind());
    }

    @Test
    void oversizeBodyIsMalformed() throws Exception {
        StringBuilder sb = new StringBuilder("{\"token\":\"");
        sb.append("A".repeat(70 * 1024));
        sb.append("\"}");
        Tokenizer t = clientFor(ex -> send(ex, 200, sb.toString()));
        TokenizerFailure f = (TokenizerFailure) t.tokenize(UUID, PiiType.SSN, "x");
        assertEquals(TokenizerFailure.FailureKind.MALFORMED_RESPONSE, f.kind());
    }

    @Test
    void connectionRefusedIsUnreachable() throws Exception {
        // Build a pinned client pointed at a port with nothing listening.
        server = TlsTestSupport.startServer(
            TlsTestSupport.load("server.p12"), "server", ex -> send(ex, 200, "{}"));
        var cfg = TlsTestSupport.pinnedConfig(
            "https://127.0.0.1:1", 0, Duration.ofMillis(500));
        Tokenizer t = HttpPiiTokenizerClient.create(cfg);
        TokenizerFailure f = (TokenizerFailure) t.tokenize(UUID, PiiType.SSN, "x");
        assertEquals(TokenizerFailure.FailureKind.UNREACHABLE, f.kind());
    }

    @Test
    void badUuidIsTerminalWithoutNetwork() throws Exception {
        Tokenizer t = clientFor(ex -> send(ex, 200, "{\"token\":\"x\"}"));
        TokenizerFailure f = (TokenizerFailure) t.tokenize("not-a-uuid", PiiType.SSN, "x");
        assertEquals(TokenizerFailure.FailureKind.TERMINAL_ERROR, f.kind());
        assertTrue(f.detail().contains("uuid"));
    }

    @Test
    void requestTimeoutIsTimeout() throws Exception {
        // Handler sleeps longer than the client request timeout before responding.
        server = TlsTestSupport.startServer(
            TlsTestSupport.load("server.p12"), "server", ex -> {
                try { Thread.sleep(1200); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                send(ex, 200, "{\"token\":\"x\"}");
            });
        // Build config with a short request timeout so the test is fast.
        // pinnedConfig hardcodes 5 s requestTimeout, so construct directly.
        TokenizerEndpointConfig cfg = new TokenizerEndpointConfig(
            java.net.URI.create(TlsTestSupport.baseUri(server)),
            TlsTestSupport.trustOnly(TlsTestSupport.load("server.p12"), "server"),
            TlsTestSupport.load("client.p12"),
            TlsTestSupport.PASS,
            java.time.Duration.ofSeconds(2),
            java.time.Duration.ofMillis(300),
            java.time.Duration.ofSeconds(900),
            0,
            java.time.Duration.ofMillis(750));
        Tokenizer t = HttpPiiTokenizerClient.create(cfg);
        TokenizerFailure f = (TokenizerFailure) t.tokenize(UUID, PiiType.SSN, "x");
        assertEquals(TokenizerFailure.FailureKind.TIMEOUT, f.kind());
    }
}
