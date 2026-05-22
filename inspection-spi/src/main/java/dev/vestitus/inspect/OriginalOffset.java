package dev.vestitus.inspect;

/**
 * A half-open character range {@code [start, endExclusive)} into the
 * originating {@link RawContent#body()}. Invariant: {@code 0 <= start <=
 * endExclusive}.
 */
public record OriginalOffset(int start, int endExclusive) {
    public OriginalOffset {
        if (start < 0)
            throw new IllegalArgumentException("start must be >= 0");
        if (endExclusive < start)
            throw new IllegalArgumentException("endExclusive must be >= start");
    }

    /** The number of characters spanned (zero for an empty range). */
    public int length() { return endExclusive - start; }
}
