package dev.vestitus.bundle.gate;

import java.util.ArrayList;
import java.util.List;

/**
 * String/comment-aware scanning primitives over Cedar policy source. Cedar uses
 * ONLY {@code //} line comments (no block comments), so only those are stripped.
 * A {@code "} is escaped iff preceded by an odd run of backslashes. These
 * helpers carry no policy semantics — that is {@link IdentityPredicateLint}.
 */
final class PolicyScanner {

    private PolicyScanner() {}

    /** True if the quote at {@code i} is escaped by an odd run of '\' before it. */
    private static boolean escapedQuote(String s, int i) {
        int back = 0;
        for (int k = i - 1; k >= 0 && s.charAt(k) == '\\'; k--) back++;
        return (back & 1) == 1;
    }

    /** Strip {@code //}-to-EOL comments that occur outside string literals. */
    static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        boolean inString = false;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            if (inString) {
                out.append(c);
                if (c == '"' && !escapedQuote(src, i)) inString = false;
                continue;
            }
            if (c == '"') {
                inString = true;
                out.append(c);
                continue;
            }
            if (c == '/' && i + 1 < src.length() && src.charAt(i + 1) == '/') {
                while (i < src.length() && src.charAt(i) != '\n') i++;
                if (i < src.length()) out.append('\n');
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    /** Split on {@code ;} outside strings; trim; drop blank statements. */
    static List<String> splitStatements(String src) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inString = false;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            if (inString) {
                cur.append(c);
                if (c == '"' && !escapedQuote(src, i)) inString = false;
                continue;
            }
            if (c == '"') { inString = true; cur.append(c); continue; }
            if (c == ';') {
                String t = cur.toString().trim();
                if (!t.isEmpty()) out.add(t);
                cur.setLength(0);
                continue;
            }
            cur.append(c);
        }
        String tail = cur.toString().trim();
        if (!tail.isEmpty()) out.add(tail);
        return out;
    }

    /**
     * From the opener at {@code openIdx}, return {@code [openIdx, closeIdx]} of
     * the matching {@code close} char (string-aware, depth-counted), or null if
     * unbalanced / no opener at that index.
     */
    static int[] balancedSpan(String s, int openIdx, char open, char close) {
        if (openIdx < 0 || openIdx >= s.length() || s.charAt(openIdx) != open) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (c == '"' && !escapedQuote(s, i)) inString = false;
                continue;
            }
            if (c == '"') { inString = true; continue; }
            if (c == open) depth++;
            else if (c == close) {
                depth--;
                if (depth == 0) return new int[]{openIdx, i};
            }
        }
        return null;
    }

    /** Split on top-level {@code sep} (string-aware, paren-depth 0). */
    static List<String> splitTopLevel(String s, char sep) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                cur.append(c);
                if (c == '"' && !escapedQuote(s, i)) inString = false;
                continue;
            }
            if (c == '"') { inString = true; cur.append(c); continue; }
            if (c == '(' || c == '[' || c == '{') depth++;
            else if (c == ')' || c == ']' || c == '}') depth--;
            if (c == sep && depth == 0) { out.add(cur.toString()); cur.setLength(0); continue; }
            cur.append(c);
        }
        out.add(cur.toString());
        return out;
    }

    /**
     * Replace string-literal *contents* with spaces (the surrounding quotes are
     * kept) so a textual token scan cannot match an identifier that lives inside
     * a {@code "..."} literal. String-state tracking mirrors the other
     * primitives (a {@code "} is escaped iff preceded by an odd backslash run).
     */
    static String blankStringContents(String s) {
        StringBuilder out = new StringBuilder(s.length());
        boolean inString = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                boolean end = c == '"' && !escapedQuote(s, i);
                out.append(end ? '"' : ' ');
                if (end) inString = false;
                continue;
            }
            if (c == '"') { inString = true; out.append('"'); continue; }
            out.append(c);
        }
        return out.toString();
    }

    /** True if {@code token} occurs in {@code s} bounded by non-identifier chars. */
    static boolean containsToken(String s, String token) {
        int from = 0;
        while (true) {
            int idx = s.indexOf(token, from);
            if (idx < 0) return false;
            boolean leftOk = idx == 0 || !isIdent(s.charAt(idx - 1));
            int end = idx + token.length();
            boolean rightOk = end >= s.length() || !isIdent(s.charAt(end));
            if (leftOk && rightOk) return true;
            from = idx + 1;
        }
    }

    private static boolean isIdent(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
