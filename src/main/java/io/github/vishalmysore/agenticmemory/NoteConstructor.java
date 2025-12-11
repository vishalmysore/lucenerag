package io.github.vishalmysore.agenticmemory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM-driven note constructor that generates structured notes from raw documents.
 * Uses OpenAI API to extract summaries, entities, and metadata.
 */
public class NoteConstructor implements AutoCloseable {
    private static final String DEFAULT_MODEL = "gpt-4";
    private static final int DEFAULT_MAX_TOKENS = 500;
    
    private final OkHttpClient client;
    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final Gson gson;

    public NoteConstructor(String apiKey) {
        this(apiKey, "https://api.openai.com/v1/chat/completions", DEFAULT_MODEL);
    }

    public NoteConstructor(String apiKey, String apiUrl, String model) {
        this.client = new OkHttpClient();
        this.apiKey = Objects.requireNonNull(apiKey, "API key cannot be null");
        this.apiUrl = apiUrl;
        this.model = model;
        this.gson = new Gson();
    }

    /**
     * Construct a note from raw document content using LLM
     */
    public Note constructNote(String noteId, String documentId, String content) throws IOException {
        return constructNote(noteId, documentId, content, new HashMap<>());
    }

    /**
     * Construct a note from raw document content with additional metadata
     */
    public Note constructNote(String noteId, String documentId, String content, Map<String, String> additionalMetadata) throws IOException {
        // Generate summary
        String summary = generateSummary(content);
        
        // Extract entities
        List<String> entities = extractEntities(content);
        
        // Extract key topics/tags
        List<String> tags = extractTags(content);
        
        // Calculate importance score
        double importanceScore = calculateImportanceScore(content, entities, tags);

        Note.Builder builder = new Note.Builder(noteId, content)
                .summary(summary)
                .addSourceDocument(documentId)
                .entities(entities)
                .tags(tags)
                .importanceScore(importanceScore)
                .metadata(additionalMetadata);

        return builder.build();
    }

    /**
     * Generate a concise summary using LLM
     */
    private String generateSummary(String content) throws IOException {
        String prompt = "Provide a concise 1-2 sentence summary of the following text:\n\n" + content;
        return callLLM(prompt, 100);
    }

    /**
     * Extract named entities using LLM
     */
    private List<String> extractEntities(String content) throws IOException {
        String prompt = "Extract all named entities (people, organizations, locations, concepts) from the following text. " +
                       "Return only the entities as a comma-separated list:\n\n" + content;
        String response = callLLM(prompt, 150);
        
        // Parse comma-separated entities
        List<String> entities = new ArrayList<>();
        if (response != null && !response.isEmpty()) {
            String[] parts = response.split(",");
            for (String part : parts) {
                String entity = part.trim();
                if (!entity.isEmpty()) {
                    entities.add(entity);
                }
            }
        }
        return entities;
    }

    /**
     * Extract key topics/tags using LLM
     */
    private List<String> extractTags(String content) throws IOException {
        String prompt = "Extract 3-5 key topics or tags from the following text. " +
                       "Return only the tags as a comma-separated list:\n\n" + content;
        String response = callLLM(prompt, 100);
        
        List<String> tags = new ArrayList<>();
        if (response != null && !response.isEmpty()) {
            String[] parts = response.split(",");
            for (String part : parts) {
                String tag = part.trim();
                if (!tag.isEmpty()) {
                    tags.add(tag);
                }
            }
        }
        return tags;
    }

    /**
     * Calculate importance score based on content characteristics
     */
    private double calculateImportanceScore(String content, List<String> entities, List<String> tags) {
        double score = 0.5; // Base score
        
        // Increase score based on content length (longer = potentially more important)
        int wordCount = content.split("\\s+").length;
        if (wordCount > 500) score += 0.1;
        if (wordCount > 1000) score += 0.1;
        
        // Increase score based on entity richness
        if (entities.size() > 3) score += 0.1;
        if (entities.size() > 6) score += 0.1;
        
        // Increase score based on topic diversity
        if (tags.size() > 3) score += 0.1;
        
        return Math.min(score, 1.0);
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
        requestBody.addProperty("temperature", 0.3); // Lower temperature for more focused responses

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
                throw new IOException("LLM API call failed: " + response.code() + " " + response.message());
            }

            String responseBody = response.body().string();
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            
            return json.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString().trim();
        }
    }

    /**
     * Construct multiple notes from a document with automatic chunking
     */
    public List<Note> constructNotesFromDocument(String baseNoteId, String documentId, String content, int chunkSize) throws IOException {
        List<Note> notes = new ArrayList<>();
        
        // Split content into chunks
        String[] words = content.split("\\s+");
        int numChunks = (int) Math.ceil((double) words.length / chunkSize);
        
        for (int i = 0; i < numChunks; i++) {
            int start = i * chunkSize;
            int end = Math.min(start + chunkSize, words.length);
            
            String chunk = String.join(" ", Arrays.copyOfRange(words, start, end));
            String noteId = baseNoteId + "_chunk_" + i;
            
            Note note = constructNote(noteId, documentId, chunk);
            notes.add(note);
        }
        
        return notes;
    }

    @Override
    public void close() {
        // OkHttpClient doesn't need explicit closing, but we implement AutoCloseable for consistency
    }
}
