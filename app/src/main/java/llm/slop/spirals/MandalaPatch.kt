package llm.slop.spirals

import llm.slop.spirals.cv.CvModulator
import llm.slop.spirals.cv.ModulationOperator

/**
 * Data model for a complete Mandala configuration (Patch).
 */
data class MandalaPatch(
    val name: String,
    val recipeId: String, // ID from MandalaRatio
    val parameterSettings: Map<String, ParameterSetting>
)

data class ParameterSetting(
    val baseValue: Float,
    val modulators: List<CvModulatorSetting>
)

data class CvModulatorSetting(
    val sourceId: String,
    val operator: ModulationOperator,
    val weight: Float
)

/**
 * Helper to convert between live VisualSource and serializable Patch.
 */
object PatchMapper {
    fun fromVisualSource(name: String, source: MandalaVisualSource): MandalaPatch {
        val settings = source.parameters.mapValues { (_, param) ->
            ParameterSetting(
                baseValue = param.baseValue,
                modulators = param.modulators.map { 
                    CvModulatorSetting(it.sourceId, it.operator, it.weight)
                }
            )
        }
        return MandalaPatch(name, source.recipe.id, settings)
    }

    fun applyToVisualSource(patch: MandalaPatch, source: MandalaVisualSource) {
        val ratio = MandalaLibrary.MandalaRatios.find { it.id == patch.recipeId }
        if (ratio != null) source.recipe = ratio
        
        patch.parameterSettings.forEach { (name, setting) ->
            source.parameters[name]?.apply {
                baseValue = setting.baseValue
                modulators.clear()
                setting.modulators.forEach { mod ->
                    modulators.add(CvModulator(mod.sourceId, mod.operator, mod.weight))
                }
            }
        }
    }
}
