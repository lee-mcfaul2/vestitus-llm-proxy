package dev.vestitus.inspect;

import java.util.List;

/**
 * A frozen, validated content-inspection pipeline for outbound content
 * (authorized-only MCP responses before they reach the LLM). Built via {@link
 * InspectionPipeline#outbound}. Has a mandatory credential floor AND a
 * mandatory PII floor.
 */
public final class OutboundPipeline implements Pipeline {

    private final RawSpanDetector credentialFloor;
    private final RawSpanDetector piiFloor;
    private final List<ConfiguredStage> extras;

    OutboundPipeline(RawSpanDetector credentialFloor, RawSpanDetector piiFloor,
                     List<ConfiguredStage> extras) {
        this.credentialFloor = credentialFloor;
        this.piiFloor = piiFloor;
        this.extras = extras;
    }

    @Override
    public PipelineOutcome run(RawContent content) {
        return PipelineExecutor.run(content, credentialFloor, piiFloor, extras);
    }
}
