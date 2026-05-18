package dev.vestitus.bundle;

import dev.vestitus.trust.BundleVersion;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VersionDecisionTest {

    @Test
    void acceptCarriesTheVersionAndIsAccepted() {
        BundleVersion v = new BundleVersion(7L);
        VersionDecision d = VersionDecision.accept(v);
        assertInstanceOf(VersionDecision.Accept.class, d);
        assertEquals(v, ((VersionDecision.Accept) d).version());
        assertTrue(d.accepted());
    }

    @Test
    void rejectCarriesTheReasonAndIsNotAccepted() {
        VersionDecision d = VersionDecision.reject("stale");
        assertInstanceOf(VersionDecision.Reject.class, d);
        assertEquals("stale", ((VersionDecision.Reject) d).reason());
        assertFalse(d.accepted());
    }

    @Test
    void acceptRejectsNullVersion() {
        assertThrows(IllegalArgumentException.class,
            () -> new VersionDecision.Accept(null));
        assertThrows(IllegalArgumentException.class,
            () -> VersionDecision.accept(null));
    }

    @Test
    void rejectRejectsNullReason() {
        assertThrows(IllegalArgumentException.class,
            () -> new VersionDecision.Reject(null));
        assertThrows(IllegalArgumentException.class,
            () -> VersionDecision.reject(null));
    }

    @Test
    void rejectRejectsBlankReason() {
        assertThrows(IllegalArgumentException.class,
            () -> new VersionDecision.Reject("   "));
        assertThrows(IllegalArgumentException.class,
            () -> VersionDecision.reject(""));
    }

    @Test
    void acceptedIsExhaustiveOverBothPermittedSubtypes() {
        assertTrue(VersionDecision.accept(new BundleVersion(1L)).accepted());
        assertFalse(VersionDecision.reject("r").accepted());
    }

    @Test
    void sealedPermitsExactlyAcceptAndReject() {
        Class<?>[] permitted = VersionDecision.class.getPermittedSubclasses();
        assertEquals(2, permitted.length);
        assertEquals(
            java.util.Set.of(VersionDecision.Accept.class,
                             VersionDecision.Reject.class),
            java.util.Set.of(permitted[0], permitted[1]));
    }
}
