package io.github.vishalmysore.graph;

/**
 * Enumeration of available OWL chunking strategies.
 * Each strategy represents a different approach to dividing an OWL ontology into manageable chunks.
 */
public enum OWLChunkingStrategy {
    /**
     * Chunks based on class hierarchies. Each chunk contains a class and its subclasses,
     * along with related properties and individuals.
     */
    CLASS_BASED("Class-Based", "Chunks by class hierarchies preserving inheritance relationships"),
    
    /**
     * Chunks based on namespace prefixes. Separates axioms by their namespace URIs.
     */
    NAMESPACE_BASED("Namespace-Based", "Chunks by namespace/URI prefixes"),
    
    /**
     * Chunks based on a maximum number of axioms per chunk.
     * Maintains entity coherence by keeping all axioms about an entity together.
     */
    SIZE_BASED("Size-Based", "Chunks by maximum axiom count while maintaining entity coherence"),
    
    /**
     * Chunks based on depth levels in the class hierarchy.
     * Groups classes at similar depths together.
     */
    DEPTH_BASED("Depth-Based", "Chunks by hierarchy depth levels"),
    
    /**
     * Uses ontology modularization to extract self-contained modules.
     * Based on syntactic locality principles.
     */
    MODULE_EXTRACTION("Module Extraction", "Extracts self-contained ontology modules"),
    
    /**
     * Analyzes the ontology as a graph and identifies connected components.
     */
    CONNECTED_COMPONENT("Connected Component", "Chunks by graph connectivity analysis"),
    
    /**
     * Chunks based on annotation properties (e.g., rdfs:label, rdfs:comment).
     * Groups entities with similar annotations together.
     */
    ANNOTATION_BASED("Annotation-Based", "Chunks by annotation properties");

    private final String displayName;
    private final String description;

    OWLChunkingStrategy(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
