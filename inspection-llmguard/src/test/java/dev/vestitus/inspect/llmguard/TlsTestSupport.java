package dev.vestitus.inspect.llmguard;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.security.cert.Certificate;

/** Offline HTTPS + mTLS harness for inspection-llmguard tests. */
final class TlsTestSupport {

    static final char[] PASS = "changeit".toCharArray();

    private TlsTestSupport() {}

    static KeyStore load(String resource) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in =
                 TlsTestSupport.class.getResourceAsStream("/" + resource)) {
            if (in == null) throw new IllegalStateException("missing " + resource);
            ks.load(in, PASS);
        }
        return ks;
    }

    static KeyStore trustOnly(KeyStore from, String alias) throws Exception {
        Certificate cert = from.getCertificate(alias);
        KeyStore ts = KeyStore.getInstance("PKCS12");
        ts.load(null, null);
        ts.setCertificateEntry(alias, cert);
        return ts;
    }

    private static SSLContext context(KeyStore key, KeyStore trust) throws Exception {
        KeyManagerFactory kmf =
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(key, PASS);
        TrustManagerFactory tmf =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trust);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return ctx;
    }

    static HttpsServer startServer(KeyStore serverKs, String serverKeyAlias,
                                   com.sun.net.httpserver.HttpHandler handler)
            throws Exception {
        KeyStore clientTrust = trustOnly(load("client.p12"), "client");
        SSLContext ctx = context(serverKs, clientTrust);
        HttpsServer server =
            HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(ctx) {
            @Override public void configure(HttpsParameters params) {
                SSLParameters p = ctx.getDefaultSSLParameters();
                p.setNeedClientAuth(true);
                params.setSSLParameters(p);
            }
        });
        server.createContext("/", handler);
        server.start();
        return server;
    }

    static String baseUri(HttpsServer s) {
        return "https://127.0.0.1:" + s.getAddress().getPort();
    }

    /** Config whose pinned trust is ONLY the {@code server.p12} certificate. */
    static LlmGuardEndpointConfig pinnedConfig(String baseUri, int maxRetries,
            java.time.Duration latencyBudget) throws Exception {
        KeyStore pinned = trustOnly(load("server.p12"), "server");
        KeyStore clientId = load("client.p12");
        return new LlmGuardEndpointConfig(
            java.net.URI.create(baseUri + "/analyze/prompt"),
            pinned, clientId, PASS,
            java.time.Duration.ofSeconds(2),
            java.time.Duration.ofSeconds(5),
            maxRetries, latencyBudget);
    }
}
