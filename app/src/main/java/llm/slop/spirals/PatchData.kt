package llm.slop.spirals

import kotlinx.serialization.Serializable
import llm.slop.spirals.cv.ModulationOperator

@Serializable
data class PatchData(
    val name: String,
    val recipeId: String,
    val parameters: List<ParameterData>,
    val version: Int = 1 // Added versioning for future schema safety
)

@Serializable
data class ParameterData(
    val id: String,
    val baseValue: Float,
    val modulators: List<ModulatorData>
)

@Serializable
data class ModulatorData(
    val sourceId: String,
    val operator: String,
    val weight: Float
)
