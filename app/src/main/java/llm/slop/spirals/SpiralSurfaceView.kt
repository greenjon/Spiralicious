package llm.slop.spirals

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet

class SpiralSurfaceView(context: Context, attrs: AttributeSet? = null) : GLSurfaceView(context, attrs) {

    private val renderer: SpiralRenderer

    init {
        // Create an OpenGL ES 3.0 context
        setEGLContextClientVersion(3)

        renderer = SpiralRenderer()

        // Set the Renderer for drawing on the GLSurfaceView
        setRenderer(renderer)

        // Render the view only when there is a change in the drawing data
        // For continuous animation, we use RENDERMODE_CONTINUOUSLY (default)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}
