package io.github.vishalmysore.agenticmemory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.IOException;
import java.util.*;

/**
 * Automatically generates links between notes based on various criteria.
 * Uses both rule-based and LLM-based approaches.
 */
public class LinkGenerator implements AutoCloseable {
    private static final String DEFAULT_MODEL = "gpt-4";
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.7;
    
    private final OkHttpClient client;
    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final Gson gson;
    private final double similarityThreshold;

    public LinkGenerator(String apiKey) {
        this(apiKey, "https://api.openai.com/v1/chat/completions", DEFAULT_MODEL, DEFAULT_SIMILARITY_THRESHOLD);
    }

    public LinkGenerator(String apiKey, String apiUrl, String model, double similarityThreshold) {
        this.client = new OkHttpClient();
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;
        this.gson = new Gson();
        this.similarityThreshold = similarityThreshold;
    }

    /**
     * Generate all possible links for a new note
     */
    public List<Link> generateLinks(Note newNote, Collection<Note> existingNotes) throws IOException {
        List<Link> links = new ArrayList<>();
        
        for (Note existingNote : existingNotes) {
            if (newNote.getId().equals(existingNote.getId())) {
                continue; // Don't link to self
            }
            
            // Try different link generation strategies
            links.addAll(generateEntityBasedLinks(newNote, existingNote));
            links.addAll(generateTopicBasedLinks(newNote, existingNote));
            links.addAll(generateSourceBasedLinks(newNote, existingNote));
            
            // Use LLM for semantic relationship detection (expensive, use sparingly)
            if (shouldUseLLMForPair(newNote, existingNote)) {
                Link llmLink = generateLLMBasedLink(newNote, existingNote);
                if (llmLink != null) {
                    links.add(llmLink);
                }
            }
        }
        
        return links;
    }

    /**
     * Generate links based on shared entities
     */
    private List<Link> generateEntityBasedLinks(Note note1, Note note2) {
        List<Link> links = new ArrayList<>();
        
        Set<String> sharedEntities = new HashSet<>(note1.getEntities());
        sharedEntities.retainAll(note2.getEntities());
        
        if (!sharedEntities.isEmpty()) {
            double strength = calculateEntitySimilarity(note1.getEntities(), note2.getEntities());
            if (strength >= similarityThreshold) {
                Link link = new Link.Builder(note1.getId(), note2.getId(), LinkType.RELATED_ENTITY)
                        .strength(strength)
                        .description("Shares entities: " + String.join(", ", sharedEntities))
                        .addMetadata("shared_entities", String.join(",", sharedEntities))
                        .build();
                links.add(link);
            }
        }
        
        return links;
    }

    /**
     * Generate links based on shared topics/tags
     */
    private List<Link> generateTopicBasedLinks(Note note1, Note note2) {
        List<Link> links = new ArrayList<>();
        
        Set<String> sharedTags = new HashSet<>(note1.getTags());
        sharedTags.retainAll(note2.getTags());
        
        if (!sharedTags.isEmpty()) {
            double strength = calculateTagSimilarity(note1.getTags(), note2.getTags());
            if (strength >= similarityThreshold) {
                Link link = new Link.Builder(note1.getId(), note2.getId(), LinkType.SIMILAR_TOPIC)
                        .strength(strength)
                        .description("Shares topics: " + String.join(", ", sharedTags))
                        .addMetadata("shared_tags", String.join(",", sharedTags))
                        .build();
                links.add(link);
            }
        }
        
        return links;
    }

    /**
     * Generate links based on shared source documents
     */
    private List<Link> generateSourceBasedLinks(Note note1, Note note2) {
        List<Link> links = new ArrayList<>();
        
        Set<String> sharedSources = new HashSet<>(note1.getSourceDocumentIds());
        sharedSources.retainAll(note2.getSourceDocumentIds());
        
        if (!sharedSources.isEmpty()) {
            Link link = new Link.Builder(note1.getId(), note2.getId(), LinkType.SAME_SOURCE)
                    .strength(0.8)
                    .description("Derived from same source document(s)")
                    .addMetadata("shared_sources", String.join(",", sharedSources))
                    .build();
            links.add(link);
        }
        
        return links;
    }

