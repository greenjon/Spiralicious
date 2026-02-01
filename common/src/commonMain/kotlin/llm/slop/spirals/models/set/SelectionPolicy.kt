package llm.slop.spirals.models.set

/**
 * Defines how mandalas are selected from a set.
 */
enum class SelectionPolicy {
    /**
     * Selects mandalas in the order they appear in the list.
     */
    SEQUENTIAL,

    /**
     * Selects mandalas randomly from the list.
     */
    RANDOM,

    /**
     * Selects mandalas based on a probability distribution.
     */
    WEIGHTED
}
