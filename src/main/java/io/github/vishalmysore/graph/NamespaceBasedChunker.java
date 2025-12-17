package io.github.vishalmysore.graph;

import org.semanticweb.owlapi.model.*;

import java.util.*;
import java.util.logging.Logger;

/**
 * Chunks an OWL ontology based on namespace prefixes.
 * Groups axioms that share the same namespace URI into separate chunks.
 */
public class NamespaceBasedChunker {
    private static final Logger LOGGER = Logger.getLogger(NamespaceBasedChunker.class.getName());
    
    public List<OWLChunk> chunk(OWLOntology ontology) {
        List<OWLChunk> chunks = new ArrayList<>();
        Map<String, Set<OWLAxiom>> namespaceMap = new HashMap<>();
        
        // Group axioms by namespace
        for (OWLAxiom axiom : ontology.getAxioms()) {
            Set<String> namespaces = extractNamespaces(axiom);
            
            // If axiom has multiple namespaces, assign to the most specific one
            String primaryNamespace = selectPrimaryNamespace(namespaces);
            
            namespaceMap.computeIfAbsent(primaryNamespace, k -> new HashSet<>()).add(axiom);
        }
        
        LOGGER.info("Found " + namespaceMap.size() + " different namespaces");
        
        // Create chunks from namespace groups
        int chunkId = 0;
        for (Map.Entry<String, Set<OWLAxiom>> entry : namespaceMap.entrySet()) {
            String namespace = entry.getKey();
            Set<OWLAxiom> axioms = entry.getValue();
            
            String id = String.format("namespace-chunk-%d-%s", chunkId++, 
                getNamespaceShortName(namespace));
            OWLChunk chunk = new OWLChunk(id, axioms, OWLChunkingStrategy.NAMESPACE_BASED);
            chunk.addMetadata("namespace", namespace);
            chunks.add(chunk);
        }
        
        LOGGER.info("Created " + chunks.size() + " namespace-based chunks");
        return chunks;
    }
    
    private Set<String> extractNamespaces(OWLAxiom axiom) {
        Set<String> namespaces = new HashSet<>();
        
        for (OWLEntity entity : axiom.getSignature()) {
            IRI iri = entity.getIRI();
            String namespace = extractNamespace(iri.toString());
            namespaces.add(namespace);
        }
        
        return namespaces.isEmpty() ? Collections.singleton("default") : namespaces;
    }
    
    private String extractNamespace(String iri) {
        // Extract namespace from IRI (everything before the last # or /)
        int hashIndex = iri.lastIndexOf('#');
        int slashIndex = iri.lastIndexOf('/');
        int splitIndex = Math.max(hashIndex, slashIndex);
        
        if (splitIndex > 0) {
            return iri.substring(0, splitIndex + 1);
        }
        return "default";
    }
    
    private String selectPrimaryNamespace(Set<String> namespaces) {
        if (namespaces.isEmpty()) {
            return "default";
        }
        
        // Filter out standard OWL/RDF namespaces if other namespaces exist
        Set<String> filtered = new HashSet<>(namespaces);
        filtered.removeIf(ns -> 
            ns.contains("www.w3.org/2002/07/owl") || 
            ns.contains("www.w3.org/1999/02/22-rdf-syntax-ns") ||
            ns.contains("www.w3.org/2000/01/rdf-schema")
        );
        
        if (!filtered.isEmpty()) {
            return filtered.iterator().next();
        }
        
        return namespaces.iterator().next();
    }
    
    private String getNamespaceShortName(String namespace) {
        if (namespace.equals("default")) {
            return "default";
        }
        
        // Extract a short identifier from the namespace
        String[] parts = namespace.split("[/#]");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (!parts[i].isEmpty()) {
                return parts[i];
            }
        }
        return "ns";
    }
}
