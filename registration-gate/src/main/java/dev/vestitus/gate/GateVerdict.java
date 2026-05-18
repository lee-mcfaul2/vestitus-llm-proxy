package dev.vestitus.gate;

import java.util.List;

/**
 * Verdict of the ADR-002 §7 static-analysis gate over a declared MCP schema
 * (set). Mirrors {@code dev.vestitus.authz.AuthorizationDecision}'s sealed-ADT
 * discipline: a sealed pass/reject pair, static factories, an exhaustive-switch
 * predicate. Verdict-only — the canonical-output transform + content-bound HMAC
 * stamp is Plan 04c; this type is internal at this stage.
 */
public sealed interface GateVerdict
        permits GateVerdict.Pass, GateVerdict.Reject {

    record Pass() implements GateVerdict {}

    record Reject(List<String> reasons) implements GateVerdict {
        public Reject {
            if (reasons == null || reasons.isEmpty())
                throw new IllegalArgumentException("reject reasons required");
            for (String r : reasons) {
                if (r == null || r.isBlank())
                    throw new IllegalArgumentException("reject reason must be non-blank");
            }
            reasons = List.copyOf(reasons);
        }
    }

    static GateVerdict pass() { return new Pass(); }

    static GateVerdict reject(List<String> reasons) { return new Reject(reasons); }

    static GateVerdict reject(String reason) { return new Reject(List.of(reason)); }

    default boolean passed() {
        return switch (this) {
            case Pass p -> true;
            case Reject r -> false;
        };
    }
}
