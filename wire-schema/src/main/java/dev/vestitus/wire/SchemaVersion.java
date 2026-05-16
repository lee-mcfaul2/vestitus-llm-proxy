package dev.vestitus.wire;

public record SchemaVersion(String value) {
    public static final SchemaVersion CURRENT = new SchemaVersion("1.0.0");

    public SchemaVersion {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("schema version must be non-blank");
        }
    }

    public static SchemaVersion parse(String s) {
        return new SchemaVersion(s == null ? null : s.trim());
    }
}
