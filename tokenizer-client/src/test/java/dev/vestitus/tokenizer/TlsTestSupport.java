package dev.vestitus.tokenizer;

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

/** Offline HTTPS + mTLS harness for tokenizer-client tests. */
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

    /** A truststore holding only {@code alias}'s certificate from {@code from}. */
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

    /**
     * Starts a loopback HTTPS server presenting {@code serverKeyAlias} from
     * {@code serverKs}, requiring client auth, trusting only the {@code client}
     * cert. Returns the started server (caller stops it).
     */
    static HttpsServer startServer(KeyStore serverKs, String serverKeyAlias,
                                   com.sun.net.httpserver.HttpHandler handler)
            throws Exception {
        KeyStore clientTrust = trustOnly(load("client.p12"), "client");
        // Server key store must expose its key under the default alias the
        // KeyManager picks; the p12 has exactly one entry so this is fine.
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
    static TokenizerEndpointConfig pinnedConfig(String baseUri,
            int maxRetries, java.time.Duration latencyBudget) throws Exception {
        KeyStore pinned = trustOnly(load("server.p12"), "server");
        KeyStore clientId = load("client.p12");
        return new TokenizerEndpointConfig(
            java.net.URI.create(baseUri),
            pinned, clientId, PASS,
            java.time.Duration.ofSeconds(2),
            java.time.Duration.ofSeconds(5),
            java.time.Duration.ofSeconds(900),
            maxRetries, latencyBudget);
    }
}
