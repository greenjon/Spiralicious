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

    /**
     * Calculates the final value based on base value and active modulators.
     * Constraint: No allocations in this method.
     */
    fun evaluate(): Float {
        var result = baseValue
        
        // FinalValue = BaseValue + (CV * Weight * Operator)
        // Interpreting this as: result = result [OPERATOR] (cvValue * weight)
        for (i in 0 until modulators.size) {
            val mod = modulators[i]
            val cvValue = CvRegistry.get(mod.sourceId)
            val modAmount = cvValue * mod.weight
            
            result = when (mod.operator) {
                ModulationOperator.ADD -> result + modAmount
                ModulationOperator.MUL -> result * modAmount
            }
        }
        
        // All values are normalized 0.0 to 1.0
        val finalValue = result.coerceIn(0f, 1f)
        history.add(finalValue)
        return finalValue
    }
}
