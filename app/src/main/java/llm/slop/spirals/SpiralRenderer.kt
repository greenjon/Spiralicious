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

    private val resolution = 2048
    
    var visualSource: MandalaVisualSource? = null

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
    private var uGlobalScaleLocation: Int = -1
    private var uColorLocation: Int = -1

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
        uGlobalScaleLocation = GLES30.glGetUniformLocation(program, "uGlobalScale")
        uColorLocation = GLES30.glGetUniformLocation(program, "uColor")

        val totalVertices = resolution * 2
        val vertexData = FloatArray(totalVertices * 2)
        for (i in 0 until resolution) {
            val u = i.toFloat() / (resolution - 1)
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
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertexData.size * 4, floatBuffer, GLES30.GL_STATIC_DRAW)

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 1, GLES30.GL_FLOAT, false, 8, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 1, GLES30.GL_FLOAT, false, 8, 4)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        aspectRatio = width.toFloat() / height.toFloat()
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        if (program == 0) return
        GLES30.glUseProgram(program)

        val source = visualSource
        val p1: Float; val p2: Float; val p3: Float; val p4: Float
        val o1: Float; val o2: Float; val o3: Float; val o4: Float
        val thick: Float
        val finalScale: Float
        val rotation: Float
        val hue: Float; val sat: Float; val alpha: Float

        if (source != null) {
            source.update()
            p1 = source.parameters["L1"]?.value ?: 0f
            p2 = source.parameters["L2"]?.value ?: 0f
            p3 = source.parameters["L3"]?.value ?: 0f
            p4 = source.parameters["L4"]?.value ?: 0f
            
            // Frequencies are now truthful integers from the recipe
            o1 = source.recipe.a.toFloat()
            o2 = source.recipe.b.toFloat()
            o3 = source.recipe.c.toFloat()
            o4 = source.recipe.d.toFloat()
            
            thick = (source.parameters["Thickness"]?.value ?: 0f) * 0.02f
            val localScale = source.parameters["Scale"]?.value ?: 0.125f
            finalScale = localScale * source.globalScale.value * 8.0f
            
            rotation = (source.parameters["Rotation"]?.value ?: 0f) * 2.0f * PI.toFloat()
            
            hue = source.parameters["Hue"]?.value ?: 0f
            sat = source.parameters["Saturation"]?.value ?: 1f
            alpha = source.globalAlpha.value
        } else {
            p1 = params.l1; p2 = params.l2; p3 = params.l3; p4 = params.l4
            o1 = params.omega1.toFloat(); o2 = params.omega2.toFloat(); o3 = params.omega3.toFloat(); o4 = params.omega4.toFloat()
            thick = params.thickness
            finalScale = 1.0f
            rotation = 0f
            hue = 0.5f; sat = 0.8f; alpha = 1.0f
        }

        GLES30.glUniform4f(uOmegaLocation, o1, o2, o3, o4)
        GLES30.glUniform4f(uLLocation, p1, p2, p3, p4)
        GLES30.glUniform1f(uTLocation, (2.0 * PI).toFloat())
        GLES30.glUniform1f(uThicknessLocation, thick)
        GLES30.glUniform1f(uGlobalScaleLocation, finalScale)
        GLES30.glUniform1f(uGlobalRotationLocation, rotation)
        GLES30.glUniform4f(uColorLocation, hue, sat, 1.0f, alpha)
        GLES30.glUniform1f(uAspectRatioLocation, aspectRatio)

        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, resolution * 2)
    }
}
