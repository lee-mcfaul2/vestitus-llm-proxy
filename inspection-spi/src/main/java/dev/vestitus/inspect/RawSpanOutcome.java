package dev.vestitus.inspect;

import java.util.List;
import java.util.Objects;

/** The outcome of {@link RawSpanDetector#inspect}. Sealed, never thrown. */
public sealed interface RawSpanOutcome {

    record Spans(List<SpanFinding> findings) implements RawSpanOutcome {
        public Spans {
            Objects.requireNonNull(findings, "findings");
            findings = List.copyOf(findings);
        }
    }

    record StageFailed(ReasonCode reason) implements RawSpanOutcome {
        public StageFailed { Objects.requireNonNull(reason, "reason"); }
    }
}
