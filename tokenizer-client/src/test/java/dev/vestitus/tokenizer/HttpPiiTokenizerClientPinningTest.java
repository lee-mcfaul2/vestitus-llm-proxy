package dev.vestitus.tokenizer;

import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class HttpPiiTokenizerClientPinningTest {

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
    void pinnedServerCertAccepted() throws Exception {
        server = TlsTestSupport.startServer(
            TlsTestSupport.load("server.p12"), "server",
            ex -> send(ex, 200, "{\"token\":\"ok\"}"));
        var cfg = TlsTestSupport.pinnedConfig(
            TlsTestSupport.baseUri(server), 0, Duration.ofMillis(750));
        Tokenizer t = HttpPiiTokenizerClient.create(cfg);
        assertInstanceOf(TokenizeOutcome.Tokenized.class,
            t.tokenize(UUID, PiiType.SSN, "x"));
    }

    @Test
    void rogueServerCertRejectedAsUnreachable() throws Exception {
        // Server presents rogue.p12; client still pins server.p12's cert.
        server = TlsTestSupport.startServer(
            TlsTestSupport.load("rogue.p12"), "rogue",
            ex -> send(ex, 200, "{\"token\":\"should-never-be-read\"}"));
        var cfg = TlsTestSupport.pinnedConfig(
            TlsTestSupport.baseUri(server), 0, Duration.ofMillis(750));
        Tokenizer t = HttpPiiTokenizerClient.create(cfg);
        TokenizerFailure f = (TokenizerFailure) t.tokenize(UUID, PiiType.SSN, "x");
        assertEquals(TokenizerFailure.FailureKind.UNREACHABLE, f.kind());
    }
}
