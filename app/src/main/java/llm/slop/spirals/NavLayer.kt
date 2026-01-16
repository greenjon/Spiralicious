package llm.slop.spirals

import kotlinx.serialization.Serializable

@Serializable
data class NavLayer(
    val id: String,
    val name: String,
    val type: LayerType,
    val isDirty: Boolean = false,
    val data: LayerContent? = null,
    val parentSlotIndex: Int? = null,  // For Mixer slots - which slot to insert into
    val createdFromParent: Boolean = false  // Track if should link back to parent on breadcrumb navigation
)
