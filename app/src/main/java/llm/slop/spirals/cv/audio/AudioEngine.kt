package llm.slop.spirals.cv.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.util.Log
import llm.slop.spirals.cv.CvRegistry
import llm.slop.spirals.cv.BeatClock
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.max

/**
 * The core analysis engine. Splits audio into bands and updates the CvRegistry.
 * Decoupled from specific CV objects; writes directly to the Unified Registry.
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
    
    // Accent logic
    private var lastAmp = 0f
    private var accentValue = 0f

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
                val readBufferSize = (minBufferSizeInBytes / 4).coerceAtLeast(512)
                val audioData = FloatArray(readBufferSize)
                
                // Temp buffers for filtering
                val lowData = FloatArray(readBufferSize)
                val midData = FloatArray(readBufferSize)
                val highData = FloatArray(readBufferSize)

                var startTime = System.currentTimeMillis()

                while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val read = audioRecord?.read(audioData, 0, audioData.size, AudioRecord.READ_BLOCKING) ?: 0
                    if (read > 0) {
                        // 1. Multiband Filtering
                        for (i in 0 until read) {
                            lowData[i] = lowPass.process(audioData[i])
                            midData[i] = midPass.process(audioData[i])
                            highData[i] = highPass.process(audioData[i])
                        }

                        // 2. Extract RMS per band
                        val amp = extractor.calculateRms(audioData.copyOfRange(0, read))
                        val bass = extractor.calculateRms(lowData.copyOfRange(0, read))
                        val mid = extractor.calculateRms(midData.copyOfRange(0, read))
                        val high = extractor.calculateRms(highData.copyOfRange(0, read))

                        // 3. Transient Detection (Accent)
                        val diff = max(0f, amp - lastAmp)
                        if (diff > 0.05f) accentValue = 1.0f
                        else accentValue *= 0.92f // Rapid decay
                        lastAmp = amp

                        // 4. Update CvRegistry
                        debugLastRms = amp
                        
                        // Reference level 0.1 for normalization
                        val ref = 0.1f
                        CvRegistry.update("amp", (amp / ref).coerceIn(0f, 2f))
                        CvRegistry.update("bass", (bass / ref).coerceIn(0f, 2f))
                        CvRegistry.update("mid", (mid / ref).coerceIn(0f, 2f))
                        CvRegistry.update("high", (high / ref).coerceIn(0f, 2f))
                        CvRegistry.update("accent", accentValue)

                        // 5. Update Beat Phase
                        val elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
                        beatClock.bpm = CvRegistry.get("bpm") // Listen to UI BPM
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
