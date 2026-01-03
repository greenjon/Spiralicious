package llm.slop.spirals.cv.modifiers

import llm.slop.spirals.cv.CvSignal

/**
 * A composite CV signal that chains Power, Gain, Offset, and Clip modifiers.
 * Formula: Clip(Gain(Power(source, exponent)) + offset, min, max)
 */
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
