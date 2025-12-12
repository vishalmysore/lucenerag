package io.github.vishalmysore.agenticmemory;

import io.github.vishalmysore.rag.RAGService;
import io.github.vishalmysore.rag.SearchResult;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages storage and retrieval of links in the Zettelkasten system.
 * 
 * Links are persisted as special documents in the RAG index with metadata
 * that enables efficient querying by source, target, or link type.
 */
public class LinkStorage {
    private final RAGService ragService;
    private final Map<String, List<Link>> linkCache;
    private static final String LINK_PREFIX = "link_";
    private static final String LINK_TYPE = "zettel_link";

    public LinkStorage(RAGService ragService) {
        this.ragService = ragService;
        this.linkCache = new HashMap<>();
    }

    /**
     * Store a link in the RAG index
     */
    public void storeLink(Link link) throws IOException {
        Map<String, String> linkMetadata = new HashMap<>();
        linkMetadata.put("type", LINK_TYPE);
        linkMetadata.put("source_id", link.getSourceNoteId());
        linkMetadata.put("target_id", link.getTargetNoteId());
        linkMetadata.put("link_type", link.getType().toString());
        linkMetadata.put("strength", String.valueOf(link.getStrength()));
        linkMetadata.put("description", link.getDescription());
        linkMetadata.put("created_at", String.valueOf(System.currentTimeMillis()));

        String linkId = generateLinkId(link.getSourceNoteId(), link.getTargetNoteId());
        String linkContent = formatLinkContent(link);

        ragService.addDocument(linkId, linkContent, linkMetadata);

        // Update cache
        updateCache(link);
    }

    /**
     * Store multiple links in batch
     */
    public void storeLinks(List<Link> links) throws IOException {
        for (Link link : links) {
            storeLink(link);
        }
    }

    /**
     * Get all links for a specific note (both incoming and outgoing)
     */
    public List<Link> getLinksForNote(String noteId) throws IOException {
        // Check cache first
        if (linkCache.containsKey(noteId)) {
            return new ArrayList<>(linkCache.get(noteId));
        }

        List<Link> links = new ArrayList<>();

        // Query for outgoing links
        String outgoingQuery = String.format("source_id:%s", noteId);
        List<SearchResult> outgoingResults = ragService.keywordSearch(outgoingQuery, 1000);
        links.addAll(parseLinksFromResults(outgoingResults));

        // Query for incoming links
        String incomingQuery = String.format("target_id:%s", noteId);
        List<SearchResult> incomingResults = ragService.keywordSearch(incomingQuery, 1000);
        links.addAll(parseLinksFromResults(incomingResults));

        // Cache results
        linkCache.put(noteId, links);

        return links;
    }

    /**
     * Get outgoing links from a note
     */
    public List<Link> getOutgoingLinks(String noteId) throws IOException {
        String query = String.format("source_id:%s", noteId);
        List<SearchResult> results = ragService.keywordSearch(query, 1000);
        return parseLinksFromResults(results);
    }

    /**
     * Get incoming links to a note
     */
    public List<Link> getIncomingLinks(String noteId) throws IOException {
        String query = String.format("target_id:%s", noteId);
        List<SearchResult> results = ragService.keywordSearch(query, 1000);
        return parseLinksFromResults(results);
    }

    /**
     * Get links by type
     */
    public List<Link> getLinksByType(LinkType type) throws IOException {
        String query = String.format("link_type:%s", type.name());
        List<SearchResult> results = ragService.keywordSearch(query, 1000);
        return parseLinksFromResults(results);
    }

    /**
     * Get links between two specific notes
     */
    public List<Link> getLinksBetween(String sourceId, String targetId) throws IOException {
        List<Link> allLinks = getOutgoingLinks(sourceId);
        return allLinks.stream()
            .filter(link -> link.getTargetNoteId().equals(targetId))
            .collect(Collectors.toList());
    }

    /**
     * Check if a link exists between two notes
     */
    public boolean linkExists(String sourceId, String targetId) throws IOException {
        return !getLinksBetween(sourceId, targetId).isEmpty();
    }

    /**
     * Delete a specific link
     */
    public void deleteLink(String sourceId, String targetId) {
        String linkId = generateLinkId(sourceId, targetId);
        // Note: RAGService would need a deleteDocument method
        // For now, we just clear from cache
        invalidateCache(sourceId);
        invalidateCache(targetId);
    }

