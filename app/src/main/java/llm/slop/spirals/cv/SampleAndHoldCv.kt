package llm.slop.spirals.cv

import kotlin.math.floor
import kotlin.random.Random

/**
 * A CV signal that generates a new random value on a clock trigger and glides to it.
 * This simulates a classic "Sample and Hold" synthesizer module.
 * 
 * This implementation is "stateless" - it calculates current and previous values
 * based solely on the beat count, ensuring that multiple parameters using the
 * same SampleAndHold instance with different subdivisions all get independent
 * random sequences.
 */
class SampleAndHoldCv : CvSignal {
    
    /**
     * Gets a deterministic random value for a specific beat cycle.
     * This ensures consistent values within the same cycle, even across
     * different parameters/controls.
     */
    private fun getRandomValueForCycle(cycleIndex: Int, seed: Int = 0): Float {
        // Create a deterministic random generator seeded by the cycle index
        val rng = Random(cycleIndex + seed)
        return rng.nextFloat()
    }

    /**
     * Gets the current and previous values based on the total beats and subdivision.
     * This ensures that each parameter with a unique subdivision gets its own
     * independent random sequence.
     */
    private fun getValuesForPosition(totalBeats: Double, subdivision: Double): Pair<Float, Float> {
        // Determine which cycle we're in based on beats and subdivision
        val currentCycle = floor(totalBeats / subdivision).toInt()
        val previousCycle = currentCycle - 1
        
        // The unique seed ensures different parameters get different sequences
        val seed = subdivision.hashCode()
        
        // Get the values deterministically based on cycle index
        val currentValue = getRandomValueForCycle(currentCycle, seed)
        val previousValue = getRandomValueForCycle(previousCycle, seed)
        
        return Pair(currentValue, previousValue)
    }

    /**
     * This getValue method will be called from within ModulatableParameter.evaluate()
     * It relies on the phase and slope being calculated and passed in from the modulator.
     * 
     * @param phase Current position in the cycle (0.0-1.0)
     * @param slope Amount of time to spend gliding (0.0-1.0)
     * @param totalBeats Total beats elapsed (used for deterministic randomness)
     * @param subdivision How many beats per cycle
     */
    fun getValue(phase: Double, slope: Float, totalBeats: Double, subdivision: Double): Float {
        // Get the current and previous random values for this position
        val (currentValue, previousValue) = getValuesForPosition(totalBeats, subdivision)
        
        // Debug glide calculation
        android.util.Log.d("GLIDE_DEBUG", "SampleAndHold phase: $phase, slope: $slope")
        
        // Calculate how far we should be in the glide
        val glideAmount = if (phase < slope) {
            // During glide phase - linear interpolation from previous to current
            val amount = (phase / slope).toFloat()
            android.util.Log.d("GLIDE_DEBUG", "In glide phase, amount: $amount")
            amount
        } else {
            // Hold phase - at the current value
            android.util.Log.d("GLIDE_DEBUG", "In hold phase, amount: 1.0")
            1.0f
        }
        
        // Interpolate between previous and current values based on glide position
        return previousValue + (currentValue - previousValue) * glideAmount
    }

    /**
     * This is the interface method from CvSignal.
     * It is not used by this implementation, as the calculation is more complex
     * and is handled directly within ModulatableParameter.evaluate().
     */
    override fun getValue(timeSeconds: Double): Float {
        // This method is intentionally left blank.
        // The logic is handled in the overloaded getValue that takes phase and slope.
        return 0.5f
    }
}
