package dev.vestitus.bundle.reload;

import dev.vestitus.trust.BundleVersion;

/**
 * Rate-aggregated reload-lifecycle audit. DECOUPLED from the per-request
 * fail-closed authorize path (that path is {@code GenerationalRegistry} and is
 * untouched by reload). An observer MUST NOT influence the reload outcome and
 * MUST NOT throw back into the orchestrator (the orchestrator still guards, but
 * this is the contract).
 */
public interface ReloadObserver {

    void onFetchUnreachable(String reason);

    void onVerifyRejected(String reason);

    void onAggregateRejected(String reason);

    void onApplied(BundleVersion version, int cellCount);

    void onRetainedLastGood(String reason);

    void onFailedClosed(String reason);
}
