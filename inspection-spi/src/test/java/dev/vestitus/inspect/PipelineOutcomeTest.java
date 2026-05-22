package dev.vestitus.inspect;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PipelineOutcomeTest {

    private static final StageId BY = new StageId("stage");
    private static final ReasonCode RC = new ReasonCode("x.y");

    @Test
    void allVariantsAreExhaustivelyMatchable() {
        assertEquals("allowed", kind(new PipelineOutcome.Allowed(List.of(), List.of())));
        assertEquals("blocked", kind(new PipelineOutcome.Blocked(BY, RC)));
        assertEquals("incident", kind(new PipelineOutcome.Incident(
            BY, IncidentKind.OTHER, Optional.empty(), RC)));
        assertEquals("failure", kind(new PipelineOutcome.StageFailure(BY, RC)));
    }

    @Test
    void allowedCopiesItsListsDefensively() {
        var findings = new java.util.ArrayList<SpanFinding>();
        var ran = new java.util.ArrayList<StageId>();
        ran.add(BY);
        PipelineOutcome.Allowed a = new PipelineOutcome.Allowed(findings, ran);
        ran.clear();
        assertEquals(1, a.ran().size());
        assertTrue(a.findings().isEmpty());
    }

    @Test
    void incidentRejectsNullOptional() {
        assertThrows(NullPointerException.class, () -> new PipelineOutcome.Incident(
            BY, IncidentKind.CREDENTIAL_DETECTED, null, RC));
    }

    private static String kind(PipelineOutcome o) {
        return switch (o) {
            case PipelineOutcome.Allowed a -> "allowed";
            case PipelineOutcome.Blocked b -> "blocked";
            case PipelineOutcome.Incident i -> "incident";
            case PipelineOutcome.StageFailure f -> "failure";
        };
    }
}
