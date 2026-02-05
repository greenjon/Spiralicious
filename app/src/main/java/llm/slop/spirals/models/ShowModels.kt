package llm.slop.spirals.models

import kotlinx.serialization.Serializable
import java.util.UUID

enum class TransitionType {
    NONE, IMPLODE_EXPLODE, EXPLODE_EXPLODE, IMPLODE_IMPLODE
}

enum class FeedbackOverrideMode {
    RSET, // Use the setting from the RandomSet
    OFF,
    LIGHT,
    MEDIUM,
    HEAVY
}

@Serializable
data class ShowPatch(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val randomSetIds: List<String> = emptyList(),
    // New: Map of RandomSet ID to its feedback override
    val feedbackOverrides: Map<String, FeedbackOverrideMode> = emptyMap(),
    val prevTrigger: ModulatableParameterData = ModulatableParameterData(0.0f),
    val nextTrigger: ModulatableParameterData = ModulatableParameterData(0.0f),
    val randomTrigger: ModulatableParameterData = ModulatableParameterData(0.0f),
    val generateTrigger: ModulatableParameterData = ModulatableParameterData(0.0f),
    val transitionType: TransitionType = TransitionType.NONE,
    val transitionDurationBeats: Float = 0.0f,
    val transitionFadeOutPercent: Float = 0.5f,
    val transitionFadeInPercent: Float = 0.5f
)
