package dev.vestitus.gate.cli;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class GateCliTest {

    // One element of the array. Cedar schema + policy lifted verbatim from
    // the authorizer-cedar known-good (code 2 / Valid) fixture; the
    // constrained principal also passes IdentityPredicateLint.
    private static final String PASS_DOC = """
        {"schemaVersion":{"value":"1.0.0"},"mcpId":"crm-mcp",
         "tools":[{"name":"findContact","description":"Find a contact",
           "fields":[{"name":"email","pii":"DIRECT_IDENTIFIER",
             "iam":{"entitlement":"crm:read"}}]}],
         "ruleset":{"text":"permit(principal == User::\\"alice\\", action == Action::\\"view\\", resource == Resource::\\"doc1\\");"},
         "cedarSchema":{"text":"entity User; entity Resource; action \\"view\\" appliesTo { principal: User, resource: Resource };"}}
        """;
    // Self-permissive (identity-less) permit -> IdentityPredicateLint rejects.
    private static final String REJECT_DOC = PASS_DOC.replace(
        "permit(principal == User::\\\"alice\\\", action == Action::\\\"view\\\", resource == Resource::\\\"doc1\\\");",
        "permit(principal, action, resource);");

    private record Run(int code, String out, String err) {}

    private static Run run(String stdin) {
        var outB = new ByteArrayOutputStream();
        var errB = new ByteArrayOutputStream();
        int code = new GateCli().run(
            new String[]{},
            new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)),
            new PrintStream(outB, true, StandardCharsets.UTF_8),
            new PrintStream(errB, true, StandardCharsets.UTF_8));
        return new Run(code,
            outB.toString(StandardCharsets.UTF_8),
            errB.toString(StandardCharsets.UTF_8));
    }

    @Test
    void passSetEmitsEnvelopeWhoseStampVerifies() {
        Run r = run("[" + PASS_DOC + "]");
        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().startsWith("{\"v\":1,\"verdict\":\"PASS\",\"canonical\":["));
        // Recompute the stamp over the emitted canonical and compare.
        String out = r.out();
        int cStart = out.indexOf("\"canonical\":") + "\"canonical\":".length();
        int sIdx = out.indexOf(",\"stamp\":\"");
        String canonical = out.substring(cStart, sIdx);
        String stamp = out.substring(sIdx + ",\"stamp\":\"".length(),
            out.length() - "\"}".length());
        assertEquals(
            GateStamp.hmacSha256Hex(canonical.getBytes(StandardCharsets.UTF_8)),
            stamp);
    }

    @Test
    void rejectSetExits1WithEmptyStdoutAndReasonsOnStderr() {
        Run r = run("[" + REJECT_DOC + "]");
        assertEquals(1, r.code());
        assertEquals("", r.out(), "transform-not-checkpoint: no loadable output");
        assertFalse(r.err().isBlank());
    }

    @Test
    void malformedJsonExits2() {
        Run r = run("not a json array");
        assertEquals(2, r.code());
        assertEquals("", r.out());
        assertFalse(r.err().isBlank());
    }

    @Test
    void notAnArrayExits2() {
        Run r = run(PASS_DOC); // a bare object, not the required array
        assertEquals(2, r.code());
    }
}