    /**
     * Decide if we should use expensive LLM call for this pair
     */
    private boolean shouldUseLLMForPair(Note note1, Note note2) {
        // Use LLM only if notes have some potential relationship
        // (shared entities, tags, or high importance)
        boolean hasSharedEntities = !Collections.disjoint(note1.getEntities(), note2.getEntities());
        boolean hasSharedTags = !Collections.disjoint(note1.getTags(), note2.getTags());
        boolean bothImportant = note1.getImportanceScore() > 0.7 && note2.getImportanceScore() > 0.7;
        
        return hasSharedEntities || hasSharedTags || bothImportant;
    }

    /**
     * Generate link using LLM to detect semantic relationships
     */
    private Link generateLLMBasedLink(Note note1, Note note2) throws IOException {
        String prompt = String.format(
            "Analyze the relationship between these two notes:\n\n" +
            "Note 1: %s\n\n" +
            "Note 2: %s\n\n" +
            "Determine if there is a semantic relationship. " +
            "Respond with one of: SUPPORTS, CONTRADICTS, EXTENDS, REFERENCES, or NONE. " +
            "If NONE, just respond with 'NONE'. " +
            "Otherwise, respond with the relationship type and a brief explanation separated by '|'.",
            note1.getSummary() != null ? note1.getSummary() : note1.getContent().substring(0, Math.min(200, note1.getContent().length())),
            note2.getSummary() != null ? note2.getSummary() : note2.getContent().substring(0, Math.min(200, note2.getContent().length()))
        );
        
        String response = callLLM(prompt, 100);
        
        if (response == null || response.trim().equalsIgnoreCase("NONE")) {
            return null;
        }
        
        // Parse response
        String[] parts = response.split("\\|", 2);
        String relationshipType = parts[0].trim().toUpperCase();
        String description = parts.length > 1 ? parts[1].trim() : "";
        
        try {
            LinkType linkType = LinkType.valueOf(relationshipType);
            return new Link.Builder(note1.getId(), note2.getId(), linkType)
                    .strength(0.75) // LLM-detected relationships get medium-high strength
                    .description(description)
                    .addMetadata("detected_by", "llm")
                    .build();
        } catch (IllegalArgumentException e) {
            // Invalid link type from LLM
            return null;
        }
    }

    /**
     * Calculate similarity based on shared entities
     */
    private double calculateEntitySimilarity(List<String> entities1, List<String> entities2) {
        if (entities1.isEmpty() && entities2.isEmpty()) return 0.0;
        
        Set<String> set1 = new HashSet<>(entities1);
        Set<String> set2 = new HashSet<>(entities2);
        
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);
        
        return (double) intersection.size() / union.size();
    }

    /**
     * Calculate similarity based on shared tags
     */
    private double calculateTagSimilarity(List<String> tags1, List<String> tags2) {
        if (tags1.isEmpty() && tags2.isEmpty()) return 0.0;
        
        Set<String> set1 = new HashSet<>(tags1);
        Set<String> set2 = new HashSet<>(tags2);
        
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);
        
        return (double) intersection.size() / union.size();
    }

    /**
     * Call OpenAI LLM API
     */
    private String callLLM(String prompt, int maxTokens) throws IOException {
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.add("messages", gson.toJsonTree(Collections.singletonList(message)));
        requestBody.addProperty("max_tokens", maxTokens);
        requestBody.addProperty("temperature", 0.3);

        Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(
                        gson.toJson(requestBody),
                        MediaType.parse("application/json")
                ))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("LLM API call failed: " + response.code());
            }

            String responseBody = response.body().string();
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            
            return json.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString().trim();
        }
    }

    @Override
    public void close() {
        // Cleanup if needed
    }
}
