package dev.vestitus.inspect.llmguard;

import dev.vestitus.inspect.ReasonCode;
import dev.vestitus.inspect.SemanticAction;
import dev.vestitus.inspect.StageId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LlmGuardDetectorConfigTest {

    @Test
    void acceptsAWellFormedConfig() {
        var cfg = new LlmGuardDetectorConfig(
            new StageId("inspection.llmguard.prompt-injection"),
            "PromptInjection", 0.5, SemanticAction.BLOCK,
            new ReasonCode("llmguard.prompt_injection"));
        assertEquals("PromptInjection", cfg.scannerName());
        assertEquals(0.5, cfg.threshold());
    }

    @Test
    void rejectsNullsBlankScannerNameAndOutOfRangeThreshold() {
        var id = new StageId("d");
        var rc = new ReasonCode("x.y");
        assertThrows(NullPointerException.class, () -> new LlmGuardDetectorConfig(
            null, "X", 0.5, SemanticAction.BLOCK, rc));
        assertThrows(IllegalArgumentException.class, () -> new LlmGuardDetectorConfig(
            id, "  ", 0.5, SemanticAction.BLOCK, rc));
        assertThrows(IllegalArgumentException.class, () -> new LlmGuardDetectorConfig(
            id, "X", -0.1, SemanticAction.BLOCK, rc));
        assertThrows(IllegalArgumentException.class, () -> new LlmGuardDetectorConfig(
            id, "X", 1.01, SemanticAction.BLOCK, rc));
        assertThrows(NullPointerException.class, () -> new LlmGuardDetectorConfig(
            id, "X", 0.5, null, rc));
        assertThrows(NullPointerException.class, () -> new LlmGuardDetectorConfig(
            id, "X", 0.5, SemanticAction.BLOCK, null));
    }
}
