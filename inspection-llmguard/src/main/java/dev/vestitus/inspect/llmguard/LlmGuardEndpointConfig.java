package dev.vestitus.inspect.llmguard;

import java.net.URI;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Objects;

/**
 * Construction-time, eagerly validated endpoint config (the project
 * {@code TokenizerEndpointConfig}/{@code VerificationConfig}/{@code
 * ReloadConfig} idiom — a config error fails fast at construction, never at
 * the first request). This is the ONLY place this module throws; configuration
 * is a deployment error, not a runtime outcome.
 *
 * <ul>
 *   <li>{@code endpoint} — the absolute analyze URL the client POSTs to (e.g.
 *       {@code https://llm-guard.internal:8000/analyze/prompt}). Scheme MUST
 *       be {@code https}; vestitus self-pins TLS and trusts no mesh.</li>
 *   <li>{@code pinnedServerTrust} — a keystore containing ONLY the pinned
 *       llm-guard-api server certificate (no system CAs).</li>
 *   <li>{@code clientIdentity}/{@code clientKeyPassword} — the client
 *       key/cert for mutual TLS.</li>
 * </ul>
 */
public record LlmGuardEndpointConfig(
        URI endpoint,
        KeyStore pinnedServerTrust,
        KeyStore clientIdentity,
        char[] clientKeyPassword,
        Duration connectTimeout,
        Duration requestTimeout,
        int maxRetries,
        Duration latencyBudget) {

    public LlmGuardEndpointConfig {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(pinnedServerTrust, "pinnedServerTrust");
        Objects.requireNonNull(clientIdentity, "clientIdentity");
        Objects.requireNonNull(clientKeyPassword, "clientKeyPassword");
        Objects.requireNonNull(connectTimeout, "connectTimeout");
        Objects.requireNonNull(requestTimeout, "requestTimeout");
        Objects.requireNonNull(latencyBudget, "latencyBudget");
        if (!"https".equalsIgnoreCase(endpoint.getScheme()))
            throw new IllegalArgumentException("endpoint scheme must be https");
        if (connectTimeout.isZero() || connectTimeout.isNegative())
            throw new IllegalArgumentException("connectTimeout must be positive");
        if (requestTimeout.isZero() || requestTimeout.isNegative())
            throw new IllegalArgumentException("requestTimeout must be positive");
        if (maxRetries < 0)
            throw new IllegalArgumentException("maxRetries must be >= 0");
        if (latencyBudget.isZero() || latencyBudget.isNegative())
            throw new IllegalArgumentException("latencyBudget must be positive");
    }
}
