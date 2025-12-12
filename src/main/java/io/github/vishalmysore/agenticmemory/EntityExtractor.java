package io.github.vishalmysore.agenticmemory;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts entities from text using Apache OpenNLP and pattern matching.
 * 
 * Identifies:
 * - Persons, organizations, locations (via NER)
 * - Technical terms and concepts (pattern-based)
 * - Key phrases and topics (frequency-based)
 */
public class EntityExtractor {
    private TokenizerME tokenizer;
    private NameFinderME personFinder;
    private NameFinderME orgFinder;
    private NameFinderME locationFinder;
    
    // Pattern for technical terms (CamelCase, acronyms, etc.)
    private static final Pattern TECHNICAL_TERM_PATTERN = 
        Pattern.compile("\\b([A-Z][a-z]+([A-Z][a-z]+)+|[A-Z]{2,})\\b");
    
    // Pattern for quoted terms
    private static final Pattern QUOTED_PATTERN = Pattern.compile("\"([^\"]+)\"");
    
    private boolean nerAvailable = false;

    public EntityExtractor() {
        initializeNER();
    }

    private void initializeNER() {
        try {
            // Load tokenizer
            InputStream tokenizerStream = getClass().getResourceAsStream("/opennlp-models/en-token.bin");
            if (tokenizerStream != null) {
                TokenizerModel tokenizerModel = new TokenizerModel(tokenizerStream);
                tokenizer = new TokenizerME(tokenizerModel);
            }

            // Load person name finder
            InputStream personStream = getClass().getResourceAsStream("/opennlp-models/en-ner-person.bin");
            if (personStream != null) {
                TokenNameFinderModel personModel = new TokenNameFinderModel(personStream);
                personFinder = new NameFinderME(personModel);
            }

            // Load organization name finder
            InputStream orgStream = getClass().getResourceAsStream("/opennlp-models/en-ner-organization.bin");
            if (orgStream != null) {
                TokenNameFinderModel orgModel = new TokenNameFinderModel(orgStream);
                orgFinder = new NameFinderME(orgModel);
            }

            // Load location name finder
            InputStream locationStream = getClass().getResourceAsStream("/opennlp-models/en-ner-location.bin");
            if (locationStream != null) {
                TokenNameFinderModel locationModel = new TokenNameFinderModel(locationStream);
                locationFinder = new NameFinderME(locationModel);
            }

            nerAvailable = (tokenizer != null && personFinder != null);
        } catch (Exception e) {
            System.err.println("NER models not available, using pattern-based extraction only: " + e.getMessage());
            nerAvailable = false;
        }
    }

    /**
     * Extract all entities from text
     */
    public List<String> extract(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> entities = new LinkedHashSet<>();

        // Extract using NER if available
        if (nerAvailable) {
            entities.addAll(extractWithNER(text));
        }

        // Extract technical terms
        entities.addAll(extractTechnicalTerms(text));

        // Extract quoted phrases
        entities.addAll(extractQuotedPhrases(text));

        // Extract key phrases by frequency
        entities.addAll(extractKeyPhrases(text));

        return new ArrayList<>(entities);
    }

    /**
     * Extract entities using OpenNLP NER
     */
    private List<String> extractWithNER(String text) {
        List<String> entities = new ArrayList<>();

        try {
            String[] tokens = tokenizer.tokenize(text);

            // Find persons
            if (personFinder != null) {
                Span[] personSpans = personFinder.find(tokens);
                entities.addAll(spansToStrings(tokens, personSpans));
                personFinder.clearAdaptiveData();
            }

            // Find organizations
            if (orgFinder != null) {
                Span[] orgSpans = orgFinder.find(tokens);
                entities.addAll(spansToStrings(tokens, orgSpans));
                orgFinder.clearAdaptiveData();
            }

            // Find locations
            if (locationFinder != null) {
                Span[] locationSpans = locationFinder.find(tokens);
                entities.addAll(spansToStrings(tokens, locationSpans));
                locationFinder.clearAdaptiveData();
            }
        } catch (Exception e) {
            System.err.println("Error during NER extraction: " + e.getMessage());
        }

        return entities;
    }

    private List<String> spansToStrings(String[] tokens, Span[] spans) {
        List<String> result = new ArrayList<>();
        for (Span span : spans) {
            StringBuilder entity = new StringBuilder();
            for (int i = span.getStart(); i < span.getEnd(); i++) {
                if (entity.length() > 0) {
                    entity.append(" ");
                }
                entity.append(tokens[i]);
            }
            String entityStr = entity.toString().trim();
            if (!entityStr.isEmpty()) {
                result.add(entityStr);
            }
        }
        return result;
    }

