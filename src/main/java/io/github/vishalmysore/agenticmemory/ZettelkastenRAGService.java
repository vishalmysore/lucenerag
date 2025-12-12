package io.github.vishalmysore.agenticmemory;

import io.github.vishalmysore.rag.*;
import io.github.vishalmysore.rag.chunking.ZettelkastenChunking;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Extended RAG service with Zettelkasten-style linking and graph-based context retrieval.
 * 
 * Provides:
 * - Automatic atomic note creation
 * - Entity-based link generation
 * - Graph-aware context expansion
 * - Bidirectional link traversal
 */
public class ZettelkastenRAGService extends RAGService {
    private final ZettelkastenGraph graph;
    private final LinkStorage linkStorage;
    private final LinkGenerator linkGenerator;
    private final EntityExtractor entityExtractor;
    private final NoteConstructor noteConstructor;
    private final ZettelkastenChunking zettelChunking;

    public ZettelkastenRAGService(String indexPath, EmbeddingProvider embeddingProvider) throws IOException {
        super(Paths.get(indexPath), embeddingProvider);
        this.linkStorage = new LinkStorage(this);
        this.graph = new ZettelkastenGraph(linkStorage);
        this.linkGenerator = null; // Optional LLM-based linking
        this.entityExtractor = new EntityExtractor();
        this.noteConstructor = null; // Optional LLM-based construction
        this.zettelChunking = new ZettelkastenChunking();
    }

    public ZettelkastenRAGService(String indexPath, EmbeddingProvider embeddingProvider, NoteConstructor noteConstructor) throws IOException {
        super(Paths.get(indexPath), embeddingProvider);
        this.linkStorage = new LinkStorage(this);
        this.graph = new ZettelkastenGraph(linkStorage);
        this.linkGenerator = null; // Optional LLM-based linking
        this.entityExtractor = new EntityExtractor();
        this.noteConstructor = noteConstructor;
        this.zettelChunking = new ZettelkastenChunking();
    }

    /**
     * Create an atomic Zettelkasten note
     */
    public ZettelNote createNote(String content, List<String> tags) throws IOException {
        return createNote(content, tags, new HashMap<>());
    }

    /**
     * Create an atomic Zettelkasten note with metadata
     */
    public ZettelNote createNote(String content, List<String> tags, Map<String, Object> additionalMetadata) throws IOException {
        // Generate unique ID
        String noteId = "zettel_" + UUID.randomUUID().toString();

        // Extract entities
        List<String> entities = entityExtractor.extract(content);

        // Generate summary (use LLM if available, otherwise use first sentence)
        String summary = generateSummary(content);

        // Create ZettelNote
        ZettelNote note = new ZettelNote.Builder(noteId, content)
            .summary(summary)
            .tags(tags)
            .entities(entities)
            .metadata(additionalMetadata)
            .build();

        // Add to RAG index with chunking
        Map<String, String> metadata = new HashMap<>();
        metadata.put("type", "zettel_note");
        metadata.put("note_id", noteId);
        metadata.put("tags", String.join(",", tags));
        metadata.put("entities", String.join(",", entities));
        metadata.put("summary", summary);
        additionalMetadata.forEach((k, v) -> metadata.put(k, v.toString()));

        // Use Zettelkasten chunking for atomic ideas
        addDocumentWithChunking(noteId, content, zettelChunking, metadata);

        // Add to graph
        graph.addNote(note);

        // Auto-generate links to existing notes
        generateLinksForNewNote(note);

        return note;
    }

    /**
     * Generate links for a new note
     */
    private void generateLinksForNewNote(ZettelNote note) throws IOException {
        // Find related notes by entity co-occurrence
        List<SearchResult> entityMatches = findByEntities(note.getEntities(), 20);

        // Find related notes by semantic similarity
        List<SearchResult> semanticMatches = search(note.getContent(), 20);

        // Find related notes by tag similarity
        List<SearchResult> tagMatches = findByTags(note.getTags(), 20);

        // Combine and deduplicate results
        Map<String, SearchResult> combinedResults = new HashMap<>();
        for (SearchResult result : entityMatches) {
            combinedResults.put(result.getId(), result);
        }
        for (SearchResult result : semanticMatches) {
            combinedResults.put(result.getId(), result);
        }
        for (SearchResult result : tagMatches) {
            combinedResults.put(result.getId(), result);
        }

        // Generate links for top matches
        for (SearchResult match : combinedResults.values()) {
            if (match.getId().equals(note.getId())) {
                continue; // Skip self-links
            }

            ZettelNote targetNote = graph.getNote(match.getId());
            if (targetNote == null) {
                continue;
            }

            // Determine link type and strength
            Link link = determineLinkType(note, targetNote, match.getScore());

            if (link != null) {
                graph.addLink(link);
            }
        }
    }

