package dev.vestitus.bundle.reload;

import dev.vestitus.trust.Bundle;
import java.util.List;
import java.util.Objects;

/**
 * Fail-closed result of a bundle pull. A source NEVER throws through to the
 * orchestrator; an unreachable / malformed / oversize endpoint is modelled as
 * {@link Unreachable}, not an exception.
 */
public sealed interface FetchResult permits FetchResult.Fetched, FetchResult.Unreachable {

    record Fetched(List<Bundle> bundles) implements FetchResult {
        public Fetched {
            Objects.requireNonNull(bundles, "bundles");
            if (bundles.isEmpty()) {
                throw new IllegalArgumentException("Fetched bundle list must be non-empty");
            }
            bundles = List.copyOf(bundles);
        }
    }

    record Unreachable(String reason) implements FetchResult {
        public Unreachable {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("Unreachable reason must be non-blank");
            }
        }
    }
}
