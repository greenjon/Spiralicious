package llm.slop.spirals.models

import kotlinx.serialization.Serializable

@Serializable
data class PatchData(
    val name: String,
    val recipeId: String,
    val parameters: List<Parameter>
)

@Serializable
data class Parameter(
    val name: String,
    val value: Float
)
