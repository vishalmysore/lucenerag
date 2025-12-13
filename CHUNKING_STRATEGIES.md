# Chunking Strategies Deep Dive

## Why RAG Systems Need Built-in Chunking

Retrieval-Augmented Generation (RAG) systems face a fundamental challenge: **LLMs have context window limits**, yet documents often exceed these limits. Simply stuffing an entire document into a prompt isn't feasible for large corpora. This is where chunking becomes critical.

### The Chunking Challenge

Without proper chunking, RAG systems suffer from:
- **Lost context**: Breaking text at arbitrary boundaries destroys semantic meaning
- **Poor retrieval**: Overly large chunks reduce precision; overly small chunks lose context
- **Inefficient embedding**: Vector databases work best with semantically coherent units
- **Token waste**: Irrelevant information consumes precious context window space

**Built-in chunking strategies** solve these problems by providing intelligent, domain-aware text segmentation that preserves semantic boundaries while optimizing for retrieval performance.

## What is Chunking?

**Chunking** is the process of breaking down large documents into smaller, semantically meaningful segments that can be:
1. **Embedded** as dense vectors for similarity search
2. **Retrieved** independently based on relevance to a query
3. **Fed** to an LLM within its context window constraints

Effective chunking balances two competing goals:
- **Chunks must be small enough** to be precise and fit within embedding model limits (typically 512-8192 tokens)
- **Chunks must be large enough** to contain sufficient context for accurate retrieval and generation

The optimal chunking strategy depends on your document type, retrieval task, and downstream LLM usage.

---

## Framework Overview

The Agentic Memory library includes an extensible chunking framework that allows you to split documents into optimal chunks for semantic search and retrieval.

## Architecture

All chunking strategies are part of the **core framework** in the `io.github.vishalmysore.rag.chunking` package. The example code simply demonstrates how to use these strategies.

### Core Components

1. **`ChunkingStrategy` interface** - Base interface for all chunking strategies
   - `List<String> chunk(String content)` - Splits content into chunks
   - `String getName()` - Returns strategy name
   - `String getDescription()` - Returns strategy description

2. **`RAGService.addDocumentWithChunking()`** - Convenience method for automatic chunking
   ```java
   int chunkCount = rag.addDocumentWithChunking(
       "document_id", 
       content, 
       chunkingStrategy
   );
   ```

## Built-in Strategies

### 1. Sliding Window Chunking
**Package:** `io.github.vishalmysore.rag.chunking.SlidingWindowChunking`

Creates overlapping chunks to preserve context across boundaries.

**Technical Details:**
- Uses a sliding window approach with configurable window size and overlap
- Overlap percentage ensures context continuity between adjacent chunks
- Word-based tokenization with configurable delimiters
- Maintains approximately equal chunk sizes for consistent embedding quality

```java
ChunkingStrategy strategy = new SlidingWindowChunking(150, 30);
// 150 words per chunk, 30 words overlap (20%)
```

**Parameters:**
- `windowSize`: Number of words per chunk (typical: 100-300)
- `overlap`: Number of overlapping words between chunks (typical: 10-20% of window size)

**Best for:** Healthcare records, continuous narratives, patient notes where context flows across boundaries

---

### 2. Adaptive Chunking
**Package:** `io.github.vishalmysore.rag.chunking.AdaptiveChunking`

Respects natural document boundaries while staying within token limits.

**Technical Details:**
- Uses regex pattern matching to identify semantic boundaries (sections, paragraphs, etc.)
- Dynamically adjusts chunk size based on boundary locations
- Enforces min/max token constraints to balance precision and context
- Preserves document structure by never splitting across matched boundaries

```java
ChunkingStrategy strategy = new AdaptiveChunking(
    "(?m)^SECTION \\d+:",  // Boundary pattern (regex)
    800,                    // Min tokens
    1200                    // Max tokens
);
```

**Parameters:**
- `boundaryPattern`: Regex to identify split points (e.g., section headers, paragraph markers)
- `minTokens`: Minimum chunk size to maintain context
- `maxTokens`: Maximum chunk size to fit embedding model limits

