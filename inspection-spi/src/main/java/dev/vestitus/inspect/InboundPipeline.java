package dev.vestitus.inspect;

import java.util.List;

/**
 * A frozen, validated content-inspection pipeline for inbound content
 * (untrusted user/agent content at ingress). Built via {@link
 * InspectionPipeline#inbound}. Has a mandatory credential floor and NO PII
 * floor — inbound PII is the user's own data, which they typed. The credential
 * floor applies both directions: a credential must never reach the LLM from
 * either side.
 */
public final class InboundPipeline implements Pipeline {

    private final RawSpanDetector credentialFloor;
    private final List<ConfiguredStage> extras;

    InboundPipeline(RawSpanDetector credentialFloor, List<ConfiguredStage> extras) {
        this.credentialFloor = credentialFloor;
        this.extras = extras;
    }

    @Override
    public PipelineOutcome run(RawContent content) {
        return PipelineExecutor.run(content, credentialFloor, null, extras);
    }
}
