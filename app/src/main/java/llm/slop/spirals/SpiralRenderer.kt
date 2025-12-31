package llm.slop.spirals

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI

class SpiralRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private var program: Int = 0
    private var vao: Int = 0
    private var vbo: Int = 0

    private val resolution = 2048 // 2048 points * 2 vertices per point for triangle strip
    
    @Volatile
    var params = MandalaParams(omega1 = 20, omega2 = 17, omega3 = 11, thickness = 0.005f)
    
    private var aspectRatio: Float = 1f

    private var uOmegaLocation: Int = -1
    private var uLLocation: Int = -1
    private var uPhiLocation: Int = -1
    private var uTLocation: Int = -1
    private var uGlobalRotationLocation: Int = -1
    private var uAspectRatioLocation: Int = -1
    private var uTimeLocation: Int = -1
    private var uThicknessLocation: Int = -1

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        program = ShaderHelper.buildProgram(context, R.raw.mandala_vertex, R.raw.mandala_fragment)
        
        uOmegaLocation = GLES30.glGetUniformLocation(program, "uOmega")
        uLLocation = GLES30.glGetUniformLocation(program, "uL")
        uPhiLocation = GLES30.glGetUniformLocation(program, "uPhi")
        uTLocation = GLES30.glGetUniformLocation(program, "uT")
        uGlobalRotationLocation = GLES30.glGetUniformLocation(program, "uGlobalRotation")
        uAspectRatioLocation = GLES30.glGetUniformLocation(program, "uAspectRatio")
        uTimeLocation = GLES30.glGetUniformLocation(program, "uTime")
        uThicknessLocation = GLES30.glGetUniformLocation(program, "uThickness")

        // Interleaved buffer: [u, side, u, side, ...]
        val totalVertices = resolution * 2
        val vertexData = FloatArray(totalVertices * 2)
        for (i in 0 until resolution) {
            val u = i.generateU()
            vertexData[i * 4 + 0] = u
            vertexData[i * 4 + 1] = -1.0f
            vertexData[i * 4 + 2] = u
            vertexData[i * 4 + 3] = 1.0f
        }

        val byteBuffer = ByteBuffer.allocateDirect(vertexData.size * 4)
        byteBuffer.order(ByteOrder.nativeOrder())
        val floatBuffer = byteBuffer.asFloatBuffer()
        floatBuffer.put(vertexData)
        floatBuffer.position(0)

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
            vertexData.size * 4,
            floatBuffer,
            GLES30.GL_STATIC_DRAW
        )

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 1, GLES30.GL_FLOAT, false, 8, 0)
        
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 1, GLES30.GL_FLOAT, false, 8, 4)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindVertexArray(0)
    }

    private fun Int.generateU(): Float = this.toFloat() / (resolution - 1)

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        aspectRatio = width.toFloat() / height.toFloat()
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        if (program == 0) return

        GLES30.glUseProgram(program)

        val currentParams = params
        val period = (2.0 * PI).toFloat()
        
        GLES30.glUniform4f(uOmegaLocation, currentParams.omega1.toFloat(), currentParams.omega2.toFloat(), currentParams.omega3.toFloat(), currentParams.omega4.toFloat())
        GLES30.glUniform4f(uLLocation, currentParams.l1, currentParams.l2, currentParams.l3, currentParams.l4)
        GLES30.glUniform4f(uPhiLocation, currentParams.phi1, currentParams.phi2, currentParams.phi3, currentParams.phi4)
        GLES30.glUniform1f(uTLocation, period)
        GLES30.glUniform1f(uThicknessLocation, currentParams.thickness)
        
        GLES30.glUniform1f(uGlobalRotationLocation, 0.0f)
        GLES30.glUniform1f(uAspectRatioLocation, aspectRatio)
        GLES30.glUniform1f(uTimeLocation, 0.0f)

        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, resolution * 2)
        
        GLES30.glBindVertexArray(0)
    }
}
