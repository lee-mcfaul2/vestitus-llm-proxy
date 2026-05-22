package dev.vestitus.inspect;

/**
 * Per-extra-stage failure policy. FAIL_CLOSED aborts the run with a
 * StageFailure; FAIL_OPEN records the failure and continues. Floor stages are
 * unconditionally FAIL_CLOSED and are never wrapped in a {@link ConfiguredStage}.
 */
public enum FailureMode { FAIL_CLOSED, FAIL_OPEN }
