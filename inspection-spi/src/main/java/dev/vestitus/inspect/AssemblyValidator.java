package dev.vestitus.inspect;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The design-spec §7 assembly-time validator. Runs inside the {@link
 * InspectionPipeline} factory methods; on any violation it throws {@link
 * PipelineAssemblyException} so a misconfigured pipeline fails at process
 * start, never silently at request time.
 *
 * <p>Rule 1 (floor presence) is enforced structurally by the factory's
 * non-null constructor arguments — {@code floor} is already a non-null,
 * identity-deduplicated list when this runs. Rules 2-5 are checked here. This
 * validator does NOT and cannot verify transformer span-fidelity correctness
 * (the §5.7.1 footgun) — that is the transformer's promise.
 */
final class AssemblyValidator {

    private AssemblyValidator() {}

    static void validate(List<Stage> floor, List<ConfiguredStage> extras) {
        // Rule 4: unique StageId across floor + extras.
        Set<String> seen = new HashSet<>();
        for (Stage s : floor)
            if (!seen.add(s.id().name()))
                throw fail("assembly.duplicate_stage_id",
                    "duplicate stage id among floor stages: " + s.id().name());
        for (ConfiguredStage cs : extras)
            if (!seen.add(cs.stage().id().name()))
                throw fail("assembly.duplicate_stage_id",
                    "duplicate stage id: " + cs.stage().id().name());

        // Rules 2, 3, 5 over extras in declared order.
        boolean transformerSeen = false;
        for (ConfiguredStage cs : extras) {
            Stage s = cs.stage();
            switch (s) {
                case Transformer t -> {
                    if (transformerSeen)
                        throw fail("assembly.multiple_transformers",
                            "v1 permits at most one Transformer; offending stage: "
                                + t.id().name());
                    transformerSeen = true;
                }
                case RawSpanDetector rsd -> {
                    if (transformerSeen)
                        throw fail("assembly.raw_detector_after_transformer",
                            "RawSpanDetector " + rsd.id().name()
                                + " is ordered after a Transformer; a"
                                + " RawSpanDetector always inspects the original"
                                + " RawContent");
                }
                case SemanticDetector sd -> {
                    if (sd.declaredActions().contains(SemanticAction.INCIDENT)
                            && cs.onStageFailure() == FailureMode.FAIL_OPEN)
                        throw fail("assembly.fail_open_incident_stage",
                            "SemanticDetector " + sd.id().name()
                                + " declares INCIDENT and may not be FAIL_OPEN");
                }
            }
        }
    }

    private static PipelineAssemblyException fail(String code, String message) {
        return new PipelineAssemblyException(new ReasonCode(code), message);
    }
}
