package llm.slop.spirals

import llm.slop.spirals.cv.*

/**
 * Enhanced mapper to handle serialization and deserialization of patches.
 */
object PatchMapper {
    fun fromVisualSource(name: String, source: MandalaVisualSource): MandalaPatch {
        val settings = mutableMapOf<String, ParameterSetting>()
        
        // Include geometry parameters
        source.parameters.forEach { (name, param) ->
            settings[name] = ParameterSetting(
                baseValue = param.baseValue,
                modulators = param.modulators.map { CvModulatorSetting(it.sourceId, it.operator, it.weight) }
            )
        }
        
        // Include global parameters
        settings["globalAlpha"] = ParameterSetting(source.globalAlpha.baseValue, source.globalAlpha.modulators.map { CvModulatorSetting(it.sourceId, it.operator, it.weight) })
        settings["globalScale"] = ParameterSetting(source.globalScale.baseValue, source.globalScale.modulators.map { CvModulatorSetting(it.sourceId, it.operator, it.weight) })

        return MandalaPatch(name, source.recipe.id, settings)
    }

    fun applyToVisualSource(patch: MandalaPatch, source: MandalaVisualSource, rawSettingsJson: String? = null) {
        val ratio = MandalaLibrary.MandalaRatios.find { it.id == patch.recipeId }
        if (ratio != null) source.recipe = ratio

        // If we have raw JSON from the DB, use the deserializer logic
        if (rawSettingsJson != null) {
            try {
                val parts = rawSettingsJson.split("|")
                parts.forEach { part ->
                    val segments = part.split(":")
                    if (segments.size >= 2) {
                        val name = segments[0]
                        val base = segments[1].toFloatOrNull() ?: 0f
                        
                        val param = if (name == "globalAlpha") source.globalAlpha 
                                   else if (name == "globalScale") source.globalScale
                                   else source.parameters[name]
                        
                        param?.apply {
                            baseValue = base
                            modulators.clear()
                            if (segments.size > 2 && segments[2].isNotEmpty()) {
                                val mods = segments[2].split(",")
                                mods.forEach { modStr ->
                                    val m = modStr.split(";") // Changed separator to avoid conflict
                                    if (m.size == 3) {
                                        modulators.add(CvModulator(m[0], ModulationOperator.valueOf(m[1]), m[2].toFloat()))
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}
