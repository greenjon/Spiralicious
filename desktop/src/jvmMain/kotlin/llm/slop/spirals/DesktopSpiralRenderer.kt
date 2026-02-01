package llm.slop.spirals

import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL30.*
import llm.slop.spirals.models.mandala.MandalaRatio

class DesktopSpiralRenderer : ISpiralRenderer {
    override fun onSurfaceCreated() {
        GL.createCapabilities() // Vital for LWJGL
        // Init shaders here...
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        glViewport(0, 0, width, height)
    }

    override fun onDrawFrame() {
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        // Your rendering loop logic...
    }

    override fun setRatio(ratio: MandalaRatio) { /* update uniforms */ }
    override fun clearFeedback() { /* clear FBOs */ }
}
