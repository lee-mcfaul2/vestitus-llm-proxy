package dev.vestitus.bundle.reload;

import dev.vestitus.authz.GenerationalRegistry;
import dev.vestitus.authz.McpAuthorizerRegistry;
import dev.vestitus.authz.PolicyCompiler;
import dev.vestitus.authz.RegistryEntry;
import dev.vestitus.bundle.NoRollbackGate;
import dev.vestitus.bundle.VersionDecision;
import dev.vestitus.bundle.gate.GateVerdict;
import dev.vestitus.bundle.gate.StructuralGate;
import dev.vestitus.mcpschema.McpSchema;
import dev.vestitus.trust.Bundle;
import dev.vestitus.trust.BundleDigester;
import dev.vestitus.trust.BundleVerifier;
import dev.vestitus.trust.BundleVersion;
import dev.vestitus.trust.VerificationOutcome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * ADR-003 runtime-pull capstone. Fetch -&gt; per-bundle verify -&gt; digest
 * -&gt; aggregate -&gt; version-gate -&gt; structural-gate -&gt; compile -&gt;
 * atomic install, ALL-N-or-NONE and fail-closed. The prior generation stays
 * live on ANY abort. The per-request authorize path ({@link
 * GenerationalRegistry}) is untouched here.
 */
public final class BundleReloadOrchestrator {

    private final BundleSource source;
    private final BundleVerifier verifier;
    private final BundleDigester digester;
    private final NoRollbackGate noRollbackGate;
    private final PolicyCompiler policyCompiler;
    private final GenerationalRegistry registry;
    private final ReloadConfig config;
    private final MonotonicClock clock;
    private final ReloadObserver observer;

    /** Monotonic nanos of the last successful apply; empty == none yet. */
    private OptionalLong lastSuccessfulApplyNanos = OptionalLong.empty();

    public BundleReloadOrchestrator(BundleSource source,
                                    BundleVerifier verifier,
                                    BundleDigester digester,
                                    NoRollbackGate noRollbackGate,
                                    PolicyCompiler policyCompiler,
                                    GenerationalRegistry registry,
                                    ReloadConfig config,
                                    MonotonicClock clock,
                                    ReloadObserver observer) {
        this.source = Objects.requireNonNull(source, "source");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.digester = Objects.requireNonNull(digester, "digester");
        this.noRollbackGate = Objects.requireNonNull(noRollbackGate, "noRollbackGate");
        this.policyCompiler = Objects.requireNonNull(policyCompiler, "policyCompiler");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.config = Objects.requireNonNull(config, "config");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.observer = Objects.requireNonNull(observer, "observer");
    }

