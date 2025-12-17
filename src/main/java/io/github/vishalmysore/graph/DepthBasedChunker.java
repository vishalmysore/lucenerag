package io.github.vishalmysore.graph;

import org.semanticweb.owlapi.model.*;

import java.util.*;
import java.util.logging.Logger;

/**
 * Chunks an OWL ontology based on depth levels in the class hierarchy.
 * Groups classes at similar depths together.
 */
public class DepthBasedChunker {
    private static final Logger LOGGER = Logger.getLogger(DepthBasedChunker.class.getName());
    
    public List<OWLChunk> chunk(OWLOntology ontology) {
        List<OWLChunk> chunks = new ArrayList<>();
        
        // Calculate depth for each class
        Map<OWLClass, Integer> classDepths = calculateClassDepths(ontology);
        
        // Group classes by depth
        Map<Integer, Set<OWLClass>> depthGroups = new HashMap<>();
        for (Map.Entry<OWLClass, Integer> entry : classDepths.entrySet()) {
            depthGroups.computeIfAbsent(entry.getValue(), k -> new HashSet<>()).add(entry.getKey());
        }
        
        LOGGER.info("Found " + depthGroups.size() + " different depth levels");
        
        // Create chunks for each depth level
        for (Map.Entry<Integer, Set<OWLClass>> entry : depthGroups.entrySet()) {
            int depth = entry.getKey();
            Set<OWLClass> classes = entry.getValue();
            Set<OWLAxiom> chunkAxioms = new HashSet<>();
            
            // Collect all axioms related to classes at this depth
            for (OWLClass cls : classes) {
                chunkAxioms.addAll(ontology.getAxioms(cls));
                
                // Add axioms about instances
                for (OWLIndividual individual : cls.getIndividualsInSignature()) {
                    chunkAxioms.addAll(ontology.getAxioms(individual));
                }
            }
            
            if (!chunkAxioms.isEmpty()) {
                String id = String.format("depth-chunk-level-%d", depth);
                OWLChunk chunk = new OWLChunk(id, chunkAxioms, OWLChunkingStrategy.DEPTH_BASED);
                chunk.addMetadata("depth", depth);
                chunk.addMetadata("classCount", classes.size());
                chunks.add(chunk);
            }
        }
        
        // Handle property and individual axioms not directly tied to classes
        Set<OWLAxiom> otherAxioms = collectNonClassAxioms(ontology, chunks);
        if (!otherAxioms.isEmpty()) {
            String id = "depth-chunk-properties-individuals";
            OWLChunk chunk = new OWLChunk(id, otherAxioms, OWLChunkingStrategy.DEPTH_BASED);
            chunk.addMetadata("type", "non-class-axioms");
            chunks.add(chunk);
        }
        
        LOGGER.info("Created " + chunks.size() + " depth-based chunks");
        return chunks;
    }
    
    private Map<OWLClass, Integer> calculateClassDepths(OWLOntology ontology) {
        Map<OWLClass, Integer> depths = new HashMap<>();
        OWLDataFactory factory = ontology.getOWLOntologyManager().getOWLDataFactory();
        OWLClass thing = factory.getOWLThing();
        
        // Start BFS from owl:Thing
        Queue<OWLClass> queue = new LinkedList<>();
        queue.add(thing);
        depths.put(thing, 0);
        
        while (!queue.isEmpty()) {
            OWLClass current = queue.poll();
            int currentDepth = depths.get(current);
            
            // Find all direct subclasses
            for (OWLSubClassOfAxiom axiom : ontology.getSubClassAxiomsForSuperClass(current)) {
                if (axiom.getSubClass().isClassExpressionLiteral()) {
                    OWLClass subClass = axiom.getSubClass().asOWLClass();
                    
                    if (!depths.containsKey(subClass)) {
                        depths.put(subClass, currentDepth + 1);
                        queue.add(subClass);
                    }
                }
            }
        }
        
        // Handle classes not connected to owl:Thing
        for (OWLClass cls : ontology.getClassesInSignature()) {
            if (!depths.containsKey(cls) && !cls.isOWLThing() && !cls.isOWLNothing()) {
                depths.put(cls, calculateDepthFromBottom(ontology, cls, new HashSet<>()));
            }
        }
        
        return depths;
    }
    
    private int calculateDepthFromBottom(OWLOntology ontology, OWLClass cls, Set<OWLClass> visited) {
        if (visited.contains(cls)) {
            return 0; // Avoid cycles
        }
        visited.add(cls);
        
        // Check if this class has any subclasses
        Set<OWLSubClassOfAxiom> subClassAxioms = ontology.getSubClassAxiomsForSuperClass(cls);
        if (subClassAxioms.isEmpty()) {
            return 0; // Leaf class
        }
        
        int maxSubDepth = 0;
        for (OWLSubClassOfAxiom axiom : subClassAxioms) {
            if (axiom.getSubClass().isClassExpressionLiteral()) {
                OWLClass subClass = axiom.getSubClass().asOWLClass();
                int subDepth = calculateDepthFromBottom(ontology, subClass, visited);
                maxSubDepth = Math.max(maxSubDepth, subDepth);
            }
        }
        
        return maxSubDepth + 1;
    }
    
    private Set<OWLAxiom> collectNonClassAxioms(OWLOntology ontology, List<OWLChunk> existingChunks) {
        Set<OWLAxiom> allChunkedAxioms = new HashSet<>();
        for (OWLChunk chunk : existingChunks) {
            allChunkedAxioms.addAll(chunk.getAxioms());
        }
        
        Set<OWLAxiom> remaining = new HashSet<>(ontology.getAxioms());
        remaining.removeAll(allChunkedAxioms);
        return remaining;
    }
}
