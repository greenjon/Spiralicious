package llm.slop.spirals.models

import kotlinx.serialization.Serializable

@Serializable
data class RandomSet(
    val id: String,
    val name: String
)
