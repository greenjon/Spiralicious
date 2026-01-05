package llm.slop.spirals.cv

import androidx.compose.runtime.mutableStateMapOf
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*

/**
 * Central registry for all Control Voltage signals.
 * Optimized for high-speed access between Audio, Renderer, and UI threads.
 */
object CvRegistry {
    // High-performance thread-safe map for Audio and Renderer math
    private val rawSignalData = ConcurrentHashMap<String, Float>().apply {
        put("amp", 0f)
        put("bass", 0f)
        put("mid", 0f)
        put("high", 0f)
        put("beatPhase", 0f)
        put("bpm", 120f)
        put("accent", 0f)
    }

    // Compose-observable map for UI state (Diagnostic Lab)
    val signals = mutableStateMapOf<String, Float>().apply {
        putAll(rawSignalData)
    }

    private var syncJob: Job? = null

    /**
     * Starts a background job to sync raw data to the UI state at a steady rate.
     * This prevents high-frequency audio updates from choking the Compose Main thread.
     */
    fun startSync(scope: CoroutineScope) {
        if (syncJob != null) return
        syncJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                // Throttled sync to UI thread (60Hz is plenty for display)
                rawSignalData.forEach { (k, v) ->
                    signals[k] = v
                }
                delay(16) 
            }
        }
    }

    /**
     * High-speed update from Audio Engine thread.
     */
    fun update(name: String, value: Float) {
        rawSignalData[name] = value
    }

    /**
     * High-speed read from Renderer/Modulation thread.
     */
    fun get(name: String): Float = rawSignalData[name] ?: 0f
}