    /**
     * Get all links with strength above threshold
     */
    public List<Link> getStrongLinks(double minStrength) {
        // This would require full scan - consider optimization
        List<Link> allLinks = new ArrayList<>();
        for (List<Link> links : linkCache.values()) {
            allLinks.addAll(links.stream()
                .filter(link -> link.getStrength() >= minStrength)
                .collect(Collectors.toList()));
        }
        return allLinks;
    }

    /**
     * Clear cache for a specific note
     */
    public void invalidateCache(String noteId) {
        linkCache.remove(noteId);
    }

    /**
     * Clear entire cache
     */
    public void clearCache() {
        linkCache.clear();
    }

    private String generateLinkId(String sourceId, String targetId) {
        return LINK_PREFIX + sourceId + "_to_" + targetId;
    }

    private String formatLinkContent(Link link) {
        return String.format("Link from %s to %s: %s [%s] (strength: %.2f)",
            link.getSourceNoteId(),
            link.getTargetNoteId(),
            link.getType(),
            link.getDescription(),
            link.getStrength());
    }

    private List<Link> parseLinksFromResults(List<SearchResult> results) {
        List<Link> links = new ArrayList<>();

        for (SearchResult result : results) {
            try {
                Link link = parseLinkFromResult(result);
                if (link != null) {
                    links.add(link);
                }
            } catch (Exception e) {
                // Log error but continue processing
                System.err.println("Error parsing link: " + e.getMessage());
            }
        }

        return links;
    }

    private Link parseLinkFromResult(SearchResult result) {
        Map<String, String> metadata = result.getMetadata();

        if (!LINK_TYPE.equals(metadata.get("type"))) {
            return null;
        }

        String sourceId = metadata.get("source_id");
        String targetId = metadata.get("target_id");
        String linkTypeStr = metadata.get("link_type");
        String strengthStr = metadata.get("strength");
        String description = metadata.get("description");

        if (sourceId == null || targetId == null || linkTypeStr == null) {
            return null;
        }

        LinkType linkType;
        try {
            linkType = LinkType.valueOf(linkTypeStr);
        } catch (IllegalArgumentException e) {
            linkType = LinkType.CUSTOM;
        }

        double strength = 0.5;
        if (strengthStr != null) {
            try {
                strength = Double.parseDouble(strengthStr);
            } catch (NumberFormatException e) {
                // Use default
            }
        }

        return new Link.Builder(sourceId, targetId, linkType)
            .strength(strength)
            .description(description != null ? description : "")
            .build();
    }

    private void updateCache(Link link) {
        String sourceId = link.getSourceNoteId();
        String targetId = link.getTargetNoteId();

        // Add to source's outgoing links
        linkCache.computeIfAbsent(sourceId, k -> new ArrayList<>()).add(link);

        // Create reverse link for target's incoming links
        Link reverseLink = new Link.Builder(targetId, sourceId, link.getType())
            .strength(link.getStrength())
            .description(link.getDescription())
            .build();
        linkCache.computeIfAbsent(targetId, k -> new ArrayList<>()).add(reverseLink);
    }

    /**
     * Get statistics about stored links
     */
    public LinkStatistics getStatistics() {
        int totalLinks = 0;
        Map<LinkType, Integer> linkTypeCounts = new HashMap<>();
        double totalStrength = 0.0;

        for (List<Link> links : linkCache.values()) {
            totalLinks += links.size();
            for (Link link : links) {
                linkTypeCounts.merge(link.getType(), 1, Integer::sum);
                totalStrength += link.getStrength();
            }
        }

        double avgStrength = totalLinks > 0 ? totalStrength / totalLinks : 0.0;

        return new LinkStatistics(totalLinks, linkTypeCounts, avgStrength);
    }

    public static class LinkStatistics {
        private final int totalLinks;
        private final Map<LinkType, Integer> linkTypeCounts;
        private final double averageStrength;

        public LinkStatistics(int totalLinks, Map<LinkType, Integer> linkTypeCounts, double averageStrength) {
            this.totalLinks = totalLinks;
            this.linkTypeCounts = linkTypeCounts;
            this.averageStrength = averageStrength;
        }

        public int getTotalLinks() {
            return totalLinks;
        }

        public Map<LinkType, Integer> getLinkTypeCounts() {
            return linkTypeCounts;
        }

        public double getAverageStrength() {
            return averageStrength;
        }

        @Override
        public String toString() {
            return String.format("LinkStatistics[total=%d, avgStrength=%.2f, types=%s]",
                totalLinks, averageStrength, linkTypeCounts);
        }
    }
}
