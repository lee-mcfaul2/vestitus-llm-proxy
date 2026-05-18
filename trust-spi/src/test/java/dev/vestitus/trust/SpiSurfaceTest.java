package dev.vestitus.trust;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SpiSurfaceTest {
    @Test
    void verificationOutcomePermitsExactlyVerifiedAndRejected() {
        var permitted = List.of(VerificationOutcome.class.getPermittedSubclasses());
        assertEquals(2, permitted.size(),
            "VerificationOutcome sealed surface drifted; this is a deliberate "
            + "ADT decision that must change deliberately, not silently");
        assertTrue(permitted.contains(VerificationOutcome.Verified.class));
        assertTrue(permitted.contains(VerificationOutcome.Rejected.class));
    }

    @Test
    void verificationOutcomeIsSealed() {
        assertTrue(VerificationOutcome.class.isSealed());
        assertTrue(VerificationOutcome.class.isInterface());
    }

    @Test
    void bundleVerifierIsOpenInterfaceNotSealed() {
        assertTrue(BundleVerifier.class.isInterface());
        assertFalse(BundleVerifier.class.isSealed(),
            "BundleVerifier is an open compile-time seam (ADR-003 D2/D3); "
            + "an accidental seal would break the extension point");
    }

    @Test
    void bundleDigesterIsOpenInterfaceNotSealed() {
        assertTrue(BundleDigester.class.isInterface());
        assertFalse(BundleDigester.class.isSealed(),
            "BundleDigester is an open compile-time seam (ADR-003 D2/D4); "
            + "an accidental seal would break the extension point");
    }
}
