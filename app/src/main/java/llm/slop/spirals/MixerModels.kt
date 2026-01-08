package llm.slop.spirals

import kotlinx.serialization.Serializable
import llm.slop.spirals.cv.CvModulator
import java.util.UUID

enum class AdvanceMode {
    MANUAL, BEATS, BARS, SECONDS, CV_TRIGGER, CV_THRESHOLD
}

enum class MixerMode {
    ADD, SCREEN, MULT, MAX, XFADE
}

@Serializable
data class ModulatableParameterData(
    val baseValue: Float = 0.0f,
    val modulators: List<CvModulator> = emptyList()
)

@Serializable
data class MixerGroupData(
    val mode: MixerMode = MixerMode.ADD,
    val balance: ModulatableParameterData = ModulatableParameterData(0.5f), // Input balance
    val mix: ModulatableParameterData = ModulatableParameterData(0.5f),    // Blend amount
    val gain: ModulatableParameterData = ModulatableParameterData(1.0f)     // Output gain
)

@Serializable
data class MixerSlotData(
    val mandalaSetId: String? = null,
    val enabled: Boolean = true,
    val advanceMode: AdvanceMode = AdvanceMode.MANUAL,
    val advanceParams: Map<String, Float> = emptyMap(),
    val gain: ModulatableParameterData = ModulatableParameterData(1.0f),
    val sourceIsSet: Boolean = true // Mandala | Mandala Set
)

@Serializable
data class MixerPatch(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    val slots: List<MixerSlotData>, // Fixed size 4
    val mixerA: MixerGroupData = MixerGroupData(), // Mixes Slot 1 & 2
    val mixerB: MixerGroupData = MixerGroupData(), // Mixes Slot 3 & 4
    val mixerF: MixerGroupData = MixerGroupData(), // Mixes Group A & B
    val masterAlpha: Float = 1.0f
)
