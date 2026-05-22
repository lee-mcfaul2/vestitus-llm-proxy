package dev.vestitus.inspect;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The sealed seam to gateway-core. gateway-core pattern-matches this; the
 * absence of a {@code default} arm makes forgetting a branch a compile error.
 * {@code Blocked}, {@code Incident}, and {@code StageFailure} are terminal and
 * fail-closed.
 *
 * <p><b>No-leak discipline (Inv. 13).</b> No variant carries a matched secret
 * or PII value — only {@link StageId}, {@link ReasonCode}, {@link IncidentKind},
 * {@link OriginalOffset}, and {@link SpanFinding} (which itself carries no body
 * text). {@code PipelineOutcomeNoLeakTest} asserts this reachability property.
 */
public sealed interface PipelineOutcome {

    /**
     * Content cleared the pipeline. {@code findings} are the only finding kind
     * that drives a content-mutating disposition; {@code ran} is the ordered
     * list of stage ids that executed.
     */
    record Allowed(List<SpanFinding> findings, List<StageId> ran)
            implements PipelineOutcome {
        public Allowed {
            Objects.requireNonNull(findings, "findings");
            Objects.requireNonNull(ran, "ran");
            findings = List.copyOf(findings);
            ran = List.copyOf(ran);
        }
    }

    /** Terminal: a {@link SemanticDetector} returned a BLOCK verdict. */
    record Blocked(StageId by, ReasonCode reason) implements PipelineOutcome {
        public Blocked {
            Objects.requireNonNull(by, "by");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * Terminal: a credential was detected, or a {@link SemanticDetector} raised
     * INCIDENT. {@code where} is present only for a credential span — a
     * semantic INCIDENT carries no offset.
     */
    record Incident(StageId by, IncidentKind kind,
                    Optional<OriginalOffset> where, ReasonCode reason)
            implements PipelineOutcome {
        public Incident {
            Objects.requireNonNull(by, "by");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(where, "where");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** Terminal: a stage failed under FAIL_CLOSED, or a floor stage failed. */
    record StageFailure(StageId by, ReasonCode reason) implements PipelineOutcome {
        public StageFailure {
            Objects.requireNonNull(by, "by");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
