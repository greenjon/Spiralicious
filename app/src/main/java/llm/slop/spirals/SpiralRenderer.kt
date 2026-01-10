package llm.slop.spirals

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import androidx.compose.runtime.staticCompositionLocalOf
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.roundToInt
import llm.slop.spirals.cv.ModulatableParameter
import llm.slop.spirals.models.*

val LocalSpiralRenderer = staticCompositionLocalOf<SpiralRenderer?> { null }

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
        set(value) {
            field = value
            value?.let { syncMixerParameters(it) }
        }
        
    @Volatile
    var monitorSource: String = "F" // "1", "2", "3", "4", "A", "B", "F"
    
    // We maintain 4 internal sources for the mixer slots
    private val slotSources = Array(4) { MandalaVisualSource() }

    // Internal ModulatableParameters for mixer components
    private val mixerParams = mutableMapOf<String, ModulatableParameter>()

    init {
        for (i in 1..4) {
            mixerParams["PN$i"] = ModulatableParameter(0.0f)
            mixerParams["H$i"] = ModulatableParameter(0.0f)
            mixerParams["S$i"] = ModulatableParameter(0.0f)
        }
        listOf("A", "B", "F").forEach { g ->
            mixerParams["M${g}_MODE"] = ModulatableParameter(0.0f)
            mixerParams["M${g}_BAL"] = ModulatableParameter(0.5f)
        }
        mixerParams["MF_GAIN"] = ModulatableParameter(1.0f)
    }

    fun getSlotSource(index: Int): MandalaVisualSource = slotSources[index]
    
    fun setSlotSource(index: Int, source: MandalaVisualSource) {
        if (index in 0..3) {
            slotSources[index] = source
        }
    }

    fun getMixerParam(id: String): ModulatableParameter? = mixerParams[id]

    private fun syncMixerParameters(patch: MixerPatch) {
        for (i in 0..3) {
            syncParam(mixerParams["PN${i+1}"]!!, patch.slots[i].currentIndex)
            syncParam(mixerParams["H${i+1}"]!!, patch.slots[i].hue)
            syncParam(mixerParams["S${i+1}"]!!, patch.slots[i].saturation)
        }
        syncGroup(patch.mixerA, "A")
        syncGroup(patch.mixerB, "B")
        syncGroup(patch.mixerF, "F")
        syncParam(mixerParams["MF_GAIN"]!!, patch.finalGain)
    }

    private fun syncGroup(group: MixerGroupData, prefix: String) {
        syncParam(mixerParams["M${prefix}_MODE"]!!, group.mode)
        syncParam(mixerParams["M${prefix}_BAL"]!!, group.balance)
    }

    private fun syncParam(target: ModulatableParameter, source: ModulatableParameterData) {
        target.baseValue = source.baseValue
        if (target.modulators.size != source.modulators.size || target.modulators != source.modulators) {
            target.modulators.clear()
            target.modulators.addAll(source.modulators)
        }
    }

    @Volatile
    var params = MandalaParams(omega1 = 20, omega2 = 17, omega3 = 11, thickness = 0.005f)
    
    private var aspectRatio: Float = 1f

    private var uHueOffsetLocation: Int = -1
    private var uHueSweepLocation: Int = -1
    private var uAlphaLocation: Int = -1
    private var uGlobalRotationLocation: Int = -1
    private var uAspectRatioLocation: Int = -1
    private var uGlobalScaleLocation: Int = -1
    private var uFillModeLocation: Int = -1

    private lateinit var vertexBuffer: FloatBuffer

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        program = ShaderHelper.buildProgram(context, R.raw.mandala_vertex, R.raw.mandala_fragment)
        
        uHueOffsetLocation = GLES30.glGetUniformLocation(program, "uHueOffset")
        uHueSweepLocation = GLES30.glGetUniformLocation(program, "uHueSweep")
        uAlphaLocation = GLES30.glGetUniformLocation(program, "uAlpha")
        uGlobalRotationLocation = GLES30.glGetUniformLocation(program, "uGlobalRotation")
        uAspectRatioLocation = GLES30.glGetUniformLocation(program, "uAspectRatio")
        uGlobalScaleLocation = GLES30.glGetUniformLocation(program, "uGlobalScale")
        uFillModeLocation = GLES30.glGetUniformLocation(program, "uFillMode")

        // Pre-allocate buffer for (resolution + 1) points * 3 components (X, Y, Phase)
        val byteBuffer = ByteBuffer.allocateDirect((resolution + 1) * 3 * 4)
        byteBuffer.order(ByteOrder.nativeOrder())
        vertexBuffer = byteBuffer.asFloatBuffer()

        val vaoArray = IntArray(1)
        GLES30.glGenVertexArrays(1, vaoArray, 0)
        vao = vaoArray[0]
        GLES30.glBindVertexArray(vao)

        val vboArray = IntArray(1)
        GLES30.glGenBuffers(1, vboArray, 0)
        vbo = vboArray[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        
        // Initial empty buffer allocation
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, (resolution + 1) * 3 * 4, null, GLES30.GL_STREAM_DRAW)

        // Phase 2.3: Update the Vertex Attribute Pointer to 3 components (X, Y, Phase)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 3 * 4, 0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        aspectRatio = width.toFloat() / height.toFloat()
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        if (program == 0) return
        GLES30.glUseProgram(program)

        val patch = mixerPatch
        val monitor = monitorSource

        if (patch == null) {
            renderSource(visualSource)
            return
        }

        when (monitor) {
            "1", "2", "3", "4" -> {
                val idx = monitor.toInt() - 1
                if (patch.slots[idx].enabled && patch.slots[idx].isPopulated()) {
                    renderSlot(idx, patch.slots[idx])
                }
            }
            "A" -> {
                renderHierarchicalGroup("A", slotSources[0], slotSources[1], patch.slots[0], patch.slots[1])
            }
            "B" -> {
                renderHierarchicalGroup("B", slotSources[2], slotSources[3], patch.slots[2], patch.slots[3])
            }
            "F" -> {
                val gainF = mixerParams["MF_GAIN"]?.evaluate() ?: 1.0f
                val balF = mixerParams["MF_BAL"]?.evaluate() ?: 0.5f
                val modeFIdx = ((mixerParams["MF_MODE"]?.evaluate() ?: 0f) * (MixerMode.values().size - 1)).roundToInt()
                val modeF = MixerMode.values()[modeFIdx.coerceIn(0, MixerMode.values().size - 1)]

                val balA = ((1.0f - balF) * 2.0f).coerceIn(0f, 1f)
                renderHierarchicalGroup("A", slotSources[0], slotSources[1], patch.slots[0], patch.slots[1], 
                    groupGainScale = gainF * balA)
                
                setBlendMode(modeF)
                val balB = (balF * 2.0f).coerceIn(0f, 1f)
                renderHierarchicalGroup("B", slotSources[2], slotSources[3], patch.slots[2], patch.slots[3], 
                    groupGainScale = gainF * balB)
                
                GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
                GLES30.glBlendEquation(GLES30.GL_FUNC_ADD)
            }
            else -> renderSource(visualSource)
        }
    }

    private fun renderSlot(index: Int, slot: MixerSlotData, gain: Float = 1.0f) {
        when(slot.sourceType) {
            VideoSourceType.MANDALA, VideoSourceType.MANDALA_SET -> {
                renderSource(slotSources[index], gain = gain)
            }
            VideoSourceType.COLOR -> {
                val h = mixerParams["H${index+1}"]?.evaluate() ?: 0f
                renderColor(h, gain)
            }
        }
    }

    private fun renderHierarchicalGroup(
        prefix: String,
        src1: MandalaVisualSource,
        src2: MandalaVisualSource,
        slot1: MixerSlotData,
        slot2: MixerSlotData,
        groupGainScale: Float = 1.0f
    ) {
        val bal = mixerParams["M${prefix}_BAL"]?.evaluate() ?: 0.5f
        val modeIdx = ((mixerParams["M${prefix}_MODE"]?.evaluate() ?: 0f) * (MixerMode.values().size - 1)).roundToInt()
        val mode = MixerMode.values()[modeIdx.coerceIn(0, MixerMode.values().size - 1)]

        if (slot1.enabled && slot1.isPopulated()) {
            val bal1 = ((1.0f - bal) * 2.0f).coerceIn(0f, 1f)
            val idx = if (prefix == "A") 0 else 2
            renderSlot(idx, slot1, gain = bal1 * groupGainScale)
        }
        
        if (slot2.enabled && slot2.isPopulated()) {
            setBlendMode(mode)
            val bal2 = (bal * 2.0f).coerceIn(0f, 1f)
            val idx = if (prefix == "A") 1 else 3
            renderSlot(idx, slot2, gain = bal2 * groupGainScale)
            
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            GLES30.glBlendEquation(GLES30.GL_FUNC_ADD)
        }
    }

    private fun setBlendMode(mode: MixerMode) {
        when (mode) {
            MixerMode.ADD -> {
                GLES30.glBlendEquation(GLES30.GL_FUNC_ADD)
                GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
            }
            MixerMode.SCREEN -> {
                GLES30.glBlendEquation(GLES30.GL_FUNC_ADD)
                GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_COLOR)
            }
            MixerMode.MULT -> {
                GLES30.glBlendEquation(GLES30.GL_FUNC_ADD)
                GLES30.glBlendFunc(GLES30.GL_DST_COLOR, GLES30.GL_ZERO)
            }
            MixerMode.MAX -> {
                GLES30.glBlendEquation(GLES30.GL_MAX)
                GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
            }
            MixerMode.XFADE -> {
                GLES30.glBlendEquation(GLES30.GL_FUNC_ADD)
                GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            }
        }
    }

    private fun renderColor(h: Float, a: Float) {
        GLES30.glUniform1f(uFillModeLocation, 1.0f)
        GLES30.glUniform1f(uHueOffsetLocation, h)
        GLES30.glUniform1f(uHueSweepLocation, 0.0f)
        GLES30.glUniform1f(uAlphaLocation, a)

        GLES30.glBindVertexArray(vao)
        // Draw fullscreen quad via gl_VertexID in vertex shader
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        
        GLES30.glUniform1f(uFillModeLocation, 0.0f)
    }

    private fun renderSource(source: MandalaVisualSource?, gain: Float = 1.0f, opacityOverride: Float? = null) {
        if (source == null) return
        
        source.update()
        
        // Phase 2.4: Update Uniforms
        val localScale = source.parameters["Scale"]?.value ?: 0.125f
        val finalScale = localScale * source.globalScale.value * 8.0f
        val rotation = (source.parameters["Rotation"]?.value ?: 0f) * 2.0f * PI.toFloat()
        
        val hueOffset = source.parameters["Hue Offset"]?.value ?: 0f
        val hueSweep = source.parameters["Hue Sweep"]?.value ?: 1.0f
        val alpha = (opacityOverride ?: source.globalAlpha.value) * gain

        GLES30.glUniform1f(uFillModeLocation, 0.0f)
        GLES30.glUniform1f(uHueOffsetLocation, hueOffset)
        GLES30.glUniform1f(uHueSweepLocation, hueSweep)
        GLES30.glUniform1f(uAlphaLocation, alpha)
        GLES30.glUniform1f(uGlobalScaleLocation, finalScale)
        GLES30.glUniform1f(uGlobalRotationLocation, rotation)
        GLES30.glUniform1f(uAspectRatioLocation, aspectRatio)

        // Upload new geometry data [X, Y, Phase]
        vertexBuffer.clear()
        vertexBuffer.put(source.geometryBuffer)
        vertexBuffer.position(0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, source.geometryBuffer.size * 4, vertexBuffer)

        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_LINE_STRIP, 0, resolution + 1)
    }
}
