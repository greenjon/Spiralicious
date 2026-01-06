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
            
            val rawCv = CvRegistry.get(mod.sourceId)
            
            // Advanced BEAT logic
            val finalCv = if (mod.sourceId == "beatPhase") {
                // Use totalBeats for proper subdivisions > 1 beat
                val beats = CvRegistry.get("totalBeats")
                val localPhase = ((beats / mod.subdivision) + mod.phaseOffset) % 1.0f
                
                // Ensure positive phase for % 1.0f
                val positivePhase = if (localPhase < 0) localPhase + 1.0f else localPhase
                
                when(mod.waveform) {
                    Waveform.SINE -> (sin(positivePhase * 2.0 * PI).toFloat() * 0.5f) + 0.5f
                    Waveform.TRIANGLE -> {
                        if (positivePhase < mod.slope) {
                            if (mod.slope > 0f) positivePhase / mod.slope else 1f
                        } else {
                            if (mod.slope < 1f) (1.0f - positivePhase) / (1.0f - mod.slope) else 1f
                        }
                    }
                    Waveform.SQUARE -> if (positivePhase < mod.slope) 1.0f else 0.0f
                }
            } else {
                rawCv
            }
            
            val modAmount = finalCv * mod.weight
            
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
