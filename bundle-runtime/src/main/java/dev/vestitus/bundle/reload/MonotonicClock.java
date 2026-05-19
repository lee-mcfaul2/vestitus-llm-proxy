package dev.vestitus.bundle.reload;

/**
 * The ONLY time source the reload orchestrator may consult for the
 * "last-good window". Deliberately monotonic (never wall-clock) so a clock
 * skew/jump cannot widen or shrink the window during which an unreachable
 * source still retains the last good generation.
 */
@FunctionalInterface
public interface MonotonicClock {

    long nanos();

    /** Backed by {@link System#nanoTime()}. */
    MonotonicClock SYSTEM = System::nanoTime;
}
