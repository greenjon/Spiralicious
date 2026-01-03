package llm.slop.spirals.cv

/**
 * A clock that maintains CV time.
 * Decoupled from system time for determinism and pausing.
 */
class CvClock {
    var cvTimeSeconds: Double = 0.0
        private set

    fun tick(deltaSeconds: Float) {
        cvTimeSeconds += deltaSeconds.toDouble()
    }

    fun reset() {
        cvTimeSeconds = 0.0
    }
}
