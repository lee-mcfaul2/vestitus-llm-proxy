package dev.vestitus.mcpschema;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

public final class McpSchemaJson {
    private static final ObjectMapper MAPPER = JsonMapper.builder()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
        .configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true)
        .build();

    private McpSchemaJson() {}

    public static String write(McpSchema m) {
        try {
            return MAPPER.writeValueAsString(m);
        } catch (Exception e) {
            throw new McpSchemaParseException("failed to serialize mcp schema", e);
        }
    }

    public static McpSchema read(String json) {
        try {
            return MAPPER.readValue(json, McpSchema.class);
        } catch (Exception e) {
            throw new McpSchemaParseException("failed to parse mcp schema (fail-closed)", e);
        }
    }
}
