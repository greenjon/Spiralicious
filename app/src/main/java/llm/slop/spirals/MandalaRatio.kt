package llm.slop.spirals

data class MandalaRatio(
    val omega1: Int,
    val omega2: Int,
    val omega3: Int,
    val omega4: Int = 0,
    val lobes: Int = 0,
    val shapeRatio: Float = 1.0f
)
