package dev.vestitus.bundle.reload;

/** Result of ONE reload attempt (tests target this directly; the retry
 *  wrapper interprets it). */
public enum ReloadOutcome {
    APPLIED,
    RETAINED_LAST_GOOD,
    FAILED_CLOSED,
    ABORTED_RETRYABLE
}
