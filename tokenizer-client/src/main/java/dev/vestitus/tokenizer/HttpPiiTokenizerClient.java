package dev.vestitus.tokenizer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The shipped {@link Tokenizer}: 1:1 over the published pii-tokenizer v0.1.0
 * REST contract. Builds its OWN pinned-mTLS {@link SSLContext} (no mesh trust),
 * retries only envelope-{@code retriable} errors within a hard latency budget,
 * parses strictly, and NEVER throws (every op is a whole-body
 * try/catch(Throwable) returning a {@link TokenizerFailure}).
 */
public final class HttpPiiTokenizerClient implements Tokenizer {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final int MAX_BODY_BYTES = 64 * 1024;
    private static final Duration BACKOFF = Duration.ofMillis(50);
    private static final Pattern UUID_RE = Pattern.compile(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final HttpClient http;
    private final TokenizerEndpointConfig cfg;

    HttpPiiTokenizerClient(HttpClient http, TokenizerEndpointConfig cfg) {
        this.http = Objects.requireNonNull(http, "http");
        this.cfg = Objects.requireNonNull(cfg, "cfg");
    }

    /** Builds the pinned-mTLS HttpClient from {@code cfg} and constructs the client. */
    public static HttpPiiTokenizerClient create(TokenizerEndpointConfig cfg) {
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
            return new HttpPiiTokenizerClient(client, cfg);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "failed to build pinned-mTLS client from config: "
                + e.getClass().getSimpleName(), e);
        }
    }

    @Override
    public SessionOutcome beginSession(String uuid, Duration ttl) {
        try {
            if (badUuid(uuid))
                return new TokenizerFailure(
                    TokenizerFailure.FailureKind.TERMINAL_ERROR, "invalid uuid");
            if (ttl == null || ttl.toSeconds() < 1 || ttl.toSeconds() > 86400)
                return new TokenizerFailure(
                    TokenizerFailure.FailureKind.TERMINAL_ERROR,
                    "ttl out of range");
            ObjectNode body = MAPPER.createObjectNode();
            body.put("request_uuid", uuid);
            body.put("ttl_seconds", ttl.toSeconds());
            Resp r = call("/v1/init_request", body);
            if (r.failure != null) return r.failure;
            JsonNode n = parse(r.body);
            if (n == null) return malformed("init body unparseable");
            JsonNode exp = n.get("expires_at");
            if (exp == null || !exp.isTextual())
                return malformed("init missing expires_at");
            Instant when;
            try {
                when = Instant.parse(exp.asText());
            } catch (Exception pe) {
                return malformed("init expires_at not an instant");
            }
            return new SessionOutcome.SessionOpened(when);
        } catch (Throwable t) {
            return new TokenizerFailure(
                TokenizerFailure.FailureKind.UNREACHABLE,
                "beginSession error: " + t.getClass().getSimpleName());
        }
    }

    @Override
    public TokenizeOutcome tokenize(String uuid, PiiType type, String target) {
        try {
            if (badUuid(uuid))
                return new TokenizerFailure(
                    TokenizerFailure.FailureKind.TERMINAL_ERROR, "invalid uuid");
            if (type == null || target == null)
                return new TokenizerFailure(
                    TokenizerFailure.FailureKind.TERMINAL_ERROR,
                    "type/target required");
            ObjectNode body = MAPPER.createObjectNode();
            body.put("request_uuid", uuid);
            body.put("type", type.name());
            body.put("plaintext", target);
            Resp r = call("/v1/tokenize", body);
            if (r.failure != null) return r.failure;
            JsonNode n = parse(r.body);
            if (n == null) return malformed("tokenize body unparseable");
            JsonNode tok = n.get("token");
            if (tok == null || !tok.isTextual() || tok.asText().isBlank())
                return malformed("tokenize missing token");
            return new TokenizeOutcome.Tokenized(tok.asText());
        } catch (Throwable t) {
            return new TokenizerFailure(
                TokenizerFailure.FailureKind.UNREACHABLE,
                "tokenize error: " + t.getClass().getSimpleName());
        }
    }

    @Override
    public DetokenizeOutcome detokenize(String uuid, String token) {
        try {
            if (badUuid(uuid))
                return new TokenizerFailure(
                    TokenizerFailure.FailureKind.TERMINAL_ERROR, "invalid uuid");
            if (token == null || token.isBlank())
                return new TokenizerFailure(
                    TokenizerFailure.FailureKind.TERMINAL_ERROR,
                    "token required");
            ObjectNode body = MAPPER.createObjectNode();
            body.put("request_uuid", uuid);
            body.put("token", token);
            Resp r = call("/v1/detokenize", body);
            if (r.failure != null) return r.failure;
            JsonNode n = parse(r.body);
            if (n == null) return malformed("detokenize body unparseable");
            JsonNode pt = n.get("plaintext");
            JsonNode ty = n.get("type");
            if (pt == null || !pt.isTextual() || ty == null || !ty.isTextual())
                return malformed("detokenize missing plaintext/type");
            PiiType parsed;
            try {
                parsed = PiiType.valueOf(ty.asText());
            } catch (IllegalArgumentException ue) {
                return malformed("detokenize unknown type");
            }
            return new DetokenizeOutcome.Detokenized(pt.asText(), parsed);
        } catch (Throwable t) {
            return new TokenizerFailure(
                TokenizerFailure.FailureKind.UNREACHABLE,
                "detokenize error: " + t.getClass().getSimpleName());
        }
    }

    @Override
    public SessionOutcome endSession(String uuid) {
        try {
            if (badUuid(uuid))
                return new TokenizerFailure(
                    TokenizerFailure.FailureKind.TERMINAL_ERROR, "invalid uuid");
            ObjectNode body = MAPPER.createObjectNode();
            body.put("request_uuid", uuid);
            Resp r = call("/v1/release_request", body);
            if (r.failure != null) return r.failure;
            return new SessionOutcome.SessionEnded();
        } catch (Throwable t) {
            return new TokenizerFailure(
                TokenizerFailure.FailureKind.UNREACHABLE,
                "endSession error: " + t.getClass().getSimpleName());
        }
    }

    // ---- internals -------------------------------------------------------

    private static boolean badUuid(String uuid) {
        return uuid == null || !UUID_RE.matcher(uuid).matches();
    }

    /**
     * One POST with bounded retries. Status mapping happens HERE so a
     * {@code retriable:true} envelope is retried within the latency budget.
     * Returns either a {@code Resp} carrying a success 2xx/204 body or a
     * {@code Resp} carrying a terminal/exhausted {@link TokenizerFailure}.
     */
    private Resp call(String path, ObjectNode body) {
        long deadlineNanos = System.nanoTime() + cfg.latencyBudget().toNanos();
        int attempts = cfg.maxRetries() + 1;
        Resp last = null;
        for (int i = 0; i < attempts; i++) {
            Resp r = once(path, body);
            if (r.failure != null && !r.transportError)
                return r; // non-retriable transport-independent failure (e.g. oversize)
            boolean retry;
            if (r.failure != null) {
                retry = r.transportError; // timeout / IOException -> retry
            } else if (isSuccess(path, r.status)) {
                return r; // success
            } else {
                TokenizerFailure mapped = mapError(r);
                if (mapped.kind() == TokenizerFailure.FailureKind.RETRIABLE_EXHAUSTED) {
                    retry = true;
                } else {
                    return new Resp(mapped); // terminal / malformed
                }
            }
            last = r;
            long remaining = deadlineNanos - System.nanoTime();
            if (!retry || i == attempts - 1 || remaining <= 0) {
                // If the last attempt itself was a transport failure, propagate its
                // kind (UNREACHABLE/TIMEOUT) rather than wrapping as RETRIABLE_EXHAUSTED.
                if (last.failure != null && last.transportError)
                    return new Resp(last.failure);
                return new Resp(new TokenizerFailure(
                    TokenizerFailure.FailureKind.RETRIABLE_EXHAUSTED,
                    "retries exhausted (last status " + last.status + ")"));
            }
            long sleep = Math.min(BACKOFF.toNanos(), remaining);
            try {
                Thread.sleep(sleep / 1_000_000L, (int) (sleep % 1_000_000L));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return new Resp(new TokenizerFailure(
                    TokenizerFailure.FailureKind.UNREACHABLE,
                    "interrupted during retry backoff"));
            }
        }
        return last;
    }

    private static boolean isSuccess(String path, int status) {
        if (path.equals("/v1/release_request")) return status == 204;
        if (path.equals("/v1/init_request")) return status == 200 || status == 201;
        return status == 200;
    }

    /** Maps a non-success status via the error envelope. Inv. 13: never the message. */
    private TokenizerFailure mapError(Resp r) {
        JsonNode n = parse(r.body);
        if (n == null)
            return malformed("non-success status " + r.status
                + " with unparseable error body");
        JsonNode et = n.get("error_type");
        JsonNode rt = n.get("retriable");
        if (et == null || !et.isTextual() || rt == null || !rt.isBoolean())
            return malformed("non-success status " + r.status
                + " with malformed error envelope");
        if (rt.asBoolean())
            return new TokenizerFailure(
                TokenizerFailure.FailureKind.RETRIABLE_EXHAUSTED,
                "retriable " + et.asText() + " (status " + r.status + ")");
        return new TokenizerFailure(
            TokenizerFailure.FailureKind.TERMINAL_ERROR,
            et.asText() + " (status " + r.status + ")");
    }

    private Resp once(String path, ObjectNode body) {
        try {
            URI uri = cfg.endpoint().resolve(path);
            HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(cfg.requestTimeout())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                    MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
            HttpResponse<byte[]> resp =
                http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            byte[] b = resp.body();
            if (b != null && b.length > MAX_BODY_BYTES)
                return new Resp(new TokenizerFailure(
                    TokenizerFailure.FailureKind.MALFORMED_RESPONSE,
                    "response body exceeds 64 KiB cap"));
            return new Resp(resp.statusCode(),
                b == null ? "" : new String(b, StandardCharsets.UTF_8));
        } catch (java.net.http.HttpTimeoutException te) {
            Resp r = new Resp(new TokenizerFailure(
                TokenizerFailure.FailureKind.TIMEOUT, "connect/request timeout"));
            r.transportError = true;
            return r;
        } catch (java.io.IOException io) {
            Resp r = new Resp(new TokenizerFailure(
                TokenizerFailure.FailureKind.UNREACHABLE,
                io.getClass().getSimpleName()));
            r.transportError = true;
            return r;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return new Resp(new TokenizerFailure(
                TokenizerFailure.FailureKind.UNREACHABLE, "interrupted"));
        } catch (Throwable t) {
            return new Resp(new TokenizerFailure(
                TokenizerFailure.FailureKind.UNREACHABLE,
                t.getClass().getSimpleName()));
        }
    }

    private static JsonNode parse(String body) {
        try {
            return MAPPER.readTree(body);
        } catch (Exception e) {
            return null;
        }
    }

    private static TokenizerFailure malformed(String why) {
        return new TokenizerFailure(
            TokenizerFailure.FailureKind.MALFORMED_RESPONSE, why);
    }

    /** Internal one-attempt result; never escapes the class. */
    private static final class Resp {
        final int status;
        final String body;
        final TokenizerFailure failure;
        boolean transportError;

        Resp(int status, String body) {
            this.status = status; this.body = body; this.failure = null;
        }
        Resp(TokenizerFailure failure) {
            this.status = -1; this.body = ""; this.failure = failure;
        }
    }
}
