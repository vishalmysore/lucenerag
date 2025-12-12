package io.github.vishalmysore.rag.chunking;

import io.github.vishalmysore.rag.ChunkingStrategy;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chunking strategy optimized for Zettelkasten-style atomic notes.
 * 
 * Splits content into atomic, self-contained ideas that follow the Zettelkasten principle:
 * - One idea per chunk
 * - Self-contained and understandable in isolation
 * - Sized for optimal link discovery (300-500 words)
 * 
 * Uses multiple heuristics to identify atomic boundaries:
 * - Paragraph breaks
 * - Heading markers
 * - Bullet/numbered lists
 * - Logical connectors (therefore, however, etc.)
 */
public class ZettelkastenChunking implements ChunkingStrategy {
    private final int minChunkSize;
    private final int maxChunkSize;
    private final int targetChunkSize;
    
    // Patterns for identifying atomic boundaries
    private static final Pattern HEADING_PATTERN = Pattern.compile("^#{1,6}\\s+.+$", Pattern.MULTILINE);
    private static final Pattern LIST_ITEM_PATTERN = Pattern.compile("^\\s*[-*•]\\s+.+$", Pattern.MULTILINE);
    private static final Pattern NUMBERED_LIST_PATTERN = Pattern.compile("^\\s*\\d+\\.\\s+.+$", Pattern.MULTILINE);
    
    // Logical connectors that indicate idea boundaries
    private static final Set<String> LOGICAL_CONNECTORS = new HashSet<>(Arrays.asList(
        "therefore", "thus", "consequently", "hence", "accordingly",
        "however", "nevertheless", "nonetheless", "yet", "still",
        "furthermore", "moreover", "additionally", "also", "besides",
        "in conclusion", "to summarize", "in summary", "overall"
    ));

    public ZettelkastenChunking() {
        this(100, 500, 300);
    }

    public ZettelkastenChunking(int minChunkSize, int maxChunkSize, int targetChunkSize) {
        this.minChunkSize = minChunkSize;
        this.maxChunkSize = maxChunkSize;
        this.targetChunkSize = targetChunkSize;
    }

    @Override
    public List<String> chunk(String content) {
        if (content == null || content.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> chunks = new ArrayList<>();
        
        // First split by major section markers (double newlines)
        String[] sections = content.split("\n\n+");
        
        StringBuilder currentChunk = new StringBuilder();
        
        for (String section : sections) {
            section = section.trim();
            if (section.isEmpty()) {
                continue;
            }
            
            // Check if this is a heading - start new chunk
            if (isHeading(section)) {
                if (currentChunk.length() > 0) {
                    chunks.addAll(finalizeChunk(currentChunk.toString()));
                    currentChunk = new StringBuilder();
                }
                currentChunk.append(section).append("\n\n");
                continue;
            }
            
            // Check if adding this section would exceed max size
            int potentialSize = currentChunk.length() + section.length();
            
            if (potentialSize > maxChunkSize && currentChunk.length() >= minChunkSize) {
                // Finalize current chunk and start new one
                chunks.addAll(finalizeChunk(currentChunk.toString()));
                currentChunk = new StringBuilder();
            }
            
            // Check if section itself is atomic
            if (isAtomicIdea(section)) {
                if (currentChunk.length() >= minChunkSize) {
                    chunks.addAll(finalizeChunk(currentChunk.toString()));
                    currentChunk = new StringBuilder();
                }
                currentChunk.append(section).append("\n\n");
            } else {
                // Section needs further splitting
                List<String> subIdeas = splitIntoAtomicIdeas(section);
                for (String idea : subIdeas) {
                    if (currentChunk.length() + idea.length() > maxChunkSize &&
                        currentChunk.length() >= minChunkSize) {
                        chunks.addAll(finalizeChunk(currentChunk.toString()));
                        currentChunk = new StringBuilder();
                    }
                    currentChunk.append(idea).append("\n\n");
                }
            }
            
            // Check if current chunk has reached target size
            if (currentChunk.length() >= targetChunkSize) {
                chunks.addAll(finalizeChunk(currentChunk.toString()));
                currentChunk = new StringBuilder();
            }
        }
        
        // Add remaining content
        if (currentChunk.length() > 0) {
            chunks.addAll(finalizeChunk(currentChunk.toString()));
        }
        
        return chunks.stream()
            .filter(chunk -> !chunk.trim().isEmpty())
            .toList();
    }

    /**
     * Check if text represents a single, atomic idea
     */
    private boolean isAtomicIdea(String text) {
        // Count sentences
        String[] sentences = text.split("[.!?]+");
        int sentenceCount = (int) Arrays.stream(sentences)
            .filter(s -> s.trim().length() > 10)
            .count();
        
        // Count words
        int wordCount = text.split("\\s+").length;
        
        // Atomic ideas should be:
        // - 1-5 sentences
        // - 50-500 words
        // - Single focused topic (no major logical connectors)
        return sentenceCount >= 1 && sentenceCount <= 5 &&
               wordCount >= 50 && wordCount <= 500 &&
               !containsMultipleIdeas(text);
    }

    /**
     * Check if text contains multiple distinct ideas
     */
    private boolean containsMultipleIdeas(String text) {
        String lowerText = text.toLowerCase();
        
        // Check for logical connectors that indicate idea transitions
        long connectorCount = LOGICAL_CONNECTORS.stream()
            .filter(connector -> lowerText.contains(connector.toLowerCase()))
            .count();
        
        return connectorCount >= 2;
    }

    /**
     * Split complex text into atomic ideas
     */
    private List<String> splitIntoAtomicIdeas(String text) {
        List<String> ideas = new ArrayList<>();
        
        // Try splitting by sentences first
        String[] sentences = text.split("(?<=[.!?])\\s+");
        
        StringBuilder currentIdea = new StringBuilder();
        
        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.isEmpty()) {
                continue;
            }
            
            // Check if sentence starts with a logical connector
            boolean startsNewIdea = startsWithLogicalConnector(sentence);
            
            if (startsNewIdea && currentIdea.length() >= minChunkSize) {
                ideas.add(currentIdea.toString().trim());
                currentIdea = new StringBuilder();
            }
            
            currentIdea.append(sentence).append(" ");
            
            // Check if we've reached a good stopping point
            if (currentIdea.length() >= targetChunkSize) {
                ideas.add(currentIdea.toString().trim());
                currentIdea = new StringBuilder();
            }
        }
        
