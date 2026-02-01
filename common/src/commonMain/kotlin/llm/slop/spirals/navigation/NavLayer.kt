package llm.slop.spirals.navigation

import kotlinx.serialization.Serializable
import llm.slop.spirals.LayerContent
import llm.slop.spirals.LayerType

@Serializable
data class NavLayer(
    val id: String,
    val name: String,
    val type: LayerType,
    val isDirty: Boolean = false,
    val data: LayerContent? = null,
    val parentSlotIndex: Int? = null,
    val createdFromParent: Boolean = false,
    val openedFromMenu: Boolean = false
)
