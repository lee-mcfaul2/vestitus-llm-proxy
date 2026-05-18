package dev.vestitus.bundle.gate;

import dev.vestitus.mcpschema.CedarRuleset;
import java.util.ArrayList;
import java.util.List;

/**
 * ADR-002 §7 check 2 — the security linchpin. {@code cedar validate} is
 * typecheck-only and the C ABI exposes no policy AST, so identity-less /
 * self-permissive {@code permit} rejection is a deterministic, conservative,
 * fail-closed TEXTUAL lint: any unknown / ambiguous / unbalanced shape rejects.
 *
 * <p>A {@code permit} is acceptable iff its principal scope is constrained
 * (slot 0 differs from the bare token {@code principal}) OR a {@code when} /
 * {@code unless} clause body textually references the {@code principal} token.
 *
 * <p><b>Deferred residual (named, not hidden):</b> the semantic tautology case
 * ({@code when { principal == principal }}, {@code when { 1 == 1 }}) is the
 * ADR-002 §7 cvc5 symbolic follow-on and is intentionally NOT caught here — a
 * {@code when} textually containing {@code principal} passes this lint.
 */
public final class IdentityPredicateLint {

    private IdentityPredicateLint() {}

    public static GateVerdict check(CedarRuleset ruleset) {
        try {
            String src = PolicyScanner.stripComments(ruleset.text());
            List<String> reasons = new ArrayList<>();
            for (String raw : PolicyScanner.splitStatements(src)) {
                String stmt = stripAnnotations(raw).trim();
                if (startsWithKeyword(stmt, "forbid")) {
                    continue;
                }
                if (!startsWithKeyword(stmt, "permit")) {
                    return GateVerdict.reject(
                        "unrecognized policy statement (fail-closed): " + trim80(stmt));
                }
                String reason = checkPermit(stmt);
                if (reason != null) reasons.add(reason);
            }
            return reasons.isEmpty() ? GateVerdict.pass() : GateVerdict.reject(reasons);
        } catch (Throwable t) {
            return GateVerdict.reject("identity-predicate lint error (fail-closed): " + t);
        }
    }

    /** Strip zero+ leading {@code @ident("...")} annotations (string-aware). */
    private static String stripAnnotations(String s) {
        String cur = s.trim();
        while (cur.startsWith("@")) {
            int p = cur.indexOf('(');
            if (p < 0) break;
            int[] span = PolicyScanner.balancedSpan(cur, p, '(', ')');
            if (span == null) break;
            cur = cur.substring(span[1] + 1).trim();
        }
        return cur;
    }

    /** True if {@code s} begins with {@code kw} followed by whitespace or '('. */
    private static boolean startsWithKeyword(String s, String kw) {
        if (!s.startsWith(kw)) return false;
        if (s.length() == kw.length()) return true;
        char c = s.charAt(kw.length());
        return Character.isWhitespace(c) || c == '(';
    }

    /** @return a rejection reason for a bad permit, or null if acceptable. */
    private static String checkPermit(String stmt) {
        int open = stmt.indexOf('(');
        int[] scope = PolicyScanner.balancedSpan(stmt, open, '(', ')');
        if (scope == null) {
            return "malformed permit scope (fail-closed): " + trim80(stmt);
        }
        String inner = stmt.substring(scope[0] + 1, scope[1]);
        List<String> slots = PolicyScanner.splitTopLevel(inner, ',');
        if (slots.size() != 3) {
            return "malformed permit scope (fail-closed, expected 3 slots): "
                + trim80(stmt);
        }
        boolean scopeConstrained = !slots.get(0).trim().equals("principal");
        if (scopeConstrained) {
            return null;
        }
        String tail = stmt.substring(scope[1] + 1);
        if (conditionMentionsPrincipal(tail)) {
            return null;
        }
        if (ambiguousTail(tail)) {
            return "malformed permit condition (fail-closed): " + trim80(stmt);
        }
        return "self-permissive / identity-less permit (no principal constraint "
            + "and no principal-referencing when/unless): " + trim80(stmt);
    }

    /** A when/unless block whose brace body textually references `principal`. */
    private static boolean conditionMentionsPrincipal(String tail) {
        for (String kw : new String[]{"when", "unless"}) {
            int from = 0;
            while (true) {
                int idx = indexOfToken(tail, kw, from);
                if (idx < 0) break;
                int brace = tail.indexOf('{', idx);
                if (brace < 0) break;
                int[] span = PolicyScanner.balancedSpan(tail, brace, '{', '}');
                if (span == null) break;
                String body = tail.substring(span[0] + 1, span[1]);
                if (PolicyScanner.containsToken(
                        PolicyScanner.blankStringContents(body), "principal")) return true;
                from = span[1] + 1;
            }
        }
        return false;
    }

    /** True if the tail has a when/unless we could not cleanly isolate. */
    private static boolean ambiguousTail(String tail) {
        for (String kw : new String[]{"when", "unless"}) {
            int idx = indexOfToken(tail, kw, 0);
            if (idx >= 0) {
                int brace = tail.indexOf('{', idx);
                if (brace < 0
                    || PolicyScanner.balancedSpan(tail, brace, '{', '}') == null) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int indexOfToken(String s, String token, int from) {
        int i = from;
        while (true) {
            int idx = s.indexOf(token, i);
            if (idx < 0) return -1;
            boolean leftOk = idx == 0
                || !Character.isLetterOrDigit(s.charAt(idx - 1)) && s.charAt(idx - 1) != '_';
            int end = idx + token.length();
            boolean rightOk = end >= s.length()
                || !Character.isLetterOrDigit(s.charAt(end)) && s.charAt(end) != '_';
            if (leftOk && rightOk) return idx;
            i = idx + 1;
        }
    }

    private static String trim80(String s) {
        return s.length() <= 80 ? s : s.substring(0, 80);
    }
}
