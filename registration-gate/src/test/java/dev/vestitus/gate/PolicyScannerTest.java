package dev.vestitus.gate;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PolicyScannerTest {
    @Test
    void stripsLineCommentOutsideStrings() {
        String in = "permit(principal, action, resource); // self-permissive permit\n";
        assertEquals("permit(principal, action, resource); \n",
            PolicyScanner.stripComments(in));
    }

    @Test
    void doesNotStripSlashesInsideString() {
        String in = "permit(principal == User::\"a//b\", action, resource);";
        assertEquals(in, PolicyScanner.stripComments(in));
    }

    @Test
    void respectsEscapedQuoteWhenTrackingStringState() {
        // The \" is escaped: still inside the string, so the // is NOT a comment.
        String in = "permit(principal == User::\"a\\\"//x\", action, resource);";
        assertEquals(in, PolicyScanner.stripComments(in));
    }

    @Test
    void splitsStatementsOnTopLevelSemicolonsOnly() {
        String in = "permit(principal, action, resource);"
            + "forbid(principal, action, resource) when { resource == Resource::\"a;b\" };";
        List<String> s = PolicyScanner.splitStatements(in);
        assertEquals(2, s.size());
        assertEquals("permit(principal, action, resource)", s.get(0));
        assertEquals(
            "forbid(principal, action, resource) when { resource == Resource::\"a;b\" }",
            s.get(1));
    }

    @Test
    void splitStatementsDropsBlanks() {
        assertEquals(List.of("permit(principal, action, resource)"),
            PolicyScanner.splitStatements("permit(principal, action, resource); ;  ;"));
    }

    @Test
    void balancedScopeFindsParenSpanIgnoringStrings() {
        String stmt = "permit(principal == User::\"x)y\", action, resource) when { 1 == 1 }";
        int[] span = PolicyScanner.balancedSpan(stmt, stmt.indexOf('('), '(', ')');
        assertNotNull(span);
        assertEquals("principal == User::\"x)y\", action, resource",
            stmt.substring(span[0] + 1, span[1]));
    }

    @Test
    void balancedScopeReturnsNullWhenUnbalanced() {
        assertNull(PolicyScanner.balancedSpan("permit(principal, action", 6, '(', ')'));
    }

    @Test
    void splitTopLevelCommasIgnoresStringsAndNestedParens() {
        String scope = "principal == User::\"a,b\", action, resource in Group::\"g\"";
        List<String> slots = PolicyScanner.splitTopLevel(scope, ',');
        assertEquals(3, slots.size());
        assertEquals("principal == User::\"a,b\"", slots.get(0).trim());
        assertEquals("action", slots.get(1).trim());
        assertEquals("resource in Group::\"g\"", slots.get(2).trim());
    }

    @Test
    void containsTokenIsBoundaryAware() {
        assertTrue(PolicyScanner.containsToken("a == principal && x", "principal"));
        assertFalse(PolicyScanner.containsToken("principalId == 1", "principal"));
        assertTrue(PolicyScanner.containsToken("principal", "principal"));
    }

    @Test
    void blankStringContentsBlanksOnlyStringInteriors() {
        // "principal" has 9 interior chars → 9 spaces; code outside untouched
        assertEquals("a == \"         \" && principal",
            PolicyScanner.blankStringContents("a == \"principal\" && principal"));
        // principal outside the string is still found by containsToken
        assertTrue(PolicyScanner.containsToken(
            PolicyScanner.blankStringContents("a == \"principal\" && principal"), "principal"));
        // principal inside the string is no longer found
        assertFalse(PolicyScanner.containsToken(
            PolicyScanner.blankStringContents("x == \"principal\""), "principal"));
        // real principal reference outside string is preserved
        assertTrue(PolicyScanner.containsToken(
            PolicyScanner.blankStringContents("principal == \"x\""), "principal"));
    }

    @Test
    void blankStringContentsEscapedQuoteCase() {
        // "a\"b" — interior is the 4 chars: a \ " b → 4 spaces; kept: opening " and closing "
        assertEquals("x == \"    \"",
            PolicyScanner.blankStringContents("x == \"a\\\"b\""));
    }
}
