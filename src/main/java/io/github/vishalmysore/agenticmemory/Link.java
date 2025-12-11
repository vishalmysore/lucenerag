package io.github.vishalmysore.agenticmemory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a link/relationship between two notes in the agentic memory system.
 */
public class Link {
    private final String id;
    private final String sourceNoteId;
    private final String targetNoteId;
    private final LinkType type;
    private final double strength;
    private final String description;
    private final Map<String, String> metadata;
    private final Instant createdAt;
    private Instant lastAccessedAt;

    private Link(Builder builder) {
        this.id = builder.id;
        this.sourceNoteId = builder.sourceNoteId;
        this.targetNoteId = builder.targetNoteId;
        this.type = builder.type;
        this.strength = builder.strength;
        this.description = builder.description;
        this.metadata = new HashMap<>(builder.metadata);
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.lastAccessedAt = this.createdAt;
    }

    public String getId() { return id; }
    public String getSourceNoteId() { return sourceNoteId; }
    public String getTargetNoteId() { return targetNoteId; }
    public LinkType getType() { return type; }
    public double getStrength() { return strength; }
    public String getDescription() { return description; }
    public Map<String, String> getMetadata() { return Map.copyOf(metadata); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastAccessedAt() { return lastAccessedAt; }

    public void updateAccessTime() {
        this.lastAccessedAt = Instant.now();
    }

    /**
     * Get the other note ID in this link (given one end)
     */
    public String getOtherNoteId(String noteId) {
        if (sourceNoteId.equals(noteId)) {
            return targetNoteId;
        } else if (targetNoteId.equals(noteId)) {
            return sourceNoteId;
        }
        throw new IllegalArgumentException("Note ID " + noteId + " is not part of this link");
    }

    /**
     * Check if this link connects two specific notes
     */
    public boolean connects(String noteId1, String noteId2) {
        return (sourceNoteId.equals(noteId1) && targetNoteId.equals(noteId2)) ||
               (sourceNoteId.equals(noteId2) && targetNoteId.equals(noteId1));
    }

    @Override
    public String toString() {
        return String.format("Link[%s -%s(%.2f)-> %s]", sourceNoteId, type, strength, targetNoteId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Link link = (Link) o;
        return Objects.equals(id, link.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public static class Builder {
        private String id;
        private String sourceNoteId;
        private String targetNoteId;
        private LinkType type;
        private double strength = 0.5;
        private String description;
        private Map<String, String> metadata = new HashMap<>();
        private Instant createdAt;

        public Builder(String sourceNoteId, String targetNoteId, LinkType type) {
            this.sourceNoteId = Objects.requireNonNull(sourceNoteId, "Source note ID cannot be null");
            this.targetNoteId = Objects.requireNonNull(targetNoteId, "Target note ID cannot be null");
            this.type = Objects.requireNonNull(type, "Link type cannot be null");
            this.id = generateId(sourceNoteId, targetNoteId, type);
        }

        private String generateId(String source, String target, LinkType type) {
            return String.format("%s_%s_%s", source, target, type.name().toLowerCase());
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder strength(double strength) {
            if (strength < 0.0 || strength > 1.0) {
                throw new IllegalArgumentException("Strength must be between 0.0 and 1.0");
            }
            this.strength = strength;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata.putAll(metadata);
            return this;
        }

        public Builder addMetadata(String key, String value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Link build() {
            return new Link(this);
        }
    }
}
