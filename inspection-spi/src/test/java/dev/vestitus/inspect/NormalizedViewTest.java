package dev.vestitus.inspect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NormalizedViewTest {

    @Test
    void rejectsNulls() {
        RawContent src = new RawContent("hello", ContentKind.TEXT);
        assertThrows(NullPointerException.class,
            () -> new NormalizedView(null, src, SpanMap.identity()));
        assertThrows(NullPointerException.class,
            () -> new NormalizedView("hello", null, SpanMap.identity()));
        assertThrows(NullPointerException.class,
            () -> new NormalizedView("hello", src, null));
    }

    @Test
    void identityOfRejectsNull() {
        assertThrows(NullPointerException.class,
            () -> NormalizedView.identityOf(null));
    }

    @Test
    void identityOfCarriesTheSameBodyAndAnIdentityMap() {
        RawContent src = new RawContent("hello", ContentKind.TEXT);
        NormalizedView v = NormalizedView.identityOf(src);
        assertEquals("hello", v.body());
        assertSame(src, v.source());
        assertTrue(v.toOriginal().isIdentity());
    }
}
