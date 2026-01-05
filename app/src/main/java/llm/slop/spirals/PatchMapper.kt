package llm.slop.spirals

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import llm.slop.spirals.cv.*

/**
 * Robust mapper to handle serialization and deserialization of patches.
 * Excludes global mixing parameters (Alpha/Scale) to allow for higher-level mixing later.
 */
object PatchMapper {
    private val jsonConfiguration = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val legacyMigrations = mapOf(
        "arm1" to "L1",
        "arm2" to "L2",
        "arm3" to "L3",
        "arm4" to "L4"
    )

    fun fromVisualSource(name: String, source: MandalaVisualSource): PatchData {
        val parameters = mutableListOf<ParameterData>()
        
        // Only include internal geometry and color parameters in the individual patch
        source.parameters.forEach { (id, param) ->
            parameters.add(ParameterData(
                id = id,
                baseValue = param.baseValue,
                modulators = param.modulators.map { ModulatorData(it.sourceId, it.operator.name, it.weight) }
            ))
        }
        
        return PatchData(name, source.recipe.id, parameters)
    }

    fun applyToVisualSource(patchData: PatchData, source: MandalaVisualSource) {
        val ratio = MandalaLibrary.MandalaRatios.find { it.id == patchData.recipeId }
        if (ratio != null) source.recipe = ratio

        patchData.parameters.forEach { paramData ->
            val currentId = legacyMigrations[paramData.id] ?: paramData.id
            
            // We ignore globalAlpha and globalScale here so they aren't overwritten by individual patches
            val param = source.parameters[currentId]
            
            param?.apply {
                baseValue = paramData.baseValue
                
                val validModulators = mutableListOf<CvModulator>()
                paramData.modulators.forEach { modData ->
                    try {
                        val op = when(modData.operator.uppercase()) {
                            "MUL" -> ModulationOperator.MUL
                            else -> ModulationOperator.ADD
                        }
                        validModulators.add(CvModulator(modData.sourceId, op, modData.weight))
                    } catch (e: Exception) {
                        // Suppress or log exception
                    }
                }
                
                modulators.clear()
                modulators.addAll(validModulators)
            }
        }
    }

    fun toJson(patchData: PatchData): String = jsonConfiguration.encodeToString(patchData)
    
    fun fromJson(jsonStr: String?): PatchData? {
        if (jsonStr == null) return null
        return try {
            jsonConfiguration.decodeFromString<PatchData>(jsonStr)
        } catch (e: Exception) {
            null
        }
    }

    fun allToJson(patches: List<PatchData>): String = jsonConfiguration.encodeToString(patches)
}
