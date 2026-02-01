package llm.slop.spirals

import llm.slop.spirals.models.mandala.MandalaRatio

interface ISpiralRenderer {
    fun onSurfaceCreated()
    fun onSurfaceChanged(width: Int, height: Int)
    fun onDrawFrame()
    
    // Commands used by the UI
    fun setRatio(ratio: MandalaRatio)
    fun clearFeedback()
}
