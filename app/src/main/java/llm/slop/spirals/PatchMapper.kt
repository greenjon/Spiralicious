package llm.slop.spirals

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import llm.slop.spirals.cv.*

/**
 * Robust mapper to handle serialization and deserialization of patches.
 * Implements a "Default and Fallback" strategy to prevent crashes on schema changes.
 */
object PatchMapper {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /**
     * Map of legacy parameter names to current ones to support migration.
     */
    private val legacyMigrations = mapOf(
        "arm1" to "L1",
        "arm2" to "L2",
        "arm3" to "L3",
        "arm4" to "L4"
    )

    fun fromVisualSource(name: String, source: MandalaVisualSource): PatchData {
        val parameters = mutableListOf<ParameterData>()
        
        source.parameters.forEach { (id, param) ->
            parameters.add(ParameterData(
                id = id,
                baseValue = param.baseValue,
                modulators = param.modulators.map { ModulatorData(it.sourceId, it.operator.name, it.weight) }
            ))
        }
        
        parameters.add(ParameterData("globalAlpha", source.globalAlpha.baseValue, source.globalAlpha.modulators.map { ModulatorData(it.sourceId, it.operator.name, it.weight) }))
        parameters.add(ParameterData("globalScale", source.globalScale.baseValue, source.globalScale.modulators.map { ModulatorData(it.sourceId, it.operator.name, it.weight) }))

        return PatchData(name, source.recipe.id, parameters)
    }

    fun applyToVisualSource(patchData: PatchData, source: MandalaVisualSource) {
        val ratio = MandalaLibrary.MandalaRatios.find { it.id == patchData.recipeId }
        if (ratio != null) source.recipe = ratio

        patchData.parameters.forEach { paramData ->
            // Resolve current ID, checking migrations for backward compatibility
            val currentId = legacyMigrations[paramData.id] ?: paramData.id
            
            val param = if (currentId == "globalAlpha") source.globalAlpha 
                       else if (currentId == "globalScale") source.globalScale
                       else source.parameters[currentId]
            
            param?.apply {
                // Graceful fallback: only update if data is valid
                baseValue = paramData.baseValue
                
                // Clear and rebuild modulators carefully
                val validModulators = mutableListOf<CvModulator>()
                paramData.modulators.forEach { modData ->
                    try {
                        val op = when(modData.operator.uppercase()) {
                            "MUL" -> ModulationOperator.MUL
                            else -> ModulationOperator.ADD // Default to ADD for unknown strings
                        }
                        validModulators.add(CvModulator(modData.sourceId, op, modData.weight))
                    } catch (e: Exception) {
                        // Log and skip invalid modulator data
                    }
                }
                
                modulators.clear()
                modulators.addAll(validModulators)
            }
        }
    }

    fun toJson(patchData: PatchData): String = json.encodeToString(patchData)
    
    fun fromJson(jsonStr: String?): PatchData? {
        if (jsonStr == null) return null
        return try {
            json.decodeFromString<PatchData>(jsonStr)
        } catch (e: Exception) {
            null
        }
    }

    fun allToJson(patches: List<PatchData>): String = json.encodeToString(patches)
}
