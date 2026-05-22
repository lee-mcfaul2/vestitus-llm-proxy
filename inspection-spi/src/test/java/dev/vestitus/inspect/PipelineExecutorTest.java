package dev.vestitus.inspect;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PipelineExecutorTest {

    private static final RawContent IN = new RawContent("payload", ContentKind.TEXT);
    private static final RawSpanOutcome CLEAN = new RawSpanOutcome.Spans(List.of());

    private static RawSpanOutcome credential(String id) {
        return new RawSpanOutcome.Spans(List.of(new SpanFinding(
            new StageId(id), new ReasonCode("cred.aws_key"),
            new OriginalOffset(0, 20), FindingKind.CREDENTIAL)));
    }

    private static RawSpanOutcome pii(String id) {
        return new RawSpanOutcome.Spans(List.of(new SpanFinding(
            new StageId(id), new ReasonCode("pii.email"),
            new OriginalOffset(1, 8), FindingKind.PII)));
    }

    @Test
    void runsFloorThenExtrasInDeclaredOrderAndReportsRan() {
        PipelineOutcome o = PipelineExecutor.run(IN,
            FakeStages.rawDetector("cred", CLEAN),
            FakeStages.rawDetector("pii", CLEAN),
            List.of(
                ConfiguredStage.failClosed(FakeStages.rawDetector("extra", CLEAN)),
                ConfiguredStage.failClosed(FakeStages.semantic(
                    "sem", Set.of(SemanticAction.BLOCK), new SemanticOutcome.Clean()))));
        PipelineOutcome.Allowed a = assertInstanceOf(PipelineOutcome.Allowed.class, o);
        assertEquals(List.of(new StageId("cred"), new StageId("pii"),
            new StageId("extra"), new StageId("sem")), a.ran());
    }

    @Test
    void aCredentialFromTheFloorEndsTheRunAsIncidentAndShortCircuits() {
        List<String> log = new ArrayList<>();
        PipelineOutcome o = PipelineExecutor.run(IN,
            FakeStages.rawDetector("cred", credential("cred")),
            FakeStages.rawDetector("pii", CLEAN),
            List.of(ConfiguredStage.failClosed(
                FakeStages.recordingRaw("extra", log, CLEAN))));
        PipelineOutcome.Incident i = assertInstanceOf(PipelineOutcome.Incident.class, o);
        assertEquals(IncidentKind.CREDENTIAL_DETECTED, i.kind());
        assertEquals(new OriginalOffset(0, 20), i.where().orElseThrow());
        assertTrue(log.isEmpty(), "stages after the credential must not run");
    }

    @Test
    void thePiiFloorAccumulatesPiiFindingsIntoAllowed() {
        PipelineOutcome o = PipelineExecutor.run(IN,
            FakeStages.rawDetector("cred", CLEAN),
            FakeStages.rawDetector("pii", pii("pii")),
            List.of());
        PipelineOutcome.Allowed a = assertInstanceOf(PipelineOutcome.Allowed.class, o);
        assertEquals(1, a.findings().size());
        assertEquals(FindingKind.PII, a.findings().get(0).kind());
    }

    @Test
    void aFloorStageFailedIsTerminalStageFailure() {
        PipelineOutcome o = PipelineExecutor.run(IN,
            FakeStages.rawDetector("cred",
                new RawSpanOutcome.StageFailed(new ReasonCode("cred.io"))),
            FakeStages.rawDetector("pii", CLEAN),
            List.of());
        PipelineOutcome.StageFailure f =
            assertInstanceOf(PipelineOutcome.StageFailure.class, o);
        assertEquals(new StageId("cred"), f.by());
    }

    @Test
    void anExtraStageFailedFailsClosedOrOpenPerItsMode() {
        ConfiguredStage closed = new ConfiguredStage(
            FakeStages.rawDetector("e",
                new RawSpanOutcome.StageFailed(new ReasonCode("e.io"))),
            FailureMode.FAIL_CLOSED);
        assertInstanceOf(PipelineOutcome.StageFailure.class,
            PipelineExecutor.run(IN, FakeStages.rawDetector("cred", CLEAN),
                null, List.of(closed)));

        ConfiguredStage open = new ConfiguredStage(
            FakeStages.rawDetector("e",
                new RawSpanOutcome.StageFailed(new ReasonCode("e.io"))),
            FailureMode.FAIL_OPEN);
        assertInstanceOf(PipelineOutcome.Allowed.class,
            PipelineExecutor.run(IN, FakeStages.rawDetector("cred", CLEAN),
                null, List.of(open)));
    }

    @Test
    void semanticBlockAndIncidentAreTerminal() {
        SemanticOutcome block = new SemanticOutcome.Verdict(new SemanticVerdict(
            new StageId("s"), new ReasonCode("s.block"), SemanticAction.BLOCK));
        assertInstanceOf(PipelineOutcome.Blocked.class,
            PipelineExecutor.run(IN, FakeStages.rawDetector("cred", CLEAN), null,
                List.of(ConfiguredStage.failClosed(FakeStages.semantic(
                    "s", Set.of(SemanticAction.BLOCK), block)))));

        SemanticOutcome incident = new SemanticOutcome.Verdict(new SemanticVerdict(
            new StageId("s"), new ReasonCode("s.incident"), SemanticAction.INCIDENT));
        PipelineOutcome.Incident i = assertInstanceOf(PipelineOutcome.Incident.class,
            PipelineExecutor.run(IN, FakeStages.rawDetector("cred", CLEAN), null,
                List.of(ConfiguredStage.failClosed(FakeStages.semantic(
                    "s", Set.of(SemanticAction.INCIDENT), incident)))));
        assertEquals(IncidentKind.OTHER, i.kind());
        assertTrue(i.where().isEmpty(), "a semantic INCIDENT carries no offset");
    }

    @Test
    void anUndeclaredActionIsTreatedAsAStageFailure() {
        // Detector declares only BLOCK but returns an INCIDENT verdict.
        SemanticOutcome rogue = new SemanticOutcome.Verdict(new SemanticVerdict(
            new StageId("s"), new ReasonCode("s.x"), SemanticAction.INCIDENT));
        PipelineOutcome o = PipelineExecutor.run(IN,
            FakeStages.rawDetector("cred", CLEAN), null,
            List.of(ConfiguredStage.failClosed(
                FakeStages.semantic("s", Set.of(SemanticAction.BLOCK), rogue))));
        assertInstanceOf(PipelineOutcome.StageFailure.class, o);
    }

    @Test
    void aSemanticDetectorSeesTheTransformedView() {
        // Transformer rewrites the body to "TRANSFORMED"; the semantic detector
        // blocks iff it sees that body — proving the view was threaded through.
        TransformOutcome rewrite = new TransformOutcome.Normalized(new NormalizedView(
            "TRANSFORMED", IN, SpanMap.identity()));
        PipelineOutcome o = PipelineExecutor.run(IN,
            FakeStages.rawDetector("cred", CLEAN), null,
            List.of(
                ConfiguredStage.failClosed(
                    FakeStages.transformer("t", SpanFidelity.LOSSY, rewrite)),
                ConfiguredStage.failClosed(
                    FakeStages.semanticBlockingOn("s", "TRANSFORMED"))));
        assertInstanceOf(PipelineOutcome.Blocked.class, o);
    }
}
