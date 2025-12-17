package io.github.vishalmysore.graph;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Main class for chunking OWL ontologies using various strategies.
 * This class provides the entry point for loading and processing OWL files or streams,
 * and applying different chunking strategies to break down large ontologies into manageable pieces.
 * 
 * Designed to be compatible with Protege plugin architecture (no Protege dependencies required).
 */
public class OWLGraphChunker {
    private static final Logger LOGGER = Logger.getLogger(OWLGraphChunker.class.getName());
    
    private final OWLOntologyManager manager;
    private OWLOntology ontology;
    private OWLChunkingStrategy strategy;
    private int maxChunkSize = 1000; // Default for size-based chunking
    
    public OWLGraphChunker() {
        this.manager = OWLManager.createOWLOntologyManager();
        this.strategy = OWLChunkingStrategy.CLASS_BASED; // Default strategy
    }
    
    public OWLGraphChunker(OWLChunkingStrategy strategy) {
        this.manager = OWLManager.createOWLOntologyManager();
        this.strategy = strategy;
    }
    
    /**
     * Loads an ontology from a file
     * @param file The OWL file to load
     * @throws OWLOntologyCreationException if the file cannot be loaded
     */
    public void loadFromFile(File file) throws OWLOntologyCreationException {
        LOGGER.info("Loading ontology from file: " + file.getAbsolutePath());
        this.ontology = manager.loadOntologyFromOntologyDocument(file);
        LOGGER.info("Loaded ontology with " + ontology.getAxiomCount() + " axioms");
    }
    
    /**
     * Loads an ontology from an InputStream
     * This is useful for Protege plugins or when the ontology is not stored as a file
     * @param inputStream The input stream containing the OWL data
     * @throws OWLOntologyCreationException if the stream cannot be loaded
     */
    public void loadFromStream(InputStream inputStream) throws OWLOntologyCreationException {
        LOGGER.info("Loading ontology from input stream");
        this.ontology = manager.loadOntologyFromOntologyDocument(inputStream);
        LOGGER.info("Loaded ontology with " + ontology.getAxiomCount() + " axioms");
    }
    
    /**
     * Loads an existing OWLOntology object (useful for Protege integration)
     * @param ontology The ontology to chunk
     */
    public void loadOntology(OWLOntology ontology) {
        this.ontology = ontology;
        LOGGER.info("Loaded ontology with " + ontology.getAxiomCount() + " axioms");
    }
    
    /**
     * Sets the chunking strategy to use
     * @param strategy The chunking strategy
     */
    public void setStrategy(OWLChunkingStrategy strategy) {
        this.strategy = strategy;
    }
    
    /**
     * Sets the maximum chunk size for size-based chunking
     * @param maxChunkSize Maximum number of axioms per chunk
     */
    public void setMaxChunkSize(int maxChunkSize) {
        this.maxChunkSize = maxChunkSize;
    }
    
    /**
     * Performs the chunking operation based on the selected strategy
     * @return List of OWL chunks
     * @throws IllegalStateException if no ontology is loaded
     */
    public List<OWLChunk> chunk() {
        if (ontology == null) {
            throw new IllegalStateException("No ontology loaded. Call loadFromFile, loadFromStream, or loadOntology first.");
        }
        
        LOGGER.info("Chunking ontology using strategy: " + strategy);
        
        switch (strategy) {
            case CLASS_BASED:
                return new ClassBasedChunker().chunk(ontology);
            case NAMESPACE_BASED:
                return new NamespaceBasedChunker().chunk(ontology);
            case SIZE_BASED:
                return new SizeBasedChunker(maxChunkSize).chunk(ontology);
            case DEPTH_BASED:
                return new DepthBasedChunker().chunk(ontology);
            case MODULE_EXTRACTION:
                return new ModuleExtractionChunker().chunk(ontology);
            case CONNECTED_COMPONENT:
                return new ConnectedComponentChunker().chunk(ontology);
            case ANNOTATION_BASED:
                return new AnnotationBasedChunker().chunk(ontology);
            default:
                throw new UnsupportedOperationException("Strategy not implemented: " + strategy);
        }
    }
    
    /**
     * Gets the loaded ontology
     * @return The loaded OWL ontology or null if none is loaded
     */
    public OWLOntology getOntology() {
        return ontology;
    }
    
    /**
     * Gets statistics about the loaded ontology
     * @return String containing ontology statistics
     */
    public String getOntologyStats() {
        if (ontology == null) {
            return "No ontology loaded";
        }
        
        return String.format(
            "Ontology Statistics:\n" +
            "  Total Axioms: %d\n" +
            "  Logical Axioms: %d\n" +
            "  Classes: %d\n" +
            "  Object Properties: %d\n" +
            "  Data Properties: %d\n" +
            "  Individuals: %d",
            ontology.getAxiomCount(),
            ontology.getLogicalAxiomCount(),
            ontology.getClassesInSignature().size(),
            ontology.getObjectPropertiesInSignature().size(),
            ontology.getDataPropertiesInSignature().size(),
            ontology.getIndividualsInSignature().size()
        );
    }
}
