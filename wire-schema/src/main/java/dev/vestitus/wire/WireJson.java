package dev.vestitus.wire;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

public final class WireJson {
    private static final ObjectMapper MAPPER = JsonMapper.builder()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
        .configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true)
        .build();

    private WireJson() {}

    public static String write(WireMessage m) {
        try {
            return MAPPER.writeValueAsString(m);
        } catch (Exception e) {
            throw new WireParseException("failed to serialize wire message", e);
        }
    }

    public static WireMessage read(String json) {
        try {
            return MAPPER.readValue(json, WireMessage.class);
        } catch (Exception e) {
            throw new WireParseException("failed to parse wire message (fail-closed)", e);
        }
    }
}
