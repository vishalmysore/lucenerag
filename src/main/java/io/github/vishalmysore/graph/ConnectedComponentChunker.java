package io.github.vishalmysore.graph;

import org.semanticweb.owlapi.model.*;

import java.util.*;
import java.util.logging.Logger;

/**
 * Chunks an OWL ontology by analyzing graph connectivity.
 * Identifies weakly connected components in the ontology graph structure.
 */
public class ConnectedComponentChunker {
    private static final Logger LOGGER = Logger.getLogger(ConnectedComponentChunker.class.getName());
    
    public List<OWLChunk> chunk(OWLOntology ontology) {
        List<OWLChunk> chunks = new ArrayList<>();
        
        // Build graph representation
        Map<OWLEntity, Set<OWLEntity>> graph = buildEntityGraph(ontology);
        
        LOGGER.info("Built graph with " + graph.size() + " nodes");
        
        // Find connected components
        List<Set<OWLEntity>> components = findConnectedComponents(graph);
        
        LOGGER.info("Found " + components.size() + " connected components");
        
        // Create chunks from components
        int chunkId = 0;
        for (Set<OWLEntity> component : components) {
            Set<OWLAxiom> componentAxioms = new HashSet<>();
            
            // Collect all axioms involving entities in this component
            for (OWLEntity entity : component) {
                componentAxioms.addAll(ontology.getReferencingAxioms(entity));
            }
            
            if (!componentAxioms.isEmpty()) {
                String id = String.format("component-chunk-%d", chunkId++);
                OWLChunk chunk = new OWLChunk(id, componentAxioms, OWLChunkingStrategy.CONNECTED_COMPONENT);
                chunk.addMetadata("componentSize", component.size());
                chunk.addMetadata("sampleEntity", component.iterator().next().getIRI().toString());
                chunks.add(chunk);
            }
        }
        
        LOGGER.info("Created " + chunks.size() + " connected component chunks");
        return chunks;
    }
    
    private Map<OWLEntity, Set<OWLEntity>> buildEntityGraph(OWLOntology ontology) {
        Map<OWLEntity, Set<OWLEntity>> graph = new HashMap<>();
        
        // Initialize graph with all entities
        for (OWLEntity entity : ontology.getSignature()) {
            graph.putIfAbsent(entity, new HashSet<>());
        }
        
        // Build edges based on axiom relationships
        for (OWLAxiom axiom : ontology.getAxioms()) {
            Set<OWLEntity> entitiesInAxiom = axiom.getSignature();
            
            // Create edges between all entities that appear together in an axiom
            List<OWLEntity> entityList = new ArrayList<>(entitiesInAxiom);
            for (int i = 0; i < entityList.size(); i++) {
                for (int j = i + 1; j < entityList.size(); j++) {
                    OWLEntity e1 = entityList.get(i);
                    OWLEntity e2 = entityList.get(j);
                    
                    graph.get(e1).add(e2);
                    graph.get(e2).add(e1);
                }
            }
        }
        
        return graph;
    }
    
    private List<Set<OWLEntity>> findConnectedComponents(Map<OWLEntity, Set<OWLEntity>> graph) {
        List<Set<OWLEntity>> components = new ArrayList<>();
        Set<OWLEntity> visited = new HashSet<>();
        
        for (OWLEntity entity : graph.keySet()) {
            if (!visited.contains(entity)) {
                Set<OWLEntity> component = new HashSet<>();
                dfs(entity, graph, visited, component);
                
                if (!component.isEmpty()) {
                    components.add(component);
                }
            }
        }
        
        return components;
    }
    
    private void dfs(OWLEntity entity, Map<OWLEntity, Set<OWLEntity>> graph, 
                     Set<OWLEntity> visited, Set<OWLEntity> component) {
        if (visited.contains(entity)) {
            return;
        }
        
        visited.add(entity);
        component.add(entity);
        
        Set<OWLEntity> neighbors = graph.get(entity);
        if (neighbors != null) {
            for (OWLEntity neighbor : neighbors) {
                if (!visited.contains(neighbor)) {
                    dfs(neighbor, graph, visited, component);
                }
            }
        }
    }
}