**Best for:** Legal contracts, structured documents, policy documents with clear section markers

---

### 3. Entity-Based Chunking
**Package:** `io.github.vishalmysore.rag.chunking.EntityBasedChunking`

Groups sentences by mentioned entities (people, companies, locations).

**Technical Details:**
- Performs Named Entity Recognition (NER) on input text
- Groups consecutive sentences that reference the same entities
- Uses entity co-occurrence analysis to determine chunk boundaries
- Maintains entity context within each chunk for improved retrieval precision

```java
String[] entities = {"Elon Musk", "Tesla", "SpaceX"};
ChunkingStrategy strategy = new EntityBasedChunking(entities);
```

**Parameters:**
- `entities`: Array of entity names to track (people, organizations, locations, etc.)
- Optional: Entity types (PERSON, ORG, LOCATION) for automatic detection

**Algorithm:**
1. Scan text for entity mentions
2. Group sentences with shared entity references
3. Create chunks when entity focus shifts
4. Preserve co-occurrence relationships

**Best for:** News articles, research papers, multi-person biographies, documents with multiple actors

---

### 4. Topic/Theme-Based Chunking
**Package:** `io.github.vishalmysore.rag.chunking.TopicBasedChunking`

Groups content by underlying topics or themes.

**Technical Details:**
- Uses topic modeling or keyword matching to identify thematic shifts
- Regex-based topic boundary detection for structured documents
- Optional: Latent Dirichlet Allocation (LDA) for unsupervised topic discovery
- Creates semantically coherent chunks around single topics

```java
ChunkingStrategy strategy = new TopicBasedChunking(
    "(EDUCATION|CAREER|PATENTS):"
);
```

**Parameters:**
- `topicPattern`: Regex pattern to identify topic boundaries
- Optional: Topic model configuration for unsupervised chunking

**Best for:** Research papers, technical documentation, structured content with explicit topic markers

---

### 5. Hybrid Chunking
**Package:** `io.github.vishalmysore.rag.chunking.HybridChunking`

Combines multiple strategies in a pipeline.

```java
ChunkingStrategy adaptive = new AdaptiveChunking("(?m)^===\\s*$");
ChunkingStrategy topic = new TopicBasedChunking("(INTRO|BODY|CONCLUSION):");

ChunkingStrategy strategy = new HybridChunking(adaptive, topic);
```

**Best for:** Complex documents requiring multi-stage processing

---

### 6. Task-Aware Chunking
**Package:** `io.github.vishalmysore.rag.chunking.TaskAwareChunking`

Adapts chunking based on downstream task (summarization/search/Q&A).

**Technical Details:**
- Implements task-specific heuristics for optimal chunk sizing
- **Summarization**: Small, focused chunks (50-100 tokens) for granular summaries
- **Search**: Medium chunks (200-400 tokens) with function signatures + docstrings
- **Q&A**: Large chunks (500-1000 tokens) preserving full context for accurate answers
- Adjusts overlap and boundaries based on task requirements

```java
// For summarization (small chunks)
ChunkingStrategy strategy = new TaskAwareChunking(TaskType.SUMMARIZATION);

// For search (medium chunks - signatures + docstrings)
ChunkingStrategy strategy = new TaskAwareChunking(TaskType.SEARCH);

// For Q&A (large chunks - full context)
ChunkingStrategy strategy = new TaskAwareChunking(TaskType.QA);
```

**Task Types:**
- `SUMMARIZATION`: Optimized for generating summaries (small, atomic chunks)
- `SEARCH`: Optimized for semantic search (medium chunks with metadata)
- `QA`: Optimized for question answering (large, context-rich chunks)

**Best for:** Code repositories, multi-purpose document processing, systems serving multiple use cases

---

### 7. HTML Tag-Based Chunking
**Package:** `io.github.vishalmysore.rag.chunking.HTMLTagBasedChunking`

