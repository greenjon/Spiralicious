package llm.slop.spirals

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import androidx.compose.runtime.staticCompositionLocalOf
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.roundToInt
import llm.slop.spirals.cv.ModulatableParameter

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
            mixerParams["G$i"] = ModulatableParameter(1.0f)
            mixerParams["PN$i"] = ModulatableParameter(0.0f)
        }
        listOf("A", "B", "F").forEach { g ->
            mixerParams["M${g}_MODE"] = ModulatableParameter(0.0f)
            mixerParams["M${g}_BAL"] = ModulatableParameter(0.5f)
            mixerParams["M${g}_MIX"] = ModulatableParameter(0.5f)
            mixerParams["M${g}_GAIN"] = ModulatableParameter(1.0f)
        }
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
            syncParam(mixerParams["G${i+1}"]!!, patch.slots[i].gain)
            syncParam(mixerParams["PN${i+1}"]!!, patch.slots[i].currentIndex)
        }
        syncGroup(patch.mixerA, "A")
        syncGroup(patch.mixerB, "B")
        syncGroup(patch.mixerF, "F")
    }

    private fun syncGroup(group: MixerGroupData, prefix: String) {
        syncParam(mixerParams["M${prefix}_MODE"]!!, group.mode)
        syncParam(mixerParams["M${prefix}_BAL"]!!, group.balance)
        syncParam(mixerParams["M${prefix}_MIX"]!!, group.mix)
        syncParam(mixerParams["M${prefix}_GAIN"]!!, group.gain)
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

        val patch = mixerPatch
        val monitor = monitorSource

        if (patch == null) {
            renderSource(visualSource)
            return
        }

        when (monitor) {
            "1", "2", "3", "4" -> {
                val idx = monitor.toInt() - 1
                if (patch.slots[idx].enabled) {
                    val gain = mixerParams["G$monitor"]?.evaluate() ?: 1.0f
                    renderSource(slotSources[idx], gain = gain)
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
                val mixF = mixerParams["MF_MIX"]?.evaluate() ?: 0.5f
                val modeFIdx = ((mixerParams["MF_MODE"]?.evaluate() ?: 0f) * (MixerMode.values().size - 1)).roundToInt()
                val modeF = MixerMode.values()[modeFIdx.coerceIn(0, MixerMode.values().size - 1)]

                // Group A contribution
                renderHierarchicalGroup("A", slotSources[0], slotSources[1], patch.slots[0], patch.slots[1], 
                    groupGainScale = gainF * (1.0f - balF) * 2.0f)
                
                // Group B contribution
                setBlendMode(modeF)
                renderHierarchicalGroup("B", slotSources[2], slotSources[3], patch.slots[2], patch.slots[3], 
                    groupGainScale = gainF * balF * 2.0f * mixF)
                
                GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
                GLES30.glBlendEquation(GLES30.GL_FUNC_ADD)
            }
            else -> renderSource(visualSource)
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
        val gain = mixerParams["M${prefix}_GAIN"]?.evaluate() ?: 1.0f
        val bal = mixerParams["M${prefix}_BAL"]?.evaluate() ?: 0.5f
        val mix = mixerParams["M${prefix}_MIX"]?.evaluate() ?: 0.5f
        val modeIdx = ((mixerParams["M${prefix}_MODE"]?.evaluate() ?: 0f) * (MixerMode.values().size - 1)).roundToInt()
        val mode = MixerMode.values()[modeIdx.coerceIn(0, MixerMode.values().size - 1)]

        val totalGroupGain = gain * groupGainScale

        if (slot1.enabled) {
            val bal1 = (1.0f - bal) * 2.0f
            val slot1Gain = if (prefix == "A") mixerParams["G1"]!!.evaluate() else mixerParams["G3"]!!.evaluate()
            renderSource(src1, gain = slot1Gain * bal1.coerceIn(0f, 1f) * totalGroupGain)
        }
        
        if (slot2.enabled) {
            setBlendMode(mode)
            val bal2 = bal * 2.0f
            val slot2Gain = if (prefix == "A") mixerParams["G2"]!!.evaluate() else mixerParams["G4"]!!.evaluate()
            renderSource(src2, gain = slot2Gain * bal2.coerceIn(0f, 1f) * mix * totalGroupGain)
            
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

    private fun renderSource(source: MandalaVisualSource?, gain: Float = 1.0f, opacityOverride: Float? = null) {
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
        val alpha = (opacityOverride ?: source.globalAlpha.value) * gain

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
