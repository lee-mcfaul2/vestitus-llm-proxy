package dev.vestitus.inspect;

import java.util.Objects;

/** The outcome of {@link SemanticDetector#inspect}. Sealed, never thrown. */
public sealed interface SemanticOutcome {

    record Verdict(SemanticVerdict verdict) implements SemanticOutcome {
        public Verdict { Objects.requireNonNull(verdict, "verdict"); }
    }

    record Clean() implements SemanticOutcome {}

    record StageFailed(ReasonCode reason) implements SemanticOutcome {
        public StageFailed { Objects.requireNonNull(reason, "reason"); }
    }
}
