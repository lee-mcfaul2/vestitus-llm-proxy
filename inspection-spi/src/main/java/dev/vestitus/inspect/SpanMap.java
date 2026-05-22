package dev.vestitus.inspect;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The back-mapping from offsets in a {@link NormalizedView} body to offsets in
 * the originating {@link RawContent} body. A {@link Transformer} DECLARES this;
 * its correctness is the transformer's promise and is NOT statically
 * verifiable (the design-spec §5.7.1 footgun). The v1 executor never consults
 * a SpanMap to translate a finding — a {@link RawSpanDetector} always inspects
 * the original — so SpanMap is a declared artifact carried for gateway-core
 * and future transformer chaining. An empty segment list is the identity map.
 */
public record SpanMap(List<Segment> segments) {

    /** One contiguous view range and the original range it was produced from. */
    public record Segment(OriginalOffset inView, OriginalOffset inOriginal) {
        public Segment {
            Objects.requireNonNull(inView, "inView");
            Objects.requireNonNull(inOriginal, "inOriginal");
        }
    }

    public SpanMap {
        Objects.requireNonNull(segments, "segments");
        segments = List.copyOf(segments);
    }

    /** The identity map: every view range maps to the same original range. */
    public static SpanMap identity() { return new SpanMap(List.of()); }

    /** True iff this is the identity map (no explicit segments). */
    public boolean isIdentity() { return segments.isEmpty(); }

    /**
     * Maps a view range back to the original. The identity map returns the
     * same range; an explicit map returns the {@code inOriginal} of the first
     * segment whose {@code inView} fully contains {@code viewRange}, or empty
     * if no segment does.
     */
    public Optional<OriginalOffset> toOriginal(OriginalOffset viewRange) {
        Objects.requireNonNull(viewRange, "viewRange");
        if (isIdentity()) return Optional.of(viewRange);
        for (Segment s : segments)
            if (s.inView().start() <= viewRange.start()
                    && viewRange.endExclusive() <= s.inView().endExclusive())
                return Optional.of(s.inOriginal());
        return Optional.empty();
    }
}
