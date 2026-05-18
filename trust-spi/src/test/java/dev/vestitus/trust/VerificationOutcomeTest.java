package dev.vestitus.trust;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VerificationOutcomeTest {
    @Test
    void verifiedFactoryConstructs() {
        var v = VerificationOutcome.verified(new byte[]{1, 2}, "acme", new BundleVersion(3));
        assertTrue(v.isVerified());
        assertInstanceOf(VerificationOutcome.Verified.class, v);
        var ver = (VerificationOutcome.Verified) v;
        assertEquals("acme", ver.subjectId());
        assertEquals(new BundleVersion(3), ver.version());
        assertArrayEquals(new byte[]{1, 2}, ver.authenticatedPayload());
    }

    @Test
    void rejectedFactoryConstructs() {
        var r = VerificationOutcome.rejected("bad signature");
        assertFalse(r.isVerified());
        assertEquals("bad signature", ((VerificationOutcome.Rejected) r).reason());
    }

    @Test
    void verifiedPayloadClonedIn() {
        byte[] src = {1, 2, 3};
        var v = new VerificationOutcome.Verified(src, "s", new BundleVersion(1));
        src[0] = 99;
        assertArrayEquals(new byte[]{1, 2, 3}, v.authenticatedPayload());
    }

    @Test
    void verifiedPayloadClonedOut() {
        var v = new VerificationOutcome.Verified(new byte[]{1, 2, 3}, "s", new BundleVersion(1));
        v.authenticatedPayload()[0] = 99;
        assertArrayEquals(new byte[]{1, 2, 3}, v.authenticatedPayload());
    }

    @Test
    void verifiedNullPayloadRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new VerificationOutcome.Verified(null, "s", new BundleVersion(1)));
    }

    @Test
    void verifiedBlankSubjectRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new VerificationOutcome.Verified(new byte[]{0}, " ", new BundleVersion(1)));
    }

    @Test
    void verifiedNullVersionRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new VerificationOutcome.Verified(new byte[]{0}, "s", null));
    }

    @Test
    void rejectedNullReasonRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new VerificationOutcome.Rejected(null));
    }

    @Test
    void rejectedBlankReasonRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new VerificationOutcome.Rejected("  "));
    }

    @Test
    void isVerifiedTrueForVerified() {
        assertTrue(VerificationOutcome.verified(new byte[]{0}, "s", new BundleVersion(0))
            .isVerified());
    }

    @Test
    void isVerifiedFalseForRejected() {
        assertFalse(VerificationOutcome.rejected("x").isVerified());
    }

    @Test
    void exhaustiveSwitchHasNoDefault() {
        VerificationOutcome o = VerificationOutcome.rejected("nope");
        String tag = switch (o) {                  // no default: sealed exhaustiveness
            case VerificationOutcome.Verified v -> "ok:" + v.subjectId();
            case VerificationOutcome.Rejected r -> "no:" + r.reason();
        };
        assertEquals("no:nope", tag);
    }

    @Test
    void sealedPermitsExactlyTwo() {
        var permitted = List.of(VerificationOutcome.class.getPermittedSubclasses());
        assertEquals(2, permitted.size());
        assertTrue(permitted.contains(VerificationOutcome.Verified.class));
        assertTrue(permitted.contains(VerificationOutcome.Rejected.class));
    }
}
