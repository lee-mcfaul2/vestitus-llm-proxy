package dev.vestitus.inspect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContentModelTest {

    @Test
    void rawContentRejectsNulls() {
        assertThrows(NullPointerException.class,
            () -> new RawContent(null, ContentKind.TEXT));
        assertThrows(NullPointerException.class,
            () -> new RawContent("x", null));
    }

    @Test
    void rawContentAllowsEmptyBody() {
        RawContent c = new RawContent("", ContentKind.JSON_VALUE);
        assertEquals("", c.body());
        assertEquals(ContentKind.JSON_VALUE, c.kind());
    }

    @Test
    void originalOffsetValidatesRange() {
        assertThrows(IllegalArgumentException.class,
            () -> new OriginalOffset(-1, 4));
        assertThrows(IllegalArgumentException.class,
            () -> new OriginalOffset(5, 4));
    }

    @Test
    void originalOffsetAllowsEmptyAndReportsLength() {
        assertEquals(0, new OriginalOffset(7, 7).length());
        assertEquals(4, new OriginalOffset(3, 7).length());
    }
}
