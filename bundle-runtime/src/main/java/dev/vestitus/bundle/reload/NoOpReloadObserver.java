package dev.vestitus.bundle.reload;

import dev.vestitus.trust.BundleVersion;

/** Minimal default observer. No metrics library; deliberately silent. */
public final class NoOpReloadObserver implements ReloadObserver {
    @Override public void onFetchUnreachable(String reason) { }
    @Override public void onVerifyRejected(String reason) { }
    @Override public void onAggregateRejected(String reason) { }
    @Override public void onApplied(BundleVersion version, int cellCount) { }
    @Override public void onRetainedLastGood(String reason) { }
    @Override public void onFailedClosed(String reason) { }
}
