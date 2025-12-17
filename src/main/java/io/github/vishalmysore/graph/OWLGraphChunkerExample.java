package io.github.vishalmysore.graph;

import org.semanticweb.owlapi.model.OWLOntologyCreationException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Example usage of the OWL Graph Chunker.
 * Demonstrates how to load and chunk OWL ontologies using different strategies.
 */
public class OWLGraphChunkerExample {
    
    public static void main(String[] args) {
        try {
            // Example 1: Load from file and chunk by class hierarchy
            exampleClassBasedChunking();
            
            // Example 2: Load from stream and chunk by namespace
            // exampleNamespaceBasedChunking();
            
            // Example 3: Size-based chunking
            // exampleSizeBasedChunking();
            
            // Example 4: Depth-based chunking
            // exampleDepthBasedChunking();
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static void exampleClassBasedChunking() throws OWLOntologyCreationException {
        System.out.println("=== Class-Based Chunking Example ===\n");
        
        // Create chunker with class-based strategy
        OWLGraphChunker chunker = new OWLGraphChunker(OWLChunkingStrategy.CLASS_BASED);
        
        // Load OWL file (replace with your actual OWL file path)
        File owlFile = new File("path/to/your/ontology.owl");
        
        // For demonstration, you can also use one of the Pizza ontology or other sample ontologies
        // Download from: https://protege.stanford.edu/ontologies/pizza/pizza.owl
        
        if (!owlFile.exists()) {
            System.out.println("OWL file not found at: " + owlFile.getAbsolutePath());
            System.out.println("Please update the file path in the example.");
            return;
        }
        
        chunker.loadFromFile(owlFile);
        
        // Print ontology statistics
        System.out.println(chunker.getOntologyStats());
        System.out.println();
        
        // Perform chunking
        List<OWLChunk> chunks = chunker.chunk();
        
        // Display results
        System.out.println("Created " + chunks.size() + " chunks:");
        for (OWLChunk chunk : chunks) {
            System.out.println("  - " + chunk);
            System.out.println("    Metadata: " + chunk.getMetadata());
        }
    }
    
    public static void exampleNamespaceBasedChunking() throws OWLOntologyCreationException, IOException {
        System.out.println("\n=== Namespace-Based Chunking Example ===\n");
        
        OWLGraphChunker chunker = new OWLGraphChunker(OWLChunkingStrategy.NAMESPACE_BASED);
        
        // Load from InputStream (useful for Protege plugins)
        File owlFile = new File("path/to/your/ontology.owl");
        try (InputStream inputStream = new FileInputStream(owlFile)) {
            chunker.loadFromStream(inputStream);
        }
        
        System.out.println(chunker.getOntologyStats());
        System.out.println();
        
        List<OWLChunk> chunks = chunker.chunk();
        
        System.out.println("Created " + chunks.size() + " namespace-based chunks:");
        for (OWLChunk chunk : chunks) {
            System.out.println("  - " + chunk);
            System.out.println("    Namespace: " + chunk.getMetadata().get("namespace"));
        }
    }
    
    public static void exampleSizeBasedChunking() throws OWLOntologyCreationException {
        System.out.println("\n=== Size-Based Chunking Example ===\n");
        
        OWLGraphChunker chunker = new OWLGraphChunker(OWLChunkingStrategy.SIZE_BASED);
        chunker.setMaxChunkSize(500); // Set maximum axioms per chunk
        
        File owlFile = new File("path/to/your/ontology.owl");
        chunker.loadFromFile(owlFile);
        
        System.out.println(chunker.getOntologyStats());
        System.out.println();
        
        List<OWLChunk> chunks = chunker.chunk();
        
        System.out.println("Created " + chunks.size() + " size-based chunks (max 500 axioms each):");
        for (OWLChunk chunk : chunks) {
            System.out.println("  - " + chunk);
            System.out.println("    Actual size: " + chunk.getAxiomCount());
        }
    }
    
    public static void exampleDepthBasedChunking() throws OWLOntologyCreationException {
        System.out.println("\n=== Depth-Based Chunking Example ===\n");
        
        OWLGraphChunker chunker = new OWLGraphChunker(OWLChunkingStrategy.DEPTH_BASED);
        
        File owlFile = new File("path/to/your/ontology.owl");
        chunker.loadFromFile(owlFile);
        
        System.out.println(chunker.getOntologyStats());
        System.out.println();
        
        List<OWLChunk> chunks = chunker.chunk();
        
        System.out.println("Created " + chunks.size() + " depth-based chunks:");
        for (OWLChunk chunk : chunks) {
            System.out.println("  - " + chunk);
            System.out.println("    Depth level: " + chunk.getMetadata().get("depth"));
            System.out.println("    Classes: " + chunk.getMetadata().get("classCount"));
        }
    }
    
    /**
     * Example for Protege plugin integration.
     * Shows how to use an existing OWLOntology object.
     */
    public static List<OWLChunk> chunkForProtege(org.semanticweb.owlapi.model.OWLOntology ontology, 
                                                   OWLChunkingStrategy strategy) {
        OWLGraphChunker chunker = new OWLGraphChunker(strategy);
        chunker.loadOntology(ontology);
        return chunker.chunk();
    }
}
