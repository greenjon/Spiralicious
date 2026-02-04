package llm.slop.spirals.models.mandala

object MandalaLibrary {
    val MandalaRatios = listOf(
        MandalaRatio(
            id = "default",
            name = "Default",
            baseScale = 1.0f,
            feedbackAmount = 0.5f,
            feedbackRotation = 0.1f,
            feedbackScale = 1.0f,
            arms = listOf(
                MandalaArm(freq = 1, length = 1.0f),
                MandalaArm(freq = 2, length = 0.5f),
                MandalaArm(freq = 3, length = 0.25f),
            )
        )
    )
}
