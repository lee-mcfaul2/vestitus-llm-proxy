package dev.vestitus.bundle.reload;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class MonotonicClockTest {

    @Test
    void systemClockReturnsMonotonicNonDecreasingNanos() {
        long a = MonotonicClock.SYSTEM.nanos();
        long b = MonotonicClock.SYSTEM.nanos();
        assertTrue(b >= a, "System.nanoTime must not decrease");
    }

    @Test
    void aFakeClockIsFullyDriverControlled() {
        AtomicLong t = new AtomicLong(0);
        MonotonicClock fake = t::get;
        assertEquals(0L, fake.nanos());
        t.set(1_000_000_000L);
        assertEquals(1_000_000_000L, fake.nanos());
    }
}
