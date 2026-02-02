package llm.slop.spirals.models.mandala

import llm.slop.spirals.ModulatableParameter
import llm.slop.spirals.models.ModulatableParameterData

data class MandalaRatio(
    val iter: Int = 2048,
    val m: Int = 5,
    val n: Int = 3,
    val p: Float = 10.0f,
    val q: Float = 1.0f,
    val r: Float = 1.0f,
    val baseScale: Float = 0.1f,

    val feedbackAmount: ModulatableParameter = ModulatableParameter(ModulatableParameterData(0.98f)),
    val feedbackScale: ModulatableParameter = ModulatableParameter(ModulatableParameterData(1.002f)),
    val feedbackRotation: ModulatableParameter = ModulatableParameter(ModulatableParameterData(0.001f))
) {
    companion object {
        // Update the default to create a visibly modulated effect for testing
        val default = MandalaRatio(
            iter = 4096,
            m = 7,
            n = 6,
            p = 10f,
            r = 1.5f,
            baseScale = 0.08f,
            feedbackRotation = ModulatableParameter(
                ModulatableParameterData(
                    baseValue = 0.0f,
                    modSource = "lfo1", // We will create this LFO
                    modAmount = 0.005f
                )
            ),
            feedbackScale = ModulatableParameter(
                ModulatableParameterData(
                    baseValue = 1.001f,
                    modSource = "lfo2",
                    modAmount = 0.004f
                )
            )
        )
    }
}
