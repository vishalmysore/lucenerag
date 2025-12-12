package io.github.vishalmysore.agenticmemory.examples;

import io.github.vishalmysore.agenticmemory.*;
import io.github.vishalmysore.rag.MockEmbeddingProvider;

import java.io.IOException;
import java.util.*;

/**
 * Comprehensive example demonstrating Zettelkasten-style linking system.
 * 
 * Shows:
 * 1. Creating atomic notes with automatic link generation
 * 2. Graph traversal and path finding
 * 3. Link-aware context retrieval
 * 4. Knowledge graph analytics
 * 5. Entity-based and semantic connections
 */
public class ZettelkastenExample {

    public static void main(String[] args) throws IOException {
        System.out.println("=== Zettelkasten-Style Linking Example ===\n");

        // Initialize Zettelkasten RAG service
        MockEmbeddingProvider embeddings = new MockEmbeddingProvider(128);
        ZettelkastenRAGService zettelRAG = new ZettelkastenRAGService(
            "zettelkasten-index",
            embeddings
        );

        // === Step 1: Create Atomic Notes ===
        System.out.println("Step 1: Creating Atomic Notes");
        System.out.println("==============================");

        ZettelNote note1 = zettelRAG.createNote(
            "Knowledge is constructed through making connections between individual ideas. " +
            "The Zettelkasten method emphasizes creating atomic notes that represent single concepts, " +
            "which can then be linked together to form a web of knowledge.",
            Arrays.asList("learning", "knowledge-management", "zettelkasten")
        );
        System.out.println("Created Note 1: " + note1.getSummary());
        System.out.println("  Entities: " + note1.getEntities());
        System.out.println("  Tags: " + note1.getTags());
        System.out.println();

        ZettelNote note2 = zettelRAG.createNote(
            "Neural networks learn by adjusting connection weights between nodes. " +
            "This process mirrors how the human brain forms knowledge through strengthening " +
            "synaptic connections between neurons.",
            Arrays.asList("AI", "machine-learning", "neural-networks")
        );
        System.out.println("Created Note 2: " + note2.getSummary());
        System.out.println("  Entities: " + note2.getEntities());
        System.out.println("  Tags: " + note2.getTags());
        System.out.println();

        ZettelNote note3 = zettelRAG.createNote(
            "Graph databases represent knowledge as nodes and edges, enabling efficient traversal " +
            "of relationships. This structure is ideal for representing interconnected knowledge " +
            "systems like the Zettelkasten method.",
            Arrays.asList("databases", "knowledge-management", "graphs")
        );
        System.out.println("Created Note 3: " + note3.getSummary());
        System.out.println("  Entities: " + note3.getEntities());
        System.out.println("  Tags: " + note3.getTags());
        System.out.println();

        ZettelNote note4 = zettelRAG.createNote(
            "Machine learning models require large amounts of training data to learn patterns. " +
            "The quality and structure of this data significantly impacts model performance. " +
            "Similar to how the Zettelkasten method requires well-structured atomic notes.",
            Arrays.asList("AI", "machine-learning", "data")
        );
        System.out.println("Created Note 4: " + note4.getSummary());
        System.out.println("  Entities: " + note4.getEntities());
        System.out.println("  Tags: " + note4.getTags());
        System.out.println();

        ZettelNote note5 = zettelRAG.createNote(
            "The concept of atomic notes comes from Niklas Luhmann's Zettelkasten method. " +
            "Each note should contain exactly one idea, making it easy to link with other notes. " +
            "This atomicity enables flexible recombination of ideas.",
            Arrays.asList("zettelkasten", "learning", "note-taking")
        );
        System.out.println("Created Note 5: " + note5.getSummary());
        System.out.println("  Entities: " + note5.getEntities());
        System.out.println("  Tags: " + note5.getTags());
        System.out.println();

        // === Step 2: Explore Generated Links ===
        System.out.println("\nStep 2: Exploring Automatically Generated Links");
        System.out.println("==============================================");

        ZettelNote exampleNote = zettelRAG.getZettelNote(note1.getId());
        if (exampleNote != null) {
            System.out.println("Links for Note 1:");
            System.out.println("  Outgoing links: " + exampleNote.getOutgoingLinks().size());
            for (Link link : exampleNote.getOutgoingLinks()) {
                System.out.println(String.format("    -> %s [%s] (strength: %.2f) - %s",
                    link.getTargetNoteId(),
                    link.getType(),
                    link.getStrength(),
                    link.getDescription()));
            }
            System.out.println("  Incoming links: " + exampleNote.getIncomingLinks().size());
            System.out.println("  Total links: " + exampleNote.getAllLinks().size());
        }
        System.out.println();

        // === Step 3: Graph Statistics ===
        System.out.println("\nStep 3: Knowledge Graph Statistics");
        System.out.println("=================================");

        ZettelkastenGraph.GraphStatistics stats = zettelRAG.getGraphStatistics();
        System.out.println(stats);
        System.out.println();

        // === Step 4: Graph Traversal ===
        System.out.println("\nStep 4: Graph Traversal from Note 1");
        System.out.println("==================================");

        List<ZettelNote> traversedNotes = zettelRAG.traverseFromNote(note1.getId(), 2);
        System.out.println("Found " + traversedNotes.size() + " notes within 2 hops:");
        for (int i = 0; i < traversedNotes.size(); i++) {
            ZettelNote note = traversedNotes.get(i);
            System.out.println(String.format("  %d. [%s] %s",
                i + 1,
                note.getId(),
                note.getSummary()));
        }
        System.out.println();

        // === Step 5: Path Finding ===
        System.out.println("\nStep 5: Finding Path Between Notes");
        System.out.println("==================================");

        List<ZettelNote> path = zettelRAG.findPathBetweenNotes(note1.getId(), note2.getId());
        if (!path.isEmpty()) {
            System.out.println("Path from Note 1 to Note 2:");
            for (int i = 0; i < path.size(); i++) {
                ZettelNote note = path.get(i);
                System.out.println(String.format("  %d. %s", i + 1, note.getSummary()));
                if (i < path.size() - 1) {
                    System.out.println("     ↓");
                }
            }
        } else {
            System.out.println("No path found between notes.");
        }
        System.out.println();

        // === Step 6: Most Connected Notes ===
        System.out.println("\nStep 6: Most Connected Notes (Network Hubs)");
        System.out.println("==========================================");

        List<ZettelNote> hubs = zettelRAG.getMostConnectedNotes(3);
        System.out.println("Top 3 most connected notes:");
        for (int i = 0; i < hubs.size(); i++) {
            ZettelNote hub = hubs.get(i);
            System.out.println(String.format("  %d. %s",
                i + 1,
                hub.getSummary()));
            System.out.println(String.format("     Connectivity score: %.2f",
                hub.getConnectivityScore()));
        }
        System.out.println();

        // === Step 7: Link-Aware Context Retrieval ===
        System.out.println("\nStep 7: Link-Aware Context Retrieval");
        System.out.println("===================================");

        String query = "How does learning work through connections?";
        System.out.println("Query: " + query);
        System.out.println();

        String context = zettelRAG.retrieveContextWithLinks(query, 2, 1);
        System.out.println("Retrieved context (with 1-hop link expansion):");
        System.out.println(context);
        System.out.println();

        // === Step 8: Entity-Based Search ===
        System.out.println("\nStep 8: Entity-Based Search");
        System.out.println("===========================");

        List<String> searchEntities = Arrays.asList("Zettelkasten", "knowledge");
        System.out.println("Searching for notes with entities: " + searchEntities);
        
        var entityResults = zettelRAG.findByEntities(searchEntities, 5);
        System.out.println("Found " + entityResults.size() + " notes:");
        for (var result : entityResults) {
            System.out.println(String.format("  - [%s] Score: %.2f",
                result.getId(),
                result.getScore()));
        }
        System.out.println();

        // === Step 9: Tag-Based Search ===
        System.out.println("\nStep 9: Tag-Based Search");
        System.out.println("=======================");

        List<String> searchTags = Arrays.asList("learning", "knowledge-management");
        System.out.println("Searching for notes with tags: " + searchTags);
        
        var tagResults = zettelRAG.findByTags(searchTags, 5);
        System.out.println("Found " + tagResults.size() + " notes:");
        for (var result : tagResults) {
            System.out.println(String.format("  - [%s] %s",
                result.getId(),
                result.getMetadata().get("tags")));
        }
        System.out.println();

        // === Step 10: Link Type Analysis ===
        System.out.println("\nStep 10: Link Type Distribution");
        System.out.println("==============================");

        LinkStorage linkStorage = zettelRAG.getLinkStorage();
        LinkStorage.LinkStatistics linkStats = linkStorage.getStatistics();
        System.out.println(linkStats);
        System.out.println();

        System.out.println("Link types breakdown:");
        for (Map.Entry<LinkType, Integer> entry : linkStats.getLinkTypeCounts().entrySet()) {
            System.out.println(String.format("  %s: %d links",
                entry.getKey(),
                entry.getValue()));
        }
        System.out.println();

        // === Step 11: Connected Components ===
        System.out.println("\nStep 11: Knowledge Clusters (Connected Components)");
        System.out.println("=================================================");

        List<List<ZettelNote>> components = zettelRAG.getGraph().findConnectedComponents();
        System.out.println("Found " + components.size() + " knowledge cluster(s):");
        for (int i = 0; i < components.size(); i++) {
            List<ZettelNote> component = components.get(i);
            System.out.println(String.format("  Cluster %d: %d notes",
                i + 1,
                component.size()));
        }
        System.out.println();

        // === Summary ===
        System.out.println("\n=== Summary ===");
        System.out.println("Total notes created: " + zettelRAG.getAllZettelNotes().size());
        System.out.println("Total links generated: " + linkStats.getTotalLinks());
        System.out.println("Average link strength: " + String.format("%.2f", linkStats.getAverageStrength()));
        System.out.println("Knowledge graph is " +
            (components.size() == 1 ? "fully connected" : "fragmented into " + components.size() + " clusters"));
        System.out.println();
        
        System.out.println("Zettelkasten features demonstrated:");
        System.out.println("  ✓ Atomic note creation");
        System.out.println("  ✓ Automatic link generation (entity, topic, semantic)");
        System.out.println("  ✓ Graph traversal and path finding");
        System.out.println("  ✓ Link-aware context expansion");
        System.out.println("  ✓ Network analytics (hubs, clusters)");
        System.out.println("  ✓ Multi-dimensional search (entity, tag, semantic)");
    }
}
