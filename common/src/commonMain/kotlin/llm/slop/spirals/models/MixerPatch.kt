package llm.slop.spirals.models

import kotlinx.serialization.Serializable

@Serializable
data class MixerPatch(
    val id: String,
    val name: String,
    val slots: List<MixerSlotData>
)

@Serializable
data class MixerSlotData(
    val selectedMandalaId: String? = null,
    val mandalaSetId: String? = null,
    val randomSetId: String? = null,
    val sourceType: VideoSourceType = VideoSourceType.MANDALA
)

enum class VideoSourceType {
    MANDALA,
    MANDALA_SET,
    RANDOM_SET
}
