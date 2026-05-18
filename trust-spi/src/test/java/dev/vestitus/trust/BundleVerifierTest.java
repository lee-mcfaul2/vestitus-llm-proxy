package dev.vestitus.trust;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BundleVerifierTest {
    /** A trivial fail-closed-shaped in-test impl proving the seam compiles. */
    private static final class AlwaysReject implements BundleVerifier {
        @Override
        public VerificationOutcome verify(Bundle bundle, VerificationConfig config) {
            return VerificationOutcome.rejected("test verifier rejects all");
        }
    }

    @Test
    void seamCompilesAndIsFailClosedShaped() {
        BundleVerifier v = new AlwaysReject();
        var outcome = v.verify(
            new Bundle(new byte[]{1}, new byte[0], "ref"),
            new VerificationConfig("^x$", "iss", Map.of()));
        assertFalse(outcome.isVerified());
        assertInstanceOf(VerificationOutcome.Rejected.class, outcome);
    }

    @Test
    void verifierIsAnOpenInterfaceNotSealed() {
        assertTrue(BundleVerifier.class.isInterface());
        assertFalse(BundleVerifier.class.isSealed());
    }
}
