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

    private val resolution = 4096
    
    @Volatile
    var params = MandalaParams(omega1 = 17, omega2 = -4, omega3 = 2)
    
    private var aspectRatio: Float = 1f

    private var uOmegaLocation: Int = -1
    private var uLLocation: Int = -1
    private var uPhiLocation: Int = -1
    private var uTLocation: Int = -1
    private var uGlobalRotationLocation: Int = -1
    private var uAspectRatioLocation: Int = -1
    private var uTimeLocation: Int = -1

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        program = ShaderHelper.buildProgram(context, R.raw.mandala, R.raw.mandala.frag)
        
        uOmegaLocation = GLES30.glGetUniformLocation(program, "uOmega")
        uLLocation = GLES30.glGetUniformLocation(program, "uL")
        uPhiLocation = GLES30.glGetUniformLocation(program, "uPhi")
        uTLocation = GLES30.glGetUniformLocation(program, "uT")
        uGlobalRotationLocation = GLES30.glGetUniformLocation(program, "uGlobalRotation")
        uAspectRatioLocation = GLES30.glGetUniformLocation(program, "uAspectRatio")
        uTimeLocation = GLES30.glGetUniformLocation(program, "uTime")

        val uData = FloatArray(resolution) { it.toFloat() / (resolution - 1) }
        val byteBuffer = ByteBuffer.allocateDirect(uData.size * 4)
        byteBuffer.order(ByteOrder.nativeOrder())
        val floatBuffer = byteBuffer.asFloatBuffer()
        floatBuffer.put(uData)
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
            uData.size * 4,
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

        val currentParams = params
        val symmetryInfo = currentParams.computeSymmetryInfo()
        
        GLES30.glUniform3f(uOmegaLocation, currentParams.omega1.toFloat(), currentParams.omega2.toFloat(), currentParams.omega3.toFloat())
        GLES30.glUniform3f(uLLocation, currentParams.l1, currentParams.l2, currentParams.l3)
        GLES30.glUniform3f(uPhiLocation, currentParams.phi1, currentParams.phi2, currentParams.phi3)
        GLES30.glUniform1f(uTLocation, symmetryInfo.period)
        
        val totalTime = SystemClock.uptimeMillis() / 1000.0f
        
        // Global rotation: 1 full rotation every 20 seconds
        val angle = (2.0f * Math.PI.toFloat() * (totalTime % 20.0f)) / 20.0f
        GLES30.glUniform1f(uGlobalRotationLocation, angle)
        GLES30.glUniform1f(uAspectRatioLocation, aspectRatio)
        GLES30.glUniform1f(uTimeLocation, totalTime)

        GLES30.glBindVertexArray(vao)
        GLES30.glLineWidth(3.0f)
        GLES30.glDrawArrays(GLES30.GL_LINE_STRIP, 0, resolution)
        
        GLES30.glBindVertexArray(0)
    }
}
