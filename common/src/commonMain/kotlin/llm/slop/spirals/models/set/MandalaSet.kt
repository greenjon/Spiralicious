package llm.slop.spirals.models.set

import llm.slop.spirals.models.mandala.MandalaPatch

/**
 * A collection of mandalas that can be selected from.
 */
data class MandalaSet(
    val id: String,
    val name: String,
    val mandalas: List<MandalaPatch>,
    val selectionPolicy: SelectionPolicy
) {
    /**
     * Creates a deep copy of the mandala set.
     */
    fun deepCopy(): MandalaSet {
        return this.copy(
            mandalas = this.mandalas.map { it.deepCopy() }
        )
    }
}
