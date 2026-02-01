package llm.slop.spirals.models.mandala

/**
 * Represents a set of integer frequencies for a 4-arm mandala.
 */
data class MandalaRatio(
    val id: String,
    val name: String,
    val arms: Mandala4Arm
)
