package dev.vestitus.trust;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BundleTest {
    @Test
    void accessorsHoldValues() {
        var b = new Bundle(new byte[]{1, 2, 3}, new byte[]{9}, "ref-1");
        assertArrayEquals(new byte[]{1, 2, 3}, b.payload());
        assertArrayEquals(new byte[]{9}, b.signatureMaterial());
        assertEquals("ref-1", b.sourceRef());
    }

    @Test
    void payloadIsClonedIn() {
        byte[] src = {1, 2, 3};
        var b = new Bundle(src, new byte[]{0}, "ref");
        src[0] = 99;
        assertArrayEquals(new byte[]{1, 2, 3}, b.payload());
    }

    @Test
    void signatureMaterialIsClonedIn() {
        byte[] sig = {7, 7};
        var b = new Bundle(new byte[]{0}, sig, "ref");
        sig[0] = 99;
        assertArrayEquals(new byte[]{7, 7}, b.signatureMaterial());
    }

    @Test
    void payloadIsClonedOut() {
        var b = new Bundle(new byte[]{1, 2, 3}, new byte[]{0}, "ref");
        b.payload()[0] = 99;
        assertArrayEquals(new byte[]{1, 2, 3}, b.payload());
    }

    @Test
    void signatureMaterialIsClonedOut() {
        var b = new Bundle(new byte[]{0}, new byte[]{7, 7}, "ref");
        b.signatureMaterial()[0] = 99;
        assertArrayEquals(new byte[]{7, 7}, b.signatureMaterial());
    }

    @Test
    void emptySignatureMaterialAllowed() {
        var b = new Bundle(new byte[]{1}, new byte[0], "ref");
        assertEquals(0, b.signatureMaterial().length);
    }

    @Test
    void nullPayloadRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new Bundle(null, new byte[]{0}, "ref"));
    }

    @Test
    void nullSignatureMaterialRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new Bundle(new byte[]{0}, null, "ref"));
    }

    @Test
    void nullSourceRefRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new Bundle(new byte[]{0}, new byte[]{0}, null));
    }

    @Test
    void blankSourceRefRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new Bundle(new byte[]{0}, new byte[]{0}, "  "));
    }
}