    /** One reload attempt. Returns the outcome; the retry wrapper interprets it. */
    public ReloadOutcome attemptReload() {
        // Step 1: fetch.
        FetchResult fetched;
        try {
            fetched = source.fetch();
        } catch (Throwable t) {
            fetched = new FetchResult.Unreachable(
                "source threw: " + t.getClass().getSimpleName());
        }
        if (fetched instanceof FetchResult.Unreachable u) {
            observer.onFetchUnreachable(u.reason());
            return handleUnreachable(u.reason());
        }
        List<Bundle> bundles = ((FetchResult.Fetched) fetched).bundles();

        // Steps 2-4: verify + digest each bundle; aggregate.
        List<McpSchema> allSchemas = new ArrayList<>();
        Map<String, String> mcpIdToSubject = new HashMap<>();
        BundleVersion setVersion = null;
        for (Bundle b : bundles) {
            VerificationOutcome vo;
            try {
                vo = verifier.verify(b, config.verificationConfig());
            } catch (Throwable t) {
                vo = VerificationOutcome.rejected(
                    "verifier threw: " + t.getClass().getSimpleName());
            }
            if (vo == null || !vo.isVerified()) {
                String reason = (vo instanceof VerificationOutcome.Rejected r)
                    ? r.reason() : "verifier returned non-verified";
                observer.onVerifyRejected(reason);
                return ReloadOutcome.ABORTED_RETRYABLE;
            }
            var verified = (VerificationOutcome.Verified) vo;

            // Step 5 (per-bundle): all verified versions must agree.
            if (setVersion == null) {
                setVersion = verified.version();
            } else if (setVersion.value() != verified.version().value()) {
                observer.onAggregateRejected(
                    "bundles disagree on version: "
                        + setVersion.value() + " vs " + verified.version().value());
                return ReloadOutcome.ABORTED_RETRYABLE;
            }

            // Step 3: digest.
            List<McpSchema> schemas;
            try {
                schemas = digester.digest(verified.authenticatedPayload());
            } catch (Throwable t) {
                observer.onAggregateRejected(
                    "digest failed: " + t.getClass().getSimpleName());
                return ReloadOutcome.ABORTED_RETRYABLE;
            }

            // subject<->mcpId bind: every digested mcpId is associated with
            // THIS verified publisher subject. A second bundle re-binding an
            // already-seen mcpId is rejected (cross-bundle smuggling guard);
            // the ultimate duplicate-mcpId rejection is also enforced by
            // McpAuthorizerRegistry.ofEntries below (defence in depth).
            for (McpSchema s : schemas) {
                String prior = mcpIdToSubject.putIfAbsent(s.mcpId(), verified.subjectId());
                if (prior != null) {
                    observer.onAggregateRejected(
                        "duplicate mcpId across bundles: " + s.mcpId());
                    return ReloadOutcome.ABORTED_RETRYABLE;
                }
            }
            allSchemas.addAll(schemas);
        }

        // Step 5: no-rollback gate on the single authenticated set version.
        if (setVersion == null) {
            observer.onAggregateRejected("no verified version");
            return ReloadOutcome.ABORTED_RETRYABLE;
        }
        VersionDecision vd;
        try {
            vd = noRollbackGate.evaluate(setVersion);
        } catch (Throwable t) {
            vd = VersionDecision.reject("gate threw: " + t.getClass().getSimpleName());
        }
        if (!vd.accepted()) {
            String reason = (vd instanceof VersionDecision.Reject r)
                ? r.reason() : "version rejected";
            observer.onAggregateRejected(reason);
            return ReloadOutcome.ABORTED_RETRYABLE;
        }

        // Step 6: structural gate over the whole assembled image.
        GateVerdict gv;
        try {
            gv = StructuralGate.vet(allSchemas);
        } catch (Throwable t) {
            gv = GateVerdict.reject("structural gate threw: "
                + t.getClass().getSimpleName());
        }
        if (!gv.passed()) {
            String reason = (gv instanceof GateVerdict.Reject r)
                ? String.join("; ", r.reasons()) : "structural gate rejected";
            observer.onAggregateRejected(reason);
            return ReloadOutcome.ABORTED_RETRYABLE;
        }

        // Step 7: compile every schema.
        List<RegistryEntry> entries = new ArrayList<>();
        for (McpSchema s : allSchemas) {
            try {
                entries.add(new RegistryEntry(s.mcpId(), policyCompiler.compile(s)));
            } catch (Throwable t) {
                observer.onAggregateRejected(
                    "compile failed for " + s.mcpId() + ": "
                        + t.getClass().getSimpleName());
                return ReloadOutcome.ABORTED_RETRYABLE;
            }
        }

        // Step 8: build the next registry (duplicate mcpId => IAE => abort).
        McpAuthorizerRegistry next;
        try {
            next = McpAuthorizerRegistry.ofEntries(entries);
        } catch (Throwable t) {
            observer.onAggregateRejected(
                "registry assembly failed: " + t.getClass().getSimpleName());
            return ReloadOutcome.ABORTED_RETRYABLE;
        }

        // Step 9: atomic install — only now.
        registry.install(next);
        lastSuccessfulApplyNanos = OptionalLong.of(clock.nanos());
        observer.onApplied(setVersion, entries.size());
        return ReloadOutcome.APPLIED;
    }

    private ReloadOutcome handleUnreachable(String reason) {
        long now = clock.nanos();
        if (lastSuccessfulApplyNanos.isPresent()) {
            long elapsed = now - lastSuccessfulApplyNanos.getAsLong();
            long windowNanos = config.lastGoodWindow().toNanos();
            if (elapsed <= windowNanos) {
                observer.onRetainedLastGood(
                    "unreachable within last-good window: " + reason);
                return ReloadOutcome.RETAINED_LAST_GOOD;
            }
        }
        // Cold start (no prior apply) OR beyond the window => fail-closed.
        registry.install(McpAuthorizerRegistry.of(Map.of()));
        observer.onFailedClosed("unreachable beyond window / cold start: " + reason);
        return ReloadOutcome.FAILED_CLOSED;
    }

    /**
     * Thin retry wrapper. Drives {@link #attemptReload()} up to
     * {@code config.maxRetries()} extra times on a retryable abort. Backoff is
     * delegated to the injectable sleeper so tests never really sleep.
     */
    public ReloadOutcome runWithRetries(Sleeper sleeper) {
        Objects.requireNonNull(sleeper, "sleeper");
        ReloadOutcome last = attemptReload();
        int attempts = 0;
        while (last == ReloadOutcome.ABORTED_RETRYABLE
                && attempts < config.maxRetries()) {
            sleeper.sleep(config.retryBackoff().toMillis());
            last = attemptReload();
            attempts++;
        }
        if (last == ReloadOutcome.ABORTED_RETRYABLE) {
            // Exhausted retries with no success.
            if (lastSuccessfulApplyNanos.isPresent()) {
                long elapsed = clock.nanos() - lastSuccessfulApplyNanos.getAsLong();
                if (elapsed <= config.lastGoodWindow().toNanos()) {
                    observer.onRetainedLastGood("retries exhausted, within window");
                    return ReloadOutcome.RETAINED_LAST_GOOD;
                }
            }
            registry.install(McpAuthorizerRegistry.of(Map.of()));
            observer.onFailedClosed("retries exhausted beyond window");
            return ReloadOutcome.FAILED_CLOSED;
        }
        return last;
    }

    /** Injectable backoff so unit tests never call Thread.sleep. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis);

        Sleeper NO_SLEEP = millis -> { };
    }
}
