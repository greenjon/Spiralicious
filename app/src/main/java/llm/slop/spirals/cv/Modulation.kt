package llm.slop.spirals.cv

import llm.slop.spirals.cv.ui.CvHistoryBuffer

/**
 * Defines how a CV signal interacts with a parameter.
 */
enum class ModulationOperator {
    ADD, MUL
}

/**
 * Data class for a single modulation connection.
 */
data class CvModulator(
    val sourceId: String,
    val operator: ModulationOperator = ModulationOperator.ADD,
    val weight: Float = 0.0f
)

/**
 * A parameter that can be controlled by a base value and multiple CV modulators.
 * Maintains its own history for visualization.
 */
class ModulatableParameter(
    var baseValue: Float = 0.0f,
    val historySize: Int = 200
) {
    val modulators = mutableListOf<CvModulator>()
    val history = CvHistoryBuffer(historySize)
    
    // The most recently calculated value
    var value: Float = baseValue
        private set

    /**
     * Calculates the final value based on base value and active modulators.
     */
    fun evaluate(): Float {
        var result = baseValue
        
        for (i in 0 until modulators.size) {
            val mod = modulators[i]
            val cvValue = CvRegistry.get(mod.sourceId)
            val modAmount = cvValue * mod.weight
            
            result = when (mod.operator) {
                ModulationOperator.ADD -> result + modAmount
                ModulationOperator.MUL -> result * modAmount
            }
        }
        
        value = result.coerceIn(0f, 1f)
        history.add(value)
        return value
    }
}
