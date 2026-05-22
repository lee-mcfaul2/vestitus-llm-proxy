package dev.vestitus.inspect;

/**
 * A {@link Detector} that ALWAYS inspects the original {@link RawContent} —
 * never a translation. This is the Inv. 9 guarantee made structural: only a
 * RawSpanDetector can produce a {@link SpanFinding}, and it only ever sees
 * pre-LOSSY content. A detector that must look inside a decoded payload
 * (base64, etc.) performs the decode and the offset mapping back to the
 * original inside its own implementation; the SPI does not split that out. An
 * implementation MUST NOT throw — any failure is a {@link
 * RawSpanOutcome.StageFailed}.
 */
public non-sealed interface RawSpanDetector extends Detector {

    /** Inspects the original content; never throws. */
    RawSpanOutcome inspect(RawContent in);
}
