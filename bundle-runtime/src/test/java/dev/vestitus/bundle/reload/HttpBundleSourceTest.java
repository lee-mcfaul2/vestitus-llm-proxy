package dev.vestitus.bundle.reload;

import com.sun.net.httpserver.HttpServer;
import dev.vestitus.trust.Bundle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class HttpBundleSourceTest {

    private HttpServer server;

    private URI start(int status, String body, long delayMs) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/bundles", ex -> {
            try {
                if (delayMs > 0) Thread.sleep(delayMs);
            } catch (InterruptedException ignored) { }
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, b.length == 0 ? -1 : b.length);
            try (var os = ex.getResponseBody()) { os.write(b); }
        });
        server.start();
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/bundles");
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private static String b64(byte[] x) {
        return Base64.getEncoder().encodeToString(x);
    }

    private static HttpBundleSource src(URI u) {
        return new HttpBundleSource(u,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
            1_000_000, Duration.ofMillis(500));
    }

    @Test
    void twoHundredWithValidArrayYieldsFetched() throws Exception {
        String json = "[{\"payload\":\"" + b64(new byte[]{1, 2})
            + "\",\"signatureMaterial\":\"" + b64(new byte[]{3})
            + "\",\"sourceRef\":\"r1\"}]";
        URI u = start(200, json, 0);
        FetchResult r = src(u).fetch();
        assertInstanceOf(FetchResult.Fetched.class, r);
        var f = (FetchResult.Fetched) r;
        assertEquals(1, f.bundles().size());
        Bundle b = f.bundles().get(0);
        assertArrayEquals(new byte[]{1, 2}, b.payload());
        assertArrayEquals(new byte[]{3}, b.signatureMaterial());
        assertEquals("r1", b.sourceRef());
    }

    @Test
    void nonTwoHundredIsUnreachableNotThrown() throws Exception {
        URI u = start(503, "down", 0);
        FetchResult r = src(u).fetch();
        assertInstanceOf(FetchResult.Unreachable.class, r);
        assertTrue(((FetchResult.Unreachable) r).reason().contains("503"));
    }

    @Test
    void oversizeBodyIsUnreachable() throws Exception {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 5000; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"payload\":\"AA==\",\"signatureMaterial\":\"AA==\",\"sourceRef\":\"r\"}");
        }
        sb.append(']');
        URI u = start(200, sb.toString(), 0);
        HttpBundleSource s = new HttpBundleSource(u,
            HttpClient.newHttpClient(), 256, Duration.ofMillis(500));
        FetchResult r = s.fetch();
        assertInstanceOf(FetchResult.Unreachable.class, r);
        assertTrue(((FetchResult.Unreachable) r).reason().toLowerCase().contains("size"));
    }

    @Test
    void timeoutIsUnreachable() throws Exception {
        URI u = start(200, "[]", 1500);
        HttpBundleSource s = new HttpBundleSource(u,
            HttpClient.newHttpClient(), 1_000_000, Duration.ofMillis(150));
        FetchResult r = s.fetch();
        assertInstanceOf(FetchResult.Unreachable.class, r);
    }

    @Test
    void malformedJsonIsUnreachable() throws Exception {
        URI u = start(200, "{not-an-array}", 0);
        FetchResult r = src(u).fetch();
        assertInstanceOf(FetchResult.Unreachable.class, r);
    }

    @Test
    void emptyArrayIsUnreachableNotEmptyFetched() throws Exception {
        URI u = start(200, "[]", 0);
        FetchResult r = src(u).fetch();
        assertInstanceOf(FetchResult.Unreachable.class, r);
    }

    @Test
    void connectionRefusedIsUnreachable() {
        // Nothing listening on this port.
        HttpBundleSource s = new HttpBundleSource(
            URI.create("http://127.0.0.1:1/bundles"),
            HttpClient.newHttpClient(), 1_000_000, Duration.ofMillis(300));
        assertInstanceOf(FetchResult.Unreachable.class, s.fetch());
    }

    @Test
    void constructorRejectsNullArgs() {
        assertThrows(NullPointerException.class, () -> new HttpBundleSource(
            null, HttpClient.newHttpClient(), 10, Duration.ofMillis(1)));
        assertThrows(IllegalArgumentException.class, () -> new HttpBundleSource(
            URI.create("http://x/"), HttpClient.newHttpClient(), 0,
            Duration.ofMillis(1)));
    }
}
