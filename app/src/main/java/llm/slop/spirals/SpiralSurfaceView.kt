package llm.slop.spirals

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import llm.slop.spirals.models.MixerPatch

class SpiralSurfaceView(context: Context, attrs: AttributeSet? = null) : GLSurfaceView(context, attrs) {

    val renderer: SpiralRenderer = SpiralRenderer(context)

    init {
        setEGLContextClientVersion(3)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
    
    fun setParams(params: MandalaParams) {
        renderer.params = params
    }

    fun setVisualSource(source: MandalaVisualSource) {
        renderer.visualSource = source
    }

    fun setMixerState(patch: MixerPatch?, monitor: String) {
        renderer.mixerPatch = patch
        renderer.monitorSource = monitor
    }
}
