package llm.slop.spirals.cv

import llm.slop.spirals.cv.ui.CvHistoryBuffer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.serialization.Serializable

enum class ModulationOperator {
    ADD, MUL
}

enum class Waveform {
    SINE, TRIANGLE, SQUARE
}

@Serializable
data class CvModulator(
    val sourceId: String,
    val operator: ModulationOperator = ModulationOperator.ADD,
    val weight: Float = 0.0f,
    val bypassed: Boolean = false,
    // Beat expansion fields
    val waveform: Waveform = Waveform.SINE,
    val subdivision: Float = 1.0f,
    val phaseOffset: Float = 0.0f,
    val slope: Float = 0.5f
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
            if (mod.bypassed) continue
            
            // Advanced BEAT logic - checks for "beatPhase" or "none" (if subdivision is set)
            // But specifically, we use sourceId "beatPhase" as the trigger for the precision clock.
            val finalCv = if (mod.sourceId == "beatPhase") {
                // Use Synchronized precision clock (Predictive Interpolation)
                val beats = CvRegistry.getSynchronizedTotalBeats()
                val localPhase = ((beats / mod.subdivision) + mod.phaseOffset) % 1.0
                
                // Ensure positive phase for % 1.0
                val positivePhase = if (localPhase < 0) (localPhase + 1.0) else localPhase
                
                when(mod.waveform) {
                    Waveform.SINE -> (sin(positivePhase * 2.0 * Math.PI).toFloat() * 0.5f) + 0.5f
                    Waveform.TRIANGLE -> {
                        // Edge-snapping: If slope is effectively 0 or 1, treat as saw
                        val s = mod.slope.toDouble()
                        if (s <= 0.001) {
                            (1.0 - positivePhase).toFloat() // Falling Saw
                        } else if (s >= 0.999) {
                            positivePhase.toFloat() // Rising Saw
                        } else {
                            if (positivePhase < s) {
                                (positivePhase / s).toFloat()
                            } else {
                                ((1.0 - positivePhase) / (1.0 - s)).toFloat()
                            }
                        }
                    }
                    Waveform.SQUARE -> if (positivePhase < mod.slope) 1.0f else 0.0f
                }
            } else {
                CvRegistry.get(mod.sourceId)
            }
            
            val modAmount = finalCv * mod.weight
            
            result = when (mod.operator) {
                ModulationOperator.ADD -> result + modAmount
                ModulationOperator.MUL -> result * (1.0f + modAmount)
            }
        }
        
        value = result.coerceIn(0f, 1f)
        history.add(value)
        return value
    }
}
