package llm.slop.spirals

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class SpiralRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private var program: Int = 0
    private var vao: Int = 0
    private var vbo: Int = 0

    private val resolution = 2048
    private var params = MandalaParams()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        // 5. Implement shader loader utility (Build program)
        program = ShaderHelper.buildProgram(context, R.raw.mandala, R.raw.mandala.frag)

        // 8. Create parameter buffer
        val uBuffer = FloatArray(resolution) { it.toFloat() / (resolution - 1) }
        val floatBuffer = ByteBuffer.allocateDirect(uBuffer.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(uBuffer)
        floatBuffer.position(0)

        // 9. Configure VAO
        val vaoArray = IntArray(1)
        GLES30.glGenVertexArrays(1, vaoArray, 0)
        vao = vaoArray[0]
        GLES30.glBindVertexArray(vao)

        val vboArray = IntArray(1)
        GLES30.glGenBuffers(1, vboArray, 0)
        vbo = vboArray[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            uBuffer.size * 4,
            floatBuffer,
            GLES30.GL_STATIC_DRAW
        )

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 1, GLES30.GL_FLOAT, false, 4, 0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindVertexArray(0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        if (program == 0) return

        GLES30.glUseProgram(program)
        GLES30.glBindVertexArray(vao)
        
        // Draw the points (will be connected lines later)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, resolution)
        
        GLES30.glBindVertexArray(0)
    }
}
