package llm.slop.spirals

import kotlinx.serialization.Serializable
import llm.slop.spirals.cv.CvModulator
import java.util.UUID

enum class AdvanceMode {
    MANUAL, BEATS, BARS, SECONDS, CV_TRIGGER, CV_THRESHOLD
}

@Serializable
data class MixerSlotData(
    val mandalaSetId: String? = null,
    val enabled: Boolean = true,
    val advanceMode: AdvanceMode = AdvanceMode.MANUAL,
    val advanceParams: Map<String, Float> = emptyMap(),
    val opacityBaseValue: Float = 1.0f,
    val opacityModulators: List<CvModulator> = emptyList(),
    val blendMode: String = "Normal"
)

@Serializable
data class MixerPatch(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    val slots: List<MixerSlotData>, // Should always be size 4
    val masterAlpha: Float = 1.0f
)
