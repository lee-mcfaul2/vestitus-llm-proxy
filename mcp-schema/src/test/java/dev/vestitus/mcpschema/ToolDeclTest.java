package dev.vestitus.mcpschema;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ToolDeclTest {
    private static FieldDecl field() {
        return new FieldDecl("email", PiiType.DIRECT_IDENTIFIER, new IamBinding("crm:read"));
    }

    @Test
    void validToolConstructs() {
        var t = new ToolDecl("findContact", "Find a contact by email", List.of(field()));
        assertEquals("findContact", t.name());
        assertEquals("Find a contact by email", t.description());
        assertEquals(1, t.fields().size());
    }

    @Test
    void emptyFieldsAllowed() {
        var t = new ToolDecl("ping", "", List.of());
        assertEquals(0, t.fields().size());
    }

    @Test
    void blankNameIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new ToolDecl(" ", "d", List.of()));
    }

    @Test
    void nullDescriptionIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new ToolDecl("t", null, List.of()));
    }

    @Test
    void nullFieldsIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new ToolDecl("t", "d", null));
    }

    @Test
    void fieldsListIsImmutableCopy() {
        var src = new ArrayList<FieldDecl>();
        src.add(field());
        var t = new ToolDecl("t", "d", src);
        src.clear();
        assertEquals(1, t.fields().size());
        assertThrows(UnsupportedOperationException.class, () -> t.fields().add(field()));
    }
}
