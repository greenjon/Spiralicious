package llm.slop.spirals.cv.core

import kotlin.math.abs

/**
 * Represents a parameter that can be modulated by one or more CV signals.
 *
 * @property baseValue The base value of the parameter before modulation.
 * @property modulators A list of modulators that affect the parameter's value.
 * @property value The final computed value of the parameter after modulation.
 */
class ModulatableParameter(var baseValue: Float) {
    val modulators = mutableListOf<Modulator>()
    var value: Float = baseValue
        private set

    /**
     * Recomputes the parameter's value based on its modulators.
     * This should be called once per frame or update cycle.
     */
    fun evaluate() {
        value = baseValue + modulators.sumOf { it.evaluate().toDouble() }.toFloat()
        value = value.coerceIn(0f, 1f)
    }

    /**
     * Adds a modulator to the parameter.
     *
     * @param modulator The modulator to add.
     */
    fun addModulator(modulator: Modulator) {
        if (!modulators.contains(modulator)) {
            modulators.add(modulator)
        }
    }

    /**
     * Removes a modulator from the parameter.
     *
     * @param modulator The modulator to remove.
     */
    fun removeModulator(modulator: Modulator) {
        modulators.remove(modulator)
    }
}

/**
 * Represents a CV signal modulator.
 *
 * @property cvSignal The CV signal source for the modulator.
 * @property amount The modulation amount, ranging from -1.0 to 1.0.
 * @property isInverted Toggles the polarity of the modulation.
 */
data class Modulator(
    val cvSignal: CvSignal,
    var amount: Float,
    var isInverted: Boolean = false
) {
    /**
     * Evaluates the modulator's current value.
     *
     * @return The computed modulation value.
     */
    fun evaluate(): Float {
        val signalValue = if (isInverted) 1.0f - cvSignal.value else cvSignal.value
        return signalValue * amount
    }

    /**
     * Checks if the modulator is active (i.e., has a non-zero modulation amount).
     *
     * @return `true` if the modulator is active, `false` otherwise.
     */
    fun isActive(): Boolean {
        return abs(amount) > 0.001
    }
}

/**
 * A placeholder for a CV signal, providing a value between 0.0 and 1.0.
 */
interface CvSignal {
    val value: Float
}
