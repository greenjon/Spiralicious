package llm.slop.spirals.cv.modifiers

import llm.slop.spirals.cv.CvSignal

/**
 * Adds an offset to the source CV signal.
 */
class OffsetCv(private val source: CvSignal, private val offset: Float) : CvSignal {
    override fun getValue(timeSeconds: Double): Float = source.getValue(timeSeconds) + offset
}
