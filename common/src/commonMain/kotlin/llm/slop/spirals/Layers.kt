package llm.slop.spirals

import kotlinx.serialization.Serializable
import llm.slop.spirals.models.MixerPatch
import llm.slop.spirals.models.RandomSet
import llm.slop.spirals.models.ShowPatch
import llm.slop.spirals.models.set.MandalaSet
import llm.slop.spirals.models.PatchData

@Serializable
enum class LayerType { MIXER, SET, MANDALA, SHOW, RANDOM_SET }

@Serializable
sealed interface LayerContent

@Serializable
data class MandalaLayerContent(val patch: PatchData) : LayerContent

@Serializable
data class SetLayerContent(val set: MandalaSet) : LayerContent

@Serializable
data class MixerLayerContent(val mixer: MixerPatch) : LayerContent

@Serializable
data class ShowLayerContent(val show: ShowPatch) : LayerContent

@Serializable
data class RandomSetLayerContent(val randomSet: RandomSet) : LayerContent
