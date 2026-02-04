package llm.slop.spirals

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import llm.slop.spirals.display.SpiralRenderer
import llm.slop.spirals.display.SharedEGLContextFactory

class SpiralSurfaceView(context: Context, attrs: AttributeSet? = null) : GLSurfaceView(context, attrs) {

    init {
        setEGLContextClientVersion(3)
        setEGLContextFactory(SharedEGLContextFactory())
        renderMode = RENDERMODE_CONTINUOUSLY
    }
    
    fun setSpiralRenderer(renderer: GLSurfaceView.Renderer) {
         setRenderer(renderer)
    }
}
