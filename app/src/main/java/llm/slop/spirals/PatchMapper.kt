package llm.slop.spirals

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import llm.slop.spirals.cv.*

/**
 * Enhanced mapper to handle serialization and deserialization of patches using PatchData.
 */
object PatchMapper {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun fromVisualSource(name: String, source: MandalaVisualSource): PatchData {
        val parameters = mutableListOf<ParameterData>()
        
        // Include geometry parameters
        source.parameters.forEach { (id, param) ->
            parameters.add(ParameterData(
                id = id,
                baseValue = param.baseValue,
                modulators = param.modulators.map { ModulatorData(it.sourceId, it.operator.name, it.weight) }
            ))
        }
        
        // Include global parameters
        parameters.add(ParameterData("globalAlpha", source.globalAlpha.baseValue, source.globalAlpha.modulators.map { ModulatorData(it.sourceId, it.operator.name, it.weight) }))
        parameters.add(ParameterData("globalScale", source.globalScale.baseValue, source.globalScale.modulators.map { ModulatorData(it.sourceId, it.operator.name, it.weight) }))

        return PatchData(name, source.recipe.id, parameters)
    }

    fun applyToVisualSource(patchData: PatchData, source: MandalaVisualSource) {
        val ratio = MandalaLibrary.MandalaRatios.find { it.id == patchData.recipeId }
        if (ratio != null) source.recipe = ratio

        patchData.parameters.forEach { paramData ->
            val param = if (paramData.id == "globalAlpha") source.globalAlpha 
                       else if (paramData.id == "globalScale") source.globalScale
                       else source.parameters[paramData.id]
            
            param?.apply {
                baseValue = paramData.baseValue
                modulators.clear()
                paramData.modulators.forEach { modData ->
                    try {
                        modulators.add(CvModulator(
                            modData.sourceId, 
                            ModulationOperator.valueOf(modData.operator), 
                            modData.weight
                        ))
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
        }
    }

    fun toJson(patchData: PatchData): String = json.encodeToString(patchData)
    
    fun fromJson(jsonStr: String): PatchData = json.decodeFromString(jsonStr)

    fun allToJson(patches: List<PatchData>): String = json.encodeToString(patches)
}
