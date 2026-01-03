package llm.slop.spirals.cv

/**
 * Interface for a Control Voltage signal.
 */
interface CvSignal {
    /**
     * Returns the signal value at the given time.
     * Constraint: No allocations in this method.
     */
    fun getValue(timeSeconds: Double): Float
}

/**
 * A CV signal that returns a constant value.
 */
class ConstantCv(private val value: Float) : CvSignal {
    override fun getValue(timeSeconds: Double): Float = value
}
