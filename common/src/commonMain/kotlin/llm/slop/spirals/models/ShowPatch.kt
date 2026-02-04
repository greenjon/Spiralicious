package llm.slop.spirals.models

import kotlinx.serialization.Serializable

@Serializable
data class ShowPatch(
    val id: String,
    val name: String,
    val randomSetIds: List<String> = emptyList()
)
