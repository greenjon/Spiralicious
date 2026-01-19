package llm.slop.spirals.cv

import kotlin.random.Random

/**
 * A CV signal that generates a new random value on a clock trigger and glides to it.
 * This simulates a classic "Sample and Hold" synthesizer module.
 */
class SampleAndHoldCv : CvSignal {

    private var previousValue = 0f
    private var currentValue = 0.5f
    private var lastTriggerTime = -1.0
    private val rng = Random.Default

    /**
     * This getValue method will be called from within ModulatableParameter.evaluate()
     * It relies on the phase and slope being calculated and passed in from the modulator.
     */
    fun getValue(phase: Double, slope: Float, interval: Double): Float {
        val currentTime = System.currentTimeMillis() / 1000.0

        // Detect the rising edge of the trigger (phase resets)
        if (isNewCycle(phase, interval)) {
            lastTriggerTime = currentTime
            previousValue = currentValue
            currentValue = rng.nextFloat()
        }

        val glideTime = interval * slope
        val timeSinceTrigger = currentTime - lastTriggerTime

        return if (timeSinceTrigger < glideTime && glideTime > 0) {
            // Glide phase
            val glideProgress = (timeSinceTrigger / glideTime).toFloat()
            // Linear interpolation
            previousValue + (currentValue - previousValue) * glideProgress
        } else {
            // Hold phase
            currentValue
        }
    }

    private var lastPhase = -1.0
    private fun isNewCycle(phase: Double, interval: Double): Boolean {
        // A new cycle starts when the phase resets (e.g., goes from ~0.99 to 0.0)
        // We also check if enough time has passed to prevent spurious triggers.
        val phaseWentBackwards = phase < lastPhase
        val timeSinceLastTrigger = (System.currentTimeMillis() / 1000.0) - lastTriggerTime
        val readyForNextTrigger = timeSinceLastTrigger > (interval * 0.5) // Debounce

        val isNew = lastTriggerTime < 0.0 || (phaseWentBackwards && readyForNextTrigger)
        lastPhase = phase
        return isNew
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
