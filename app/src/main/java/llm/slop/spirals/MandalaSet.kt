package llm.slop.spirals

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class MandalaSet(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    val orderedMandalaIds: MutableList<String>,
    var selectionPolicy: SelectionPolicy = SelectionPolicy.SEQUENTIAL
)
