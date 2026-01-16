package llm.slop.spirals

import kotlinx.serialization.Serializable

@Serializable
data class NavLayer(
    val id: String,
    val name: String,
    val type: LayerType,
    val isDirty: Boolean = false,
    val data: LayerContent? = null
)
