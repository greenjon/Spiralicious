package llm.slop.spirals.models

import kotlinx.serialization.Serializable
import llm.slop.spirals.models.set.MandalaSet

@Serializable
sealed class LayerContent

@Serializable
data class MandalaLayerContent(val patch: PatchData) : LayerContent()

@Serializable
data class SetLayerContent(val set: MandalaSet) : LayerContent()

@Serializable
data class MixerLayerContent(val mixer: MixerPatch) : LayerContent()

@Serializable
data class ShowLayerContent(val show: ShowPatch) : LayerContent()

@Serializable
data class RandomSetLayerContent(val randomSet: RandomSet) : LayerContent()
