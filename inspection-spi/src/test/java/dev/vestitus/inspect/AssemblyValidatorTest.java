package dev.vestitus.inspect;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AssemblyValidatorTest {

    private static final RawSpanOutcome CLEAN = new RawSpanOutcome.Spans(List.of());
    private static final TransformOutcome ID_OK = new TransformOutcome.Normalized(
        NormalizedView.identityOf(new RawContent("x", ContentKind.TEXT)));
    private static final SemanticOutcome SEM_CLEAN = new SemanticOutcome.Clean();

    private static List<Stage> floor(String credId) {
        return List.of(FakeStages.rawDetector(credId, CLEAN));
    }

    @Test
    void aValidConfigurationPasses() {
        assertDoesNotThrow(() -> AssemblyValidator.validate(
            floor("cred"),
            List.of(ConfiguredStage.failClosed(
                FakeStages.semantic("sem", Set.of(SemanticAction.BLOCK), SEM_CLEAN)))));
    }

    @Test
    void rejectsMoreThanOneTransformer() {
        var ex = assertThrows(PipelineAssemblyException.class,
            () -> AssemblyValidator.validate(floor("cred"), List.of(
                ConfiguredStage.failClosed(
                    FakeStages.transformer("t1", SpanFidelity.LOSSY, ID_OK)),
                ConfiguredStage.failClosed(
                    FakeStages.transformer("t2", SpanFidelity.LOSSY, ID_OK)))));
        assertEquals(new ReasonCode("assembly.multiple_transformers"), ex.reason());
    }

    @Test
    void rejectsARawSpanDetectorOrderedAfterATransformer() {
        var ex = assertThrows(PipelineAssemblyException.class,
            () -> AssemblyValidator.validate(floor("cred"), List.of(
                ConfiguredStage.failClosed(
                    FakeStages.transformer("t", SpanFidelity.LOSSY, ID_OK)),
                ConfiguredStage.failClosed(
                    FakeStages.rawDetector("late", CLEAN)))));
        assertEquals(new ReasonCode("assembly.raw_detector_after_transformer"),
            ex.reason());
    }

    @Test
    void rejectsADuplicateStageIdBetweenFloorAndExtras() {
        var ex = assertThrows(PipelineAssemblyException.class,
            () -> AssemblyValidator.validate(floor("cred"), List.of(
                ConfiguredStage.failClosed(FakeStages.rawDetector("cred", CLEAN)))));
        assertEquals(new ReasonCode("assembly.duplicate_stage_id"), ex.reason());
    }

    @Test
    void rejectsFailOpenOnAnIncidentCapableSemanticDetector() {
        var ex = assertThrows(PipelineAssemblyException.class,
            () -> AssemblyValidator.validate(floor("cred"), List.of(
                new ConfiguredStage(
                    FakeStages.semantic("sem", Set.of(SemanticAction.INCIDENT),
                        SEM_CLEAN),
                    FailureMode.FAIL_OPEN))));
        assertEquals(new ReasonCode("assembly.fail_open_incident_stage"), ex.reason());
    }

    @Test
    void failOpenIsPermittedOnANonIncidentSemanticDetector() {
        assertDoesNotThrow(() -> AssemblyValidator.validate(floor("cred"), List.of(
            new ConfiguredStage(
                FakeStages.semantic("sem", Set.of(SemanticAction.BLOCK), SEM_CLEAN),
                FailureMode.FAIL_OPEN))));
    }
}
