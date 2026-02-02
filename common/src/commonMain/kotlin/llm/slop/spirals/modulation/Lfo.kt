package llm.slop.spirals.modulation

import kotlin.math.sin

class Lfo(
    val rate: Float = 1.0f, // Cycles per second (Hz)
    val phase: Float = 0.0f // Phase offset (0.0 to 1.0)
) {
    /**
     * Calculates the LFO's value at a given time.
     * @param timeSeconds The total elapsed time in seconds.
     * @return A value typically in the range [-1.0, 1.0].
     */
    fun getValue(timeSeconds: Float): Float {
        val twoPi = 2.0f * Math.PI.toFloat()
        val phaseOffset = phase * twoPi
        return sin(timeSeconds * rate * twoPi + phaseOffset)
    }
}
