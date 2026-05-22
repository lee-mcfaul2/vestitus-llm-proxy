package dev.vestitus.inspect;

import java.util.Objects;

/** The outcome of {@link Transformer#transform}. Sealed, never thrown. */
public sealed interface TransformOutcome {

    record Normalized(NormalizedView view) implements TransformOutcome {
        public Normalized { Objects.requireNonNull(view, "view"); }
    }

    record StageFailed(ReasonCode reason) implements TransformOutcome {
        public StageFailed { Objects.requireNonNull(reason, "reason"); }
    }
}
