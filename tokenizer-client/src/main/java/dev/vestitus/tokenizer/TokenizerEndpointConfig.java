package dev.vestitus.tokenizer;

import java.net.URI;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Objects;

/**
 * Construction-time, eagerly validated endpoint config (the project
 * {@code VerificationConfig}/{@code ReloadConfig} idiom — a config error fails
 * fast at construction, never at the first request). This is the ONLY place
 * this module throws; configuration is a deployment error, not a runtime
 * outcome.
 *
 * <ul>
 *   <li>{@code endpoint} — scheme MUST be {@code https}. The service's own
 *       cluster {@code http}/mesh-TLS deployment is irrelevant; vestitus
 *       self-pins TLS and trusts no mesh.</li>
 *   <li>{@code pinnedServerTrust} — a keystore containing ONLY the pinned
 *       tokenizer server certificate (no system CAs).</li>
 *   <li>{@code clientIdentity}/{@code clientKeyPassword} — the client
 *       key/cert for mutual TLS.</li>
 * </ul>
 */
public record TokenizerEndpointConfig(
        URI endpoint,
        KeyStore pinnedServerTrust,
        KeyStore clientIdentity,
        char[] clientKeyPassword,
        Duration connectTimeout,
        Duration requestTimeout,
        Duration sessionTtl,
        int maxRetries,
        Duration latencyBudget) {

    public TokenizerEndpointConfig {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(pinnedServerTrust, "pinnedServerTrust");
        Objects.requireNonNull(clientIdentity, "clientIdentity");
        Objects.requireNonNull(clientKeyPassword, "clientKeyPassword");
        Objects.requireNonNull(connectTimeout, "connectTimeout");
        Objects.requireNonNull(requestTimeout, "requestTimeout");
        Objects.requireNonNull(sessionTtl, "sessionTtl");
        Objects.requireNonNull(latencyBudget, "latencyBudget");
        if (!"https".equalsIgnoreCase(endpoint.getScheme()))
            throw new IllegalArgumentException("endpoint scheme must be https");
        if (connectTimeout.isZero() || connectTimeout.isNegative())
            throw new IllegalArgumentException("connectTimeout must be positive");
        if (requestTimeout.isZero() || requestTimeout.isNegative())
            throw new IllegalArgumentException("requestTimeout must be positive");
        long ttl = sessionTtl.toSeconds();
        if (ttl < 1 || ttl > 86400)
            throw new IllegalArgumentException(
                "sessionTtl must be within 1..86400 seconds");
        if (maxRetries < 0)
            throw new IllegalArgumentException("maxRetries must be >= 0");
        if (latencyBudget.isZero() || latencyBudget.isNegative())
            throw new IllegalArgumentException("latencyBudget must be positive");
    }
}
