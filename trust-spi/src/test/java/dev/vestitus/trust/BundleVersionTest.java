package dev.vestitus.trust;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BundleVersionTest {
    @Test
    void compareToOrders() {
        var lo = new BundleVersion(1);
        var hi = new BundleVersion(2);
        assertTrue(lo.compareTo(hi) < 0);
        assertTrue(hi.compareTo(lo) > 0);
        assertEquals(0, lo.compareTo(new BundleVersion(1)));
    }

    @Test
    void isStrictlyAfterTrue() {
        assertTrue(new BundleVersion(5).isStrictlyAfter(new BundleVersion(4)));
    }

    @Test
    void isStrictlyAfterFalseWhenLess() {
        assertFalse(new BundleVersion(4).isStrictlyAfter(new BundleVersion(5)));
    }

    @Test
    void isStrictlyAfterFalseWhenEqual() {
        assertFalse(new BundleVersion(5).isStrictlyAfter(new BundleVersion(5)));
    }

    @Test
    void zeroIsAllowed() {
        assertEquals(0L, new BundleVersion(0).value());
    }

    @Test
    void negativeRejected() {
        assertThrows(IllegalArgumentException.class, () -> new BundleVersion(-1));
    }

    @Test
    void isStrictlyAfterNullRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new BundleVersion(1).isStrictlyAfter(null));
    }

    @Test
    void comparableContractConsistentWithStrictlyAfter() {
        var a = new BundleVersion(7);
        var b = new BundleVersion(3);
        assertEquals(a.compareTo(b) > 0, a.isStrictlyAfter(b));
        assertEquals(b.compareTo(a) > 0, b.isStrictlyAfter(a));
    }

    @Test
    void comparableContractAntisymmetric() {
        var a = new BundleVersion(2);
        var b = new BundleVersion(8);
        assertEquals(-Integer.signum(b.compareTo(a)), Integer.signum(a.compareTo(b)));
    }
}
