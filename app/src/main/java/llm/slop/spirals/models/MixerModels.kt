package llm.slop.spirals.models

import kotlinx.serialization.Serializable
import llm.slop.spirals.cv.CvModulator
import java.util.UUID

enum class AdvanceMode {
    MANUAL, BEATS, BARS, SECONDS, CV_TRIGGER, CV_THRESHOLD
}

enum class MixerMode {
    ADD, SCREEN, MULT, MAX, XFADE
}

enum class VideoSourceType {
    MANDALA, MANDALA_SET, COLOR
}

@Serializable
data class ModulatableParameterData(
    val baseValue: Float = 0.0f,
    val modulators: List<CvModulator> = emptyList()
)

@Serializable
data class MixerGroupData(
    val mode: ModulatableParameterData = ModulatableParameterData(0.0f), // baseValue is index of MixerMode
    val balance: ModulatableParameterData = ModulatableParameterData(0.5f)
)

@Serializable
data class MixerSlotData(
    val mandalaSetId: String? = null,
    val selectedMandalaId: String? = null,
    val currentIndex: ModulatableParameterData = ModulatableParameterData(0.0f),
    val enabled: Boolean = false,
    val advanceMode: AdvanceMode = AdvanceMode.MANUAL,
    val advanceParams: Map<String, Float> = emptyMap(),
    val sourceIsSet: Boolean = true, // Legacy support, prefer sourceType
    val sourceType: VideoSourceType = if (sourceIsSet) VideoSourceType.MANDALA_SET else VideoSourceType.MANDALA,
    val hue: ModulatableParameterData = ModulatableParameterData(0.0f),
    val saturation: ModulatableParameterData = ModulatableParameterData(0.0f)
) {
    fun isPopulated(): Boolean = when(sourceType) {
        VideoSourceType.MANDALA_SET -> mandalaSetId != null
        VideoSourceType.MANDALA -> selectedMandalaId != null
        VideoSourceType.COLOR -> true
    }
}

@Serializable
data class MixerPatch(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    val slots: List<MixerSlotData>, // Fixed size 4
    val mixerA: MixerGroupData = MixerGroupData(),
    val mixerB: MixerGroupData = MixerGroupData(),
    val mixerF: MixerGroupData = MixerGroupData(),
    val finalGain: ModulatableParameterData = ModulatableParameterData(1.0f),
    val masterAlpha: Float = 1.0f
)
