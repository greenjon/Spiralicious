package llm.slop.spirals.cv.modifiers

import llm.slop.spirals.cv.CvSignal
import kotlin.math.pow

/**
 * Applies a power curve (exponent) to the source CV signal.
 * Formula: source.getValue(time).pow(exponent)
 */
class PowerCv(
    private val source: CvSignal,
    private val exponent: Float = 1.0f
) : CvSignal {
    override fun getValue(timeSeconds: Double): Float {
        val value = source.getValue(timeSeconds)
        // Ensure we don't pow a negative number unless exponent is 1
        return if (value < 0f && exponent != 1.0f) 0f else value.pow(exponent)
    }
}
