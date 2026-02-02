package llm.slop.spirals.models.mandala

import llm.slop.spirals.ModulatableParameter
import llm.slop.spirals.models.ModulatableParameterData

data class MandalaArm(
    val freq: Int = 0,
    val length: Float = 0.0f
)

data class MandalaRatio(
    val id: String,
    val arms: List<MandalaArm> = listOf(MandalaArm(), MandalaArm(), MandalaArm(), MandalaArm()),
    val baseScale: Float = 0.1f,

    // Feedback control parameters
    val feedbackAmount: ModulatableParameter = ModulatableParameter(ModulatableParameterData(0.98f)),
    val feedbackScale: ModulatableParameter = ModulatableParameter(ModulatableParameterData(1.002f)),
    val feedbackRotation: ModulatableParameter = ModulatableParameter(ModulatableParameterData(0.001f))
) {
    companion object {
        val default = MandalaRatio(
            id = "default",
            arms = listOf(
                MandalaArm(26, 0.5f),
                MandalaArm(23, 0.5f),
                MandalaArm(14, 0.5f),
                MandalaArm(14, 0.5f)
            ),
            baseScale = 0.1f,
            feedbackRotation = ModulatableParameter(
                ModulatableParameterData(
                    baseValue = 0.0f,
                    modSource = "lfo1",
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
