package llm.slop.spirals.cv.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.util.Log
import llm.slop.spirals.cv.CvRegistry
import llm.slop.spirals.cv.BeatClock
import kotlinx.coroutines.*
import kotlin.math.max

/**
 * The core analysis engine. Splits audio into bands and updates the CvRegistry.
 * Processes in blocks for efficiency.
 */
class AudioEngine {
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_FLOAT
    
    private var audioRecord: AudioRecord? = null
    private var job: Job? = null
    
    // Filters for multiband analysis
    private val lowPass = BiquadFilter(BiquadFilter.Type.LOWPASS, 44100f, 150f)
    private val midPass = BiquadFilter(BiquadFilter.Type.BANDPASS, 44100f, 1000f)
    private val highPass = BiquadFilter(BiquadFilter.Type.HIGHPASS, 44100f, 5000f)
    
    private val extractor = AmplitudeExtractor()
    private val beatClock = BeatClock(120f)
    
    // Transient (Accent) state
    private var lastAmp = 0f
    private var accentValue = 0f
    private var rollingAverageAmp = 0f

    @Volatile
    var debugLastRms: Float = 0f
        private set

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope, record: AudioRecord?) {
        if (record == null) return
        stop()
        audioRecord = record
        
        try {
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) return
            audioRecord?.startRecording()

            job = scope.launch(Dispatchers.Default) {
                val minBufferSizeInBytes = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                // Ensure we have a reasonable block size (at least 512 samples)
                val readBufferSize = (minBufferSizeInBytes / 4).coerceAtLeast(512)
                val audioData = FloatArray(readBufferSize)
                
                val lowData = FloatArray(readBufferSize)
                val midData = FloatArray(readBufferSize)
                val highData = FloatArray(readBufferSize)

                var startTime = System.currentTimeMillis()

                while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val read = audioRecord?.read(audioData, 0, audioData.size, AudioRecord.READ_BLOCKING) ?: 0
                    if (read > 0) {
                        // 1. Block-based Filtering
                        for (i in 0 until read) {
                            lowData[i] = lowPass.process(audioData[i])
                            midData[i] = midPass.process(audioData[i])
                            highData[i] = highPass.process(audioData[i])
                        }

                        // 2. Block-based Analysis (Only 4 RMS calculations per block)
                        val amp = extractor.calculateRms(audioData.copyOfRange(0, read))
                        val bass = extractor.calculateRms(lowData.copyOfRange(0, read))
                        val mid = extractor.calculateRms(midData.copyOfRange(0, read))
                        val high = extractor.calculateRms(highData.copyOfRange(0, read))

                        // 3. Optimized Transient Detection
                        // Spike if current block is significantly louder than rolling average
                        val threshold = rollingAverageAmp * 1.5f + 0.02f
                        if (amp > threshold && amp > 0.05f) {
                            accentValue = 1.0f
                        } else {
                            accentValue *= 0.85f // Fast decay for visual snappiness
                        }
                        
                        // Update rolling average slowly
                        rollingAverageAmp = rollingAverageAmp * 0.9f + amp * 0.1f
                        lastAmp = amp

                        // 4. Batch Registry Updates (Once per block)
                        debugLastRms = amp
                        val ref = 0.1f
                        CvRegistry.update("amp", (amp / ref).coerceIn(0f, 2f))
                        CvRegistry.update("bass", (bass / ref).coerceIn(0f, 2f))
                        CvRegistry.update("mid", (mid / ref).coerceIn(0f, 2f))
                        CvRegistry.update("high", (high / ref).coerceIn(0f, 2f))
                        CvRegistry.update("accent", accentValue)

                        // 5. Time-base update
                        val elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
                        beatClock.bpm = CvRegistry.get("bpm")
                        CvRegistry.update("beatPhase", beatClock.getPhase(elapsedSec))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AudioEngine", "Error", e)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {}
        audioRecord = null
    }
}
