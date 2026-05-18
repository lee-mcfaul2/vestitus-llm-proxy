package dev.vestitus.gate.cli;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import static org.junit.jupiter.api.Assertions.*;

class GateStampTest {

    @Test
    void hmacIsDeterministic() {
        byte[] in = "canonical-bytes".getBytes(StandardCharsets.UTF_8);
        assertEquals(GateStamp.hmacSha256Hex(in), GateStamp.hmacSha256Hex(in));
    }

    @Test
    void differentInputDifferentStamp() {
        assertNotEquals(
            GateStamp.hmacSha256Hex("a".getBytes(StandardCharsets.UTF_8)),
            GateStamp.hmacSha256Hex("b".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void stampIs64LowercaseHex() {
        String s = GateStamp.hmacSha256Hex(new byte[]{1, 2, 3});
        assertTrue(s.matches("[0-9a-f]{64}"), s);
    }

    @Test
    void knownAnswerMatchesIndependentHmacOverTheDocumentedKey() throws Exception {
        // KAT: recompute HMAC-SHA256 with the documented synthetic key bytes
        // and assert GateStamp produces the identical value.
        byte[] key = "vestitus-gate-pass-hmac-key-v1!!"
            .getBytes(StandardCharsets.US_ASCII);
        assertEquals(32, key.length);
        byte[] msg = "the-canonical-set".getBytes(StandardCharsets.UTF_8);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        byte[] expected = mac.doFinal(msg);
        StringBuilder hex = new StringBuilder(64);
        for (byte x : expected) hex.append(String.format("%02x", x));
        assertEquals(hex.toString(), GateStamp.hmacSha256Hex(msg));
    }
}
