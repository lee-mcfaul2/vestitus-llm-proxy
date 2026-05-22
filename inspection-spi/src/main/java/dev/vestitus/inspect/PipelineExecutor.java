package dev.vestitus.inspect;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The stateless, immutable, pure-Java pipeline executor (design-spec §8). No
 * I/O — all I/O lives inside {@link Stage} implementations. Shared by {@link
 * OutboundPipeline} and {@link InboundPipeline}; {@code piiFloor == null}
 * selects the inbound shape (no PII floor, §8.2).
 *
 * <p>Every terminal branch carries a {@link StageId} and a stable {@link
 * ReasonCode} so gateway-core can emit the Inv. 7 audit event and
 * reason-labelled metric. The executor emits nothing itself — a silent
 * fail-closed is impossible because each abort is a distinct sealed {@link
 * PipelineOutcome} variant.
 */
final class PipelineExecutor {

    private PipelineExecutor() {}

    static PipelineOutcome run(RawContent original,
                               RawSpanDetector credentialFloor,
                               RawSpanDetector piiFloor,
                               List<ConfiguredStage> extras) {
        try {
            List<SpanFinding> pending = new ArrayList<>();
            List<StageId> ran = new ArrayList<>();

            // Step 1: credential floor — unconditionally FAIL_CLOSED.
            PipelineOutcome credTerm = runFloor(credentialFloor, original, pending, ran);
            if (credTerm != null) return credTerm;

            // Step 2: PII floor (outbound only) — unconditionally FAIL_CLOSED.
            if (piiFloor != null) {
                PipelineOutcome piiTerm = runFloor(piiFloor, original, pending, ran);
                if (piiTerm != null) return piiTerm;
            }

            // Step 3: extras, in declared order.
            Optional<NormalizedView> view = Optional.empty();
            for (ConfiguredStage cs : extras) {
                Stage stage = cs.stage();
                switch (stage) {
                    case RawSpanDetector rsd -> {
                        RawSpanOutcome o = rsd.inspect(original);
                        ran.add(rsd.id());
                        switch (o) {
                            case RawSpanOutcome.Spans spans -> {
                                PipelineOutcome cred =
                                    applyCredentialRule(rsd.id(), spans.findings());
                                if (cred != null) return cred;
                                pending.addAll(spans.findings());
                            }
                            case RawSpanOutcome.StageFailed f -> {
                                if (cs.onStageFailure() == FailureMode.FAIL_CLOSED)
                                    return new PipelineOutcome.StageFailure(
                                        rsd.id(), f.reason());
                                // FAIL_OPEN: recorded via `ran`; the run continues.
                            }
                        }
                    }
                    case Transformer t -> {
                        TransformOutcome o =
                            t.transform(NormalizedView.identityOf(original));
                        ran.add(t.id());
                        switch (o) {
                            case TransformOutcome.Normalized n ->
                                view = Optional.of(n.view());
                            case TransformOutcome.StageFailed f -> {
                                if (cs.onStageFailure() == FailureMode.FAIL_CLOSED)
                                    return new PipelineOutcome.StageFailure(
                                        t.id(), f.reason());
                                // FAIL_OPEN: downstream falls back to the identity view.
                            }
                        }
                    }
                    case SemanticDetector sd -> {
                        SemanticOutcome o = sd.inspect(
                            view.orElse(NormalizedView.identityOf(original)));
                        ran.add(sd.id());
                        PipelineOutcome term = applySemantic(sd, cs, o);
                        if (term != null) return term;
                    }
                }
            }

            return new PipelineOutcome.Allowed(
                List.copyOf(pending), List.copyOf(ran));
        } catch (Throwable t) {
            // A Stage MUST NOT throw; if one does, the entrypoint fails closed.
            return new PipelineOutcome.StageFailure(
                new StageId("pipeline"), new ReasonCode("pipeline.stage_threw"));
        }
    }

    /**
     * Runs a mandatory floor RawSpanDetector. Returns a terminal PipelineOutcome
     * (Incident on a credential, StageFailure on a StageFailed) or null to
     * continue. Floor stages are unconditionally FAIL_CLOSED.
     */
    private static PipelineOutcome runFloor(RawSpanDetector floor,
                                            RawContent original,
                                            List<SpanFinding> pending,
                                            List<StageId> ran) {
        RawSpanOutcome o = floor.inspect(original);
        ran.add(floor.id());
        return switch (o) {
            case RawSpanOutcome.Spans spans -> {
                PipelineOutcome cred =
                    applyCredentialRule(floor.id(), spans.findings());
                if (cred != null) yield cred;
                // A non-CREDENTIAL finding from a floor slot is accumulated,
                // never dropped (§8.1 steps 1 and 2).
                pending.addAll(spans.findings());
                yield null;
            }
            case RawSpanOutcome.StageFailed f ->
                new PipelineOutcome.StageFailure(floor.id(), f.reason());
        };
    }

    /**
     * The global credential rule (§8): the first CREDENTIAL-kind SpanFinding
     * ends the run as a terminal Incident, regardless of which RawSpanDetector
     * found it. Returns the Incident, or null if no credential is present.
     */
    private static PipelineOutcome applyCredentialRule(StageId by,
                                                       List<SpanFinding> findings) {
        for (SpanFinding f : findings)
            if (f.kind() == FindingKind.CREDENTIAL)
                return new PipelineOutcome.Incident(by,
                    IncidentKind.CREDENTIAL_DETECTED,
                    Optional.of(f.where()), f.reason());
        return null;
    }

    /**
     * Applies a SemanticDetector's outcome. Returns a terminal PipelineOutcome
     * (Blocked / Incident / StageFailure) or null to continue. The executor
     * re-checks declaredActions(): a Verdict whose action was not declared at
     * assembly is fail-closed treated as a StageFailed.
     */
    private static PipelineOutcome applySemantic(SemanticDetector sd,
                                                 ConfiguredStage cs,
                                                 SemanticOutcome o) {
        return switch (o) {
            case SemanticOutcome.Clean c -> null;
            case SemanticOutcome.StageFailed f ->
                cs.onStageFailure() == FailureMode.FAIL_CLOSED
                    ? new PipelineOutcome.StageFailure(sd.id(), f.reason())
                    : null;
            case SemanticOutcome.Verdict v -> {
                SemanticVerdict verdict = v.verdict();
                if (!sd.declaredActions().contains(verdict.action())) {
                    yield cs.onStageFailure() == FailureMode.FAIL_CLOSED
                        ? new PipelineOutcome.StageFailure(sd.id(),
                            new ReasonCode("executor.undeclared_action"))
                        : null;
                }
                yield switch (verdict.action()) {
                    case BLOCK -> new PipelineOutcome.Blocked(
                        sd.id(), verdict.reason());
                    case INCIDENT -> new PipelineOutcome.Incident(sd.id(),
                        IncidentKind.OTHER, Optional.empty(), verdict.reason());
                };
            }
        };
    }
}
