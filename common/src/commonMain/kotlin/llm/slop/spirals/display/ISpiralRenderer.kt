package llm.slop.spirals.display

import llm.slop.spirals.VisualSource
import llm.slop.spirals.ModulatableParameter
import llm.slop.spirals.models.mandala.MandalaRatio
import llm.slop.spirals.models.MixerPatch

interface ISpiralRenderer {
    fun onSurfaceCreated()
    fun onSurfaceChanged(width: Int, height: Int)
    fun onDrawFrame()

    // Commands used by the UI
    fun setRatio(ratio: MandalaRatio)
    fun clearFeedback()

    fun setVisualSource(source: VisualSource?)
    fun getMixerParam(name: String): ModulatableParameter?
    fun setMixerState(patch: MixerPatch?, monitor: String)
}
