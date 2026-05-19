package dev.vestitus.authz;

/** Fail-closed signal that a schema could not be safely compiled. */
public final class PolicyCompileException extends RuntimeException {
    public PolicyCompileException(String message, Throwable cause) {
        super(message, cause);
    }
}
