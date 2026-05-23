package dev.vestitus.inspect.llmguard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.vestitus.inspect.ReasonCode;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * The shipped {@link LlmGuardScannerApi}: 1:1 over the pinned {@code
 * llm-guard-api} v0.1.x analyze contract. Builds its OWN pinned-mTLS {@link
 * SSLContext} (no mesh trust), retries ONLY transport errors within a hard
 * latency budget, parses strictly, NEVER throws (every op is a whole-body
 * {@code try/catch(Throwable)} returning an {@link AnalyzeOutcome.Failed}).
 *
 * <p>Inv. 13: {@code Failed.reason()} carries ONLY a stable reason code (and
 * an HTTP-status or exception-class qualifier). It NEVER carries the request
 * body, response body substring, header value, or any matched value.
 */
public final class HttpLlmGuardClient implements LlmGuardScannerApi {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final int MAX_BODY_BYTES = 64 * 1024;
    private static final Duration BACKOFF = Duration.ofMillis(50);

    private final HttpClient http;
    private final LlmGuardEndpointConfig cfg;

    HttpLlmGuardClient(HttpClient http, LlmGuardEndpointConfig cfg) {
        this.http = Objects.requireNonNull(http, "http");
        this.cfg = Objects.requireNonNull(cfg, "cfg");
    }

    /** Builds the pinned-mTLS HttpClient from {@code cfg} and constructs the client. */
    public static HttpLlmGuardClient create(LlmGuardEndpointConfig cfg) {
        Objects.requireNonNull(cfg, "cfg");
        try {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(cfg.clientIdentity(), cfg.clientKeyPassword());
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(cfg.pinnedServerTrust());
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(cfg.connectTimeout())
                .sslContext(ctx)
                .build();
            return new HttpLlmGuardClient(client, cfg);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "failed to build pinned-mTLS client from config: "
                    + e.getClass().getSimpleName(), e);
        }
    }

    @Override
    public AnalyzeOutcome analyze(String scannerName, String body) {
        try {
            if (scannerName == null || scannerName.isBlank())
                return failed("llmguard.bad_request");
            if (body == null)
                return failed("llmguard.bad_request");
            ObjectNode req = MAPPER.createObjectNode();
            req.put("prompt", body);
            req.putArray("scanners").add(scannerName);
            Resp r = call(req);
            if (r.failure != null) return new AnalyzeOutcome.Failed(r.failure);
            JsonNode n = parse(r.body);
            if (n == null) return failed("llmguard.malformed");
            JsonNode scores = n.get("scanner-scores");
            if (scores == null || !scores.isObject())
                return failed("llmguard.malformed");
            Map<String, Double> out = new HashMap<>();
            for (Iterator<Map.Entry<String, JsonNode>> it = scores.fields();
                    it.hasNext(); ) {
                Map.Entry<String, JsonNode> e = it.next();
                if (!e.getValue().isNumber())
                    return failed("llmguard.malformed");
                out.put(e.getKey(), e.getValue().doubleValue());
            }
            return new AnalyzeOutcome.Scores(out);
        } catch (Throwable t) {
            return failed("llmguard.unreachable");
        }
    }

    // ---- internals -------------------------------------------------------

    private Resp call(ObjectNode body) {
        long deadlineNanos = System.nanoTime() + cfg.latencyBudget().toNanos();
        int attempts = cfg.maxRetries() + 1;
        Resp last = null;
        for (int i = 0; i < attempts; i++) {
            Resp r = once(body);
            if (r.failure != null && !r.transportError)
                return r; // non-retriable failure (oversize / interrupted / other)
            if (r.failure == null && r.status >= 200 && r.status < 300)
                return r; // success
            if (r.failure == null) {
                // non-2xx — terminal, do NOT retry.
                return new Resp(new ReasonCode(
                    "llmguard.terminal_status_" + r.status));
            }
            last = r;
            long remaining = deadlineNanos - System.nanoTime();
            if (i == attempts - 1 || remaining <= 0)
                return last;
            long sleep = Math.min(BACKOFF.toNanos(), remaining);
            try {
                Thread.sleep(sleep / 1_000_000L, (int) (sleep % 1_000_000L));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return new Resp(new ReasonCode("llmguard.unreachable"));
            }
        }
        return last;
    }

    private Resp once(ObjectNode body) {
        try {
            HttpRequest req = HttpRequest.newBuilder(cfg.endpoint())
                .timeout(cfg.requestTimeout())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                    MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
            HttpResponse<byte[]> resp =
                http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            byte[] b = resp.body();
            if (b != null && b.length > MAX_BODY_BYTES)
                return new Resp(new ReasonCode("llmguard.malformed"));
            return new Resp(resp.statusCode(),
                b == null ? "" : new String(b, StandardCharsets.UTF_8));
        } catch (java.net.http.HttpTimeoutException te) {
            Resp r = new Resp(new ReasonCode("llmguard.timeout"));
            r.transportError = true;
            return r;
        } catch (java.io.IOException io) {
            Resp r = new Resp(new ReasonCode("llmguard.unreachable"));
            r.transportError = true;
            return r;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return new Resp(new ReasonCode("llmguard.unreachable"));
        } catch (Throwable t) {
            return new Resp(new ReasonCode("llmguard.unreachable"));
        }
    }

    private static JsonNode parse(String body) {
        try {
            return MAPPER.readTree(body);
        } catch (Exception e) {
            return null;
        }
    }

    private static AnalyzeOutcome.Failed failed(String code) {
        return new AnalyzeOutcome.Failed(new ReasonCode(code));
    }

    /** Internal one-attempt result; never escapes the class. */
    private static final class Resp {
        final int status;
        final String body;
        final ReasonCode failure;
        boolean transportError;

        Resp(int status, String body) {
            this.status = status; this.body = body; this.failure = null;
        }
        Resp(ReasonCode failure) {
            this.status = -1; this.body = ""; this.failure = failure;
        }
    }
}
