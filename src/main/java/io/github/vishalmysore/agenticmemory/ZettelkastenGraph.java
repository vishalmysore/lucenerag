package io.github.vishalmysore.agenticmemory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Graph data structure for managing and traversing Zettelkasten notes.
 * 
 * Provides algorithms for:
 * - Graph traversal (BFS, DFS)
 * - Path finding (shortest path)
 * - Connectivity analysis
 * - Note clustering and communities
 */
public class ZettelkastenGraph {
    private final Map<String, ZettelNote> notes;
    private final LinkStorage linkStorage;
    private final Map<String, List<String>> adjacencyList;

    public ZettelkastenGraph(LinkStorage linkStorage) {
        this.notes = new HashMap<>();
        this.linkStorage = linkStorage;
        this.adjacencyList = new HashMap<>();
    }

    /**
     * Add a note to the graph
     */
    public void addNote(ZettelNote note) {
        notes.put(note.getId(), note);
        adjacencyList.putIfAbsent(note.getId(), new ArrayList<>());
    }

    /**
     * Get a note by ID
     */
    public ZettelNote getNote(String noteId) {
        return notes.get(noteId);
    }

    /**
     * Get all notes in the graph
     */
    public Collection<ZettelNote> getAllNotes() {
        return new ArrayList<>(notes.values());
    }

    /**
     * Add a link between two notes
     */
    public void addLink(Link link) throws IOException {
        String sourceId = link.getSourceNoteId();
        String targetId = link.getTargetNoteId();

        // Update adjacency list
        adjacencyList.computeIfAbsent(sourceId, k -> new ArrayList<>()).add(targetId);
        adjacencyList.computeIfAbsent(targetId, k -> new ArrayList<>()).add(sourceId);

        // Update note objects
        ZettelNote source = notes.get(sourceId);
        ZettelNote target = notes.get(targetId);

        if (source != null) {
            source.addOutgoingLink(link);
        }
        if (target != null) {
            Link reverseLink = new Link.Builder(targetId, sourceId, link.getType())
                .strength(link.getStrength())
                .description(link.getDescription())
                .build();
            target.addIncomingLink(reverseLink);
        }

        // Persist link
        linkStorage.storeLink(link);
    }

    /**
     * Traverse graph from a starting note using BFS
     * @param startNoteId Starting note ID
     * @param maxDepth Maximum depth to traverse
     * @return List of notes in traversal order
     */
    public List<ZettelNote> traverseFromNote(String startNoteId, int maxDepth) {
        if (!notes.containsKey(startNoteId)) {
            return Collections.emptyList();
        }

        List<ZettelNote> visited = new ArrayList<>();
        Queue<NodeDepth> queue = new LinkedList<>();
        Set<String> processed = new HashSet<>();

        queue.add(new NodeDepth(startNoteId, 0));

        while (!queue.isEmpty()) {
            NodeDepth current = queue.poll();
            String currentId = current.nodeId;
            int currentDepth = current.depth;

            if (processed.contains(currentId) || currentDepth > maxDepth) {
                continue;
            }

            processed.add(currentId);
            ZettelNote note = notes.get(currentId);
            if (note != null) {
                visited.add(note);
            }

            // Add neighbors to queue
            if (currentDepth < maxDepth) {
                List<String> neighbors = adjacencyList.getOrDefault(currentId, Collections.emptyList());
                for (String neighborId : neighbors) {
                    if (!processed.contains(neighborId)) {
                        queue.add(new NodeDepth(neighborId, currentDepth + 1));
                    }
                }
            }
        }

        return visited;
    }

    /**
     * Traverse graph using DFS
     */
    public List<ZettelNote> traverseDFS(String startNoteId, int maxDepth) {
        if (!notes.containsKey(startNoteId)) {
            return Collections.emptyList();
        }

        List<ZettelNote> visited = new ArrayList<>();
        Set<String> processed = new HashSet<>();

        traverseDFSHelper(startNoteId, 0, maxDepth, visited, processed);

        return visited;
    }

