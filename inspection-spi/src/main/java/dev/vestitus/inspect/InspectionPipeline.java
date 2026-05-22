package dev.vestitus.inspect;

import java.util.List;
import java.util.Objects;

/**
 * The assembly entry point. The structural floor is non-omittable: there is no
 * way to build a pipeline without naming the credential floor (and, outbound,
 * the PII floor) as a constructor argument — a pipeline without the floor does
 * not compile (Inv. 10). Each factory runs the design-spec §7 assembly-time
 * validator and returns a frozen, immutable pipeline.
 */
public final class InspectionPipeline {

    private InspectionPipeline() {}

    /**
     * An outbound pipeline. {@code credentialFloor} and {@code piiFloor} are
     * mandatory and unconditionally FAIL_CLOSED; the same {@link
     * RawSpanDetector} instance may be supplied for both. {@code extras} run in
     * declared order.
     *
     * @throws PipelineAssemblyException if the §7 validator rejects the
     *         configuration
     */
    public static OutboundPipeline outbound(RawSpanDetector credentialFloor,
                                            RawSpanDetector piiFloor,
                                            List<ConfiguredStage> extras) {
        Objects.requireNonNull(credentialFloor, "credentialFloor");
        Objects.requireNonNull(piiFloor, "piiFloor");
        Objects.requireNonNull(extras, "extras");
        List<ConfiguredStage> frozen = List.copyOf(extras);
        // Identity-dedup the floor: a single detector may fill both slots
        // without tripping the unique-StageId rule.
        List<Stage> floor = credentialFloor == piiFloor
            ? List.<Stage>of(credentialFloor)
            : List.<Stage>of(credentialFloor, piiFloor);
        AssemblyValidator.validate(floor, frozen);
        return new OutboundPipeline(credentialFloor, piiFloor, frozen);
    }

    /**
     * An inbound pipeline — credential floor only, no PII floor.
     *
     * @throws PipelineAssemblyException if the §7 validator rejects the
     *         configuration
     */
    public static InboundPipeline inbound(RawSpanDetector credentialFloor,
                                          List<ConfiguredStage> extras) {
        Objects.requireNonNull(credentialFloor, "credentialFloor");
        Objects.requireNonNull(extras, "extras");
        List<ConfiguredStage> frozen = List.copyOf(extras);
        AssemblyValidator.validate(List.<Stage>of(credentialFloor), frozen);
        return new InboundPipeline(credentialFloor, frozen);
    }
}
