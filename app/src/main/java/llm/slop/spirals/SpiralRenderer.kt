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
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import llm.slop.spirals.cv.ModulatableParameter
import llm.slop.spirals.models.*

val LocalSpiralRenderer = staticCompositionLocalOf<SpiralRenderer?> { null }

class SpiralRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private var program: Int = 0
    private var vao: Int = 0
    private var vbo: Int = 0
    
    // Trail Post-Processing
    private var trailProgram: Int = 0
    private var trailVao: Int = 0
    private var trailVbo: Int = 0
    private var uTrailAlphaLocation: Int = -1
    private var uTrailTextureLocation: Int = -1

    private val resolution = 2048
    
    // Ghost Trails FBO Ring Buffer
    private val MAX_GHOSTS = 16
    private val ghostTextures = IntArray(MAX_GHOSTS)
    private val ghostFramebuffers = IntArray(MAX_GHOSTS)
    private var currentFrameFBO: Int = 0
    private var currentFrameTexture: Int = 0
    private var ghostIndex = 0
    private var screenWidth = 0
    private var screenHeight = 0
    
    // Snapshot Trigger State
    private var isSnapshotArmed: Boolean = true
    
    // Dirty Guard State
    private var lastL1 = -1f
    private var lastL2 = -1f
    private var lastL3 = -1f
    private var lastL4 = -1f
    private var lastRotation = -1f

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
    
    private var uDepthLocation: Int = -1
    private var uMinRLocation: Int = -1
    private var uMaxRLocation: Int = -1

    private lateinit var vertexBuffer: FloatBuffer

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        program = ShaderHelper.buildProgram(context, R.raw.mandala_vertex, R.raw.mandala_fragment)
        trailProgram = ShaderHelper.buildProgram(context, R.raw.trail_vertex, R.raw.trail_fragment)
        
        uHueOffsetLocation = GLES30.glGetUniformLocation(program, "uHueOffset")
        uHueSweepLocation = GLES30.glGetUniformLocation(program, "uHueSweep")
        uAlphaLocation = GLES30.glGetUniformLocation(program, "uAlpha")
        uGlobalRotationLocation = GLES30.glGetUniformLocation(program, "uGlobalRotation")
        uAspectRatioLocation = GLES30.glGetUniformLocation(program, "uAspectRatio")
        uGlobalScaleLocation = GLES30.glGetUniformLocation(program, "uGlobalScale")
        uFillModeLocation = GLES30.glGetUniformLocation(program, "uFillMode")
        
        uDepthLocation = GLES30.glGetUniformLocation(program, "uDepth")
        uMinRLocation = GLES30.glGetUniformLocation(program, "uMinR")
        uMaxRLocation = GLES30.glGetUniformLocation(program, "uMaxR")
        
        uTrailAlphaLocation = GLES30.glGetUniformLocation(trailProgram, "uAlpha")
        uTrailTextureLocation = GLES30.glGetUniformLocation(trailProgram, "uTexture")

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
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, (resolution + 1) * 3 * 4, null, GLES30.GL_STREAM_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 3 * 4, 0)

        // Setup Fullscreen Quad for Trails
        val trailVaoArray = IntArray(1)
        GLES30.glGenVertexArrays(1, trailVaoArray, 0)
        trailVao = trailVaoArray[0]
        GLES30.glBindVertexArray(trailVao)
        
        val trailVboArray = IntArray(1)
        GLES30.glGenBuffers(1, trailVboArray, 0)
        trailVbo = trailVboArray[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, trailVbo)
        
        val quadCoords = floatArrayOf(
            -1f, -1f,
             1f, -1f,
            -1f,  1f,
             1f,  1f 
        )
        val quadBuffer = ByteBuffer.allocateDirect(quadCoords.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        quadBuffer.put(quadCoords).position(0)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quadCoords.size * 4, quadBuffer, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        aspectRatio = width.toFloat() / height.toFloat()
        screenWidth = width
        screenHeight = height
        setupGhostFBOs(width, height)
    }

    private fun setupGhostFBOs(width: Int, height: Int) {
        deleteGhostFBOs()
        
        GLES30.glGenFramebuffers(MAX_GHOSTS, ghostFramebuffers, 0)
        GLES30.glGenTextures(MAX_GHOSTS, ghostTextures, 0)
        
        for (i in 0 until MAX_GHOSTS) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ghostTextures[i])
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, width, height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, ghostFramebuffers[i])
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, ghostTextures[i], 0)
            
            GLES30.glClearColor(0f, 0f, 0f, 0f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        }
        
        val fboArr = IntArray(1)
        val texArr = IntArray(1)
        GLES30.glGenFramebuffers(1, fboArr, 0)
        GLES30.glGenTextures(1, texArr, 0)
        currentFrameFBO = fboArr[0]
        currentFrameTexture = texArr[0]
        
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, currentFrameTexture)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, width, height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, currentFrameFBO)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, currentFrameTexture, 0)
        
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    private fun deleteGhostFBOs() {
        if (ghostFramebuffers[0] != 0) {
            GLES30.glDeleteFramebuffers(MAX_GHOSTS, ghostFramebuffers, 0)
            GLES30.glDeleteTextures(MAX_GHOSTS, ghostTextures, 0)
            ghostFramebuffers.fill(0)
        }
        if (currentFrameFBO != 0) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(currentFrameFBO), 0)
            GLES30.glDeleteTextures(1, intArrayOf(currentFrameTexture), 0)
            currentFrameFBO = 0
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        if (program == 0 || screenWidth == 0) return

        val source = visualSource
        val currentGate = source?.parameters?.get("Snap Trigger")?.value ?: 0f
        val trailsParam = source?.parameters?.get("Trails")?.value ?: 0f
        val snapCount = ((source?.parameters?.get("Snap Count")?.value ?: 0.5f) * 14f + 2f).toInt().coerceIn(2, 16)
        val snapMode = source?.parameters?.get("Snap Mode")?.value ?: 0f
        val snapBlend = source?.parameters?.get("Snap Blend")?.value ?: 0f

        // Phase A: Render Current to offscreen FBO
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, currentFrameFBO)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        
        GLES30.glUseProgram(program)
        renderSource(source)
        
        // Dirty Guard Logic
        val l1 = source?.parameters?.get("L1")?.value ?: 0f
        val l2 = source?.parameters?.get("L2")?.value ?: 0f
        val l3 = source?.parameters?.get("L3")?.value ?: 0f
        val l4 = source?.parameters?.get("L4")?.value ?: 0f
        val rot = source?.parameters?.get("Rotation")?.value ?: 0f
        val isStatic = l1 == lastL1 && l2 == lastL2 && l3 == lastL3 && l4 == lastL4 && rot == lastRotation
        
        lastL1 = l1; lastL2 = l2; lastL3 = l3; lastL4 = l4; lastRotation = rot

        // Phase B: Gated Snapshot Update (Edge Trigger)
        var shouldCapture = false
        if (currentGate > 0.5f) {
            if (isSnapshotArmed && !isStatic) {
                shouldCapture = true
                isSnapshotArmed = false
            }
        } else {
            isSnapshotArmed = true
        }

        if (shouldCapture && trailsParam > 0.01f) {
            ghostIndex = (ghostIndex + 1) % MAX_GHOSTS
            GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, currentFrameFBO)
            GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, ghostFramebuffers[ghostIndex])
            GLES30.glBlitFramebuffer(0, 0, screenWidth, screenHeight, 0, 0, screenWidth, screenHeight, GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_NEAREST)
        }

        // Phase C & D: Composite to Screen
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        
        GLES30.glUseProgram(trailProgram)
        GLES30.glBindVertexArray(trailVao)
        GLES30.glEnable(GLES30.GL_BLEND)
        
        // Set Blend Mode
        if (snapBlend >= 0.5f) {
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE) // Additive
        } else {
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA) // Normal
        }

        val drawGhosts = {
            for (i in (snapCount - 1) downTo 1) {
                val idx = (ghostIndex - i + MAX_GHOSTS) % MAX_GHOSTS
                // Gaussian Decay scaled to Snap Count
                val decayAlpha = trailsParam * exp(-(i.toFloat() * i.toFloat()) / (snapCount.toFloat() * 1.5f))
                
                GLES30.glUniform1f(uTrailAlphaLocation, decayAlpha)
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ghostTextures[idx])
                GLES30.glUniform1i(uTrailTextureLocation, 0)
                GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            }
        }

        val drawCurrent = {
            GLES30.glUniform1f(uTrailAlphaLocation, 1.0f)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, currentFrameTexture)
            GLES30.glUniform1i(uTrailTextureLocation, 0)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        }

        if (snapMode < 0.5f) {
            drawGhosts()
            drawCurrent()
        } else {
            drawCurrent()
            drawGhosts()
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
        
        GLES30.glUniform1f(uDepthLocation, 0.0f)

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
        val depth = source.parameters["Depth"]?.value ?: 0.35f
        val alpha = (opacityOverride ?: source.globalAlpha.value) * gain

        GLES30.glUniform1f(uFillModeLocation, 0.0f)
        GLES30.glUniform1f(uHueOffsetLocation, hueOffset)
        GLES30.glUniform1f(uHueSweepLocation, hueSweep)
        GLES30.glUniform1f(uAlphaLocation, alpha)
        GLES30.glUniform1f(uGlobalScaleLocation, finalScale)
        GLES30.glUniform1f(uGlobalRotationLocation, rotation)
        GLES30.glUniform1f(uAspectRatioLocation, aspectRatio)
        
        GLES30.glUniform1f(uDepthLocation, depth)
        GLES30.glUniform1f(uMinRLocation, source.minR)
        GLES30.glUniform1f(uMaxRLocation, source.maxR)

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
