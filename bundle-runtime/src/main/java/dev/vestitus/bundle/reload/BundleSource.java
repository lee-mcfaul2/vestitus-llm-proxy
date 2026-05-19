package dev.vestitus.bundle.reload;

/**
 * ADR-003 runtime-pull fetch seam. Compile-time interface only (no runtime
 * code-load). Fail-closed: implementations return a {@link FetchResult} and
 * never throw through to the orchestrator.
 */
@FunctionalInterface
public interface BundleSource {
    FetchResult fetch();
}
