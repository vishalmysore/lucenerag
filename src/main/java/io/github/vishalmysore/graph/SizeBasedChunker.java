package io.github.vishalmysore.graph;

import org.semanticweb.owlapi.model.*;

import java.util.*;
import java.util.logging.Logger;

/**
 * Chunks an OWL ontology based on a maximum number of axioms per chunk.
 * Maintains entity coherence by keeping all axioms about an entity together.
 */
public class SizeBasedChunker {
    private static final Logger LOGGER = Logger.getLogger(SizeBasedChunker.class.getName());
    private final int maxChunkSize;
    
    public SizeBasedChunker(int maxChunkSize) {
        this.maxChunkSize = maxChunkSize;
    }
    
    public List<OWLChunk> chunk(OWLOntology ontology) {
        List<OWLChunk> chunks = new ArrayList<>();
        
        // Group axioms by their primary entity to maintain coherence
        Map<OWLEntity, Set<OWLAxiom>> entityAxiomMap = groupAxiomsByEntity(ontology);
        
        LOGGER.info("Grouped axioms by " + entityAxiomMap.size() + " entities");
        
        // Create chunks respecting size limit
        Set<OWLAxiom> currentChunk = new HashSet<>();
        int chunkId = 0;
        
        for (Map.Entry<OWLEntity, Set<OWLAxiom>> entry : entityAxiomMap.entrySet()) {
            Set<OWLAxiom> entityAxioms = entry.getValue();
            
            // If adding these axioms would exceed the limit, create a new chunk
            if (!currentChunk.isEmpty() && currentChunk.size() + entityAxioms.size() > maxChunkSize) {
                createChunk(chunks, currentChunk, chunkId++);
                currentChunk = new HashSet<>();
            }
            
            // If a single entity's axioms exceed the max size, create a dedicated chunk
            if (entityAxioms.size() > maxChunkSize) {
                LOGGER.warning("Entity " + entry.getKey() + " has " + entityAxioms.size() + 
                    " axioms, exceeding max chunk size. Creating dedicated chunk.");
                Set<OWLAxiom> dedicatedChunk = new HashSet<>(entityAxioms);
                OWLChunk chunk = new OWLChunk(
                    String.format("size-chunk-%d-large", chunkId++),
                    dedicatedChunk,
                    OWLChunkingStrategy.SIZE_BASED
                );
                chunk.addMetadata("entity", entry.getKey().getIRI().toString());
                chunk.addMetadata("oversized", true);
                chunks.add(chunk);
                continue;
            }
            
            currentChunk.addAll(entityAxioms);
        }
        
        // Add remaining axioms
        if (!currentChunk.isEmpty()) {
            createChunk(chunks, currentChunk, chunkId);
        }
        
        LOGGER.info("Created " + chunks.size() + " size-based chunks (max size: " + maxChunkSize + ")");
        return chunks;
    }
    
    private Map<OWLEntity, Set<OWLAxiom>> groupAxiomsByEntity(OWLOntology ontology) {
        Map<OWLEntity, Set<OWLAxiom>> entityMap = new HashMap<>();
        Set<OWLAxiom> processedAxioms = new HashSet<>();
        
        // Group by classes
        for (OWLClass cls : ontology.getClassesInSignature()) {
            Set<OWLAxiom> axioms = new HashSet<>(ontology.getAxioms(cls));
            if (!axioms.isEmpty()) {
                entityMap.put(cls, axioms);
                processedAxioms.addAll(axioms);
            }
        }
        
        // Group by object properties
        for (OWLObjectProperty prop : ontology.getObjectPropertiesInSignature()) {
            Set<OWLAxiom> axioms = new HashSet<>(ontology.getAxioms(prop));
            axioms.removeAll(processedAxioms);
            if (!axioms.isEmpty()) {
                entityMap.put(prop, axioms);
                processedAxioms.addAll(axioms);
            }
        }
        
        // Group by data properties
        for (OWLDataProperty prop : ontology.getDataPropertiesInSignature()) {
            Set<OWLAxiom> axioms = new HashSet<>(ontology.getAxioms(prop));
            axioms.removeAll(processedAxioms);
            if (!axioms.isEmpty()) {
                entityMap.put(prop, axioms);
                processedAxioms.addAll(axioms);
            }
        }
        
        // Group by individuals
        for (OWLNamedIndividual individual : ontology.getIndividualsInSignature()) {
            Set<OWLAxiom> axioms = new HashSet<>(ontology.getAxioms(individual));
            axioms.removeAll(processedAxioms);
            if (!axioms.isEmpty()) {
                entityMap.put(individual, axioms);
                processedAxioms.addAll(axioms);
            }
        }
        
        // Handle remaining axioms
        Set<OWLAxiom> remainingAxioms = new HashSet<>(ontology.getAxioms());
        remainingAxioms.removeAll(processedAxioms);
        if (!remainingAxioms.isEmpty()) {
            OWLEntity syntheticEntity = ontology.getOWLOntologyManager()
                .getOWLDataFactory()
                .getOWLClass(IRI.create("urn:synthetic:remaining"));
            entityMap.put(syntheticEntity, remainingAxioms);
        }
        
        return entityMap;
    }
    
    private void createChunk(List<OWLChunk> chunks, Set<OWLAxiom> axioms, int chunkId) {
        String id = String.format("size-chunk-%d", chunkId);
        OWLChunk chunk = new OWLChunk(id, axioms, OWLChunkingStrategy.SIZE_BASED);
        chunk.addMetadata("actualSize", axioms.size());
        chunk.addMetadata("maxSize", maxChunkSize);
        chunks.add(chunk);
    }
}
