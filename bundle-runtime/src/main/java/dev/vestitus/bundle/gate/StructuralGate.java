package dev.vestitus.bundle.gate;

import dev.vestitus.mcpschema.McpSchema;
import java.util.ArrayList;
import java.util.List;

/**
 * ADR-003 D5 minimum structural gate over the already-digested MCP schema set.
 * Runs in the ADR-003 D6 core, downstream of {@code BundleDigester} (Plan 05c)
 * and BEFORE the Cedar compile (wired by Plan 05h:
 * verify -> bind -> digest -> no-rollback (05d) -> THIS gate -> Cedar compile
 * -> set-atomic swap (05e)).
 *
 * <p>Two checks, fail-closed, reasons unioned:
 * <ul>
 *   <li>cross-MCP resource-identity injectivity (Inv. 11) via
 *       {@link CrossMcpInjectivityCheck#checkSet} (set-unique {@code mcpId},
 *       {@code /}/control-char-free + intra-scope-unique tool/field names);</li>
 *   <li>per-schema rejection of identity-less / self-permissive {@code permit}
 *       via {@link IdentityPredicateLint#check} over each schema's ruleset.</li>
 * </ul>
 *
 * <p><b>Deliberate D5 boundary (not a gap):</b>
 * <ul>
 *   <li>The "reject a field missing its PII or IAM annotation" D5 clause is
 *       <em>already structurally enforced upstream</em> by {@code mcp-schema}
 *       {@code FieldDecl}'s compact constructor (a {@code FieldDecl} with a
 *       null {@code pii} or {@code iam} cannot be constructed), so a digested
 *       {@code McpSchema} structurally cannot carry a PII/IAM-incomplete field
 *       — it is not re-checked here. Documented non-gap, not a coverage hole.</li>
 *   <li>The semantic self-permissive tautology
 *       ({@code when { principal == principal }}) is the named ADR-003 §4
 *       boundary-2 residual — the deleted cvc5/symbolic follow-on. It is
 *       intentionally NOT caught here (a {@code when} textually containing
 *       {@code principal} passes the conservative textual lint).</li>
 *   <li>This is NOT cvc5/symbolic. The ADR-002 native-{@code cedar_validate}
 *       ceremony ({@code CedarValidateCheck}/{@code StaticAnalysisGate}) is
 *       deliberately NOT salvaged (ADR-003 D5/D10). This gate does not verify,
 *       fetch, digest, no-rollback, swap, or Cedar-compile — those are the
 *       sibling/capstone steps (05c/05d/05e/05g/05h).</li>
 * </ul>
 *
 * <p>Fail-closed: a {@code null} set, any sub-check {@code Reject}, or any
 * {@link Throwable} yields a {@code Reject}. An empty list yields {@code Pass}
 * — there are no MCPs to vet, and set-admissibility (is an empty set
 * admissible) is the caller's per ADR-003 D6, not an error here (Plan 05h).
 */
public final class StructuralGate {

    private StructuralGate() {}

    public static GateVerdict vet(List<McpSchema> schemas) {
        try {
            if (schemas == null)
                return GateVerdict.reject("mcp-schema set must be non-null (fail-closed)");
            List<String> reasons = new ArrayList<>();
            collect(reasons, CrossMcpInjectivityCheck.checkSet(schemas));
            for (McpSchema s : schemas)
                collect(reasons, IdentityPredicateLint.check(s.ruleset()));
            return reasons.isEmpty() ? GateVerdict.pass() : GateVerdict.reject(reasons);
        } catch (Throwable t) {
            return GateVerdict.reject("structural gate error (fail-closed): " + t);
        }
    }

    private static void collect(List<String> acc, GateVerdict v) {
        if (v instanceof GateVerdict.Reject r) {
            acc.addAll(r.reasons());
        }
    }
}
