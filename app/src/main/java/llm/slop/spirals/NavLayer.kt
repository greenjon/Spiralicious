package llm.slop.spirals

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class NavLayer(
    val id: String,
    val name: String,
    val type: LayerType,
    val isDirty: Boolean = false,
    @Transient
    var data: Any? = null
)
