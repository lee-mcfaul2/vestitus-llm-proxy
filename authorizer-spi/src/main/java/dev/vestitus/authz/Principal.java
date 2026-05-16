package dev.vestitus.authz;

import java.util.Map;
import java.util.Set;

public record Principal(String id, Set<String> scopes, Map<String, String> attributes) {
    public Principal {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("principal id required");
        if (scopes == null)
            throw new IllegalArgumentException("scopes required");
        if (attributes == null)
            throw new IllegalArgumentException("attributes required");
        scopes = Set.copyOf(scopes);
        attributes = Map.copyOf(attributes);
    }
}
