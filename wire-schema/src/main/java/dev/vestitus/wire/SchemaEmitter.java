package dev.vestitus.wire;

/**
 * SPI seam: Phase 6 supplies OpenAPI and signed-JSON-Schema emitters.
 * In Plan 01 the only consumer is the conformance test. Implementations
 * receive each canonical root wire type exactly once.
 */
@FunctionalInterface
public interface SchemaEmitter {
    void emit(Class<? extends WireMessage> rootType);
}
