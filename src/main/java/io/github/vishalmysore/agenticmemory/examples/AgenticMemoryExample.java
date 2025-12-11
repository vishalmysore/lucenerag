package io.github.vishalmysore.agenticmemory.examples;

import io.github.vishalmysore.agenticmemory.*;
import io.github.vishalmysore.rag.*;

import java.nio.file.Paths;
import java.util.List;

/**
 * Demonstrates Agentic Memory capabilities:
 * - LLM-driven note construction from documents
 * - Automatic link generation between related notes
 * - Knowledge graph traversal and multi-hop reasoning
 * - Context-aware retrieval
 */
public class AgenticMemoryExample {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Agentic Memory Demo ===\n");

        // Setup (replace with your API key)
        String openAIKey = System.getenv("OPENAI_API_KEY");
        if (openAIKey == null || openAIKey.isEmpty()) {
            System.out.println("Please set OPENAI_API_KEY environment variable");
            System.out.println("Using mock embeddings instead (no LLM features)");
            demoWithoutLLM();
            return;
        }

        // Initialize Agentic Memory Service
        OpenAIEmbeddingProvider embeddings = new OpenAIEmbeddingProvider(
                openAIKey,
                "text-embedding-3-small",
                1536
        );

        try (AgenticMemoryService agenticMemory = new AgenticMemoryService.Builder(
                Paths.get("agentic-memory-index"),
                embeddings,
                openAIKey
        ).build()) {

            // Step 1: Add documents - notes are automatically constructed
            System.out.println("Step 1: Adding documents with automatic note construction...\n");

            String doc1 = "Albert Einstein developed the theory of relativity in the early 20th century. " +
                         "His most famous equation E=mc² revolutionized physics. " +
                         "Einstein won the Nobel Prize in Physics in 1921.";

            String doc2 = "The theory of relativity consists of special and general relativity. " +
                         "Special relativity deals with objects moving at constant speeds. " +
                         "General relativity extends this to include gravity and acceleration.";

            String doc3 = "Marie Curie was a pioneering physicist and chemist. " +
                         "She conducted groundbreaking research on radioactivity. " +
                         "Curie was the first woman to win a Nobel Prize.";

            List<Note> notes1 = agenticMemory.addDocumentAsNotes("einstein_bio", doc1);
            List<Note> notes2 = agenticMemory.addDocumentAsNotes("relativity_theory", doc2);
            List<Note> notes3 = agenticMemory.addDocumentAsNotes("curie_bio", doc3);

            System.out.println("Created " + notes1.size() + " notes from document 1");
            System.out.println("Created " + notes2.size() + " notes from document 2");
            System.out.println("Created " + notes3.size() + " notes from document 3");

            // Display sample note
            if (!notes1.isEmpty()) {
                Note note = notes1.get(0);
                System.out.println("\nSample Note:");
                System.out.println("  ID: " + note.getId());
                System.out.println("  Summary: " + note.getSummary());
                System.out.println("  Entities: " + note.getEntities());
                System.out.println("  Tags: " + note.getTags());
                System.out.println("  Importance: " + String.format("%.2f", note.getImportanceScore()));
            }

            // Step 2: Explore the knowledge graph
            System.out.println("\n\nStep 2: Knowledge Graph Statistics...\n");
            MemoryGraph.GraphStatistics stats = agenticMemory.getStatistics();
            System.out.println(stats);

            // Step 3: Find notes by entity
            System.out.println("\n\nStep 3: Finding notes by entity...\n");
            List<Note> einsteinNotes = agenticMemory.findByEntity("Einstein");
            System.out.println("Found " + einsteinNotes.size() + " notes mentioning Einstein");

            // Step 4: Semantic search
            System.out.println("\n\nStep 4: Semantic search for 'Nobel Prize physics'...\n");
            List<Note> searchResults = agenticMemory.searchNotes("Nobel Prize physics", 5);
            for (int i = 0; i < searchResults.size() && i < 3; i++) {
                Note note = searchResults.get(i);
                System.out.println((i + 1) + ". " + note.getSummary());
                System.out.println("   Importance: " + String.format("%.2f", note.getImportanceScore()));
            }

            // Step 5: Multi-hop reasoning
            System.out.println("\n\nStep 5: Context retrieval with multi-hop reasoning...\n");
            String context = agenticMemory.retrieveContextWithReasoning(
                    "What are the contributions of Nobel Prize winners?",
                    2,  // Start with top 2 relevant notes
                    2   // Expand up to 2 hops in the knowledge graph
            );
            System.out.println("Retrieved context (with graph expansion):");
            System.out.println(context.substring(0, Math.min(300, context.length())) + "...");

            // Step 6: Most important and connected notes
            System.out.println("\n\nStep 6: Most important notes...\n");
            List<Note> importantNotes = agenticMemory.getMostImportantNotes(3);
            for (int i = 0; i < importantNotes.size(); i++) {
                Note note = importantNotes.get(i);
                System.out.println((i + 1) + ". " + note.getId() +
                        " (importance: " + String.format("%.2f", note.getImportanceScore()) + ")");
            }

            System.out.println("\n\nMost connected notes...\n");
            List<Note> connectedNotes = agenticMemory.getMostConnectedNotes(3);
            for (int i = 0; i < connectedNotes.size(); i++) {
                Note note = connectedNotes.get(i);
                int linkCount = agenticMemory.getMemoryGraph()
                        .getLinksForNote(note.getId()).size();
                System.out.println((i + 1) + ". " + note.getId() +
                        " (" + linkCount + " links)");
            }

            // Step 7: Explore relationships
            if (!notes1.isEmpty()) {
                System.out.println("\n\nStep 7: Exploring relationships...\n");
                Note firstNote = notes1.get(0);
                List<Note> relatedNotes = agenticMemory.getRelatedNotes(firstNote.getId());

                System.out.println("Notes related to '" + firstNote.getId() + "':");
                for (Note related : relatedNotes) {
                    System.out.println("  - " + related.getId());

                    // Show the link type
                    List<Link> links = agenticMemory.getMemoryGraph()
                            .getLinksForNote(firstNote.getId());
                    for (Link link : links) {
                        if (link.connects(firstNote.getId(), related.getId())) {
                            System.out.println("    Type: " + link.getType() +
                                    ", Strength: " + String.format("%.2f", link.getStrength()));
                        }
                    }
                }
            }

            System.out.println("\n=== Demo Complete ===");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Demo without LLM (just shows structure)
     */
    private static void demoWithoutLLM() throws Exception {
        System.out.println("\n=== Demo without LLM (Structure Only) ===\n");

        // Create notes manually
        Note note1 = new Note.Builder("note1", "Einstein developed relativity")
                .summary("Einstein's theory of relativity")
                .addEntity("Einstein")
                .addEntity("relativity")
                .addTag("physics")
                .addTag("science")
                .importanceScore(0.9)
                .build();

        Note note2 = new Note.Builder("note2", "Relativity includes special and general theories")
                .summary("Two types of relativity")
                .addEntity("relativity")
                .addTag("physics")
                .addTag("theory")
                .importanceScore(0.7)
                .build();

        // Create links
        Link link = new Link.Builder("note1", "note2", LinkType.EXTENDS)
                .strength(0.8)
                .description("Note 2 extends note 1 with more details")
                .build();

        // Create knowledge graph
        MemoryGraph graph = new MemoryGraph();
        graph.addNote(note1);
        graph.addNote(note2);
        graph.addLink(link);

        System.out.println("Created knowledge graph:");
        System.out.println(graph.getStatistics());

        System.out.println("\nNotes by entity 'Einstein':");
        for (Note note : graph.findNotesByEntity("Einstein")) {
            System.out.println("  - " + note.getId() + ": " + note.getSummary());
        }

        System.out.println("\nNeighbors of note1:");
        for (Note neighbor : graph.getNeighbors("note1")) {
            System.out.println("  - " + neighbor.getId());
        }

        System.out.println("\n=== Demo Complete ===");
    }
}
