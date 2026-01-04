package llm.slop.spirals.cv

import llm.slop.spirals.cv.ui.CvHistoryBuffer
import java.util.concurrent.CopyOnWriteArrayList

enum class ModulationOperator {
    ADD, MUL
}

data class CvModulator(
    val sourceId: String,
    val operator: ModulationOperator = ModulationOperator.ADD,
    val weight: Float = 0.0f
)

/**
 * A parameter that can be controlled by a base value and multiple CV modulators.
 * Uses thread-safe collections for cross-thread access between UI and Renderer.
 */
class ModulatableParameter(
    var baseValue: Float = 0.0f,
    val historySize: Int = 200
) {
    // Thread-safe list for iteration in GL thread and modification in UI thread
    val modulators = CopyOnWriteArrayList<CvModulator>()
    val history = CvHistoryBuffer(historySize)
    
    var value: Float = baseValue
        private set

    /**
     * Calculates the final value. Called at 120Hz from the Renderer.
     */
    fun evaluate(): Float {
        var result = baseValue
        
        // Concurrent-safe iteration
        for (mod in modulators) {
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
