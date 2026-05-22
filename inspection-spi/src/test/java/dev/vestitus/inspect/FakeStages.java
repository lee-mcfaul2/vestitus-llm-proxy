package dev.vestitus.inspect;

import java.util.List;
import java.util.Set;

/** Deterministic in-memory Stage fakes shared by the validator and executor tests. */
final class FakeStages {

    private FakeStages() {}

    /** A RawSpanDetector returning a fixed outcome. */
    static RawSpanDetector rawDetector(String id, RawSpanOutcome outcome) {
        StageId sid = new StageId(id);
        return new RawSpanDetector() {
            @Override public StageId id() { return sid; }
            @Override public RawSpanOutcome inspect(RawContent in) { return outcome; }
        };
    }

    /** A RawSpanDetector that appends its id to {@code log} when inspected. */
    static RawSpanDetector recordingRaw(String id, List<String> log,
                                        RawSpanOutcome outcome) {
        StageId sid = new StageId(id);
        return new RawSpanDetector() {
            @Override public StageId id() { return sid; }
            @Override public RawSpanOutcome inspect(RawContent in) {
                log.add(id);
                return outcome;
            }
        };
    }

    /** A Transformer returning a fixed outcome. */
    static Transformer transformer(String id, SpanFidelity fidelity,
                                   TransformOutcome outcome) {
        StageId sid = new StageId(id);
        return new Transformer() {
            @Override public StageId id() { return sid; }
            @Override public SpanFidelity fidelity() { return fidelity; }
            @Override public TransformOutcome transform(NormalizedView in) {
                return outcome;
            }
        };
    }

    /** A SemanticDetector with the given declared actions returning a fixed outcome. */
    static SemanticDetector semantic(String id, Set<SemanticAction> declared,
                                     SemanticOutcome outcome) {
        StageId sid = new StageId(id);
        return new SemanticDetector() {
            @Override public StageId id() { return sid; }
            @Override public Set<SemanticAction> declaredActions() { return declared; }
            @Override public SemanticOutcome inspect(NormalizedView view) {
                return outcome;
            }
        };
    }

    /**
     * A SemanticDetector (declares BLOCK) that returns Verdict(BLOCK) iff the
     * inspected view body equals {@code triggerBody}, else Clean — used to
     * prove the executor threads the transformed view to semantic detectors.
     */
    static SemanticDetector semanticBlockingOn(String id, String triggerBody) {
        StageId sid = new StageId(id);
        return new SemanticDetector() {
            @Override public StageId id() { return sid; }
            @Override public Set<SemanticAction> declaredActions() {
                return Set.of(SemanticAction.BLOCK);
            }
            @Override public SemanticOutcome inspect(NormalizedView view) {
                return view.body().equals(triggerBody)
                    ? new SemanticOutcome.Verdict(new SemanticVerdict(
                        sid, new ReasonCode("test.blocked"), SemanticAction.BLOCK))
                    : new SemanticOutcome.Clean();
            }
        };
    }
}
