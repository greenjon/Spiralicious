package llm.slop.spirals

import kotlinx.serialization.Serializable
import llm.slop.spirals.models.MixerPatch
import llm.slop.spirals.models.ShowPatch

/**
 * Sealed interface for polymorphic serialization of NavLayer data.
 * This replaces the unsafe Any? type that was causing serialization crashes.
 * 
 * CRITICAL: This is the fix for the deterministic serialization crash.
 * kotlinx.serialization cannot handle `Any?` but DOES support sealed interfaces.
 * 
 * Pattern:
 * - When setting layer.data: wrap in appropriate type → MixerLayerContent(patch)
 * - When reading layer.data: safe cast → (layer.data as? MixerLayerContent)?.mixer
 * - When branching: use when expressions → when (layer.data) { is MixerLayerContent -> ... }
 * 
 * Adding new layer types:
 * 1. Create new @Serializable data class implementing LayerContent
 * 2. Update all when expressions (compiler will enforce exhaustiveness)
 * 3. Update MandalaViewModel layer creation/update functions
 */
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
