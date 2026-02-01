package llm.slop.spirals.models

import llm.slop.spirals.cv.core.CvSignal
import llm.slop.spirals.cv.core.Modulator
import llm.slop.spirals.models.mandala.MandalaPatch
import llm.slop.spirals.models.set.MandalaSet

/**
 * A container for a complete state snapshot of the application,
 * used for serialization and persistence.
 */
data class PatchData(
    val mixer: MixerPatch,
    val sets: List<MandalaSet>,
    val mandalas: List<MandalaPatch>
)

/**
 * Creates a deep copy of a [PatchData] object.
 */
fun PatchData.deepCopy(): PatchData {
    return PatchData(
        mixer = this.mixer.deepCopy(),
        sets = this.sets.map { it.deepCopy() },
        mandalas = this.mandalas.map { it.deepCopy() }
    )
}

/**
 * Attaches CV signals to modulators based on a registry.
 */
fun PatchData.hydrate(registry: Map<String, CvSignal>): PatchData {
    val hydratedMandalas = this.mandalas.map { mandala ->
        val hydratedParameters = mandala.parameters.mapValues { (_, param) ->
            param.baseValue to param.modulators.map { modulator ->
                registry[modulator.cvSignal.id]?.let {
                    Modulator(it, modulator.amount, modulator.isInverted)
                }
            }.filterNotNull()
        }
        mandala.copy(parameters = hydratedParameters)
    }
    return this.copy(mandalas = hydratedMandalas)
}
