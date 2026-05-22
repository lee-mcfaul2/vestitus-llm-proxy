package dev.vestitus.inspect;

/**
 * Thrown by an {@link InspectionPipeline} factory when the assembly-time
 * validator (design-spec §7) rejects a configuration. The process fails to
 * start — never silently at request time. The message names the offending
 * stage and the rule; {@link #reason()} is a stable {@link ReasonCode}.
 */
public final class PipelineAssemblyException extends RuntimeException {

    private final ReasonCode reason;

    public PipelineAssemblyException(ReasonCode reason, String message) {
        super(message);
        if (reason == null)
            throw new IllegalArgumentException("reason required");
        this.reason = reason;
    }

    /** The stable reason code for this assembly rejection. */
    public ReasonCode reason() { return reason; }
}
