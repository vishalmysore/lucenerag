package io.github.vishalmysore.agenticmemory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Knowledge graph for managing notes and their relationships in agentic memory.
 * Provides graph traversal, querying, and analysis capabilities.
 */
public class MemoryGraph {
    private final Map<String, Note> notes;
    private final Map<String, Link> links;
    private final Map<String, Set<String>> noteToLinks; // noteId -> linkIds

    public MemoryGraph() {
        this.notes = new HashMap<>();
        this.links = new HashMap<>();
        this.noteToLinks = new HashMap<>();
    }

    /**
     * Add a note to the graph
     */
    public void addNote(Note note) {
        notes.put(note.getId(), note);
        noteToLinks.putIfAbsent(note.getId(), new HashSet<>());
    }

    /**
     * Add a link to the graph
     */
    public void addLink(Link link) {
        links.put(link.getId(), link);
        
        // Update bidirectional index
        noteToLinks.computeIfAbsent(link.getSourceNoteId(), k -> new HashSet<>()).add(link.getId());
        noteToLinks.computeIfAbsent(link.getTargetNoteId(), k -> new HashSet<>()).add(link.getId());
    }

    /**
     * Get a note by ID
     */
    public Note getNote(String noteId) {
        return notes.get(noteId);
    }

    /**
     * Get a link by ID
     */
    public Link getLink(String linkId) {
        return links.get(linkId);
    }

    /**
     * Get all notes
     */
    public Collection<Note> getAllNotes() {
        return Collections.unmodifiableCollection(notes.values());
    }

    /**
     * Get all links
     */
    public Collection<Link> getAllLinks() {
        return Collections.unmodifiableCollection(links.values());
    }

