package llm.slop.spirals.cv

import androidx.compose.runtime.mutableStateMapOf
import llm.slop.spirals.cv.ui.CvHistoryBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*

/**
 * Central registry for all Control Voltage signals.
 */
object CvRegistry {
    private val rawSignalData = ConcurrentHashMap<String, Float>().apply {
        put("amp", 0f)
        put("bass", 0f)
        put("mid", 0f)
        put("high", 0f)
        put("bassFlux", 0f)
        put("onset", 0f)
        put("accent", 0f)
        put("beatPhase", 0f)
        put("bpm", 120f)
    }

    // Diagnostic History Buffers (Accessible anywhere)
    val history = ConcurrentHashMap<String, CvHistoryBuffer>().apply {
        rawSignalData.keys.forEach { put(it, CvHistoryBuffer(200)) }
    }

    val signals = mutableStateMapOf<String, Float>().apply {
        putAll(rawSignalData)
    }

    private var syncJob: Job? = null

    /**
     * Starts background sync for both UI state and Diagnostic history.
     */
    fun startSync(scope: CoroutineScope) {
        if (syncJob != null) return
        syncJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val start = System.currentTimeMillis()
                
                // 1. Update History (Always running)
                rawSignalData.forEach { (k, v) ->
                    history[k]?.add(v)
                }

                // 2. Sync to UI State (Main Thread)
                withContext(Dispatchers.Main) {
                    rawSignalData.forEach { (k, v) ->
                        if (signals[k] != v) {
                            signals[k] = v
                        }
                    }
                }

                // Aim for ~60Hz (16ms)
                val elapsed = System.currentTimeMillis() - start
                delay((16 - elapsed).coerceAtLeast(1))
            }
        }
    }

    fun update(name: String, value: Float) {
        rawSignalData[name] = value
    }

    fun get(name: String): Float = rawSignalData[name] ?: 0f
}
