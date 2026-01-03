package llm.slop.spirals.cv.modifiers

import llm.slop.spirals.cv.CvSignal

/**
 * Clips the source CV signal between min and max values.
 */
class ClipCv(
    private val source: CvSignal,
    private val min: Float,
    private val max: Float
) : CvSignal {
    override fun getValue(timeSeconds: Double): Float {
        return source.getValue(timeSeconds).coerceIn(min, max)
    }
}
