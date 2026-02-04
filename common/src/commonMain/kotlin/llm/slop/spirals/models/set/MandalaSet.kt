package llm.slop.spirals.models.set

import kotlinx.serialization.Serializable

@Serializable
data class MandalaSet(
    val id: String,
    val name: String,
    val orderedMandalaIds: MutableList<String>,
    val selectionPolicy: SelectionPolicy = SelectionPolicy.SEQUENTIAL
)