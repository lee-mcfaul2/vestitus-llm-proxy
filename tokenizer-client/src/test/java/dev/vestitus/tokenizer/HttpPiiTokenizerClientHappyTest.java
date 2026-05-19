package dev.vestitus.tokenizer;

import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class HttpPiiTokenizerClientHappyTest {

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

    @Test
    void fourOperationsHappyPathOverPinnedMtls() throws Exception {
        server = TlsTestSupport.startServer(
            TlsTestSupport.load("server.p12"), "server", ex -> {
                String path = ex.getRequestURI().getPath();
                switch (path) {
                    case "/v1/init_request" ->
                        send(ex, 201,
                            "{\"request_uuid\":\"00000000-0000-0000-0000-000000000001\",\"expires_at\":\"2026-05-19T12:00:00Z\"}");
                    case "/v1/tokenize" -> send(ex, 200, "{\"token\":\"tok_x\"}");
                    case "/v1/detokenize" ->
                        send(ex, 200, "{\"plaintext\":\"a@b.com\",\"type\":\"EMAIL\"}");
                    case "/v1/release_request" -> send(ex, 204, "");
                    default -> send(ex, 404, "{\"error_type\":\"SCOPE_NOT_FOUND\","
                        + "\"retriable\":false,\"message\":\"x\"}");
                }
            });
        var cfg = TlsTestSupport.pinnedConfig(
            TlsTestSupport.baseUri(server), 2, Duration.ofMillis(750));
        Tokenizer t = HttpPiiTokenizerClient.create(cfg);

        SessionOutcome b = t.beginSession(UUID, Duration.ofSeconds(900));
        assertInstanceOf(SessionOutcome.SessionOpened.class, b);

        TokenizeOutcome to = t.tokenize(UUID, PiiType.EMAIL, "a@b.com");
        assertEquals("tok_x", ((TokenizeOutcome.Tokenized) to).token());

        DetokenizeOutcome de = t.detokenize(UUID, "tok_x");
        var d = (DetokenizeOutcome.Detokenized) de;
        assertEquals("a@b.com", d.plaintext());
        assertEquals(PiiType.EMAIL, d.type());

        assertInstanceOf(SessionOutcome.SessionEnded.class, t.endSession(UUID));
    }

    @Test
    void initIdempotent200AlsoSucceeds() throws Exception {
        server = TlsTestSupport.startServer(
            TlsTestSupport.load("server.p12"), "server", ex ->
                send(ex, 200,
                    "{\"request_uuid\":\"00000000-0000-0000-0000-000000000001\",\"expires_at\":\"2026-05-19T12:00:00Z\"}"));
        var cfg = TlsTestSupport.pinnedConfig(
            TlsTestSupport.baseUri(server), 0, Duration.ofMillis(750));
        Tokenizer t = HttpPiiTokenizerClient.create(cfg);
        assertInstanceOf(SessionOutcome.SessionOpened.class,
            t.beginSession(UUID, Duration.ofSeconds(900)));
    }
}
