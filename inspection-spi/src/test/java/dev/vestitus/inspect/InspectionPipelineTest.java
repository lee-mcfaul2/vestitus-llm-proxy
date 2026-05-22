package dev.vestitus.inspect;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class InspectionPipelineTest {

    private static final RawContent IN = new RawContent("payload", ContentKind.TEXT);
    private static final RawSpanOutcome CLEAN = new RawSpanOutcome.Spans(List.of());

    private static RawSpanDetector credentialDetector(String id) {
        return FakeStages.rawDetector(id, new RawSpanOutcome.Spans(List.of(
            new SpanFinding(new StageId(id), new ReasonCode("cred.aws_key"),
                new OriginalOffset(0, 20), FindingKind.CREDENTIAL))));
    }

    @Test
    void outboundRunsBothFloorsThenExtras() {
        OutboundPipeline p = InspectionPipeline.outbound(
            FakeStages.rawDetector("cred", CLEAN),
            FakeStages.rawDetector("pii", CLEAN),
            List.of());
        PipelineOutcome.Allowed a = assertInstanceOf(
            PipelineOutcome.Allowed.class, p.run(IN));
        assertEquals(List.of(new StageId("cred"), new StageId("pii")), a.ran());
    }

    @Test
    void theSameDetectorMaySatisfyBothOutboundFloorSlots() {
        RawSpanDetector both = FakeStages.rawDetector("floor", CLEAN);
        OutboundPipeline p = InspectionPipeline.outbound(both, both, List.of());
        // The shared instance must not trip the duplicate-StageId rule.
        assertInstanceOf(PipelineOutcome.Allowed.class, p.run(IN));
    }

    @Test
    void inboundHasNoPiiFloorAndRunsCredentialFloorOnly() {
        InboundPipeline p = InspectionPipeline.inbound(
            FakeStages.rawDetector("cred", CLEAN), List.of());
        PipelineOutcome.Allowed a = assertInstanceOf(
            PipelineOutcome.Allowed.class, p.run(IN));
        assertEquals(List.of(new StageId("cred")), a.ran());
    }

    @Test
    void aFactoryRejectsAnInvalidConfigurationAtAssembly() {
        // Two transformers — §7 rule 2 — must fail at assembly, not at run time.
        TransformOutcome ok = new TransformOutcome.Normalized(
            NormalizedView.identityOf(IN));
        assertThrows(PipelineAssemblyException.class,
            () -> InspectionPipeline.inbound(
                FakeStages.rawDetector("cred", CLEAN), List.of(
                    ConfiguredStage.failClosed(
                        FakeStages.transformer("t1", SpanFidelity.LOSSY, ok)),
                    ConfiguredStage.failClosed(
                        FakeStages.transformer("t2", SpanFidelity.LOSSY, ok)))));
    }

    @Test
    void boundaryThree_outboundCredentialIsAnIncidentWithOffsetAndNoSecret() {
        OutboundPipeline p = InspectionPipeline.outbound(
            credentialDetector("cred"),
            FakeStages.rawDetector("pii", CLEAN),
            List.of());
        PipelineOutcome.Incident i = assertInstanceOf(
            PipelineOutcome.Incident.class, p.run(IN));
        assertEquals(IncidentKind.CREDENTIAL_DETECTED, i.kind());
        assertEquals(new OriginalOffset(0, 20), i.where().orElseThrow());
        assertFalse(i.reason().code().contains("payload"),
            "the outcome must not echo body text");
    }

    @Test
    void boundaryOne_inboundCredentialOnHostileInputIsAnIncident() {
        InboundPipeline p = InspectionPipeline.inbound(
            credentialDetector("cred"), List.of());
        assertInstanceOf(PipelineOutcome.Incident.class, p.run(IN));
    }

    @Test
    void factoriesRejectNullArguments() {
        assertThrows(NullPointerException.class,
            () -> InspectionPipeline.outbound(null,
                FakeStages.rawDetector("pii", CLEAN), List.of()));
        assertThrows(NullPointerException.class,
            () -> InspectionPipeline.inbound(
                FakeStages.rawDetector("cred", CLEAN), null));
    }
}
