package llm.slop.spirals.cv

import llm.slop.spirals.cv.ui.CvHistoryBuffer
import java.util.concurrent.CopyOnWriteArrayList

enum class ModulationOperator {
    ADD, MUL
}

data class CvModulator(
    val sourceId: String,
    val operator: ModulationOperator = ModulationOperator.ADD,
    val weight: Float = 0.0f,
    val bypassed: Boolean = false // Added bypass support
)

/**
 * A parameter that can be controlled by a base value and multiple CV modulators.
 */
class ModulatableParameter(
    var baseValue: Float = 0.0f,
    val historySize: Int = 200
) {
    val modulators = CopyOnWriteArrayList<CvModulator>()
    val history = CvHistoryBuffer(historySize)
    
    var value: Float = baseValue
        private set

    /**
     * Calculates the final value. Called at 120Hz from the Renderer.
     */
    fun evaluate(): Float {
        var result = baseValue
        
        for (mod in modulators) {
            if (mod.bypassed) continue // Respect bypass flag
            
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
