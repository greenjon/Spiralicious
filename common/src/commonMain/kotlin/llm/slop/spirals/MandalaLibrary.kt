package llm.slop.spirals

import llm.slop.spirals.models.mandala.MandalaRatio

object MandalaLibrary {
    val MandalaRatios = listOf(
        MandalaRatio(
            id = "default",
            name = "Default",
            baseScale = 1.0f,
            feedbackAmount = 0.5f,
            feedbackRotation = 0.1f,
            feedbackScale = 0.9f,
            arms = emptyList()
        )
    )
}