Uses HTML/XML structure to determine boundaries.

```java
ChunkingStrategy strategy = new HTMLTagBasedChunking(
    "h2",   // Split on <h2> tags
    true    // Strip HTML tags from output
);
```

**Best for:** Web content, HTML documentation, XML documents

---

### 8. Code-Specific Chunking
**Package:** `io.github.vishalmysore.rag.chunking.CodeSpecificChunking`

Uses language syntax for AST-inspired boundaries.

**Technical Details:**
- Parses code into Abstract Syntax Tree (AST) nodes
- Chunks by logical units: classes, functions, methods, modules
- Preserves code structure and dependencies
- Maintains indentation and scope boundaries
- Supports Python, Java, JavaScript with language-specific parsers

```java
ChunkingStrategy strategy = new CodeSpecificChunking(
    CodeSpecificChunking.Language.PYTHON
);
// Also supports JAVA and JAVASCRIPT
```

**Chunking Units:**
- **Classes**: Complete class definitions with methods
- **Functions/Methods**: Individual functions with docstrings
- **Modules**: Top-level module code
- **Imports**: Grouped import statements

**Best for:** Source code repositories, code documentation, programming tutorials, code search systems

---

### 9. Regex Chunking
**Package:** `io.github.vishalmysore.rag.chunking.RegexChunking`

Uses regex patterns to identify boundaries and group chunks.

**Technical Details:**
- Dual-pattern approach: split pattern + optional grouping pattern
- Split pattern defines chunk boundaries
- Grouping pattern clusters related chunks together
- Supports complex regex with lookaheads, capture groups, and backreferences
- Ideal for highly structured or predictable text formats

```java
ChunkingStrategy strategy = new RegexChunking(
    "\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\]",  // Split pattern
    "(ERROR|WARN|INFO|DEBUG)"                            // Group pattern
);
```

**Parameters:**
- `splitPattern`: Regex to identify chunk boundaries (e.g., timestamps, delimiters)
- `groupPattern`: Optional regex to group related chunks by common attributes (e.g., log levels)

**Use Cases:**
- Log files with timestamps
- Structured data with consistent delimiters
- Documents with predictable formatting

**Best for:** Server logs, structured text, timestamp-based data, CSV-like formats

---

## Creating Custom Strategies

The framework is **fully extensible**. Create your own chunking strategy by implementing `ChunkingStrategy`:

```java
public class MyCustomChunking implements ChunkingStrategy {
    
    @Override
    public List<String> chunk(String content) {
        // Your chunking logic here
        List<String> chunks = new ArrayList<>();
        // ... split content into chunks ...
        return chunks;
    }
    
    @Override
    public String getName() {
        return "My Custom Chunking";
    }
    
    @Override
    public String getDescription() {
        return "Splits content using my custom algorithm";
    }
}
```

Then use it with `RAGService`:

```java
ChunkingStrategy myStrategy = new MyCustomChunking();
rag.addDocumentWithChunking("doc_id", content, myStrategy);
```

See `CustomChunkingExample.java` for a complete example.

## Usage Examples

### Basic Usage

```java
// 1. Create a chunking strategy
ChunkingStrategy strategy = new SlidingWindowChunking(150, 30);

// 2. Use it with RAGService
try (RAGService rag = new RAGService(indexPath, embeddingProvider)) {
    int chunkCount = rag.addDocumentWithChunking(
        "my_document",
        documentContent,
        strategy
    );
    rag.commit();
    
    System.out.println("Created " + chunkCount + " chunks");
}
```

### Manual Chunking

If you prefer to manually control chunk IDs:

```java
ChunkingStrategy strategy = new EntityBasedChunking("Entity1", "Entity2");
List<String> chunks = strategy.chunk(content);

for (int i = 0; i < chunks.size(); i++) {
    String customId = "custom_prefix_" + i;
    rag.addDocument(customId, chunks.get(i));
}
```

## Running the Examples

### Comprehensive Demo (All 9 Strategies)

