package dev.vestitus.bundle.gate;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GateVerdictTest {
    @Test
    void passConstructsAndPasses() {
        GateVerdict v = GateVerdict.pass();
        assertInstanceOf(GateVerdict.Pass.class, v);
        assertTrue(v.passed());
    }

    @Test
    void rejectFromListConstructsAndDoesNotPass() {
        GateVerdict v = GateVerdict.reject(List.of("r1", "r2"));
        assertInstanceOf(GateVerdict.Reject.class, v);
        assertFalse(v.passed());
        assertEquals(List.of("r1", "r2"), ((GateVerdict.Reject) v).reasons());
    }

    @Test
    void rejectFromSingleStringWraps() {
        GateVerdict v = GateVerdict.reject("only");
        assertEquals(List.of("only"), ((GateVerdict.Reject) v).reasons());
        assertFalse(v.passed());
    }

    @Test
    void rejectRejectsNullList() {
        assertThrows(IllegalArgumentException.class,
            () -> GateVerdict.reject((List<String>) null));
    }

    @Test
    void rejectRejectsEmptyList() {
        assertThrows(IllegalArgumentException.class,
            () -> GateVerdict.reject(List.of()));
    }

    @Test
    void rejectRejectsBlankReason() {
        assertThrows(IllegalArgumentException.class,
            () -> GateVerdict.reject(List.of("ok", "  ")));
    }

    @Test
    void rejectReasonsAreImmutableCopy() {
        var src = new ArrayList<String>();
        src.add("r1");
        GateVerdict.Reject v = (GateVerdict.Reject) GateVerdict.reject(src);
        src.clear();
        assertEquals(1, v.reasons().size());
        assertThrows(UnsupportedOperationException.class, () -> v.reasons().add("x"));
    }
}
