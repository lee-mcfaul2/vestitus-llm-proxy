package dev.vestitus.authz;

/**
 * Pluggable authorization SPI (Kafka-style extension point — an enterprise
 * implements this in its own repo and selects it at build/config time).
 * NOT sealed: it is an open extension point. The decision type IS sealed.
 *
 * Contract: implementations MUST be fail-closed — return a {@code Deny} on
 * any uncertainty rather than throwing. {@link McpAuthorizerRegistry} treats a
 * thrown exception or a {@code null} return as {@code Deny} (defence in depth),
 * but implementations must not rely on that.
 */
public interface Authorizer {
    AuthorizationDecision authorize(AuthorizationRequest request);
}
