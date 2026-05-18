package dev.vestitus.authz;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ADR-003 D8 atomic generational holder over immutable {@link
 * McpAuthorizerRegistry} generations. Cold start denies all (empty deny-all
 * generation, fail-closed per ADR-003 D9) until the first {@link #install}.
 * {@link #currentGeneration()} is the per-request INGRESS SNAPSHOT: a request
 * pins one immutable generation at ingress and uses it for its whole lifetime,
 * so a concurrent {@link #install} can never perturb an in-flight request (no
 * generation straddle). {@code install} is the ATOMIC PUBLISH; building the
 * complete next generation from ALL verified bundles (build-all-N-or-none) and
 * NOT installing on any failure (prior generation retained) is the caller's
 * contract (Plan 05h) — this holder guarantees only that the swap itself is
 * atomic and a fully-constructed generation is happens-before-visible to
 * subsequent readers.
 */
public final class GenerationalRegistry {

    private final AtomicReference<McpAuthorizerRegistry> current =
        new AtomicReference<>(McpAuthorizerRegistry.of(Map.of()));

    /** The current immutable generation — snapshot ONCE per request at ingress. */
    public McpAuthorizerRegistry currentGeneration() {
        return current.get();
    }

    /** Atomically publish a fully-built next generation (build-all-or-none is the caller's). */
    public void install(McpAuthorizerRegistry next) {
        if (next == null)
            throw new IllegalArgumentException("next generation required");
        current.set(next);
    }

    /**
     * Convenience fail-closed delegate to the CURRENT generation. NOTE: a
     * request handler MUST instead snapshot once via {@link #currentGeneration()}
     * at ingress and authorize against that snapshot for its whole lifetime, to
     * avoid a mid-request generation straddle (ADR-003 D8).
     */
    public AuthorizationDecision authorize(String mcpId, AuthorizationRequest request) {
        return current.get().authorize(mcpId, request);
    }
}
