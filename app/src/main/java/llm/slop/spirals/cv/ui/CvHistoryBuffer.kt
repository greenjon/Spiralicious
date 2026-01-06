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
     * Copies the samples in chronological order (oldest to newest) into the target array.
     * Prevents allocation in the draw loop.
     */
    fun copyTo(target: FloatArray) {
        val count = size.coerceAtMost(target.size)
        for (i in 0 until count) {
            target[i] = buffer[(index + i) % size]
        }
    }

    /**
     * Returns the samples in chronological order (oldest to newest).
     * Warning: This allocates a new array. Use copyTo() for real-time loops.
     */
    fun getSamples(): FloatArray {
        val result = FloatArray(size)
        copyTo(result)
        return result
    }
}
