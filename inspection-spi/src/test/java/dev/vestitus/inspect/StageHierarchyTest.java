package dev.vestitus.inspect;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StageHierarchyTest {

    @Test
    void stageAndDetectorAreSealed() {
        assertTrue(Stage.class.isSealed());
        assertTrue(Detector.class.isSealed());
        assertEquals(Set.of(Transformer.class, Detector.class),
            Set.of(Stage.class.getPermittedSubclasses()));
        assertEquals(Set.of(RawSpanDetector.class, SemanticDetector.class),
            Set.of(Detector.class.getPermittedSubclasses()));
    }

    @Test
    void aRawSpanDetectorIsImplementableAndReturnsAnOutcome() {
        RawSpanDetector det = new RawSpanDetector() {
            @Override public StageId id() { return new StageId("d"); }
            @Override public RawSpanOutcome inspect(RawContent in) {
                return new RawSpanOutcome.Spans(List.of());
            }
        };
        assertEquals(new StageId("d"), det.id());
        assertInstanceOf(RawSpanOutcome.Spans.class,
            det.inspect(new RawContent("x", ContentKind.TEXT)));
        assertInstanceOf(Detector.class, det);
        assertInstanceOf(Stage.class, det);
    }

    @Test
    void aSemanticDetectorDeclaresItsActions() {
        SemanticDetector det = new SemanticDetector() {
            @Override public StageId id() { return new StageId("s"); }
            @Override public Set<SemanticAction> declaredActions() {
                return Set.of(SemanticAction.BLOCK);
            }
            @Override public SemanticOutcome inspect(NormalizedView view) {
                return new SemanticOutcome.Clean();
            }
        };
        assertEquals(Set.of(SemanticAction.BLOCK), det.declaredActions());
    }

    @Test
    void aTransformerDeclaresItsFidelity() {
        Transformer t = new Transformer() {
            @Override public StageId id() { return new StageId("t"); }
            @Override public SpanFidelity fidelity() { return SpanFidelity.LOSSY; }
            @Override public TransformOutcome transform(NormalizedView in) {
                return new TransformOutcome.Normalized(in);
            }
        };
        assertEquals(SpanFidelity.LOSSY, t.fidelity());
    }
}
