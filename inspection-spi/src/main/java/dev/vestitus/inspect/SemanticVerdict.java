package dev.vestitus.inspect;

import java.util.Objects;

/**
 * A {@link SemanticDetector}'s verdict — BLOCK or INCIDENT, never an offset,
 * never TOKENIZE / REDACT. A SemanticDetector cannot return a
 * {@link SpanFinding}: acting on a span discovered in a translation is
 * unrepresentable.
 */
public record SemanticVerdict(StageId by, ReasonCode reason, SemanticAction action)
        implements Finding {
    public SemanticVerdict {
        Objects.requireNonNull(by, "by");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(action, "action");
    }
}
