package llm.slop.spirals

import llm.slop.spirals.models.ModulatableParameterData

class ModulatableParameter(
    val data: ModulatableParameterData
) {
    /**
     * Calculates the final, modulated value of this parameter.
     * @param modSources A map of modulator names to their current values (e.g., "lfo1" -> 0.5f).
     * @return The final float value to be sent to a shader uniform.
     */
    fun getValue(modSources: Map<String, Float>): Float {
        val modValue = modSources[data.modSource] ?: 0.0f
        return data.baseValue + (modValue * data.modAmount)
    }
}
