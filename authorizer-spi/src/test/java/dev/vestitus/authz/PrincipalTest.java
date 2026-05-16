package dev.vestitus.authz;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class PrincipalTest {
    @Test
    void buildsAndIsValueEqual() {
        Principal p = new Principal("u1", Set.of("read"), Map.of("dept", "fin"));
        assertEquals("u1", p.id());
        assertEquals(Set.of("read"), p.scopes());
        assertEquals(Map.of("dept", "fin"), p.attributes());
        assertEquals(new Principal("u1", Set.of("read"), Map.of("dept", "fin")), p);
    }

    @Test
    void rejectsBlankIdAndNulls() {
        assertThrows(IllegalArgumentException.class, () -> new Principal(" ", Set.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new Principal("u", null, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new Principal("u", Set.of(), null));
    }

    @Test
    void defensivelyCopiesCollections() {
        Set<String> sc = new HashSet<>(Set.of("read"));
        Map<String, String> at = new HashMap<>(Map.of("k", "v"));
        Principal p = new Principal("u", sc, at);
        sc.add("write");
        at.put("k2", "v2");
        assertEquals(Set.of("read"), p.scopes());
        assertEquals(Map.of("k", "v"), p.attributes());
        assertThrows(UnsupportedOperationException.class, () -> p.scopes().add("x"));
    }
}
