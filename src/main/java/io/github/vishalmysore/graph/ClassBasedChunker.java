package io.github.vishalmysore.graph;

import org.semanticweb.owlapi.model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Chunks an OWL ontology based on class hierarchies.
 * Each chunk contains a top-level class and its descendant subclasses,
 * along with related properties and individuals.
 */
public class ClassBasedChunker {
    private static final Logger LOGGER = Logger.getLogger(ClassBasedChunker.class.getName());
    
    public List<OWLChunk> chunk(OWLOntology ontology) {
        List<OWLChunk> chunks = new ArrayList<>();
        OWLDataFactory factory = ontology.getOWLOntologyManager().getOWLDataFactory();
        
        // Get all top-level classes (classes with no superclasses or only Thing as superclass)
        Set<OWLClass> topLevelClasses = findTopLevelClasses(ontology);
        LOGGER.info("Found " + topLevelClasses.size() + " top-level classes");
        
        int chunkId = 0;
        for (OWLClass topClass : topLevelClasses) {
            Set<OWLAxiom> chunkAxioms = new HashSet<>();
            Set<OWLClass> relatedClasses = new HashSet<>();
            
            // Collect all subclasses recursively
            collectSubClasses(ontology, topClass, relatedClasses);
            relatedClasses.add(topClass);
            
            // Collect all axioms related to these classes
            for (OWLClass cls : relatedClasses) {
                chunkAxioms.addAll(ontology.getAxioms(cls));
                
                // Add axioms about instances of this class
                for (OWLIndividual individual : cls.getIndividualsInSignature()) {
                    chunkAxioms.addAll(ontology.getAxioms(individual));
                }
            }
            
            if (!chunkAxioms.isEmpty()) {
                String id = String.format("class-chunk-%d-%s", chunkId++, 
                    getSimpleName(topClass.getIRI().toString()));
                OWLChunk chunk = new OWLChunk(id, chunkAxioms, OWLChunkingStrategy.CLASS_BASED);
                chunk.addMetadata("topClass", topClass.getIRI().toString());
                chunk.addMetadata("relatedClasses", relatedClasses.size());
                chunks.add(chunk);
            }
        }
        
        // Handle orphan axioms (not directly related to any class)
        Set<OWLAxiom> orphanAxioms = collectOrphanAxioms(ontology, chunks);
        if (!orphanAxioms.isEmpty()) {
            String id = String.format("class-chunk-%d-orphans", chunkId);
            OWLChunk chunk = new OWLChunk(id, orphanAxioms, OWLChunkingStrategy.CLASS_BASED);
            chunk.addMetadata("type", "orphan-axioms");
            chunks.add(chunk);
        }
        
        LOGGER.info("Created " + chunks.size() + " class-based chunks");
        return chunks;
    }
    
    private Set<OWLClass> findTopLevelClasses(OWLOntology ontology) {
        Set<OWLClass> topLevel = new HashSet<>();
        OWLDataFactory factory = ontology.getOWLOntologyManager().getOWLDataFactory();
        OWLClass thing = factory.getOWLThing();
        
        for (OWLClass cls : ontology.getClassesInSignature()) {
            if (cls.isOWLThing() || cls.isOWLNothing()) {
                continue;
            }
            
            Set<OWLClass> superClasses = new HashSet<>();
            for (OWLSubClassOfAxiom axiom : ontology.getSubClassAxiomsForSubClass(cls)) {
                if (axiom.getSuperClass().isClassExpressionLiteral()) {
                    OWLClass superClass = axiom.getSuperClass().asOWLClass();
                    if (!superClass.isOWLThing()) {
                        superClasses.add(superClass);
                    }
                }
            }
            
            if (superClasses.isEmpty()) {
                topLevel.add(cls);
            }
        }
        
        return topLevel.isEmpty() ? ontology.getClassesInSignature() : topLevel;
    }
    
    private void collectSubClasses(OWLOntology ontology, OWLClass cls, Set<OWLClass> collected) {
        for (OWLSubClassOfAxiom axiom : ontology.getSubClassAxiomsForSuperClass(cls)) {
            if (axiom.getSubClass().isClassExpressionLiteral()) {
                OWLClass subClass = axiom.getSubClass().asOWLClass();
                if (!collected.contains(subClass)) {
                    collected.add(subClass);
                    collectSubClasses(ontology, subClass, collected);
                }
            }
        }
    }
    
    private Set<OWLAxiom> collectOrphanAxioms(OWLOntology ontology, List<OWLChunk> existingChunks) {
        Set<OWLAxiom> allChunkedAxioms = new HashSet<>();
        for (OWLChunk chunk : existingChunks) {
            allChunkedAxioms.addAll(chunk.getAxioms());
        }
        
        Set<OWLAxiom> orphans = new HashSet<>(ontology.getAxioms());
        orphans.removeAll(allChunkedAxioms);
        return orphans;
    }
    
    private String getSimpleName(String iri) {
        String[] parts = iri.split("[/#]");
        return parts[parts.length - 1];
    }
}
