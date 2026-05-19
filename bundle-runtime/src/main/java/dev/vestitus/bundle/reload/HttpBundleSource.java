package dev.vestitus.bundle.reload;

import dev.vestitus.trust.Bundle;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * Default {@link BundleSource}: HTTP GET of a JSON array
 * {@code [{ "payload": <base64>, "signatureMaterial": <base64>,
 * "sourceRef": <string> }, ...]}. Fail-closed: every failure mode
 * (non-2xx, timeout, oversize, malformed, empty) maps to
 * {@link FetchResult.Unreachable}; it never throws through to the caller.
 *
 * <p>The JSON is parsed by a minimal hand-rolled scanner to avoid pulling a
 * JSON library into {@code bundle-runtime}. The wire shape is intentionally
 * tiny and strict; anything off-shape is rejected as Unreachable.
 */
public final class HttpBundleSource implements BundleSource {

    private final URI endpoint;
    private final HttpClient client;
    private final int maxBodyBytes;
    private final Duration requestTimeout;

    public HttpBundleSource(URI endpoint, HttpClient client,
                            int maxBodyBytes, Duration requestTimeout) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.client = Objects.requireNonNull(client, "client");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        if (maxBodyBytes <= 0) {
            throw new IllegalArgumentException("maxBodyBytes must be > 0");
        }
        this.maxBodyBytes = maxBodyBytes;
    }

    @Override
    public FetchResult fetch() {
        try {
            HttpRequest req = HttpRequest.newBuilder(endpoint)
                .timeout(requestTimeout)
                .GET()
                .build();
            HttpResponse<byte[]> resp =
                client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            int sc = resp.statusCode();
            if (sc < 200 || sc >= 300) {
                return new FetchResult.Unreachable("HTTP status " + sc);
            }
            byte[] body = resp.body();
            if (body == null) {
                return new FetchResult.Unreachable("empty body");
            }
            if (body.length > maxBodyBytes) {
                return new FetchResult.Unreachable(
                    "body size " + body.length + " exceeds cap " + maxBodyBytes);
            }
            List<Bundle> bundles = parse(new String(body, StandardCharsets.UTF_8));
            if (bundles.isEmpty()) {
                return new FetchResult.Unreachable("bundle array was empty");
            }
            return new FetchResult.Fetched(bundles);
        } catch (Throwable t) {
            return new FetchResult.Unreachable(
                t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    /** Strict minimal parser for the fixed wire shape. Any deviation throws. */
    private static List<Bundle> parse(String json) {
        String s = json.strip();
        if (s.isEmpty() || s.charAt(0) != '[' || s.charAt(s.length() - 1) != ']') {
            throw new IllegalArgumentException("body is not a JSON array");
        }
        String inner = s.substring(1, s.length() - 1).strip();
        List<Bundle> out = new ArrayList<>();
        if (inner.isEmpty()) {
            return out;
        }
        int i = 0;
        Base64.Decoder dec = Base64.getDecoder();
        while (i < inner.length()) {
            while (i < inner.length() && Character.isWhitespace(inner.charAt(i))) i++;
            if (i >= inner.length() || inner.charAt(i) != '{') {
                throw new IllegalArgumentException("expected object at " + i);
            }
            int end = inner.indexOf('}', i);
            if (end < 0) {
                throw new IllegalArgumentException("unterminated object");
            }
            String obj = inner.substring(i + 1, end);
            String payload = field(obj, "payload");
            String sig = field(obj, "signatureMaterial");
            String ref = field(obj, "sourceRef");
            out.add(new Bundle(dec.decode(payload), dec.decode(sig), ref));
            i = end + 1;
            while (i < inner.length() && Character.isWhitespace(inner.charAt(i))) i++;
            if (i < inner.length()) {
                if (inner.charAt(i) != ',') {
                    throw new IllegalArgumentException("expected ',' at " + i);
                }
                i++;
            }
        }
        return out;
    }

    private static String field(String obj, String key) {
        String marker = "\"" + key + "\"";
        int k = obj.indexOf(marker);
        if (k < 0) {
            throw new IllegalArgumentException("missing field " + key);
        }
        int colon = obj.indexOf(':', k + marker.length());
        if (colon < 0) {
            throw new IllegalArgumentException("malformed field " + key);
        }
        int q1 = obj.indexOf('"', colon + 1);
        if (q1 < 0) {
            throw new IllegalArgumentException("unquoted value for " + key);
        }
        int q2 = obj.indexOf('"', q1 + 1);
        if (q2 < 0) {
            throw new IllegalArgumentException("unterminated value for " + key);
        }
        return obj.substring(q1 + 1, q2);
    }
}
