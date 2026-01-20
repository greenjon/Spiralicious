# Spirals Modulation System Documentation

This document provides detailed documentation of the Control Voltage (CV) modulation system in Spirals. This system is responsible for the dynamic audio-reactive behavior of visual parameters throughout the application.

## Table of Contents

1. [Modulation Architecture Overview](#modulation-architecture-overview)
2. [CV Signal Sources](#cv-signal-sources)
   - [Audio-Derived Sources](#audio-derived-sources)
   - [Beat-Synchronized Sources](#beat-synchronized-sources)
   - [LFO Sources](#lfo-sources)
   - [Sample and Hold](#sample-and-hold)
3. [Parameter Modulation System](#parameter-modulation-system)
   - [ModulatableParameter Class](#modulatableparameter-class)
   - [Modulation Operators](#modulation-operators)
   - [Waveform Types](#waveform-types)
4. [Signal Processing Chain](#signal-processing-chain)
   - [Audio Capture](#audio-capture)
   - [Audio Analysis](#audio-analysis)
   - [Signal Normalization](#signal-normalization)
   - [Parameter Application](#parameter-application)
5. [CV Signal Modifiers](#cv-signal-modifiers)
   - [Gain and Offset](#gain-and-offset)
   - [Power Curve](#power-curve)
   - [Clipping](#clipping)
   - [Composite Modifiers](#composite-modifiers)
6. [Modulation Registry](#modulation-registry)
   - [Central Signal Repository](#central-signal-repository)
   - [Signal History Buffering](#signal-history-buffering)
   - [Precision Beat Clock](#precision-beat-clock)
7. [Audio Engine](#audio-engine)
   - [Signal Extraction](#signal-extraction)
   - [Frequency Bands](#frequency-bands)
   - [Onset Detection](#onset-detection)
   - [BPM Detection](#bpm-detection)
8. [Integration with Rendering](#integration-with-rendering)
   - [Parameter Update Cycle](#parameter-update-cycle)
   - [Rendering Impact](#rendering-impact)

## Modulation Architecture Overview

The Spirals modulation system is built around a "Control Voltage" (CV) metaphor, inspired by modular synthesizers. This architecture allows parameters throughout the application to be controlled by a variety of dynamic sources and processed through a flexible signal chain.

Key components of the system include:

1. **CV Signal Interface**: The foundation of all modulation sources
   ```kotlin
   interface CvSignal {
       fun getValue(timeSeconds: Double): Float
   }
   ```

2. **ModulatableParameter**: A parameter that can be influenced by multiple CV modulators
   ```kotlin
   class ModulatableParameter(
       var baseValue: Float = 0.0f,
       val historySize: Int = 200
   )
   ```

3. **ModulationRegistry**: Central repository for all CV signals
   ```kotlin
   object ModulationRegistry {
       // Stores raw signal data
       private val rawSignalData = ConcurrentHashMap<String, Float>()
       // Stores signal history
       val history = ConcurrentHashMap<String, CvHistoryBuffer>()
   }
   ```

4. **Audio Engine**: Processes audio input and extracts modulation signals
   ```kotlin
   class AudioEngine(context: Context) {
       val sourceManager = AudioSourceManager(appContext)
       private val extractor = AmplitudeExtractor()
   }
   ```

## CV Signal Sources

### Audio-Derived Sources

Audio-derived sources extract control voltages directly from audio input, providing real-time audio reactivity.

1. **Amplitude Source** (`amp`): Overall audio level
   ```kotlin
   class AmplitudeCv(
       updateRate: Float = 60f,
       attackMs: Float = 15f,
       releaseMs: Float = 150f,
       var referenceLevel: Float = 0.1f
   ) : CvSignal
   ```

2. **Frequency Band Sources**:
   - `bass`: Low frequency energy (below 150Hz)
   - `mid`: Mid frequency energy (around 1kHz)
   - `high`: High frequency energy (above 5kHz)

3. **Transient Sources**:
   - `bassFlux`: Rate of change in bass energy
   - `onset`: Normalized transient detection
   - `accent`: Smoothed, weighted onset with decay

Implementation in AudioEngine:
```kotlin
val lowPass = BiquadFilter(BiquadFilter.Type.LOWPASS, 44100f, 150f)
val midPass = BiquadFilter(BiquadFilter.Type.BANDPASS, 44100f, 1000f)
val highPass = BiquadFilter(BiquadFilter.Type.HIGHPASS, 44100f, 5000f)

// In the processing loop:
val bassFlux = max(0f, bass - prevBass)
val onsetRaw = (bassFlux * 1.0f) + (midFlux * 0.6f) + (highFlux * 0.3f)
val onsetNormalized = (onsetRaw / 0.05f).coerceIn(0f, 2f)

// Update registry with normalized values
ModulationRegistry.update("amp", (amp / ref).coerceIn(0f, 2f))
ModulationRegistry.update("bass", (bass / ref).coerceIn(0f, 2f))
ModulationRegistry.update("mid", (mid / ref).coerceIn(0f, 2f))
ModulationRegistry.update("high", (high / ref).coerceIn(0f, 2f))
ModulationRegistry.update("bassFlux", (bassFlux / 0.05f).coerceIn(0f, 2f))
ModulationRegistry.update("onset", onsetNormalized)
ModulationRegistry.update("accent", accentLevel)
```

### Beat-Synchronized Sources

Beat-synchronized sources are tied to the application's beat clock, providing rhythmic control that stays in sync with music.

1. **Beat Phase** (`beatPhase`): Cyclic value (0-1) that completes one cycle per beat
   ```kotlin
   class BeatPhaseCv(private val beatClock: BeatClock) : CvSignal {
       override fun getValue(timeSeconds: Double): Float {
           return beatClock.getPhase(timeSeconds)
       }
   }
   ```

2. **Beat Waveforms**: Generated from beat phase, including:
   - Sine waves
   - Triangle waves
   - Square waves

Implementation in ModulatableParameter:
```kotlin
val localPhase = ((beats / mod.subdivision) + mod.phaseOffset) % 1.0
val positivePhase = if (localPhase < 0) (localPhase + 1.0) else localPhase
calculateWaveform(mod.waveform, positivePhase, mod.slope)

// Waveform calculation
private fun calculateWaveform(waveform: Waveform, phase: Double, slope: Float): Float {
    return when(waveform) {
        Waveform.SINE -> (sin(phase * 2.0 * Math.PI).toFloat() * 0.5f) + 0.5f
        Waveform.TRIANGLE -> {
            val s = slope.toDouble()
            if (s <= 0.001) (1.0 - phase).toFloat()
            else if (s >= 0.999) phase.toFloat()
            else if (phase < s) (phase / s).toFloat()
            else ((1.0 - phase) / (1.0 - s)).toFloat()
        }
        Waveform.SQUARE -> if (phase < slope) 1.0f else 0.0f
    }
}
```

### LFO Sources

Low Frequency Oscillator (LFO) sources generate continuous cyclic waveforms, independent of audio or beat.

1. **Time-Based LFO** (`lfo`): Generates waveforms based on real-time
   - Three speed modes: SLOW, MEDIUM, FAST
   - Adjustable subdivisions and waveforms

Implementation in ModulatableParameter:
```kotlin
val seconds = ModulationRegistry.getElapsedRealtimeSec()
val period = when (mod.lfoSpeedMode) {
    LfoSpeedMode.FAST -> mod.subdivision * 10.0     // 0 to 10 seconds
    LfoSpeedMode.MEDIUM -> mod.subdivision * 900.0  // 0 to 15 minutes
    LfoSpeedMode.SLOW -> mod.subdivision * 86400.0  // 0 to 24 hours
}.coerceAtLeast(0.001)

val localPhase = ((seconds / period) + mod.phaseOffset) % 1.0
val positivePhase = if (localPhase < 0) (localPhase + 1.0) else localPhase
calculateWaveform(mod.waveform, positivePhase, mod.slope)
```

### Sample and Hold

The Sample and Hold source generates random values that change periodically and optionally glide between values.

1. **Sample and Hold** (`sampleAndHold`): Deterministically random values that change on beat divisions
   ```kotlin
   class SampleAndHoldCv : CvSignal {
       fun getValue(phase: Double, slope: Float, totalBeats: Double, subdivision: Double): Float
   }
   ```

Key features:
- Deterministic randomness based on beat count
- Glide control for smooth or stepped transitions
- Independent sequences for each parameter

Implementation in SampleAndHoldCv:
```kotlin
// Get the current and previous random values for this position
val (currentValue, previousValue) = getValuesForPosition(totalBeats, subdivision)

// Calculate how far we should be in the glide
val glideAmount = if (phase < slope) {
    // During glide phase - linear interpolation from previous to current
    (phase / slope).toFloat()
} else {
    // Hold phase - at the current value
    1.0f
}

// Interpolate between previous and current values based on glide position
return previousValue + (currentValue - previousValue) * glideAmount
```

## Parameter Modulation System

### ModulatableParameter Class

The `ModulatableParameter` class is the core of the modulation system, combining a base value with multiple CV modulators.

```kotlin
class ModulatableParameter(
    var baseValue: Float = 0.0f,
    val historySize: Int = 200
) {
    val modulators = CopyOnWriteArrayList<CvModulator>()
    val history = CvHistoryBuffer(historySize)
    
    var value: Float = baseValue
        private set

    fun evaluate(): Float {
        var result = baseValue
        
        for (mod in modulators) {
            if (mod.bypassed) continue
            
            val finalCv = getMappedSourceValue(mod) // Get CV value from source
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
```

Key aspects:
- Thread-safe modulator list using `CopyOnWriteArrayList`
- History buffer for visualization and debugging
- Value clamping to ensure valid parameter ranges (0-1)
- Called at animation rate (120Hz) from the renderer

### Modulation Operators

Modulation operators determine how a CV signal affects the parameter value.

```kotlin
enum class ModulationOperator {
    ADD, MUL
}
```

1. **ADD**: Additive modulation - adds the weighted signal to the base value
   ```kotlin
   result + modAmount
   ```

2. **MUL**: Multiplicative modulation - multiplies the base value by the weighted signal plus 1
   ```kotlin
   result * (1.0f + modAmount)
   ```

### Waveform Types

The system supports multiple waveform types for oscillator-based modulation sources.

```kotlin
enum class Waveform {
    SINE, TRIANGLE, SQUARE
}
```

1. **SINE**: Smooth sinusoidal oscillation, good for gentle movement
   ```kotlin
   (sin(phase * 2.0 * Math.PI).toFloat() * 0.5f) + 0.5f
   ```

2. **TRIANGLE**: Linear ramp up and down, with adjustable slope
   ```kotlin
   if (phase < s) (phase / s).toFloat() else ((1.0 - phase) / (1.0 - s)).toFloat()
   ```

3. **SQUARE**: Instant transitions between high and low, with adjustable duty cycle
   ```kotlin
   if (phase < slope) 1.0f else 0.0f
   ```

## Signal Processing Chain

### Audio Capture

The audio capture process supports multiple input sources:

1. **Microphone** (`AudioSourceType.MIC`): External audio via device microphone
2. **Unprocessed** (`AudioSourceType.UNPROCESSED`): Raw microphone input with minimal processing
3. **Internal** (`AudioSourceType.INTERNAL`): Device audio (requires projection permission)

```kotlin
val audioRecord: AudioRecord? = sourceManager.buildAudioRecord(
    audioSourceType,
    sampleRate,
    audioFormat,
    channelConfig,
    bufferSize,
    projection
)
```

### Audio Analysis

Audio signals are analyzed in multiple stages:

1. **Frequency Separation**: Audio is split into frequency bands using Biquad filters
   ```kotlin
   lowData[i] = lowPass.process(audioData[i])
   midData[i] = midPass.process(audioData[i])
   highData[i] = highPass.process(audioData[i])
   ```

2. **Amplitude Extraction**: RMS amplitude is calculated for each band
   ```kotlin
   val amp = extractor.calculateRms(audioData.copyOfRange(0, read))
   val bass = extractor.calculateRms(lowData.copyOfRange(0, read))
   val mid = extractor.calculateRms(midData.copyOfRange(0, read))
   val high = extractor.calculateRms(highData.copyOfRange(0, read))
   ```

3. **Transient Detection**: Spectral flux is calculated to detect onsets
   ```kotlin
   val bassFlux = max(0f, bass - prevBass)
   val midFlux = max(0f, mid - prevMid)
   val highFlux = max(0f, high - prevHigh)
   val onsetRaw = (bassFlux * 1.0f) + (midFlux * 0.6f) + (highFlux * 0.3f)
   ```

4. **Accent Processing**: Onsets are processed into an accent signal with decay
   ```kotlin
   if (onsetNormalized > accentLevel) {
       accentLevel = onsetNormalized
   } else {
       accentLevel *= 0.88f
   }
   ```

### Signal Normalization

Raw signals are normalized to ensure consistent behavior:

1. **Amplitude Normalization**: RMS values are scaled relative to a reference level
   ```kotlin
   val ref = 0.1f // Reference RMS level
   ModulationRegistry.update("amp", (amp / ref).coerceIn(0f, 2f))
   ```

2. **Flux Normalization**: Change values are scaled for predictable behavior
   ```kotlin
   ModulationRegistry.update("bassFlux", (bassFlux / 0.05f).coerceIn(0f, 2f))
   ```

3. **Beat Normalization**: Beat-related signals are normalized to 0-1 range
   ```kotlin
   val positivePhase = if (localPhase < 0) (localPhase + 1.0) else localPhase
   ```

### Parameter Application

Signals flow from the registry to visual parameters:

1. **Registry Access**: CV signals are retrieved from the registry
   ```kotlin
   finalCv = ModulationRegistry.get(mod.sourceId)
   ```

2. **Weighting**: Signals are scaled by a weight parameter
   ```kotlin
   val modAmount = finalCv * mod.weight
   ```

3. **Application**: Weighted signals modify parameter values
   ```kotlin
   result = when (mod.operator) {
       ModulationOperator.ADD -> result + modAmount
       ModulationOperator.MUL -> result * (1.0f + modAmount)
   }
   ```

4. **Clamping**: Final values are constrained to valid ranges
   ```kotlin
   value = result.coerceIn(0f, 1f)
   ```

## CV Signal Modifiers

The system includes various signal modifiers that can process CV signals before application.

### Gain and Offset

1. **GainCv**: Multiplies the signal by a constant factor
   ```kotlin
   class GainCv(private val source: CvSignal, private val gain: Float) : CvSignal {
       override fun getValue(timeSeconds: Double): Float = source.getValue(timeSeconds) * gain
   }
   ```

2. **OffsetCv**: Adds a constant value to the signal
   ```kotlin
   class OffsetCv(private val source: CvSignal, private val offset: Float) : CvSignal {
       override fun getValue(timeSeconds: Double): Float = source.getValue(timeSeconds) + offset
   }
   ```

### Power Curve

The PowerCv modifier applies an exponent to the signal, useful for creating non-linear responses.

```kotlin
class PowerCv(
    private val source: CvSignal,
    private val exponent: Float = 1.0f
) : CvSignal {
    override fun getValue(timeSeconds: Double): Float {
        val value = source.getValue(timeSeconds)
        return if (value < 0f && exponent != 1.0f) 0f else value.pow(exponent)
    }
}
```

### Clipping

The ClipCv modifier constrains signal values to a specified range.

```kotlin
class ClipCv(
    private val source: CvSignal,
    private val min: Float,
    private val max: Float
) : CvSignal {
    override fun getValue(timeSeconds: Double): Float {
        return source.getValue(timeSeconds).coerceIn(min, max)
    }
}
```

### Composite Modifiers

The ModifiedCv class chains multiple modifiers into a single processing pipeline.

```kotlin
class ModifiedCv(
    private val source: CvSignal,
    private val exponent: Float = 1.0f,
    private val gain: Float = 1.0f,
    private val offset: Float = 0.0f,
    private val min: Float = 0.0f,
    private val max: Float = 1.0f
) : CvSignal {
    private val powerCv = PowerCv(source, exponent)
    private val gainCv = GainCv(powerCv, gain)
    private val offsetCv = OffsetCv(gainCv, offset)
    private val clipCv = ClipCv(offsetCv, min, max)

    override fun getValue(timeSeconds: Double): Float {
        return clipCv.getValue(timeSeconds)
    }
}
```

## Modulation Registry

### Central Signal Repository

The ModulationRegistry serves as the centralized repository for all CV signals in the system.

```kotlin
object ModulationRegistry {
    private val rawSignalData = ConcurrentHashMap<String, Float>().apply {
        put("amp", 0f)
        put("bass", 0f)
        put("mid", 0f)
        put("high", 0f)
        put("bassFlux", 0f)
        put("onset", 0f)
        put("accent", 0f)
        put("beatPhase", 0f)
        put("totalBeats", 0f)
        put("bpm", 120f)
    }
    
    fun update(name: String, value: Float) {
        rawSignalData[name] = value
    }
    
    fun get(name: String): Float = rawSignalData[name] ?: 0f
}
```

Key features:
- Thread-safe concurrent data structure
- Default values for all signals
- Simple update and access methods

### Signal History Buffering

The registry maintains history buffers for visualization and debugging.

```kotlin
val history = ConcurrentHashMap<String, CvHistoryBuffer>().apply {
    rawSignalData.keys.forEach { put(it, CvHistoryBuffer(200)) }
}

// Update history buffers
rawSignalData.forEach { (k, v) ->
    history[k]?.add(v)
}
```

### Precision Beat Clock

The registry includes a high-precision beat clock system for temporal synchronization.

```kotlin
// Anchor State for Precision Clock
private var anchorBeats = 0.0
private var anchorBpm = 120f
private var anchorTimeNs = System.nanoTime()

fun updateBeatAnchor(beats: Double, bpm: Float, timeNs: Long) {
    anchorBeats = beats
    anchorBpm = bpm
    anchorTimeNs = timeNs
    update("totalBeats", beats.toFloat())
    update("bpm", bpm)
}

fun getSynchronizedTotalBeats(): Double {
    val now = System.nanoTime()
    val elapsedSec = (now - anchorTimeNs) / 1_000_000_000.0
    val beatDelta = elapsedSec * (anchorBpm / 60.0)
    return anchorBeats + beatDelta
}
```

Key aspects:
- Nanosecond timing precision
- Interpolation between audio analysis updates
- Anchor-based synchronization

## Audio Engine

### Signal Extraction

The AudioEngine extracts modulation signals from audio input:

```kotlin
val extractor = AmplitudeExtractor()

// In the processing loop:
val amp = extractor.calculateRms(audioData.copyOfRange(0, read))
```

Amplitude calculation methods:
```kotlin
fun calculateRms(pcm: FloatArray): Float {
    if (pcm.isEmpty()) return 0f
    var sum = 0f
    for (sample in pcm) {
        sum += sample * sample
    }
    return sqrt(sum / pcm.size)
}
```

### Frequency Bands

Audio is separated into frequency bands using Biquad filters:

```kotlin
private val lowPass = BiquadFilter(BiquadFilter.Type.LOWPASS, 44100f, 150f)
private val midPass = BiquadFilter(BiquadFilter.Type.BANDPASS, 44100f, 1000f)
private val highPass = BiquadFilter(BiquadFilter.Type.HIGHPASS, 44100f, 5000f)

// In the processing loop:
for (i in 0 until read) {
    lowData[i] = lowPass.process(audioData[i])
    midData[i] = midPass.process(audioData[i])
    highData[i] = highPass.process(audioData[i])
}
```

### Onset Detection

The engine includes spectral flux-based onset detection:

```kotlin
val bassFlux = max(0f, bass - prevBass)
val midFlux = max(0f, mid - prevMid)
val highFlux = max(0f, high - prevHigh)

// Weighted combination of flux values for onset detection
val onsetRaw = (bassFlux * 1.0f) + (midFlux * 0.6f) + (highFlux * 0.3f)
val onsetNormalized = (onsetRaw / 0.05f).coerceIn(0f, 2f)

// Adaptive threshold for reliable onset detection
beatThreshold = (beatThreshold * 0.99f) + (onsetNormalized * 0.01f).coerceAtLeast(0.3f)
```

### BPM Detection

The engine includes automatic BPM detection from audio onsets:

```kotlin
if (onsetNormalized > beatThreshold && lastOnsetNormalized <= beatThreshold) {
    val interval = currentTime - lastBeatTime
    if (interval in minIntervalNs..maxIntervalNs) {
        beatIntervals.add(interval)
        if (beatIntervals.size > maxIntervals) beatIntervals.removeAt(0)
        val medianInterval = beatIntervals.sorted()[beatIntervals.size / 2]
        estimatedBpm = 60_000_000_000f / medianInterval
        
        // Smart sync logic for strong beats
    }
    lastBeatTime = currentTime
}
```

Key features:
- Adaptive thresholding for noise resistance
- Median filtering for stable BPM estimation
- Valid range limiting (40-200 BPM)
- Phase synchronization for strong beats

## Integration with Rendering

### Parameter Update Cycle

The modulation system integrates with the rendering system through the parameter update cycle:

```kotlin
// In SpiralRenderer.onDrawFrame()
visualSource?.update() // For single-mandala mode
for (i in 0..3) slotSources[i].update() // For the 4 mixer slots
mixerParams.values.forEach { it.evaluate() } // Evaluates all mixers
```

This ensures all parameters are updated before each frame is rendered.

### Rendering Impact

The modulated parameters directly affect the visual output:

1. **Mandala Parameters**: Shape, color, and motion properties
   ```kotlin
   GLES30.glUniform1f(uGlobalRotationLocation, (p["Rotation"]?.value ?: 0f) * 2f * PI.toFloat())
   ```

2. **Mixer Parameters**: Blend modes, balance, and effects
   ```kotlin
   GLES30.glUniform1f(uFBRotateLoc, ((fx["FB_ROTATE"] ?: 0.5f) - 0.5f) * 10f * (PI.toFloat() / 180f))
   ```

3. **Feedback Effects**: Zoom, rotation, blur, and other effects
   ```kotlin
   val mappedGain = if (gain <= 0.01f) 0.0f else 1.1f + (gain * 0.15f)
   GLES30.glUniform1f(uFBGainLoc, mappedGain)
   ```

The modulation system is the key to the application's dynamic, audio-reactive behavior, creating a seamless connection between sound and visuals.