        if (currentIdea.length() > 0) {
            ideas.add(currentIdea.toString().trim());
        }
        
        // If no splits were made, return original
        return ideas.isEmpty() ? Collections.singletonList(text) : ideas;
    }

    /**
     * Check if sentence starts with a logical connector
     */
    private boolean startsWithLogicalConnector(String sentence) {
        String lowerSentence = sentence.toLowerCase().trim();
        return LOGICAL_CONNECTORS.stream()
            .anyMatch(connector -> lowerSentence.startsWith(connector.toLowerCase()));
    }

    /**
     * Check if text is a heading
     */
    private boolean isHeading(String text) {
        Matcher headingMatcher = HEADING_PATTERN.matcher(text);
        return headingMatcher.matches() || 
               (text.length() < 100 && text.split("\\s+").length <= 10 && !text.endsWith("."));
    }

    /**
     * Finalize chunk, ensuring it meets quality criteria
     */
    private List<String> finalizeChunk(String chunk) {
        chunk = chunk.trim();
        
        if (chunk.isEmpty() || chunk.length() < minChunkSize) {
            return Collections.emptyList();
        }
        
        // If chunk is too large, split it further
        if (chunk.length() > maxChunkSize) {
            return splitLargeChunk(chunk);
        }
        
        return Collections.singletonList(chunk);
    }

    /**
     * Split overly large chunks
     */
    private List<String> splitLargeChunk(String chunk) {
        List<String> splits = new ArrayList<>();
        
        // Split by paragraphs
        String[] paragraphs = chunk.split("\n\n+");
        StringBuilder current = new StringBuilder();
        
        for (String para : paragraphs) {
            if (current.length() + para.length() > maxChunkSize && current.length() > 0) {
                splits.add(current.toString().trim());
                current = new StringBuilder();
            }
            current.append(para).append("\n\n");
        }
        
        if (current.length() > 0) {
            splits.add(current.toString().trim());
        }
        
        return splits;
    }

    /**
     * Validate that chunk represents an atomic idea
     */
    public boolean validateChunk(String chunk) {
        if (chunk == null || chunk.trim().isEmpty()) {
            return false;
        }
        
        int length = chunk.length();
        if (length < minChunkSize || length > maxChunkSize) {
            return false;
        }
        
        return isAtomicIdea(chunk);
    }

    @Override
    public String getName() {
        return "Zettelkasten Chunking (Atomic Ideas)";
    }

    @Override
    public String getDescription() {
        return String.format(
            "Splits content into atomic, self-contained ideas following Zettelkasten principles. " +
            "Target size: %d-%d words. Each chunk represents a single, linkable concept.",
            minChunkSize, maxChunkSize
        );
    }
}
