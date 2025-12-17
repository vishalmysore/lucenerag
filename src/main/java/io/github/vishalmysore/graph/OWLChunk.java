package io.github.vishalmysore.graph;

import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;

import java.util.*;

/**
 * Represents a chunk of an OWL ontology.
 * Each chunk contains a set of axioms that form a coherent semantic unit.
 */
public class OWLChunk {
    private final String id;
    private final Set<OWLAxiom> axioms;
    private final Map<String, Object> metadata;
    private final OWLChunkingStrategy strategy;

    public OWLChunk(String id, Set<OWLAxiom> axioms, OWLChunkingStrategy strategy) {
        this.id = id;
        this.axioms = new HashSet<>(axioms);
        this.metadata = new HashMap<>();
        this.strategy = strategy;
    }

    public String getId() {
        return id;
    }

    public Set<OWLAxiom> getAxioms() {
        return Collections.unmodifiableSet(axioms);
    }

    public int getAxiomCount() {
        return axioms.size();
    }

    public OWLChunkingStrategy getStrategy() {
        return strategy;
    }

    public Map<String, Object> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    public void addMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    /**
     * Converts the chunk to an OWL string representation
     */
    public String toOWLString() {
        StringBuilder sb = new StringBuilder();
        for (OWLAxiom axiom : axioms) {
            sb.append(axiom.toString()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Gets a serializable representation of the chunk
     */
    public String toSerializedString() {
        return String.format("Chunk[id=%s, axioms=%d, strategy=%s]", 
            id, axioms.size(), strategy);
    }

    @Override
    public String toString() {
        return toSerializedString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OWLChunk owlChunk = (OWLChunk) o;
        return Objects.equals(id, owlChunk.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
