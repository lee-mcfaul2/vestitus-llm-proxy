package dev.vestitus.authz;

public sealed interface AuthorizationDecision
        permits AuthorizationDecision.Allow, AuthorizationDecision.Deny {

    record Allow() implements AuthorizationDecision {}

    record Deny(String reason) implements AuthorizationDecision {
        public Deny {
            if (reason == null || reason.isBlank())
                throw new IllegalArgumentException("deny reason required");
        }
    }

    static AuthorizationDecision allow() { return new Allow(); }

    static AuthorizationDecision deny(String reason) { return new Deny(reason); }

    default boolean allowed() {
        return switch (this) {
            case Allow a -> true;
            case Deny d -> false;
        };
    }
}
