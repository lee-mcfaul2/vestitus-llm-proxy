package dev.vestitus.inspect;

/** A pipeline stage. Sealed: every stage is exactly a {@link Transformer} or a {@link Detector}. */
public sealed interface Stage permits Transformer, Detector {
    /** The pipeline-unique identifier of this stage. */
    StageId id();
}
