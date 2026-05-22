package dev.vestitus.inspect;

/** A pipeline-unique stage identifier, used in audit and metric tags. */
public record StageId(String name) {
    public StageId {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("stage id name required");
    }
}
