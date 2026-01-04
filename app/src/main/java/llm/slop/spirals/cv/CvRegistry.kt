package llm.slop.spirals.cv

import androidx.compose.runtime.mutableStateMapOf
import java.util.concurrent.ConcurrentHashMap

/**
 * Central registry for all Control Voltage signals.
 */
object CvRegistry {
    private val signalData = ConcurrentHashMap<String, Float>().apply {
        put("amp", 0f)
        put("bass", 0f)
        put("mid", 0f)
        put("high", 0f)
        put("beatPhase", 0f)
        put("bpm", 120f)
        put("accent", 0f)
    }

    val signals = mutableStateMapOf<String, Float>().apply {
        putAll(signalData)
    }

    fun update(name: String, value: Float) {
        signalData[name] = value
        signals[name] = value
    }

    /**
     * Gets a signal value. 
     * Requirement: If a key is missing, return 0f rather than throwing or returning null.
     */
    fun get(name: String): Float = signalData[name] ?: 0f
}
