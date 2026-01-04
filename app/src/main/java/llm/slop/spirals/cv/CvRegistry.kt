package llm.slop.spirals.cv

import androidx.compose.runtime.mutableStateMapOf
import java.util.concurrent.ConcurrentHashMap

/**
 * Central registry for all Control Voltage signals.
 * Optimized for high-speed access between Audio, Renderer, and UI threads.
 */
object CvRegistry {
    // Thread-safe map for Audio and Renderer threads
    private val signalData = ConcurrentHashMap<String, Float>().apply {
        put("amp", 0f)
        put("bass", 0f)
        put("mid", 0f)
        put("high", 0f)
        put("beatPhase", 0f)
        put("bpm", 120f)
        put("accent", 0f)
    }

    // Compose-observable map for UI state
    val signals = mutableStateMapOf<String, Float>().apply {
        putAll(signalData)
    }

    /**
     * Updates a signal value. 
     * Called from Audio Engine.
     */
    fun update(name: String, value: Float) {
        signalData[name] = value
        // Update the compose map for UI observation (Diagnostic Lab)
        signals[name] = value
    }

    /**
     * Gets a signal value.
     * Called from Renderer and Modulation logic.
     */
    fun get(name: String): Float = signalData[name] ?: 0f
}
