package llm.slop.spirals.defaults

import llm.slop.spirals.cv.Waveform
import llm.slop.spirals.models.STANDARD_BEAT_VALUES
import llm.slop.spirals.models.SpeedSource

/**
 * Data structures for storing default values for the Random Set Editor.
 * These values are used when a parameter is "unconfigured" (no specific constraints provided).
 */

/**
 * Top-level container for all Random Set default values
 */
data class RandomSetDefaults(
    val armDefaults: ArmDefaults = ArmDefaults(),
    val rotationDefaults: RotationDefaults = RotationDefaults(),
    val hueOffsetDefaults: HueOffsetDefaults = HueOffsetDefaults()
)

/**
 * Default values for arm parameters (L1-L4)
 */
data class ArmDefaults(
    // Base length range (0-100 scale)
    val baseLengthMin: Int = 0,
    val baseLengthMax: Int = 100,
    
    // Movement source probabilities
    val beatProbability: Float = 0.5f,
    val lfoProbability: Float = 0.5f,
    
    // Beat division range 
    val beatDivMin: Float = STANDARD_BEAT_VALUES.first(),  // 1/16
    val beatDivMax: Float = 32f,
    
    // Waveform selection probabilities
    val sineProbability: Float = 0.33f,
    val triangleProbability: Float = 0.34f,
    val squareProbability: Float = 0.33f,
    
    // Weight/intensity range (-100 to 100)
    val weightMin: Int = -100,
    val weightMax: Int = 100,
    
    // LFO time range
    val lfoTimeMin: Float = 1.0f,
    val lfoTimeMax: Float = 60.0f
) {
    /**
     * Factory method to create default values with normalized probabilities
     */
    companion object {
        fun createWithNormalizedProbabilities(
            baseLengthMin: Int = 0,
            baseLengthMax: Int = 100,
            beatProbability: Float = 0.5f,
            sineProbability: Float = 0.33f,
            triangleProbability: Float = 0.34f,
            squareProbability: Float = 0.33f,
            beatDivMin: Float = STANDARD_BEAT_VALUES.first(),
            beatDivMax: Float = 32f,
            weightMin: Int = -100,
            weightMax: Int = 100,
            lfoTimeMin: Float = 1.0f,
            lfoTimeMax: Float = 60.0f
        ): ArmDefaults {
            // Normalize movement source probabilities
            val totalMovementProb = beatProbability + (1 - beatProbability)
            val normalizedBeatProb = beatProbability / totalMovementProb
            
            // Normalize waveform probabilities
            val totalWaveformProb = sineProbability + triangleProbability + squareProbability
            val normalizedSineProb = sineProbability / totalWaveformProb
            val normalizedTriangleProb = triangleProbability / totalWaveformProb
            val normalizedSquareProb = squareProbability / totalWaveformProb
            
            return ArmDefaults(
                baseLengthMin = baseLengthMin,
                baseLengthMax = baseLengthMax,
                beatProbability = normalizedBeatProb,
                lfoProbability = 1 - normalizedBeatProb,
                beatDivMin = beatDivMin,
                beatDivMax = beatDivMax,
                sineProbability = normalizedSineProb,
                triangleProbability = normalizedTriangleProb,
                squareProbability = normalizedSquareProb,
                weightMin = weightMin,
                weightMax = weightMax,
                lfoTimeMin = lfoTimeMin,
                lfoTimeMax = lfoTimeMax
            )
        }
    }
    
    /**
     * Randomly select a waveform based on the configured probabilities
     */
    fun getRandomWaveform(random: kotlin.random.Random): Waveform {
        val roll = random.nextFloat()
        val normalizedSine = sineProbability
        val normalizedTriangle = triangleProbability
        
        return when {
            roll < normalizedSine -> Waveform.SINE
            roll < normalizedSine + normalizedTriangle -> Waveform.TRIANGLE
            else -> Waveform.SQUARE
        }
    }
    
    /**
     * Randomly select a movement source based on probabilities
     */
    fun getRandomMovementSource(random: kotlin.random.Random): String {
        return if (random.nextFloat() < beatProbability) "beatPhase" else "lfo1"
    }
}

/**
 * Default values for rotation parameters
 */
data class RotationDefaults(
    // Direction probabilities
    val clockwiseProbability: Float = 0.5f,
    val counterClockwiseProbability: Float = 0.5f,
    
    // Speed source probabilities
    val beatProbability: Float = 0.7f,
    val lfoProbability: Float = 0.3f,
    
    // Beat division range
    val beatDivMin: Float = 4f,
    val beatDivMax: Float = 128f,
    
    // LFO time range
    val lfoTimeMin: Float = 5.0f,
    val lfoTimeMax: Float = 30.0f
) {
    /**
     * Randomly select a direction (slope) based on probabilities
     */
    fun getRandomDirection(random: kotlin.random.Random): Float {
        val normalizedCw = clockwiseProbability / (clockwiseProbability + counterClockwiseProbability)
        return if (random.nextFloat() < normalizedCw) 0f else 1f
    }
    
    /**
     * Randomly select a speed source based on probabilities
     */
    fun getRandomSpeedSource(random: kotlin.random.Random): SpeedSource {
        val normalizedBeat = beatProbability / (beatProbability + lfoProbability)
        return if (random.nextFloat() < normalizedBeat) SpeedSource.BEAT else SpeedSource.LFO
    }
}

/**
 * Default values for hue offset (color cycling) parameters
 */
data class HueOffsetDefaults(
    // Direction probabilities
    val forwardProbability: Float = 0.5f,
    val reverseProbability: Float = 0.5f,
    
    // Speed source probabilities
    val beatProbability: Float = 0.8f,
    val lfoProbability: Float = 0.2f,
    
    // Beat division range
    val beatDivMin: Float = 4f,
    val beatDivMax: Float = 16f,
    
    // LFO time range
    val lfoTimeMin: Float = 10.0f,
    val lfoTimeMax: Float = 60.0f
) {
    /**
     * Randomly select a direction (slope) based on probabilities
     */
    fun getRandomDirection(random: kotlin.random.Random): Float {
        val normalizedForward = forwardProbability / (forwardProbability + reverseProbability)
        return if (random.nextFloat() < normalizedForward) 1f else 0f
    }
    
    /**
     * Randomly select a speed source based on probabilities
     */
    fun getRandomSpeedSource(random: kotlin.random.Random): SpeedSource {
        val normalizedBeat = beatProbability / (beatProbability + lfoProbability)
        return if (random.nextFloat() < normalizedBeat) SpeedSource.BEAT else SpeedSource.LFO
    }
}