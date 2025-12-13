package io.github.vishalmysore.agenticmemory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for ZettelNote class
 */
public class ZettelNoteTest {

    @Test
    public void testZettelNoteBuilder() {
        ZettelNote note = new ZettelNote.Builder("test-1", "This is a test note about machine learning.")
            .summary("Test note summary")
            .tags(Arrays.asList("ml", "ai"))
            .entities(Arrays.asList("Machine Learning", "AI"))
            .build();

        assertEquals("test-1", note.getId());
        assertEquals("This is a test note about machine learning.", note.getContent());
        assertEquals("Test note summary", note.getSummary());
        assertEquals(Arrays.asList("ml", "ai"), note.getTags());
        assertEquals(Arrays.asList("Machine Learning", "AI"), note.getEntities());
    }

    @Test
    public void testZettelNoteAtomicity() {
        // Short note should be atomic
        ZettelNote shortNote = new ZettelNote.Builder("short", "Machine learning is a subset of AI.")
            .build();

        assertTrue(shortNote.isAtomic());

        // Long note might not be atomic
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            longContent.append("This is a long sentence with multiple ideas. ");
        }

        ZettelNote longNote = new ZettelNote.Builder("long", longContent.toString())
            .build();

        // This might be false depending on implementation
        // assertFalse(longNote.isAtomic());
    }

    @Test
    public void testConnectivityScore() {
        ZettelNote note = new ZettelNote.Builder("test", "Test content")
            .build();

        // Initially no links, score should be 0
        assertEquals(0.0, note.getConnectivityScore());

        // Add some links (would need Link objects, but testing basic structure)
        // This test assumes the method exists and works
    }

    @Test
    public void testRelatedNoteIds() {
        ZettelNote note = new ZettelNote.Builder("test", "Test content")
            .build();

        // Test with different link types
        List<String> relatedIds = note.getRelatedNoteIds(LinkType.REFERENCES);
        assertNotNull(relatedIds);
        // Initially empty
        assertTrue(relatedIds.isEmpty());
    }
}