    /**
     * Extract technical terms (CamelCase, acronyms)
     */
    private List<String> extractTechnicalTerms(String text) {
        List<String> terms = new ArrayList<>();
        Matcher matcher = TECHNICAL_TERM_PATTERN.matcher(text);

        while (matcher.find()) {
            String term = matcher.group(1);
            if (term.length() >= 3) { // Filter out very short terms
                terms.add(term);
            }
        }

        return terms;
    }

    /**
     * Extract phrases in quotes
     */
    private List<String> extractQuotedPhrases(String text) {
        List<String> phrases = new ArrayList<>();
        Matcher matcher = QUOTED_PATTERN.matcher(text);

        while (matcher.find()) {
            String phrase = matcher.group(1).trim();
            if (!phrase.isEmpty()) {
                phrases.add(phrase);
            }
        }

        return phrases;
    }

    /**
     * Extract key phrases based on frequency and significance
     */
    private List<String> extractKeyPhrases(String text) {
        List<String> keyPhrases = new ArrayList<>();

        // Extract n-grams (2-3 words)
        String[] words = text.toLowerCase()
            .replaceAll("[^a-z0-9\\s]", " ")
            .split("\\s+");

        Map<String, Integer> phraseFreq = new HashMap<>();

        // Extract bigrams
        for (int i = 0; i < words.length - 1; i++) {
            if (isSignificantWord(words[i]) && isSignificantWord(words[i + 1])) {
                String bigram = words[i] + " " + words[i + 1];
                phraseFreq.merge(bigram, 1, Integer::sum);
            }
        }

        // Extract trigrams
        for (int i = 0; i < words.length - 2; i++) {
            if (isSignificantWord(words[i]) && isSignificantWord(words[i + 1]) && isSignificantWord(words[i + 2])) {
                String trigram = words[i] + " " + words[i + 1] + " " + words[i + 2];
                phraseFreq.merge(trigram, 1, Integer::sum);
            }
        }

        // Get top phrases (appearing more than once or capitalized in original)
        phraseFreq.entrySet().stream()
            .filter(e -> e.getValue() > 1 || isCapitalizedInText(e.getKey(), text))
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(10)
            .forEach(e -> keyPhrases.add(e.getKey()));

        return keyPhrases;
    }

    /**
     * Check if word is significant (not a stop word)
     */
    private boolean isSignificantWord(String word) {
        if (word.length() < 3) {
            return false;
        }

        // Common stop words
        Set<String> stopWords = Set.of(
            "the", "and", "for", "are", "but", "not", "you", "all",
            "can", "had", "her", "was", "one", "our", "out", "day",
            "get", "has", "him", "his", "how", "its", "may", "new",
            "now", "old", "see", "two", "who", "boy", "did", "let",
            "put", "say", "she", "too", "use", "this", "that", "with",
            "from", "have", "they", "will", "what", "when", "your",
            "been", "call", "each", "find", "into", "long", "look",
            "make", "many", "more", "than", "then", "them", "were"
        );

        return !stopWords.contains(word);
    }

    /**
     * Check if phrase appears capitalized in original text
     */
    private boolean isCapitalizedInText(String phrase, String text) {
        String[] words = phrase.split(" ");
        if (words.length == 0) {
            return false;
        }

        // Check if the first letter of first word is capitalized in text
        String firstWord = words[0];
        String capitalizedPhrase = firstWord.substring(0, 1).toUpperCase() + firstWord.substring(1);

        for (int i = 1; i < words.length; i++) {
            capitalizedPhrase += " " + words[i];
        }

        return text.contains(capitalizedPhrase);
    }

    /**
     * Extract entities by category
     */
    public Map<String, List<String>> extractByCategory(String text) {
        Map<String, List<String>> categorized = new HashMap<>();

        if (nerAvailable) {
            try {
                String[] tokens = tokenizer.tokenize(text);

                if (personFinder != null) {
                    Span[] personSpans = personFinder.find(tokens);
                    categorized.put("persons", spansToStrings(tokens, personSpans));
                    personFinder.clearAdaptiveData();
                }

                if (orgFinder != null) {
                    Span[] orgSpans = orgFinder.find(tokens);
                    categorized.put("organizations", spansToStrings(tokens, orgSpans));
                    orgFinder.clearAdaptiveData();
                }

                if (locationFinder != null) {
                    Span[] locationSpans = locationFinder.find(tokens);
                    categorized.put("locations", spansToStrings(tokens, locationSpans));
                    locationFinder.clearAdaptiveData();
                }
            } catch (Exception e) {
                System.err.println("Error during categorized extraction: " + e.getMessage());
            }
        }

        categorized.put("technical_terms", extractTechnicalTerms(text));
        categorized.put("quoted_phrases", extractQuotedPhrases(text));
        categorized.put("key_phrases", extractKeyPhrases(text));

        return categorized;
    }

    /**
     * Check if NER is available
     */
    public boolean isNERAvailable() {
        return nerAvailable;
    }
}