    private void traverseDFSHelper(String nodeId, int depth, int maxDepth,
                                   List<ZettelNote> visited, Set<String> processed) {
        if (depth > maxDepth || processed.contains(nodeId)) {
            return;
        }

        processed.add(nodeId);
        ZettelNote note = notes.get(nodeId);
        if (note != null) {
            visited.add(note);
        }

        List<String> neighbors = adjacencyList.getOrDefault(nodeId, Collections.emptyList());
        for (String neighborId : neighbors) {
            traverseDFSHelper(neighborId, depth + 1, maxDepth, visited, processed);
        }
    }

    /**
     * Find shortest path between two notes using BFS
     */
    public List<ZettelNote> findPath(String startId, String endId) {
        if (!notes.containsKey(startId) || !notes.containsKey(endId)) {
            return Collections.emptyList();
        }

        if (startId.equals(endId)) {
            return Collections.singletonList(notes.get(startId));
        }

        Queue<String> queue = new LinkedList<>();
        Map<String, String> parent = new HashMap<>();
        Set<String> visited = new HashSet<>();

        queue.add(startId);
        visited.add(startId);
        parent.put(startId, null);

        while (!queue.isEmpty()) {
            String current = queue.poll();

            if (current.equals(endId)) {
                return reconstructPath(parent, startId, endId);
            }

            List<String> neighbors = adjacencyList.getOrDefault(current, Collections.emptyList());
            for (String neighbor : neighbors) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    parent.put(neighbor, current);
                    queue.add(neighbor);
                }
            }
        }

        return Collections.emptyList(); // No path found
    }

    private List<ZettelNote> reconstructPath(Map<String, String> parent, String startId, String endId) {
        List<ZettelNote> path = new ArrayList<>();
        String current = endId;

        while (current != null) {
            path.add(0, notes.get(current));
            current = parent.get(current);
        }

        return path;
    }

    /**
     * Find all paths between two notes (up to maxPaths)
     */
    public List<List<ZettelNote>> findAllPaths(String startId, String endId, int maxPaths) {
        List<List<ZettelNote>> allPaths = new ArrayList<>();
        List<String> currentPath = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        findAllPathsHelper(startId, endId, currentPath, visited, allPaths, maxPaths);

        return allPaths;
    }

    private void findAllPathsHelper(String current, String end, List<String> currentPath,
                                    Set<String> visited, List<List<ZettelNote>> allPaths, int maxPaths) {
        if (allPaths.size() >= maxPaths) {
            return;
        }

        visited.add(current);
        currentPath.add(current);

        if (current.equals(end)) {
            List<ZettelNote> path = currentPath.stream()
                .map(notes::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            allPaths.add(path);
        } else {
            List<String> neighbors = adjacencyList.getOrDefault(current, Collections.emptyList());
            for (String neighbor : neighbors) {
                if (!visited.contains(neighbor)) {
                    findAllPathsHelper(neighbor, end, currentPath, visited, allPaths, maxPaths);
                }
            }
        }

        currentPath.remove(currentPath.size() - 1);
        visited.remove(current);
    }

    /**
     * Get immediate neighbors of a note
     */
    public List<ZettelNote> getNeighbors(String noteId) {
        List<String> neighborIds = adjacencyList.getOrDefault(noteId, Collections.emptyList());
        return neighborIds.stream()
            .map(notes::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * Get neighbors connected by specific link type
     */
    public List<ZettelNote> getNeighborsByLinkType(String noteId, LinkType linkType) {
        ZettelNote note = notes.get(noteId);
        if (note == null) {
            return Collections.emptyList();
        }

        List<String> neighborIds = note.getRelatedNoteIds(linkType);
        return neighborIds.stream()
            .map(notes::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * Find notes within N hops of the starting note
     */
    public List<ZettelNote> findNotesWithinHops(String startId, int hops) {
        return traverseFromNote(startId, hops);
    }

    /**
     * Get the most connected note (highest degree centrality)
     */
    public ZettelNote getMostConnectedNote() {
        return notes.values().stream()
            .max(Comparator.comparingInt(note ->
                adjacencyList.getOrDefault(note.getId(), Collections.emptyList()).size()))
            .orElse(null);
    }

    /**
     * Get notes sorted by connectivity
     */
    public List<ZettelNote> getMostConnectedNotes(int topN) {
        return notes.values().stream()
            .sorted(Comparator.comparingInt((ZettelNote note) ->
                adjacencyList.getOrDefault(note.getId(), Collections.emptyList()).size())
                .reversed())
            .limit(topN)
            .collect(Collectors.toList());
    }

    /**
     * Calculate degree centrality for all notes
     */
    public Map<String, Integer> calculateDegreeCentrality() {
        Map<String, Integer> centrality = new HashMap<>();
        for (String noteId : notes.keySet()) {
            int degree = adjacencyList.getOrDefault(noteId, Collections.emptyList()).size();
            centrality.put(noteId, degree);
        }
        return centrality;
    }

    /**
     * Find connected components (clusters) in the graph
     */
    public List<List<ZettelNote>> findConnectedComponents() {
        List<List<ZettelNote>> components = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        for (String noteId : notes.keySet()) {
            if (!visited.contains(noteId)) {
                List<ZettelNote> component = new ArrayList<>();
                exploreComponent(noteId, visited, component);
                components.add(component);
            }
        }

        return components;
    }

    private void exploreComponent(String nodeId, Set<String> visited, List<ZettelNote> component) {
        if (visited.contains(nodeId)) {
            return;
        }

        visited.add(nodeId);
        ZettelNote note = notes.get(nodeId);
        if (note != null) {
            component.add(note);
        }

        List<String> neighbors = adjacencyList.getOrDefault(nodeId, Collections.emptyList());
        for (String neighbor : neighbors) {
            exploreComponent(neighbor, visited, component);
        }
    }

    /**
     * Find bridge notes (notes whose removal would disconnect the graph)
     */
    public List<ZettelNote> findBridgeNotes() {
        List<ZettelNote> bridges = new ArrayList<>();
        int originalComponents = findConnectedComponents().size();

        for (String noteId : notes.keySet()) {
            // Temporarily remove note
            List<String> neighbors = adjacencyList.remove(noteId);

            // Check if components increased
            int newComponents = findConnectedComponents().size();
            if (newComponents > originalComponents) {
                bridges.add(notes.get(noteId));
            }

            // Restore note
            adjacencyList.put(noteId, neighbors);
        }

        return bridges;
    }

    /**
     * Get graph statistics
     */
    public GraphStatistics getStatistics() {
        int noteCount = notes.size();
        int linkCount = adjacencyList.values().stream()
            .mapToInt(List::size)
            .sum() / 2; // Divide by 2 for undirected edges

        double avgDegree = noteCount > 0 ? (double) linkCount * 2 / noteCount : 0.0;

        int maxDegree = adjacencyList.values().stream()
            .mapToInt(List::size)
            .max()
            .orElse(0);

        int componentCount = findConnectedComponents().size();

        return new GraphStatistics(noteCount, linkCount, avgDegree, maxDegree, componentCount);
    }

    private static class NodeDepth {
        String nodeId;
        int depth;

        NodeDepth(String nodeId, int depth) {
            this.nodeId = nodeId;
            this.depth = depth;
        }
    }

    public static class GraphStatistics {
        private final int noteCount;
        private final int linkCount;
        private final double averageDegree;
        private final int maxDegree;
        private final int connectedComponents;

        public GraphStatistics(int noteCount, int linkCount, double averageDegree,
                              int maxDegree, int connectedComponents) {
            this.noteCount = noteCount;
            this.linkCount = linkCount;
            this.averageDegree = averageDegree;
            this.maxDegree = maxDegree;
            this.connectedComponents = connectedComponents;
        }

        public int getNoteCount() {
            return noteCount;
        }

        public int getLinkCount() {
            return linkCount;
        }

        public double getAverageDegree() {
            return averageDegree;
        }

        public int getMaxDegree() {
            return maxDegree;
        }

        public int getConnectedComponents() {
            return connectedComponents;
        }

        @Override
        public String toString() {
            return String.format("GraphStatistics[notes=%d, links=%d, avgDegree=%.2f, maxDegree=%d, components=%d]",
                noteCount, linkCount, averageDegree, maxDegree, connectedComponents);
        }
    }
}
