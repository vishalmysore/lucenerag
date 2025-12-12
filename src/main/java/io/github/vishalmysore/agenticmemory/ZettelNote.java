package io.github.vishalmysore.agenticmemory;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Represents an atomic Zettelkasten note with bidirectional linking capabilities.
 * 
 * A ZettelNote is a self-contained unit of knowledge that maintains:
 * - Core content and metadata
 * - Bidirectional links to other notes
 * - Temporal information for evolution tracking
 * - Entity and tag-based organization
 */
public class ZettelNote {
    private final String id;
    private String content;
    private String summary;
    private List<String> tags;
    private List<String> entities;
    private List<Link> outgoingLinks;
    private List<Link> incomingLinks;
    private Map<String, Object> metadata;
    private final LocalDateTime created;
    private LocalDateTime lastModified;
    private float importanceScore;
    private float[] embedding;

    private ZettelNote(Builder builder) {
        this.id = builder.id;
        this.content = builder.content;
        this.summary = builder.summary;
        this.tags = builder.tags;
        this.entities = builder.entities;
        this.outgoingLinks = builder.outgoingLinks;
        this.incomingLinks = builder.incomingLinks;
        this.metadata = builder.metadata;
        this.created = builder.created;
        this.lastModified = builder.lastModified;
        this.importanceScore = builder.importanceScore;
        this.embedding = builder.embedding;
    }

    public String getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
        this.lastModified = LocalDateTime.now();
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
        this.lastModified = LocalDateTime.now();
    }

    public List<String> getTags() {
        return new ArrayList<>(tags);
    }

    public void addTag(String tag) {
        if (!tags.contains(tag)) {
            tags.add(tag);
            this.lastModified = LocalDateTime.now();
        }
    }

    public List<String> getEntities() {
        return new ArrayList<>(entities);
    }

    public void addEntity(String entity) {
        if (!entities.contains(entity)) {
            entities.add(entity);
            this.lastModified = LocalDateTime.now();
        }
    }

    public List<Link> getOutgoingLinks() {
        return new ArrayList<>(outgoingLinks);
    }

    public void addOutgoingLink(Link link) {
        if (!outgoingLinks.contains(link)) {
            outgoingLinks.add(link);
            this.lastModified = LocalDateTime.now();
        }
    }

    public List<Link> getIncomingLinks() {
        return new ArrayList<>(incomingLinks);
    }

    public void addIncomingLink(Link link) {
        if (!incomingLinks.contains(link)) {
            incomingLinks.add(link);
            this.lastModified = LocalDateTime.now();
        }
    }

    public List<Link> getAllLinks() {
        List<Link> allLinks = new ArrayList<>(outgoingLinks);
        allLinks.addAll(incomingLinks);
        return allLinks;
    }

    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }

    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
        this.lastModified = LocalDateTime.now();
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public LocalDateTime getLastModified() {
        return lastModified;
    }

    public float getImportanceScore() {
        return importanceScore;
    }

    public void setImportanceScore(float score) {
        this.importanceScore = Math.max(0.0f, Math.min(1.0f, score));
    }

    public float[] getEmbedding() {
        return embedding != null ? Arrays.copyOf(embedding, embedding.length) : null;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding != null ? Arrays.copyOf(embedding, embedding.length) : null;
    }

    /**
     * Calculate connectivity score based on number and quality of links
     */
    public double getConnectivityScore() {
        int totalLinks = outgoingLinks.size() + incomingLinks.size();
        double avgStrength = getAllLinks().stream()
            .mapToDouble(Link::getStrength)
            .average()
            .orElse(0.0);
        
        return totalLinks * avgStrength;
    }

    /**
     * Check if this note is atomically focused (single concept)
     */
    public boolean isAtomic() {
        // Atomic notes should be concise and focused
        int wordCount = content.split("\\s+").length;
        int sentenceCount = content.split("[.!?]+").length;
        
        return wordCount <= 500 && sentenceCount <= 5;
    }

    /**
     * Get related notes through links of specific type
     */
    public List<String> getRelatedNoteIds(LinkType type) {
        List<String> relatedIds = new ArrayList<>();
        
        for (Link link : outgoingLinks) {
            if (link.getType() == type) {
                relatedIds.add(link.getTargetNoteId());
            }
        }
        
        return relatedIds;
    }

    @Override
    public String toString() {
        return String.format("ZettelNote[id=%s, summary=%s, tags=%s, links=%d]",
            id, summary, tags, getAllLinks().size());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZettelNote that = (ZettelNote) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public static class Builder {
        private String id;
        private String content;
        private String summary;
        private List<String> tags = new ArrayList<>();
        private List<String> entities = new ArrayList<>();
        private List<Link> outgoingLinks = new ArrayList<>();
        private List<Link> incomingLinks = new ArrayList<>();
        private Map<String, Object> metadata = new HashMap<>();
        private LocalDateTime created = LocalDateTime.now();
        private LocalDateTime lastModified = LocalDateTime.now();
        private float importanceScore = 0.5f;
        private float[] embedding;

        public Builder(String id, String content) {
            this.id = id;
            this.content = content;
        }

        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public Builder tags(List<String> tags) {
            this.tags = new ArrayList<>(tags);
            return this;
        }

        public Builder entities(List<String> entities) {
            this.entities = new ArrayList<>(entities);
            return this;
        }

        public Builder outgoingLinks(List<Link> links) {
            this.outgoingLinks = new ArrayList<>(links);
            return this;
        }

        public Builder incomingLinks(List<Link> links) {
            this.incomingLinks = new ArrayList<>(links);
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = new HashMap<>(metadata);
            return this;
        }

        public Builder created(LocalDateTime created) {
            this.created = created;
            return this;
        }

        public Builder lastModified(LocalDateTime lastModified) {
            this.lastModified = lastModified;
            return this;
        }

        public Builder importanceScore(float score) {
            this.importanceScore = Math.max(0.0f, Math.min(1.0f, score));
            return this;
        }

        public Builder embedding(float[] embedding) {
            this.embedding = embedding != null ? Arrays.copyOf(embedding, embedding.length) : null;
            return this;
        }

        public ZettelNote build() {
            return new ZettelNote(this);
        }
    }
}
