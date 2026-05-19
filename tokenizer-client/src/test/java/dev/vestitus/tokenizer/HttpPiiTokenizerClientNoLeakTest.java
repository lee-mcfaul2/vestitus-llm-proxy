package dev.vestitus.tokenizer;

import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class HttpPiiTokenizerClientNoLeakTest {

    private static final String UUID = "00000000-0000-0000-0000-000000000001";
    private static final String SECRET = "alice-SSN-123-45-6789";
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
    void failureDetailNeverEchoesSecretOrErrorMessage() throws Exception {
        // Error body whose message field contains the secret value.
        server = TlsTestSupport.startServer(
            TlsTestSupport.load("server.p12"), "server", ex -> send(ex, 400,
                "{\"error_type\":\"SCHEMA_VALIDATION_FAILED\",\"retriable\":false,"
                + "\"message\":\"" + SECRET + "\"}"));
        var cfg = TlsTestSupport.pinnedConfig(
            TlsTestSupport.baseUri(server), 0, Duration.ofMillis(750));
        Tokenizer t = HttpPiiTokenizerClient.create(cfg);
        TokenizerFailure f = (TokenizerFailure) t.tokenize(UUID, PiiType.SSN, SECRET);
        assertEquals(TokenizerFailure.FailureKind.TERMINAL_ERROR, f.kind());
        assertFalse(f.detail().contains(SECRET),
            "failure detail must not echo the message body or the target");
        assertTrue(f.detail().contains("SCHEMA_VALIDATION_FAILED"));
    }

    @Test
    void endSessionFailureIsReportedNotFatal() throws Exception {
        server = TlsTestSupport.startServer(
            TlsTestSupport.load("server.p12"), "server", ex -> send(ex, 500,
                "{\"error_type\":\"INTERNAL_ERROR\",\"retriable\":false,\"message\":\"x\"}"));
        var cfg = TlsTestSupport.pinnedConfig(
            TlsTestSupport.baseUri(server), 0, Duration.ofMillis(750));
        Tokenizer t = HttpPiiTokenizerClient.create(cfg);
        SessionOutcome o = t.endSession(UUID);
        // It is a failure value (caller may ignore it; TTL is the backstop),
        // never an exception, never a SessionEnded on a 500.
        assertInstanceOf(TokenizerFailure.class, o);
        assertFalse(o.ok());
    }
}
