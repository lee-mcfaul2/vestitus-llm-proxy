package dev.vestitus.inspect;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpanMapTest {

    @Test
    void identityMapsEveryRangeToItself() {
        SpanMap m = SpanMap.identity();
        assertTrue(m.isIdentity());
        OriginalOffset r = new OriginalOffset(3, 9);
        assertEquals(r, m.toOriginal(r).orElseThrow());
    }

    @Test
    void explicitMapResolvesAContainedRangeToItsSegment() {
        SpanMap m = new SpanMap(List.of(new SpanMap.Segment(
            new OriginalOffset(0, 10), new OriginalOffset(0, 25))));
        assertFalse(m.isIdentity());
        assertEquals(new OriginalOffset(0, 25),
            m.toOriginal(new OriginalOffset(2, 6)).orElseThrow());
    }

    @Test
    void explicitMapReturnsEmptyForAnUnmappedRange() {
        SpanMap m = new SpanMap(List.of(new SpanMap.Segment(
            new OriginalOffset(0, 10), new OriginalOffset(0, 25))));
        assertTrue(m.toOriginal(new OriginalOffset(11, 14)).isEmpty());
    }

    @Test
    void segmentsListIsCopiedDefensively() {
        var mutable = new java.util.ArrayList<SpanMap.Segment>();
        mutable.add(new SpanMap.Segment(
            new OriginalOffset(0, 1), new OriginalOffset(0, 1)));
        SpanMap m = new SpanMap(mutable);
        mutable.clear();
        assertEquals(1, m.segments().size());
    }
}
