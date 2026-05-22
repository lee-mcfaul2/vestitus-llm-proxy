package dev.vestitus.inspect;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AssemblyConfigTest {

    private static Stage stubSemantic() {
        return new SemanticDetector() {
            @Override public StageId id() { return new StageId("s"); }
            @Override public Set<SemanticAction> declaredActions() { return Set.of(); }
            @Override public SemanticOutcome inspect(NormalizedView v) {
                return new SemanticOutcome.Clean();
            }
        };
    }

    @Test
    void configuredStageRejectsNulls() {
        assertThrows(NullPointerException.class,
            () -> new ConfiguredStage(null, FailureMode.FAIL_CLOSED));
        assertThrows(NullPointerException.class,
            () -> new ConfiguredStage(stubSemantic(), null));
    }

    @Test
    void failClosedSugarDefaultsToFailClosed() {
        ConfiguredStage cs = ConfiguredStage.failClosed(stubSemantic());
        assertEquals(FailureMode.FAIL_CLOSED, cs.onStageFailure());
    }

    @Test
    void assemblyExceptionCarriesItsReasonCode() {
        var ex = new PipelineAssemblyException(
            new ReasonCode("assembly.duplicate_stage_id"), "duplicate id: s");
        assertEquals(new ReasonCode("assembly.duplicate_stage_id"), ex.reason());
        assertEquals("duplicate id: s", ex.getMessage());
        assertThrows(IllegalArgumentException.class,
            () -> new PipelineAssemblyException(null, "x"));
    }

    @Test
    void failureModeHasTwoConstants() {
        assertEquals(List.of(FailureMode.FAIL_CLOSED, FailureMode.FAIL_OPEN),
            List.of(FailureMode.values()));
    }
}
