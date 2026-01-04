package llm.slop.spirals

import kotlinx.serialization.Serializable
import llm.slop.spirals.cv.ModulationOperator

@Serializable
data class PatchData(
    val name: String,
    val recipeId: String,
    val parameters: List<ParameterData>
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
    val operator: String, // ADD or MUL
    val weight: Float
)
