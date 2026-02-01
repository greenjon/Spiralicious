package llm.slop.spirals.models

import llm.slop.spirals.cv.core.ModulatableParameter

/**
 * Represents the state of the 4-channel video mixer.
 */
data class MixerPatch(
    val channels: List<MixerChannel>,
    val masterAlpha: ModulatableParameter,
    val masterScale: ModulatableParameter
) {
    /**
     * Creates a deep copy of the mixer patch.
     */
    fun deepCopy(): MixerPatch {
        return this.copy(
            channels = this.channels.map { it.deepCopy() },
            masterAlpha = this.masterAlpha.copy(),
            masterScale = this.masterScale.copy()
        )
    }
}

/**
 * Represents a single channel in the video mixer.
 */
data class MixerChannel(
    val slot: Int,
    val alpha: ModulatableParameter,
    val scale: ModulatableParameter,
    var mandalaId: String?
) {
    /**
     * Creates a deep copy of the mixer channel.
     */
    fun deepCopy(): MixerChannel {
        return this.copy(
            alpha = this.alpha.copy(),
            scale = this.scale.copy()
        )
    }
}
