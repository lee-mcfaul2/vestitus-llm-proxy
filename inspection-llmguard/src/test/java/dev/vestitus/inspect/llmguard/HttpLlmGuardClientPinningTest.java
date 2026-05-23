package dev.vestitus.inspect.llmguard;

import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class HttpLlmGuardClientPinningTest {

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
    void aRogueServerCertificateIsRejectedByThePinnedTrust() throws Exception {
        // The HTTPS server presents the ROGUE cert; the client's pinned trust
        // contains ONLY the SERVER cert. TLS handshake must fail; the client
        // surfaces a transport-class failure (no exception propagates).
        server = TlsTestSupport.startServer(
            TlsTestSupport.load("rogue.p12"), "rogue", ex ->
                send(ex, 200, "{\"scanner-scores\":{\"X\":0.1}}"));
        var cfg = TlsTestSupport.pinnedConfig(
            TlsTestSupport.baseUri(server), 0, Duration.ofMillis(750));
        LlmGuardScannerApi api = HttpLlmGuardClient.create(cfg);
        AnalyzeOutcome.Failed f = (AnalyzeOutcome.Failed)
            api.analyze("X", "x");
        // Either timeout or unreachable — both are transport-class failures
        // surfaced as a stable llmguard.* reason code; never as an exception.
        assertTrue(f.reason().code().startsWith("llmguard."),
            "rogue cert must produce a stable llmguard.* reason");
    }
}