    /**
     * Get all links connected to a note
     */
    public List<Link> getLinksForNote(String noteId) {
        Set<String> linkIds = noteToLinks.get(noteId);
        if (linkIds == null) return Collections.emptyList();
        
        return linkIds.stream()
                .map(links::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Get neighboring notes (directly connected)
     */
    public List<Note> getNeighbors(String noteId) {
        List<Link> noteLinks = getLinksForNote(noteId);
        
        return noteLinks.stream()
                .map(link -> link.getOtherNoteId(noteId))
                .map(notes::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Get neighbors filtered by link type
     */
    public List<Note> getNeighborsByLinkType(String noteId, LinkType linkType) {
        List<Link> noteLinks = getLinksForNote(noteId);
        
        return noteLinks.stream()
                .filter(link -> link.getType() == linkType)
                .map(link -> link.getOtherNoteId(noteId))
                .map(notes::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Multi-hop traversal: find notes reachable within N hops
     */
    public List<Note> findNotesWithinHops(String startNoteId, int maxHops) {
        Set<String> visited = new HashSet<>();
        Queue<NodeWithDistance> queue = new LinkedList<>();
        List<Note> result = new ArrayList<>();
        
        queue.offer(new NodeWithDistance(startNoteId, 0));
        visited.add(startNoteId);
        
        while (!queue.isEmpty()) {
            NodeWithDistance current = queue.poll();
            
            if (current.distance > maxHops) {
                continue;
            }
            
            if (current.distance > 0) { // Don't include start note
                Note note = notes.get(current.noteId);
                if (note != null) {
                    result.add(note);
                }
            }
            
            // Explore neighbors
            for (Note neighbor : getNeighbors(current.noteId)) {
                if (!visited.contains(neighbor.getId())) {
                    visited.add(neighbor.getId());
                    queue.offer(new NodeWithDistance(neighbor.getId(), current.distance + 1));
                }
            }
        }
        
        return result;
    }

    /**
     * Find shortest path between two notes
     */
    public List<Note> findShortestPath(String startNoteId, String endNoteId) {
        if (startNoteId.equals(endNoteId)) {
            return Collections.singletonList(notes.get(startNoteId));
        }
        
        Map<String, String> parentMap = new HashMap<>();
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        
        queue.offer(startNoteId);
        visited.add(startNoteId);
        boolean found = false;
        
        while (!queue.isEmpty() && !found) {
            String current = queue.poll();
            
            for (Note neighbor : getNeighbors(current)) {
                if (!visited.contains(neighbor.getId())) {
                    visited.add(neighbor.getId());
                    parentMap.put(neighbor.getId(), current);
                    queue.offer(neighbor.getId());
                    
                    if (neighbor.getId().equals(endNoteId)) {
                        found = true;
                        break;
                    }
                }
            }
        }
        
        if (!found) {
            return Collections.emptyList(); // No path found
        }
        
        // Reconstruct path
        List<Note> path = new ArrayList<>();
        String current = endNoteId;
        while (current != null) {
            path.add(0, notes.get(current));
            current = parentMap.get(current);
        }
        
        return path;
    }

    /**
     * Find notes by entity
     */
    public List<Note> findNotesByEntity(String entity) {
        return notes.values().stream()
                .filter(note -> note.getEntities().contains(entity))
                .collect(Collectors.toList());
    }

    /**
     * Find notes by tag
     */
    public List<Note> findNotesByTag(String tag) {
        return notes.values().stream()
                .filter(note -> note.getTags().contains(tag))
                .collect(Collectors.toList());
    }

    /**
     * Find most important notes
     */
    public List<Note> findMostImportantNotes(int limit) {
        return notes.values().stream()
                .sorted(Comparator.comparingDouble(Note::getImportanceScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Find most connected notes (highest degree centrality)
     */
    public List<Note> findMostConnectedNotes(int limit) {
        return notes.values().stream()
                .sorted(Comparator.comparingInt((Note n) -> getLinksForNote(n.getId()).size()).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Get graph statistics
     */
    public GraphStatistics getStatistics() {
        return new GraphStatistics(
                notes.size(),
                links.size(),
                calculateAverageDegree(),
                findMostConnectedNotes(1).isEmpty() ? 0 : getLinksForNote(findMostConnectedNotes(1).get(0).getId()).size(),
                countConnectedComponents()
        );
    }

    private double calculateAverageDegree() {
        if (notes.isEmpty()) return 0.0;
        
        int totalDegree = notes.keySet().stream()
                .mapToInt(noteId -> getLinksForNote(noteId).size())
                .sum();
        
        return (double) totalDegree / notes.size();
    }

    private int countConnectedComponents() {
        Set<String> visited = new HashSet<>();
        int components = 0;
        
        for (String noteId : notes.keySet()) {
            if (!visited.contains(noteId)) {
                dfsMarkComponent(noteId, visited);
                components++;
            }
        }
        
        return components;
    }

    private void dfsMarkComponent(String noteId, Set<String> visited) {
        visited.add(noteId);
        for (Note neighbor : getNeighbors(noteId)) {
            if (!visited.contains(neighbor.getId())) {
                dfsMarkComponent(neighbor.getId(), visited);
            }
        }
    }

    /**
     * Helper class for BFS traversal
     */
    private static class NodeWithDistance {
        final String noteId;
        final int distance;

        NodeWithDistance(String noteId, int distance) {
            this.noteId = noteId;
            this.distance = distance;
        }
    }

    /**
     * Graph statistics
     */
    public static class GraphStatistics {
        public final int noteCount;
        public final int linkCount;
        public final double averageDegree;
        public final int maxDegree;
        public final int connectedComponents;

        public GraphStatistics(int noteCount, int linkCount, double averageDegree, int maxDegree, int connectedComponents) {
            this.noteCount = noteCount;
            this.linkCount = linkCount;
            this.averageDegree = averageDegree;
            this.maxDegree = maxDegree;
            this.connectedComponents = connectedComponents;
        }

        @Override
        public String toString() {
            return String.format(
                    "Graph Statistics:\n" +
                    "  Notes: %d\n" +
                    "  Links: %d\n" +
                    "  Average Degree: %.2f\n" +
                    "  Max Degree: %d\n" +
                    "  Connected Components: %d",
                    noteCount, linkCount, averageDegree, maxDegree, connectedComponents
            );
        }
    }
}
