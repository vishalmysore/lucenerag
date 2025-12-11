package io.github.vishalmysore.agenticmemory;

import io.github.vishalmysore.rag.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Main API for Agentic Memory system.
 * Combines RAG foundation with autonomous note construction, link generation, and knowledge graph.
 */
public class AgenticMemoryService implements AutoCloseable {
    private final RAGService ragService;
    private final NoteConstructor noteConstructor;
    private final LinkGenerator linkGenerator;
    private final MemoryGraph memoryGraph;
    private final boolean autoGenerateLinks;

    private AgenticMemoryService(Builder builder) {
        this.ragService = builder.ragService;
        this.noteConstructor = builder.noteConstructor;
        this.linkGenerator = builder.linkGenerator;
        this.memoryGraph = new MemoryGraph();
        this.autoGenerateLinks = builder.autoGenerateLinks;
    }

    /**
     * Add a document and automatically construct note(s) from it
     */
    public List<Note> addDocumentAsNotes(String documentId, String content) throws IOException {
        return addDocumentAsNotes(documentId, content, new HashMap<>());
    }

    /**
     * Add a document with metadata and automatically construct note(s)
     */
    public List<Note> addDocumentAsNotes(String documentId, String content, Map<String, String> metadata) throws IOException {
        // Index in RAG system
        ragService.addDocument(documentId, content, metadata);
        ragService.commit();

        // Construct note(s) using LLM
        List<Note> notes;
        if (content.split("\\s+").length > 500) {
            // Long content - create multiple notes with chunking
            notes = noteConstructor.constructNotesFromDocument(documentId, documentId, content, 500);
        } else {
            // Short content - single note
            Note note = noteConstructor.constructNote(documentId, documentId, content, metadata);
            notes = Collections.singletonList(note);
        }

        // Add notes to memory graph
        for (Note note : notes) {
            memoryGraph.addNote(note);
        }

        // Auto-generate links if enabled
        if (autoGenerateLinks) {
            for (Note newNote : notes) {
                List<Link> links = linkGenerator.generateLinks(newNote, memoryGraph.getAllNotes());
                for (Link link : links) {
                    memoryGraph.addLink(link);
                }
            }
        }

        return notes;
    }

    /**
     * Add a pre-constructed note directly
     */
    public void addNote(Note note) {
        memoryGraph.addNote(note);

        if (autoGenerateLinks) {
            try {
                List<Link> links = linkGenerator.generateLinks(note, memoryGraph.getAllNotes());
                for (Link link : links) {
                    memoryGraph.addLink(link);
                }
            } catch (IOException e) {
                System.err.println("Warning: Failed to auto-generate links for note " + note.getId() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Add a link between notes
     */
    public void addLink(Link link) {
        memoryGraph.addLink(link);
    }

    /**
     * Retrieve notes using semantic search
     */
    public List<Note> searchNotes(String query, int topK) throws IOException {
        // Use RAG system to find relevant documents
        List<SearchResult> results = ragService.search(query, topK * 2); // Get more results to filter

        // Map document IDs to notes
        Set<String> relevantNoteIds = new HashSet<>();
        for (SearchResult result : results) {
            relevantNoteIds.add(result.getId());
        }

        // Return matching notes sorted by importance
        return memoryGraph.getAllNotes().stream()
                .filter(note -> note.getSourceDocumentIds().stream().anyMatch(relevantNoteIds::contains) ||
                               relevantNoteIds.contains(note.getId()))
                .sorted(Comparator.comparingDouble(Note::getImportanceScore).reversed())
                .limit(topK)
                .toList();
    }

    /**
     * Retrieve context with multi-hop reasoning
     */
    public String retrieveContextWithReasoning(String query, int topK, int maxHops) throws IOException {
        // Find initial relevant notes
        List<Note> initialNotes = searchNotes(query, topK);

        // Expand search using graph traversal
        Set<Note> expandedNotes = new HashSet<>(initialNotes);
        for (Note note : initialNotes) {
            List<Note> neighbors = memoryGraph.findNotesWithinHops(note.getId(), maxHops);
            expandedNotes.addAll(neighbors);
        }

        // Build context from expanded notes
        StringBuilder context = new StringBuilder();
        for (Note note : expandedNotes) {
            context.append(note.getSummary() != null ? note.getSummary() : note.getContent());
            context.append("\n\n");
        }

        return context.toString();
    }

    /**
     * Find notes by entity
     */
    public List<Note> findByEntity(String entity) {
        return memoryGraph.findNotesByEntity(entity);
    }

    /**
     * Find notes by tag
     */
    public List<Note> findByTag(String tag) {
        return memoryGraph.findNotesByTag(tag);
    }

    /**
     * Get most important notes
     */
    public List<Note> getMostImportantNotes(int limit) {
        return memoryGraph.findMostImportantNotes(limit);
    }

    /**
     * Get most connected notes
     */
    public List<Note> getMostConnectedNotes(int limit) {
        return memoryGraph.findMostConnectedNotes(limit);
    }

    /**
     * Get related notes (neighbors in graph)
     */
    public List<Note> getRelatedNotes(String noteId) {
        return memoryGraph.getNeighbors(noteId);
    }

    /**
     * Find shortest path between two notes
     */
    public List<Note> findPath(String fromNoteId, String toNoteId) {
        return memoryGraph.findShortestPath(fromNoteId, toNoteId);
    }

    /**
     * Get memory graph statistics
     */
    public MemoryGraph.GraphStatistics getStatistics() {
        return memoryGraph.getStatistics();
    }

    /**
     * Get the underlying RAG service
     */
    public RAGService getRAGService() {
        return ragService;
    }

    /**
     * Get the memory graph
     */
    public MemoryGraph getMemoryGraph() {
        return memoryGraph;
    }

    @Override
    public void close() throws IOException {
        if (ragService != null) ragService.close();
        if (noteConstructor != null) noteConstructor.close();
        if (linkGenerator != null) linkGenerator.close();
    }

    /**
     * Builder for AgenticMemoryService
     */
    public static class Builder {
        private RAGService ragService;
        private NoteConstructor noteConstructor;
        private LinkGenerator linkGenerator;
        private boolean autoGenerateLinks = true;

        public Builder(Path indexPath, EmbeddingProvider embeddingProvider, String openAIKey) throws IOException {
            this.ragService = new RAGService(indexPath, embeddingProvider);
            this.noteConstructor = new NoteConstructor(openAIKey);
            this.linkGenerator = new LinkGenerator(openAIKey);
        }

        public Builder(RAGService ragService, NoteConstructor noteConstructor, LinkGenerator linkGenerator) {
            this.ragService = ragService;
            this.noteConstructor = noteConstructor;
            this.linkGenerator = linkGenerator;
        }

        public Builder autoGenerateLinks(boolean autoGenerate) {
            this.autoGenerateLinks = autoGenerate;
            return this;
        }

        public AgenticMemoryService build() {
            return new AgenticMemoryService(this);
        }
    }
}
