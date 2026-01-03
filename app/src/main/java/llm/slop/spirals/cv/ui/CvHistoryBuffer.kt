package llm.slop.spirals.cv.ui

/**
 * A ring buffer to store the last N samples of a CV signal.
 */
class CvHistoryBuffer(val size: Int) {
    private val buffer = FloatArray(size)
    private var index = 0

    fun add(value: Float) {
        buffer[index] = value
        index = (index + 1) % size
    }

    /**
     * Returns the samples in chronological order (oldest to newest).
     */
    fun getSamples(): FloatArray {
        val result = FloatArray(size)
        for (i in 0 until size) {
            result[i] = buffer[(index + i) % size]
        }
        return result
    }
}
