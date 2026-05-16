package dev.vestitus.wire;

import java.util.Map;

public record AgentRequest(String requestId, String input, Map<String, String> attributes)
        implements WireMessage {
    public AgentRequest {
        if (requestId == null || requestId.isBlank())
            throw new IllegalArgumentException("requestId required");
        attributes = Map.copyOf(attributes);
    }
}
