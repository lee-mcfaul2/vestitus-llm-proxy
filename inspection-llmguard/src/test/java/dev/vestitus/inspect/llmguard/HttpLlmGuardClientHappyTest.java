package dev.vestitus.inspect.llmguard;

import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class HttpLlmGuardClientHappyTest {

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
    void analyzeReadsThePerScannerScoreFromANormalResponse() throws Exception {
        server = TlsTestSupport.startServer(
            TlsTestSupport.load("server.p12"), "server", ex ->
                send(ex, 200,
                    "{\"sanitized\":\"hello world\","
                        + "\"is_valid\":{\"PromptInjection\":true},"
                        + "\"scanner-scores\":{\"PromptInjection\":0.12}}"));
        var cfg = TlsTestSupport.pinnedConfig(
            TlsTestSupport.baseUri(server), 0, Duration.ofMillis(750));
        LlmGuardScannerApi api = HttpLlmGuardClient.create(cfg);

        AnalyzeOutcome o = api.analyze("PromptInjection", "hello world");
        AnalyzeOutcome.Scores s = assertInstanceOf(AnalyzeOutcome.Scores.class, o);
        assertEquals(0.12, s.byScanner().get("PromptInjection"));
    }

    @Test
    void analyzeReportsTheCorrectScoreWhenMultipleScannersAreReturned() throws Exception {
        server = TlsTestSupport.startServer(
            TlsTestSupport.load("server.p12"), "server", ex ->
                send(ex, 200,
                    "{\"sanitized\":\"x\",\"is_valid\":{},"
                        + "\"scanner-scores\":{\"PromptInjection\":0.9,\"Toxicity\":0.1}}"));
        var cfg = TlsTestSupport.pinnedConfig(
            TlsTestSupport.baseUri(server), 0, Duration.ofMillis(750));
        LlmGuardScannerApi api = HttpLlmGuardClient.create(cfg);

        AnalyzeOutcome.Scores s = (AnalyzeOutcome.Scores)
            api.analyze("PromptInjection", "x");
        assertEquals(0.9, s.byScanner().get("PromptInjection"));
        assertEquals(0.1, s.byScanner().get("Toxicity"));
    }

    @Test
    void unreachableEndpointFailsClosedAndReasonHasNoBodyText() throws Exception {
        var cfg = TlsTestSupport.pinnedConfig(
            "https://127.0.0.1:1", 0, Duration.ofMillis(100));
        LlmGuardScannerApi api = HttpLlmGuardClient.create(cfg);
        AnalyzeOutcome.Failed f = (AnalyzeOutcome.Failed)
            api.analyze("PromptInjection", "secret-body-text");
        assertNotNull(f.reason());
        assertFalse(f.reason().code().contains("secret-body-text"),
            "no request body text in failure reason");
    }
}