```bash
export OPENAI_API_KEY="your-key-here"
mvn exec:java -Dexec.mainClass="io.github.vishalmysore.rag.examples.ChunkingStrategiesExample" -q
```

### Custom Strategy Example

```bash
export OPENAI_API_KEY="your-key-here"
mvn exec:java -Dexec.mainClass="io.github.vishalmysore.rag.examples.CustomChunkingExample" -q
```

## Design Principles

1. **Core Framework Integration** - All strategies are part of the core library, not examples
2. **Extensibility** - Simple interface allows custom implementations
3. **Composability** - Strategies can be combined (see `HybridChunking`)
4. **Task-Specific** - Different strategies for different use cases
5. **Zero Configuration** - Sensible defaults with optional customization
6. **Semantic Preservation** - Chunks maintain meaningful boundaries, not arbitrary splits
7. **Performance Optimized** - Efficient algorithms for large-scale document processing

## Technical Considerations

### Chunk Size Guidelines

| Embedding Model | Recommended Chunk Size | Max Tokens |
|----------------|------------------------|------------|
| OpenAI text-embedding-ada-002 | 200-500 words | 8191 |
| OpenAI text-embedding-3-small | 200-500 words | 8191 |
| Sentence Transformers | 100-300 words | 512 |
| Cohere embed-english-v3.0 | 300-600 words | 512 |

### Overlap Strategies

**Why Overlap Matters:**
- Prevents information loss at chunk boundaries
- Improves retrieval recall for queries spanning boundaries
- Typical overlap: 10-20% of chunk size

**Trade-offs:**
- Higher overlap = Better recall, more storage, slower indexing
- Lower overlap = Faster indexing, less storage, potential information loss

### Performance Metrics

Evaluate chunking quality by:
1. **Retrieval Precision**: Do chunks contain relevant information?
2. **Context Completeness**: Do chunks have enough context for the LLM?
3. **Semantic Coherence**: Are chunks semantically self-contained?
4. **Boundary Quality**: Do breaks occur at natural boundaries?

## When to Use Which Strategy

| Use Case | Recommended Strategy |
|----------|---------------------|
| Healthcare records, patient notes | Sliding Window |
| Legal contracts, policies | Adaptive |
| News articles, multi-entity docs | Entity-Based |
| Research papers, structured content | Topic-Based |
| Complex multi-format docs | Hybrid |
| Code repositories | Code-Specific or Task-Aware |
| Web pages, HTML docs | HTML Tag-Based |
| Server logs, timestamped data | Regex |

## API Reference

### ChunkingStrategy Interface

```java
public interface ChunkingStrategy {
    List<String> chunk(String content);
    String getName();
    String getDescription();
}
```

### RAGService Methods

```java
// Add document with automatic chunking
int addDocumentWithChunking(String baseId, String content, ChunkingStrategy strategy)

// Add document with chunking and metadata
int addDocumentWithChunking(String baseId, String content, ChunkingStrategy strategy, 
                           Map<String, String> metadata)
```

---

## Advanced Topics

### Chunking for Multi-Modal Documents

When working with documents containing mixed content (text, tables, images):
1. Use **HTML Tag-Based Chunking** to separate content types
2. Apply different strategies per content type
3. Maintain cross-references between chunks

### Chunking at Scale

For processing millions of documents:
- **Batch processing**: Use parallel streams for chunking
- **Incremental indexing**: Chunk and index in batches
- **Memory management**: Process large documents in streaming mode
- **Caching**: Cache compiled regex patterns and AST parsers

### Dynamic Strategy Selection

Choose strategies programmatically based on document characteristics:

```java
ChunkingStrategy selectStrategy(Document doc) {
    if (doc.isCode()) return new CodeSpecificChunking(detectLanguage(doc));
    if (doc.hasStructure()) return new AdaptiveChunking(detectBoundaries(doc));
    if (doc.hasEntities()) return new EntityBasedChunking(extractEntities(doc));
    return new SlidingWindowChunking(200, 40); // fallback
}
```