    /**
     * Determine appropriate link type between notes
     */
    private Link determineLinkType(ZettelNote source, ZettelNote target, double semanticSimilarity) {
        // Check for entity overlap
        Set<String> commonEntities = new HashSet<>(source.getEntities());
        commonEntities.retainAll(target.getEntities());

        // Check for tag overlap
        Set<String> commonTags = new HashSet<>(source.getTags());
        commonTags.retainAll(target.getTags());

        // Determine link type and strength
        LinkType linkType;
        double strength = 0.0;
        String description;

        if (!commonEntities.isEmpty()) {
            linkType = LinkType.RELATED_ENTITY;
            strength = Math.min(1.0, commonEntities.size() * 0.3 + semanticSimilarity * 0.4);
            description = "Shares entities: " + String.join(", ", commonEntities);
        } else if (!commonTags.isEmpty()) {
            linkType = LinkType.SIMILAR_TOPIC;
            strength = Math.min(1.0, commonTags.size() * 0.25 + semanticSimilarity * 0.4);
            description = "Similar topics: " + String.join(", ", commonTags);
        } else if (semanticSimilarity > 0.7) {
            linkType = LinkType.REFERENCES;
            strength = semanticSimilarity;
            description = "Semantically related (similarity: " + String.format("%.2f", semanticSimilarity) + ")";
        } else {
            return null; // Not related enough
        }

        // Only create link if strength is significant
        if (strength < 0.3) {
            return null;
        }

        return new Link.Builder(source.getId(), target.getId(), linkType)
            .strength(strength)
            .description(description)
            .build();
    }

    /**
     * Retrieve context with link-aware expansion
     */
    public String retrieveContextWithLinks(String query, int topK) throws IOException {
        return retrieveContextWithLinks(query, topK, 2);
    }

    /**
     * Retrieve context with link-aware expansion and depth control
     */
    public String retrieveContextWithLinks(String query, int topK, int linkDepth) throws IOException {
        // Get base context from vector search
        List<SearchResult> directResults = search(query, topK);

        Set<String> expandedContent = new LinkedHashSet<>();
        Set<String> visitedNotes = new HashSet<>();

        // Expand context through links
        for (SearchResult result : directResults) {
            String noteId = result.getId();

            if (visitedNotes.contains(noteId)) {
                continue;
            }

            // Add direct match content
            expandedContent.add(formatResult(result));
            visitedNotes.add(noteId);

            // Traverse linked notes
            List<ZettelNote> linkedNotes = graph.findNotesWithinHops(noteId, linkDepth);

            for (ZettelNote linkedNote : linkedNotes) {
                if (visitedNotes.contains(linkedNote.getId())) {
                    continue;
                }

                expandedContent.add(formatNote(linkedNote));
                visitedNotes.add(linkedNote.getId());
            }
        }

        return String.join("\n\n---\n\n", expandedContent);
    }

    /**
     * Find notes by entities
     */
    public List<SearchResult> findByEntities(List<String> entities, int topK) throws IOException {
        String entityQuery = String.join(" ", entities);
        return search(entityQuery, topK);
    }

    /**
     * Find notes by tags
     */
    public List<SearchResult> findByTags(List<String> tags, int topK) throws IOException {
        String tagQuery = "tags:(" + String.join(" OR ", tags) + ")";
        return keywordSearch(tagQuery, topK);
    }

    /**
     * Get a ZettelNote by ID
     */
    public ZettelNote getZettelNote(String noteId) {
        return graph.getNote(noteId);
    }

    /**
     * Get all ZettelNotes
     */
    public Collection<ZettelNote> getAllZettelNotes() {
        return graph.getAllNotes();
    }

    /**
     * Traverse from a note
     */
    public List<ZettelNote> traverseFromNote(String startNoteId, int maxDepth) {
        return graph.traverseFromNote(startNoteId, maxDepth);
    }

    /**
     * Find path between two notes
     */
    public List<ZettelNote> findPathBetweenNotes(String startId, String endId) {
        return graph.findPath(startId, endId);
    }

    /**
     * Get graph statistics
     */
    public ZettelkastenGraph.GraphStatistics getGraphStatistics() {
        return graph.getStatistics();
    }

    /**
     * Get most connected notes
     */
    public List<ZettelNote> getMostConnectedNotes(int topN) {
        return graph.getMostConnectedNotes(topN);
    }

    /**
     * Get the Zettelkasten graph
     */
    public ZettelkastenGraph getGraph() {
        return graph;
    }

    /**
     * Get link storage
     */
    public LinkStorage getLinkStorage() {
        return linkStorage;
    }

    private String generateSummary(String content) throws IOException {
        if (noteConstructor != null) {
            return noteConstructor.generateSummary(content);
        }

        // Fallback: use first sentence or first 200 characters
        String[] sentences = content.split("[.!?]+");
        if (sentences.length > 0) {
            String firstSentence = sentences[0].trim();
            return firstSentence.length() > 200 ?
                firstSentence.substring(0, 197) + "..." :
                firstSentence;
        }

        return content.length() > 200 ?
            content.substring(0, 197) + "..." :
            content;
    }

    private String formatResult(SearchResult result) {
        return String.format("[ID: %s] (Score: %.2f)\n%s",
            result.getId(),
            result.getScore(),
            result.getContent());
    }

    private String formatNote(ZettelNote note) {
        return String.format("[Zettel: %s]\nSummary: %s\nTags: %s\nEntities: %s\n\n%s",
            note.getId(),
            note.getSummary(),
            String.join(", ", note.getTags()),
            String.join(", ", note.getEntities()),
            note.getContent());
    }
}
