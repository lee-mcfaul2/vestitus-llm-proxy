package dev.vestitus.authz;

import java.util.Map;

public record AuthorizationRequest(Principal principal, String action,
                                   ResourceRef resource, Map<String, String> context) {
    public AuthorizationRequest {
        if (principal == null)
            throw new IllegalArgumentException("principal required");
        if (action == null || action.isBlank())
            throw new IllegalArgumentException("action required");
        if (resource == null)
            throw new IllegalArgumentException("resource required");
        if (context == null)
            throw new IllegalArgumentException("context required");
        context = Map.copyOf(context);
    }
}
