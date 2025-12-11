package io.github.vishalmysore.agenticmemory;

/**
 * Enum representing different types of links between notes in the agentic memory system.
 */
public enum LinkType {
    /**
     * One note supports or provides evidence for another
     */
    SUPPORTS,

    /**
     * One note contradicts or refutes another
     */
    CONTRADICTS,

    /**
     * One note extends or elaborates on another
     */
    EXTENDS,

    /**
     * One note references another
     */
    REFERENCES,

    /**
     * Notes share common entities
     */
    RELATED_ENTITY,

    /**
     * Notes are about similar topics
     */
    SIMILAR_TOPIC,

    /**
     * Temporal relationship (one note happened before/after another)
     */
    TEMPORAL,

    /**
     * Causal relationship (one note describes a cause of another)
     */
    CAUSAL,

    /**
     * Hierarchical relationship (parent-child)
     */
    HIERARCHICAL,

    /**
     * Notes were derived from the same source
     */
    SAME_SOURCE,

    /**
     * Custom/user-defined relationship
     */
    CUSTOM
}
