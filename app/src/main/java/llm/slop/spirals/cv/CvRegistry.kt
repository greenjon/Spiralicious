package llm.slop.spirals.cv

import androidx.compose.runtime.mutableStateMapOf

/**
 * Central registry for all Control Voltage signals.
 * Decouples audio analysis from visual rendering.
 */
object CvRegistry {
    // Compose-observable map for real-time UI updates
    val signals = mutableStateMapOf<String, Float>(
        "amp" to 0f,
        "bass" to 0f,
        "mid" to 0f,
        "high" to 0f,
        "beatPhase" to 0f,
        "bpm" to 120f,
        "accent" to 0f
    )

    /**
     * Updates a signal value. 
     * Expected to be called at 120Hz from the CV loop.
     */
    fun update(name: String, value: Float) {
        signals[name] = value
    }

    fun get(name: String): Float = signals[name] ?: 0f
}
