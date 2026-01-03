package llm.slop.spirals.cv.modifiers

import llm.slop.spirals.cv.CvSignal

/**
 * Multiplies the source CV signal by a gain factor.
 */
class GainCv(private val source: CvSignal, private val gain: Float) : CvSignal {
    override fun getValue(timeSeconds: Double): Float = source.getValue(timeSeconds) * gain
}
