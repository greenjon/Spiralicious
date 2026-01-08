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
    
    @Volatile
    var visualSource: MandalaVisualSource? = null

    // Mixer support
    @Volatile
    var mixerPatch: MixerPatch? = null
    @Volatile
    var monitorSource: String = "1" // "1", "2", "3", "4", "A"
    
    // We maintain 4 internal sources for the mixer slots
    private val slotSources = List(4) { MandalaVisualSource() }

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

    fun getSlotSource(index: Int) = slotSources[index]

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES30.glEnable(GLES30.GL_BLEND)
        // Default blend: Normal
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

        val currentMonitor = monitorSource
        val currentMixerPatch = mixerPatch

        if (currentMonitor == "A" && currentMixerPatch != null) {
            // Render composite
            currentMixerPatch.slots.forEachIndexed { index, slot ->
                if (slot.enabled) {
                    // Set blend mode for this layer
                    when (slot.blendMode) {
                        "Add" -> GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
                        else -> GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
                    }
                    
                    val source = slotSources[index]
                    // Mixer slots use their own opacity control
                    // For Phase 1, we assume opacityBaseValue is evaluated or just used directly
                    // Actually, we should probably evaluate it.
                    val opacity = slot.opacityBaseValue 
                    renderSource(source, opacityOverride = opacity)
                }
            }
            // Reset blend mode
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        } else {
            // Render single source (either a specific slot or the global visualSource)
            val slotIndex = currentMonitor.toIntOrNull()?.minus(1)
            if (slotIndex != null && slotIndex in 0..3) {
                renderSource(slotSources[slotIndex])
            } else {
                renderSource(visualSource)
            }
        }
    }

    private fun renderSource(source: MandalaVisualSource?, opacityOverride: Float? = null) {
        if (source == null) return
        
        source.update()
        
        val p1 = source.parameters["L1"]?.value ?: 0f
        val p2 = source.parameters["L2"]?.value ?: 0f
        val p3 = source.parameters["L3"]?.value ?: 0f
        val p4 = source.parameters["L4"]?.value ?: 0f
        
        val o1 = source.recipe.a.toFloat()
        val o2 = source.recipe.b.toFloat()
        val o3 = source.recipe.c.toFloat()
        val o4 = source.recipe.d.toFloat()
        
        val thick = (source.parameters["Thickness"]?.value ?: 0f) * 0.02f
        val localScale = source.parameters["Scale"]?.value ?: 0.125f
        val finalScale = localScale * source.globalScale.value * 8.0f
        
        val rotation = (source.parameters["Rotation"]?.value ?: 0f) * 2.0f * PI.toFloat()
        
        val hue = source.parameters["Hue"]?.value ?: 0f
        val sat = source.parameters["Saturation"]?.value ?: 1f
        val alpha = opacityOverride ?: source.globalAlpha.value

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
