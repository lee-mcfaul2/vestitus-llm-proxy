package dev.vestitus.inspect;

/**
 * Internal shared supertype of the two pipeline shapes. Package-private: the
 * public surface is {@link OutboundPipeline} / {@link InboundPipeline} plus the
 * {@link InspectionPipeline} factory. The distinct factory shapes keep the
 * floor asymmetry (PII floor outbound only) honest.
 */
sealed interface Pipeline permits OutboundPipeline, InboundPipeline {

    /** Runs the pipeline over one content value, returning a sealed outcome. */
    PipelineOutcome run(RawContent content);
}
