package dev.vestitus.authz;

import java.util.Map;

public record ResourceRef(String mcpId, String tool, String field, Map<String, String> tags) {
    public ResourceRef {
        if (mcpId == null || mcpId.isBlank())
            throw new IllegalArgumentException("mcpId required");
        if (tool == null || tool.isBlank())
            throw new IllegalArgumentException("tool required");
        if (field == null || field.isBlank())
            throw new IllegalArgumentException("field required");
        if (tags == null)
            throw new IllegalArgumentException("tags required");
        tags = Map.copyOf(tags);
    }
}
