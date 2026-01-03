package llm.slop.spirals.cv.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.util.Log
import llm.slop.spirals.cv.AmplitudeCv
import kotlinx.coroutines.*

/**
 * Handles real-time audio input from various sources and updates an AmplitudeCv signal.
 */
class AudioEngine(private val amplitudeCv: AmplitudeCv) {
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_FLOAT
    
    private var audioRecord: AudioRecord? = null
    private var job: Job? = null
    private val extractor = AmplitudeExtractor()

    @Volatile
    var debugLastRms: Float = 0f
        private set

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope, record: AudioRecord?) {
        if (record == null) {
            Log.e("AudioEngine", "Cannot start: AudioRecord is null")
            return
        }
        
        stop()

        audioRecord = record
        
        try {
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("AudioEngine", "AudioRecord initialization failed")
                return
            }

            audioRecord?.startRecording()
            Log.d("AudioEngine", "AudioRecord started: ${audioRecord?.audioSource}")

            job = scope.launch(Dispatchers.Default) {
                // Buffer size in floats. MinBufferSize is in bytes.
                val minBufferSizeInBytes = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                val readBufferSize = (minBufferSizeInBytes / 4).coerceAtLeast(512)
                val audioData = FloatArray(readBufferSize)
                
                while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val read = audioRecord?.read(audioData, 0, audioData.size, AudioRecord.READ_BLOCKING) ?: 0
                    if (read > 0) {
                        val rms = extractor.calculateRms(audioData.copyOfRange(0, read))
                        debugLastRms = rms
                        amplitudeCv.update(rms)
                    } else if (read < 0) {
                        Log.e("AudioEngine", "AudioRecord read error: $read")
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AudioEngine", "Error starting audio engine", e)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e("AudioEngine", "Error stopping AudioRecord", e)
        }
        audioRecord = null
    }
}
