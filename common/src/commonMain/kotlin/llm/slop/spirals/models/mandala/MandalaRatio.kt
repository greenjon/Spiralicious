package llm.slop.spirals.models.mandala

import kotlinx.serialization.Serializable

@Serializable
data class MandalaRatio(
    val id: String,
    val name: String,
    val baseScale: Float,
    val feedbackAmount: Float,
    val feedbackRotation: Float,
    val feedbackScale: Float,
    val arms: List<MandalaArm>
)

@Serializable
data class MandalaArm(
    val freq: Int,
    val length: Float
)
