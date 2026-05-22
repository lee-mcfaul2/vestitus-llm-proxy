package dev.vestitus.inspect;

import java.util.Objects;

/**
 * A finding with an exact {@link OriginalOffset} into the original
 * {@link RawContent} body. Only a {@link RawSpanDetector} can produce one
 * (Inv. 9). It carries NO body text — the matched value never leaves the
 * detector's local frame (Inv. 13).
 */
public record SpanFinding(StageId by, ReasonCode reason,
                          OriginalOffset where, FindingKind kind)
        implements Finding {
    public SpanFinding {
        Objects.requireNonNull(by, "by");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(where, "where");
        Objects.requireNonNull(kind, "kind");
    }
}
