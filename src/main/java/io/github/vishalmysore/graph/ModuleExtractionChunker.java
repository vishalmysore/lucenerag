package io.github.vishalmysore.graph;

import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.modularity.OntologySegmenter;

import java.util.*;
import java.util.logging.Logger;

/**
 * Chunks an OWL ontology using module extraction techniques.
 * Extracts self-contained modules based on syntactic locality principles.
 */
public class ModuleExtractionChunker {
    private static final Logger LOGGER = Logger.getLogger(ModuleExtractionChunker.class.getName());
    
    public List<OWLChunk> chunk(OWLOntology ontology) {
        List<OWLChunk> chunks = new ArrayList<>();
        
        try {
            // Get signature entities to use as module seeds
            Set<OWLEntity> signature = new HashSet<>(ontology.getSignature());
            
            // Group related entities
            List<Set<OWLEntity>> entityGroups = groupRelatedEntities(ontology, signature);
            
            LOGGER.info("Created " + entityGroups.size() + " entity groups for module extraction");
            
            // Extract module for each group
            int chunkId = 0;
            for (Set<OWLEntity> group : entityGroups) {
                if (group.isEmpty()) continue;
                
                Set<OWLAxiom> moduleAxioms = extractModule(ontology, group);
                
                if (!moduleAxioms.isEmpty()) {
                    String id = String.format("module-chunk-%d", chunkId++);
                    OWLChunk chunk = new OWLChunk(id, moduleAxioms, OWLChunkingStrategy.MODULE_EXTRACTION);
                    chunk.addMetadata("seedEntityCount", group.size());
                    chunk.addMetadata("sampleEntity", group.iterator().next().getIRI().toString());
                    chunks.add(chunk);
                }
            }
            
        } catch (Exception e) {
            LOGGER.warning("Module extraction failed: " + e.getMessage() + 
                ". Falling back to simple entity-based grouping.");
            return fallbackChunking(ontology);
        }
        
        LOGGER.info("Created " + chunks.size() + " module-based chunks");
        return chunks;
    }
    
    private List<Set<OWLEntity>> groupRelatedEntities(OWLOntology ontology, Set<OWLEntity> entities) {
        List<Set<OWLEntity>> groups = new ArrayList<>();
        Set<OWLEntity> processed = new HashSet<>();
        
        for (OWLEntity entity : entities) {
            if (processed.contains(entity)) continue;
            
            Set<OWLEntity> group = new HashSet<>();
            collectRelatedEntities(ontology, entity, group, processed, 2); // 2 levels deep
            
            if (!group.isEmpty()) {
                groups.add(group);
                processed.addAll(group);
            }
        }
        
        return groups;
    }
    
    private void collectRelatedEntities(OWLOntology ontology, OWLEntity entity, 
                                       Set<OWLEntity> group, Set<OWLEntity> processed, int depth) {
        if (depth <= 0 || processed.contains(entity)) {
            return;
        }
        
        group.add(entity);
        processed.add(entity);
        
        // Get axioms mentioning this entity
        Set<OWLAxiom> relatedAxioms = ontology.getReferencingAxioms(entity);
        
        // Collect entities from these axioms
        for (OWLAxiom axiom : relatedAxioms) {
            for (OWLEntity related : axiom.getSignature()) {
                if (!processed.contains(related) && !related.equals(entity)) {
                    collectRelatedEntities(ontology, related, group, processed, depth - 1);
                }
            }
        }
    }
    
    private Set<OWLAxiom> extractModule(OWLOntology ontology, Set<OWLEntity> signature) {
        Set<OWLAxiom> moduleAxioms = new HashSet<>();
        Set<OWLAxiom> processed = new HashSet<>();
        Queue<OWLEntity> queue = new LinkedList<>(signature);
        Set<OWLEntity> visitedEntities = new HashSet<>(signature);
        
        while (!queue.isEmpty()) {
            OWLEntity entity = queue.poll();
            
            // Get all axioms referencing this entity
            Set<OWLAxiom> axioms = ontology.getReferencingAxioms(entity);
            
            for (OWLAxiom axiom : axioms) {
                if (!processed.contains(axiom)) {
                    moduleAxioms.add(axiom);
                    processed.add(axiom);
                    
                    // Add new entities to queue (limited expansion)
                    for (OWLEntity newEntity : axiom.getSignature()) {
                        if (!visitedEntities.contains(newEntity)) {
                            visitedEntities.add(newEntity);
                            // Only expand to directly related entities
                            if (isDifferentFromOriginalSignature(newEntity, signature)) {
                                queue.add(newEntity);
                            }
                        }
                    }
                }
            }
        }
        
        return moduleAxioms;
    }
    
    private boolean isDifferentFromOriginalSignature(OWLEntity entity, Set<OWLEntity> signature) {
        // Limit expansion to avoid pulling in the entire ontology
        return !entity.isBuiltIn() && !signature.contains(entity);
    }
    
    private List<OWLChunk> fallbackChunking(OWLOntology ontology) {
        // Fallback to class-based chunking
        return new ClassBasedChunker().chunk(ontology);
    }
}
