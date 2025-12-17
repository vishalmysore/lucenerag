package io.github.vishalmysore.graph;

import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

import java.util.*;
import java.util.logging.Logger;

/**
 * Chunks an OWL ontology based on annotation properties.
 * Groups entities with similar annotations together (e.g., by rdfs:label, rdfs:comment).
 */
public class AnnotationBasedChunker {
    private static final Logger LOGGER = Logger.getLogger(AnnotationBasedChunker.class.getName());
    
    public List<OWLChunk> chunk(OWLOntology ontology) {
        List<OWLChunk> chunks = new ArrayList<>();
        
        // Find common annotation properties used in the ontology
        Set<OWLAnnotationProperty> annotationProperties = findSignificantAnnotationProperties(ontology);
        
        LOGGER.info("Found " + annotationProperties.size() + " significant annotation properties");
        
        // Group entities by annotation patterns
        Map<String, Set<OWLEntity>> annotationGroups = groupByAnnotations(ontology, annotationProperties);
        
        LOGGER.info("Created " + annotationGroups.size() + " annotation-based groups");
        
        // Create chunks from annotation groups
        int chunkId = 0;
        for (Map.Entry<String, Set<OWLEntity>> entry : annotationGroups.entrySet()) {
            String annotationKey = entry.getKey();
            Set<OWLEntity> entities = entry.getValue();
            Set<OWLAxiom> chunkAxioms = new HashSet<>();
            
            // Collect axioms for all entities in this group
            for (OWLEntity entity : entities) {
                chunkAxioms.addAll(ontology.getReferencingAxioms(entity));
            }
            
            if (!chunkAxioms.isEmpty()) {
                String id = String.format("annotation-chunk-%d", chunkId++);
                OWLChunk chunk = new OWLChunk(id, chunkAxioms, OWLChunkingStrategy.ANNOTATION_BASED);
                chunk.addMetadata("annotationKey", annotationKey);
                chunk.addMetadata("entityCount", entities.size());
                chunks.add(chunk);
            }
        }
        
        // Handle entities without significant annotations
        Set<OWLEntity> unannotatedEntities = findUnannotatedEntities(ontology, annotationGroups);
        if (!unannotatedEntities.isEmpty()) {
            Set<OWLAxiom> unannotatedAxioms = new HashSet<>();
            for (OWLEntity entity : unannotatedEntities) {
                unannotatedAxioms.addAll(ontology.getReferencingAxioms(entity));
            }
            
            if (!unannotatedAxioms.isEmpty()) {
                String id = String.format("annotation-chunk-%d-unannotated", chunkId);
                OWLChunk chunk = new OWLChunk(id, unannotatedAxioms, OWLChunkingStrategy.ANNOTATION_BASED);
                chunk.addMetadata("type", "unannotated");
                chunks.add(chunk);
            }
        }
        
        LOGGER.info("Created " + chunks.size() + " annotation-based chunks");
        return chunks;
    }
    
    private Set<OWLAnnotationProperty> findSignificantAnnotationProperties(OWLOntology ontology) {
        Set<OWLAnnotationProperty> significant = new HashSet<>();
        Map<OWLAnnotationProperty, Integer> usageCount = new HashMap<>();
        
        // Count usage of each annotation property
        for (OWLEntity entity : ontology.getSignature()) {
            for (OWLAnnotationAssertionAxiom axiom : ontology.getAnnotationAssertionAxioms(entity.getIRI())) {
                OWLAnnotationProperty prop = axiom.getProperty();
                usageCount.put(prop, usageCount.getOrDefault(prop, 0) + 1);
            }
        }
        
        // Consider properties used more than 5% of entities as significant
        int threshold = ontology.getSignature().size() / 20;
        for (Map.Entry<OWLAnnotationProperty, Integer> entry : usageCount.entrySet()) {
            if (entry.getValue() > threshold) {
                significant.add(entry.getKey());
            }
        }
        
        // Always include standard annotation properties if they exist
        OWLDataFactory factory = ontology.getOWLOntologyManager().getOWLDataFactory();
        significant.add(factory.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI()));
        significant.add(factory.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_COMMENT.getIRI()));
        
        return significant;
    }
    
    private Map<String, Set<OWLEntity>> groupByAnnotations(OWLOntology ontology, 
                                                            Set<OWLAnnotationProperty> properties) {
        Map<String, Set<OWLEntity>> groups = new HashMap<>();
        
        for (OWLEntity entity : ontology.getSignature()) {
            // Create a signature based on which annotation properties this entity has
            StringBuilder signature = new StringBuilder();
            
            for (OWLAnnotationProperty prop : properties) {
                Set<OWLAnnotationAssertionAxiom> annotationAxioms = ontology.getAnnotationAssertionAxioms(entity.getIRI());
                Set<OWLAnnotation> annotations = new HashSet<>();
                for (OWLAnnotationAssertionAxiom axiom : annotationAxioms) {
                    if (axiom.getProperty().equals(prop)) {
                        annotations.add(axiom.getAnnotation());
                    }
                }
                if (!annotations.isEmpty()) {
                    signature.append(getShortName(prop.getIRI().toString())).append(":");
                    
                    // Group by annotation value prefixes (first few characters)
                    for (OWLAnnotation annotation : annotations) {
                        if (annotation.getValue() instanceof OWLLiteral) {
                            OWLLiteral literal = (OWLLiteral) annotation.getValue();
                            String value = literal.getLiteral();
                            if (value.length() > 0) {
                                // Use first character or prefix for grouping
                                String prefix = value.substring(0, Math.min(3, value.length())).toLowerCase();
                                signature.append(prefix);
                            }
                        }
                    }
                    signature.append(";");
                }
            }
            
            String key = signature.length() > 0 ? signature.toString() : "no-annotations";
            groups.computeIfAbsent(key, k -> new HashSet<>()).add(entity);
        }
        
        return groups;
    }
    
    private Set<OWLEntity> findUnannotatedEntities(OWLOntology ontology, 
                                                    Map<String, Set<OWLEntity>> annotatedGroups) {
        Set<OWLEntity> allAnnotated = new HashSet<>();
        for (Set<OWLEntity> group : annotatedGroups.values()) {
            allAnnotated.addAll(group);
        }
        
        Set<OWLEntity> unannotated = new HashSet<>(ontology.getSignature());
        unannotated.removeAll(allAnnotated);
        return unannotated;
    }
    
    private String getShortName(String iri) {
        String[] parts = iri.split("[/#]");
        return parts[parts.length - 1];
    }
}
