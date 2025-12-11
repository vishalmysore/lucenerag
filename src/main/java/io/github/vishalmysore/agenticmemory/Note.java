package io.github.vishalmysore.agenticmemory;

import java.time.Instant;
import java.util.*;

/**
 * Represents a note in the agentic memory system.
 * Notes are LLM-generated summaries/extractions from documents with rich metadata.
 */
public class Note {
    private final String id;
    private final String content;
    private final String summary;
    private final List<String> sourceDocumentIds;
    private final Map<String, String> metadata;
    private final Instant createdAt;
    private Instant updatedAt;
    private double importanceScore;
    private final List<String> tags;
    private final List<String> entities;

    private Note(Builder builder) {
        this.id = builder.id;
        this.content = builder.content;
        this.summary = builder.summary;
        this.sourceDocumentIds = new ArrayList<>(builder.sourceDocumentIds);
        this.metadata = new HashMap<>(builder.metadata);
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.updatedAt = this.createdAt;
        this.importanceScore = builder.importanceScore;
        this.tags = new ArrayList<>(builder.tags);
        this.entities = new ArrayList<>(builder.entities);
    }

    public String getId() { return id; }
    public String getContent() { return content; }
    public String getSummary() { return summary; }
    public List<String> getSourceDocumentIds() { return Collections.unmodifiableList(sourceDocumentIds); }
    public Map<String, String> getMetadata() { return Collections.unmodifiableMap(metadata); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public double getImportanceScore() { return importanceScore; }
    public List<String> getTags() { return Collections.unmodifiableList(tags); }
    public List<String> getEntities() { return Collections.unmodifiableList(entities); }

    public void setImportanceScore(double score) {
        this.importanceScore = score;
        this.updatedAt = Instant.now();
    }

    public void updateTimestamp() {
        this.updatedAt = Instant.now();
    }

    @Override
    public String toString() {
        return String.format("Note[id=%s, summary=%s, importance=%.2f, entities=%s]",
                id, summary, importanceScore, entities);
    }

    public static class Builder {
        private String id;
        private String content;
        private String summary;
        private List<String> sourceDocumentIds = new ArrayList<>();
        private Map<String, String> metadata = new HashMap<>();
        private Instant createdAt;
        private double importanceScore = 0.5;
        private List<String> tags = new ArrayList<>();
        private List<String> entities = new ArrayList<>();

        public Builder(String id, String content) {
            this.id = Objects.requireNonNull(id, "ID cannot be null");
            this.content = Objects.requireNonNull(content, "Content cannot be null");
        }

        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public Builder addSourceDocument(String docId) {
            this.sourceDocumentIds.add(docId);
            return this;
        }

        public Builder sourceDocuments(List<String> docIds) {
            this.sourceDocumentIds.addAll(docIds);
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

        public Builder importanceScore(double score) {
            this.importanceScore = score;
            return this;
        }

        public Builder tags(List<String> tags) {
            this.tags.addAll(tags);
            return this;
        }

        public Builder addTag(String tag) {
            this.tags.add(tag);
            return this;
        }

        public Builder entities(List<String> entities) {
            this.entities.addAll(entities);
            return this;
        }

        public Builder addEntity(String entity) {
            this.entities.add(entity);
            return this;
        }

        public Note build() {
            return new Note(this);
        }
    }
}
