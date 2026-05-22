package dev.vestitus.inspect;

import java.util.Objects;

/** An extra (non-floor) {@link Stage} plus its {@link FailureMode}. */
public record ConfiguredStage(Stage stage, FailureMode onStageFailure) {
    public ConfiguredStage {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(onStageFailure, "onStageFailure");
    }

    /** An extra stage that fails closed — the safe default for sugar APIs. */
    public static ConfiguredStage failClosed(Stage stage) {
        return new ConfiguredStage(stage, FailureMode.FAIL_CLOSED);
    }
}
