package dev.vestitus.mcpschema;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PiiTypeDriftTest {
    @Test
    void controlledVocabularyDoesNotDrift() {
        var actual = java.util.Arrays.stream(PiiType.values())
            .map(Enum::name).sorted().toList();
        var expected = List.of(
            "DIRECT_IDENTIFIER", "NONE", "QUASI_IDENTIFIER", "SENSITIVE");
        assertEquals(expected, actual,
            "PiiType controlled vocabulary drifted; this is a spec-silent decision "
            + "that must change deliberately, not silently");
    }
}
