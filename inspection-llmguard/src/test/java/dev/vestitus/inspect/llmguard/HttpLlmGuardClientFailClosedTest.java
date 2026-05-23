package dev.vestitus.inspect.llmguard;

import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class HttpLlmGuardClientFailClosedTest {

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
    void unreachableEndpointFailsClosedWithStableReason() throws Exception {
        var cfg = TlsTestSupport.pinnedConfig(
            "https://127.0.0.1:1", 0, Duration.ofMillis(200));
        LlmGuardScannerApi api = HttpLlmGuardClient.create(cfg);
        AnalyzeOutcome.Failed f = (AnalyzeOutcome.Failed)
            api.analyze("PromptInjection", "x");
        assertTrue(f.reason().code().startsWith("llmguard."));
    }

    @Test
    void malformedJsonResponseIsFailClosed() throws Exception {
        server = TlsTestSupport.startServer(
            TlsTestSupport.load("server.p12"), "server", ex ->
                send(ex, 200, "this is not json"));
        var cfg = TlsTestSupport.pinnedConfig(
            TlsTestSupport.baseUri(server), 0, Duration.ofMillis(750));
        LlmGuardScannerApi api = HttpLlmGuardClient.create(cfg);
        AnalyzeOutcome.Failed f = (AnalyzeOutcome.Failed)
            api.analyze("PromptInjection", "x");
        assertEquals("llmguard.malformed", f.reason().code());
    }

    @Test
    void missingScannerScoresKeyIsFailClosed() throws Exception {
        server = TlsTestSupport.startServer(
            TlsTestSupport.load("server.p12"), "server", ex ->
                send(ex, 200,
                    "{\"sanitized\":\"x\",\"is_valid\":{}}"));
        var cfg = TlsTestSupport.pinnedConfig(
            TlsTestSupport.baseUri(server), 0, Duration.ofMillis(750));
        LlmGuardScannerApi api = HttpLlmGuardClient.create(cfg);
        AnalyzeOutcome.Failed f = (AnalyzeOutcome.Failed)
            api.analyze("PromptInjection", "x");
        assertEquals("llmguard.malformed", f.reason().code());
    }

    @Test
    void oversizeResponseBodyIsFailClosed() throws Exception {
        // 65 KiB - just over the 64 KiB cap.
        StringBuilder big = new StringBuilder(65 * 1024 + 64);
        big.append("{\"scanner-scores\":{\"X\":0.1},\"pad\":\"");
        while (big.length() < 65 * 1024) big.append('x');
        big.append("\"}");
        final String payload = big.toString();
        server = TlsTestSupport.startServer(
            TlsTestSupport.load("server.p12"), "server", ex ->
                send(ex, 200, payload));
        var cfg = TlsTestSupport.pinnedConfig(
            TlsTestSupport.baseUri(server), 0, Duration.ofMillis(750));
        LlmGuardScannerApi api = HttpLlmGuardClient.create(cfg);
        AnalyzeOutcome.Failed f = (AnalyzeOutcome.Failed)
            api.analyze("X", "x");
        assertEquals("llmguard.malformed", f.reason().code());
    }

    @Test
    void terminalStatusIsNeverRetriedAndCarriesTheStatusInTheReason() throws Exception {
        server = TlsTestSupport.startServer(
            TlsTestSupport.load("server.p12"), "server", ex ->
                send(ex, 500, "internal"));
        var cfg = TlsTestSupport.pinnedConfig(
            TlsTestSupport.baseUri(server), 2, Duration.ofMillis(750));
        LlmGuardScannerApi api = HttpLlmGuardClient.create(cfg);
        AnalyzeOutcome.Failed f = (AnalyzeOutcome.Failed)
            api.analyze("X", "x");
        assertEquals("llmguard.terminal_status_500", f.reason().code());
    }

    @Test
    void failureReasonNeverContainsRequestOrResponseBodyText() throws Exception {
        String marker = "SECRET_BODY_MARKER";
        server = TlsTestSupport.startServer(
            TlsTestSupport.load("server.p12"), "server", ex ->
                send(ex, 500, marker));
        var cfg = TlsTestSupport.pinnedConfig(
            TlsTestSupport.baseUri(server), 0, Duration.ofMillis(750));
        LlmGuardScannerApi api = HttpLlmGuardClient.create(cfg);
        AnalyzeOutcome.Failed f = (AnalyzeOutcome.Failed)
            api.analyze("X", marker);
        assertFalse(f.reason().code().contains(marker),
            "failure reason must not echo body content");
    }
}
