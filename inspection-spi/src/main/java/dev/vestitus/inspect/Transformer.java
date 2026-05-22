package dev.vestitus.inspect;

/**
 * A {@link Stage} that produces a {@link NormalizedView} for downstream
 * {@link SemanticDetector}s. v1 permits at most one Transformer per pipeline
 * (design-spec §7 rule 2). An implementation MUST NOT throw — any failure is a
 * {@link TransformOutcome.StageFailed}.
 */
public non-sealed interface Transformer extends Stage {

    /** This transformer's declared offset behaviour. */
    SpanFidelity fidelity();

    /** Transforms {@code in}; never throws. */
    TransformOutcome transform(NormalizedView in);
}
