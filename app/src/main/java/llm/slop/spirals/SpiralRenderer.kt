package llm.slop.spirals

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class SpiralRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private var program: Int = 0
    private var vao: Int = 0
    private var vbo: Int = 0

    private val resolution = 4096 // Increased resolution for smoother lines
    private var params = MandalaParams()
    private var aspectRatio: Float = 1f

    private var uOmegaLocation: Int = -1
    private var uLLocation: Int = -1
    private var uPhiLocation: Int = -1
    private var uTLocation: Int = -1
    private var uGlobalRotationLocation: Int = -1
    private var uAspectRatioLocation: Int = -1

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        program = ShaderHelper.buildProgram(context, R.raw.mandala, R.raw.mandala.frag)
        
        uOmegaLocation = GLES30.glGetUniformLocation(program, "uOmega")
        uLLocation = GLES30.glGetUniformLocation(program, "uL")
        uPhiLocation = GLES30.glGetUniformLocation(program, "uPhi")
        uTLocation = GLES30.glGetUniformLocation(program, "uT")
        uGlobalRotationLocation = GLES30.glGetUniformLocation(program, "uGlobalRotation")
        uAspectRatioLocation = GLES30.glGetUniformLocation(program, "uAspectRatio")

        val uBuffer = FloatArray(resolution) { it.toFloat() / (resolution - 1) }
        val floatBuffer = ByteBuffer.allocateDirect(uBuffer.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(uBuffer)
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
        aspectRatio = width.toFloat() / height.toFloat()
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        if (program == 0) return

        GLES30.glUseProgram(program)

        val symmetryInfo = params.computeSymmetryInfo()
        
        // Pass uniforms
        GLES30.glUniform3f(uOmegaLocation, params.omega1.toFloat(), params.omega2.toFloat(), params.omega3.toFloat())
        GLES30.glUniform3f(uLLocation, params.l1, params.l2, params.l3)
        GLES30.glUniform3f(uPhiLocation, params.phi1, params.phi2, params.phi3)
        GLES30.glUniform1f(uTLocation, symmetryInfo.period)
        
        // 11. Global rotation animated over time
        val time = SystemClock.uptimeMillis() % 10000L
        val angle = (2.0f * Math.PI.toFloat() * time) / 10000.0f
        GLES30.glUniform1f(uGlobalRotationLocation, angle)
        GLES30.glUniform1f(uAspectRatioLocation, aspectRatio)

        GLES30.glBindVertexArray(vao)
        
        // Using LINE_STRIP to draw the mandala curve
        GLES30.glDrawArrays(GLES30.GL_LINE_STRIP, 0, resolution)
        
        GLES30.glBindVertexArray(0)
    }
}
