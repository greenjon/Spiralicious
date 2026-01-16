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

    companion object {
        const val TARGET_WIDTH = 1920
        const val TARGET_HEIGHT = 1080
    }

    private var program: Int = 0
    private var vao: Int = 0
    private var vbo: Int = 0
    
    private var trailProgram: Int = 0
    private var trailVao: Int = 0
    private var trailVbo: Int = -1
    private var uTrailAlphaLocation: Int = -1
    private var uTrailTextureLocation: Int = -1

    private var feedbackProgram: Int = 0
    private var uFBDecayLoc: Int = -1
    private var uFBGainLoc: Int = -1
    private var uFBZoomLoc: Int = -1
    private var uFBRotateLoc: Int = -1
    private var uFBShiftLoc: Int = -1
    private var uFBBlurLoc: Int = -1
    private var uFBTextureLiveLoc: Int = -1
    private var uFBTextureHistoryLoc: Int = -1

    private var mixerProgram: Int = 0
    private var uMixTex1Loc: Int = -1
    private var uMixTex2Loc: Int = -1
    private var uMixModeLoc: Int = -1
    private var uMixBalLoc: Int = -1
    private var uMixAlphaLoc: Int = -1

    private var blitProgram: Int = 0
    private var uBlitTextureLoc: Int = -1

    private val resolution = 2048
    
    private val MAX_GHOSTS = 16
    private val ghostTextures = IntArray(MAX_GHOSTS)
    private val ghostFramebuffers = IntArray(MAX_GHOSTS)
    private var currentFrameFBO: Int = 0
    private var currentFrameTexture: Int = 0
    private var ghostIndex = 0

    private val fbTextures = IntArray(2)
    private val fbFramebuffers = IntArray(2)
    private var fbIndex = 0

    private val masterTextures = IntArray(7)
    private val masterFramebuffers = IntArray(7)

    private var screenWidth = 0
    private var screenHeight = 0
    
    private var isSnapshotArmed: Boolean = true
    
    private var lastL1 = -1f; private var lastL2 = -1f; private var lastL3 = -1f; private var lastL4 = -1f; private var lastRotation = -1f

    @Volatile
    var visualSource: MandalaVisualSource? = null
        set(value) {
            if (field != value) {
                field = value
                clearFeedbackNextFrame = true
            }
        }

    @Volatile
    var mixerPatch: MixerPatch? = null
        set(value) {
            if (field == null || (value != null && field?.id != value.id)) {
                clearFeedbackNextFrame = true
            }
            field = value
            value?.let { syncMixerParameters(it) }
        }
        
    private var clearFeedbackNextFrame = false

    @Volatile
    var monitorSource: String = "F" 
    
    private val slotSources = Array(4) { MandalaVisualSource() }
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
        listOf("FB_DECAY", "FB_GAIN", "FB_ZOOM", "FB_ROTATE", "FB_SHIFT", "FB_BLUR", "TRAILS", "SNAP_COUNT", "SNAP_MODE", "SNAP_BLEND", "SNAP_TRIG").forEach {
            mixerParams["MF_$it"] = ModulatableParameter(0.0f)
        }
        mixerParams["MF_FB_GAIN"]?.baseValue = 1.0f
        mixerParams["MF_FB_ZOOM"]?.baseValue = 0.5f
        mixerParams["MF_FB_ROTATE"]?.baseValue = 0.5f
        mixerParams["MF_SNAP_COUNT"]?.baseValue = 0.5f
    }

    fun getSlotSource(index: Int): MandalaVisualSource = slotSources[index]
    fun getMixerParam(id: String): ModulatableParameter? = mixerParams[id]

    fun getTextureForSource(source: String): Int {
        return when (source) {
            "1" -> masterTextures[0]; "2" -> masterTextures[1]; "3" -> masterTextures[2]
            "4" -> masterTextures[3]; "A" -> masterTextures[4]; "B" -> masterTextures[5]
            "F" -> masterTextures[6]; else -> 0
        }
    }

    private fun syncMixerParameters(patch: MixerPatch) {
        for (i in 0..3) {
            mixerParams["PN${i+1}"]?.let { syncParam(it, patch.slots[i].currentIndex) }
            mixerParams["H${i+1}"]?.let { syncParam(it, patch.slots[i].hue) }
            mixerParams["S${i+1}"]?.let { syncParam(it, patch.slots[i].saturation) }
        }
        syncGroup(patch.mixerA, "A"); syncGroup(patch.mixerB, "B"); syncGroup(patch.mixerF, "F")
        mixerParams["MF_GAIN"]?.let { syncParam(it, patch.finalGain) }
        val fx = patch.effects
        mixerParams["MF_FB_DECAY"]?.let { syncParam(it, fx.fbDecay) }
        mixerParams["MF_FB_GAIN"]?.let { syncParam(it, fx.fbGain) }
        mixerParams["MF_FB_ZOOM"]?.let { syncParam(it, fx.fbZoom) }
        mixerParams["MF_FB_ROTATE"]?.let { syncParam(it, fx.fbRotate) }
        mixerParams["MF_FB_SHIFT"]?.let { syncParam(it, fx.fbShift) }
        mixerParams["MF_FB_BLUR"]?.let { syncParam(it, fx.fbBlur) }
        mixerParams["MF_TRAILS"]?.let { syncParam(it, fx.trails) }
        mixerParams["MF_SNAP_COUNT"]?.let { syncParam(it, fx.snapCount) }
        mixerParams["MF_SNAP_MODE"]?.let { syncParam(it, fx.snapMode) }
        mixerParams["MF_SNAP_BLEND"]?.let { syncParam(it, fx.snapBlend) }
        mixerParams["MF_SNAP_TRIG"]?.let { syncParam(it, fx.snapTrigger) }
    }

    private fun syncGroup(group: MixerGroupData, prefix: String) {
        mixerParams["M${prefix}_MODE"]?.let { syncParam(it, group.mode) }
        mixerParams["M${prefix}_BAL"]?.let { syncParam(it, group.balance) }
    }

    private fun syncParam(target: ModulatableParameter, source: ModulatableParameterData) {
        target.baseValue = source.baseValue
        target.modulators.clear(); target.modulators.addAll(source.modulators)
    }

    private var aspectRatio: Float = 1f
    private var uHueOffsetLocation: Int = -1; private var uHueSweepLocation: Int = -1; private var uAlphaLocation: Int = -1
    private var uGlobalRotationLocation: Int = -1; private var uAspectRatioLocation: Int = -1; private var uGlobalScaleLocation: Int = -1
    private var uFillModeLocation: Int = -1; private var uDepthLocation: Int = -1; private var uMinRLocation: Int = -1
    private var uMaxRLocation: Int = -1; private var uThicknessLocation: Int = -1; private var uLayerOffsetLocation: Int = -1
    private var uLayerAlphaLocation: Int = -1; private var uLayerScaleLocation: Int = -1; private var uL1Loc: Int = -1
    private var uL2Loc: Int = -1; private var uL3Loc: Int = -1; private var uL4Loc: Int = -1; private var uALoc: Int = -1
    private var uBLoc: Int = -1; private var uCLoc: Int = -1; private var uDLoc: Int = -1

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f); GLES30.glEnable(GLES30.GL_BLEND); GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        program = ShaderHelper.buildProgram(context, R.raw.mandala_vertex, R.raw.mandala_fragment)
        trailProgram = ShaderHelper.buildProgram(context, R.raw.trail_vertex, R.raw.trail_fragment)
        feedbackProgram = ShaderHelper.buildProgram(context, R.raw.trail_vertex, R.raw.feedback_fragment)
        mixerProgram = ShaderHelper.buildProgram(context, R.raw.trail_vertex, R.raw.mixer_fragment)
        blitProgram = ShaderHelper.buildProgram(context, R.raw.trail_vertex, R.raw.passthrough_fragment)
        uHueOffsetLocation = GLES30.glGetUniformLocation(program, "uHueOffset"); uHueSweepLocation = GLES30.glGetUniformLocation(program, "uHueSweep")
        uAlphaLocation = GLES30.glGetUniformLocation(program, "uAlpha"); uGlobalRotationLocation = GLES30.glGetUniformLocation(program, "uGlobalRotation")
        uAspectRatioLocation = GLES30.glGetUniformLocation(program, "uAspectRatio"); uGlobalScaleLocation = GLES30.glGetUniformLocation(program, "uGlobalScale")
        uFillModeLocation = GLES30.glGetUniformLocation(program, "uFillMode"); uDepthLocation = GLES30.glGetUniformLocation(program, "uDepth")
        uMinRLocation = GLES30.glGetUniformLocation(program, "uMinR"); uMaxRLocation = GLES30.glGetUniformLocation(program, "uMaxR")
        uThicknessLocation = GLES30.glGetUniformLocation(program, "uThickness"); uLayerOffsetLocation = GLES30.glGetUniformLocation(program, "uLayerOffset")
        uLayerAlphaLocation = GLES30.glGetUniformLocation(program, "uLayerAlpha"); uLayerScaleLocation = GLES30.glGetUniformLocation(program, "uLayerScale")
        uL1Loc = GLES30.glGetUniformLocation(program, "uL1"); uL2Loc = GLES30.glGetUniformLocation(program, "uL2"); uL3Loc = GLES30.glGetUniformLocation(program, "uL3")
        uL4Loc = GLES30.glGetUniformLocation(program, "uL4"); uALoc = GLES30.glGetUniformLocation(program, "uA"); uBLoc = GLES30.glGetUniformLocation(program, "uB")
        uCLoc = GLES30.glGetUniformLocation(program, "uC"); uDLoc = GLES30.glGetUniformLocation(program, "uD"); uTrailAlphaLocation = GLES30.glGetUniformLocation(trailProgram, "uAlpha")
        uTrailTextureLocation = GLES30.glGetUniformLocation(trailProgram, "uTexture"); uFBDecayLoc = GLES30.glGetUniformLocation(feedbackProgram, "uDecay")
        uFBGainLoc = GLES30.glGetUniformLocation(feedbackProgram, "uGain"); uFBZoomLoc = GLES30.glGetUniformLocation(feedbackProgram, "uZoom")
        uFBRotateLoc = GLES30.glGetUniformLocation(feedbackProgram, "uRotate"); uFBShiftLoc = GLES30.glGetUniformLocation(feedbackProgram, "uHueShift")
        uFBBlurLoc = GLES30.glGetUniformLocation(feedbackProgram, "uBlur"); uFBTextureLiveLoc = GLES30.glGetUniformLocation(feedbackProgram, "uTextureLive")
        uFBTextureHistoryLoc = GLES30.glGetUniformLocation(feedbackProgram, "uTextureHistory"); uMixTex1Loc = GLES30.glGetUniformLocation(mixerProgram, "uTex1")
        uMixTex2Loc = GLES30.glGetUniformLocation(mixerProgram, "uTex2"); uMixModeLoc = GLES30.glGetUniformLocation(mixerProgram, "uMode")
        uMixBalLoc = GLES30.glGetUniformLocation(mixerProgram, "uBalance"); uMixAlphaLoc = GLES30.glGetUniformLocation(mixerProgram, "uAlpha")
        uBlitTextureLoc = GLES30.glGetUniformLocation(blitProgram, "uTexture")
        val vaoArr = IntArray(1); GLES30.glGenVertexArrays(1, vaoArr, 0); vao = vaoArr[0]
        val vboArr = IntArray(1); GLES30.glGenBuffers(1, vboArr, 0); vbo = vboArr[0]
        GLES30.glBindVertexArray(vao); GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        val dummy = MandalaVisualSource(); GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, dummy.expansionBuffer.size * 4, FloatBuffer.wrap(dummy.expansionBuffer), GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)
        val tVaoArr = IntArray(1); GLES30.glGenVertexArrays(1, tVaoArr, 0); trailVao = tVaoArr[0]
        val tVboArr = IntArray(1); GLES30.glGenBuffers(1, tVboArr, 0); trailVbo = tVboArr[0]
        GLES30.glBindVertexArray(trailVao); GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, trailVbo)
        val quad = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 32, FloatBuffer.wrap(quad), GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height); aspectRatio = TARGET_WIDTH.toFloat() / TARGET_HEIGHT.toFloat()
        screenWidth = width; screenHeight = height; setupFBOs()
    }

    private fun setupFBOs() {
        fun cT(w: Int, h: Int): Int {
            val a = IntArray(1); GLES30.glGenTextures(1, a, 0); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, a[0])
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR); GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE); GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            return a[0]
        }
        fun cF(t: Int): Int {
            val a = IntArray(1); GLES30.glGenFramebuffers(1, a, 0); GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, a[0])
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, t, 0); return a[0]
        }
        for (i in 0..6) { masterTextures[i] = cT(TARGET_WIDTH, TARGET_HEIGHT); masterFramebuffers[i] = cF(masterTextures[i]) }
        for (i in 0 until MAX_GHOSTS) { ghostTextures[i] = cT(TARGET_WIDTH, TARGET_HEIGHT); ghostFramebuffers[i] = cF(ghostTextures[i]) }
        currentFrameTexture = cT(TARGET_WIDTH, TARGET_HEIGHT); currentFrameFBO = cF(currentFrameTexture)
        for (i in 0..1) { fbTextures[i] = cT(TARGET_WIDTH, TARGET_HEIGHT); fbFramebuffers[i] = cF(fbTextures[i]) }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (program == 0 || screenWidth == 0) return
        if (clearFeedbackNextFrame) {
            for (i in 0..1) { GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbFramebuffers[i]); GLES30.glClearColor(0f,0f,0f,0f); GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT) }
            for (i in 0 until MAX_GHOSTS) { GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, ghostFramebuffers[i]); GLES30.glClearColor(0f,0f,0f,0f); GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT) }
            clearFeedbackNextFrame = false
        }
        visualSource?.update(); for (i in 0..3) slotSources[i].update(); mixerParams.values.forEach { it.evaluate() }
        
        val p = mixerPatch
        GLES30.glViewport(0, 0, TARGET_WIDTH, TARGET_HEIGHT)
        if (p != null) {
            for (i in 0..3) {
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, masterFramebuffers[i]); GLES30.glClearColor(0f,0f,0f,0f); GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                if (p.slots[i].enabled && p.slots[i].isPopulated()) { GLES30.glUseProgram(program); renderSource(slotSources[i]) }
            }
            renderMixerGroup(4, masterTextures[0], masterTextures[1], "A")
            renderMixerGroup(5, masterTextures[2], masterTextures[3], "B")
            compositeFinalMixer(masterTextures[4], masterTextures[5])
        } else {
            val fx = visualSource?.parameters; val fxMap = if (fx != null) mapOf("FB_DECAY" to (fx["FB Decay"]?.value ?: 0f), "FB_GAIN" to (fx["FB Gain"]?.value ?: 1f), "FB_ZOOM" to (fx["FB Zoom"]?.value ?: 0.5f), "FB_ROTATE" to (fx["FB Rotate"]?.value ?: 0.5f), "FB_SHIFT" to (fx["FB Shift"]?.value ?: 0f), "FB_BLUR" to (fx["FB Blur"]?.value ?: 0f), "TRAILS" to (fx["Trails"]?.value ?: 0f), "SNAP_COUNT" to (fx["Snap Count"]?.value ?: 0.5f), "SNAP_MODE" to (fx["Snap Mode"]?.value ?: 0f), "SNAP_BLEND" to (fx["Snap Blend"]?.value ?: 0f), "SNAP_TRIG" to (fx["Snap Trigger"]?.value ?: 0f)) else emptyMap()
            compositeWithFX({ GLES30.glUseProgram(program); renderSource(visualSource!!) }, fxMap, isSourceStatic(visualSource), 6)
        }

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0); GLES30.glViewport(0, 0, screenWidth, screenHeight); GLES30.glClearColor(0f,0f,0f,1f); GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        val dTex = getTextureForSource(monitorSource); if (dTex != 0) drawTextureToCurrentBuffer(dTex)
    }

    private fun isSourceStatic(s: MandalaVisualSource?): Boolean {
        if (s == null) return true; val p = s.parameters; val res = (p["L1"]?.value ?: 0f) == lastL1 && (p["L2"]?.value ?: 0f) == lastL2 && (p["L3"]?.value ?: 0f) == lastL3 && (p["L4"]?.value ?: 0f) == lastL4 && (p["Rotation"]?.value ?: 0f) == lastRotation
        lastL1 = p["L1"]?.value ?: 0f; lastL2 = p["L2"]?.value ?: 0f; lastL3 = p["L3"]?.value ?: 0f; lastL4 = p["L4"]?.value ?: 0f; lastRotation = p["Rotation"]?.value ?: 0f; return res
    }

    fun drawTextureToCurrentBuffer(tId: Int) {
        GLES30.glUseProgram(blitProgram); GLES30.glBindVertexArray(trailVao); GLES30.glActiveTexture(GLES30.GL_TEXTURE0); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tId); GLES30.glUniform1i(uBlitTextureLoc, 0); GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun renderMixerGroup(tIdx: Int, t1: Int, t2: Int, pre: String) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, masterFramebuffers[tIdx]); GLES30.glClearColor(0f,0f,0f,0f); GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT); GLES30.glUseProgram(mixerProgram); GLES30.glBindVertexArray(trailVao)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t1); GLES30.glUniform1i(uMixTex1Loc, 0); GLES30.glActiveTexture(GLES30.GL_TEXTURE1); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t2); GLES30.glUniform1i(uMixTex2Loc, 1)
        GLES30.glUniform1i(uMixModeLoc, ((mixerParams["M${pre}_MODE"]?.value ?: 0f) * (MixerMode.entries.size - 1)).roundToInt()); GLES30.glUniform1f(uMixBalLoc, mixerParams["M${pre}_BAL"]?.value ?: 0.5f); GLES30.glUniform1f(uMixAlphaLoc, 1.0f); GLES30.glDisable(GLES30.GL_BLEND); GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4); GLES30.glEnable(GLES30.GL_BLEND)
    }

    private fun compositeFinalMixer(tA: Int, tB: Int) {
        val fx = mapOf("FB_DECAY" to mixerParams["MF_FB_DECAY"]!!.value, "FB_GAIN" to mixerParams["MF_FB_GAIN"]!!.value, "FB_ZOOM" to mixerParams["MF_FB_ZOOM"]!!.value, "FB_ROTATE" to mixerParams["MF_FB_ROTATE"]!!.value, "FB_SHIFT" to mixerParams["MF_FB_SHIFT"]!!.value, "FB_BLUR" to mixerParams["MF_FB_BLUR"]!!.value, "TRAILS" to mixerParams["MF_TRAILS"]!!.value, "SNAP_COUNT" to mixerParams["MF_SNAP_COUNT"]!!.value, "SNAP_MODE" to mixerParams["MF_SNAP_MODE"]!!.value, "SNAP_BLEND" to mixerParams["MF_SNAP_BLEND"]!!.value, "SNAP_TRIG" to mixerParams["MF_SNAP_TRIG"]!!.value)
        compositeWithFX({ GLES30.glUseProgram(mixerProgram); GLES30.glActiveTexture(GLES30.GL_TEXTURE0); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tA); GLES30.glUniform1i(uMixTex1Loc, 0); GLES30.glActiveTexture(GLES30.GL_TEXTURE1); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tB); GLES30.glUniform1i(uMixTex2Loc, 1); GLES30.glUniform1i(uMixModeLoc, ((mixerParams["MF_MODE"]?.value ?: 0f) * (MixerMode.entries.size - 1)).roundToInt()); GLES30.glUniform1f(uMixBalLoc, mixerParams["MF_BAL"]?.value ?: 0.5f); GLES30.glUniform1f(uMixAlphaLoc, mixerParams["MF_GAIN"]?.value ?: 1f); GLES30.glDisable(GLES30.GL_BLEND); GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4); GLES30.glEnable(GLES30.GL_BLEND) }, fx, false, 6)
    }

    private fun compositeWithFX(ren: () -> Unit, fx: Map<String, Float>, s: Boolean, targetFboIdx: Int) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, currentFrameFBO); GLES30.glClearColor(0f, 0f, 0f, 0f); GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT); ren()
        val dec = fx["FB_DECAY"] ?: 0f; val gain = fx["FB_GAIN"] ?: 1f
        if (dec > 0.01f || gain > 0.01f) {
            val n = (fbIndex + 1) % 2; GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbFramebuffers[n]); GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            GLES30.glUseProgram(feedbackProgram); GLES30.glBindVertexArray(trailVao)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, currentFrameTexture); GLES30.glUniform1i(uFBTextureLiveLoc, 0)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fbTextures[fbIndex]); GLES30.glUniform1i(uFBTextureHistoryLoc, 1)
            GLES30.glUniform1f(uFBDecayLoc, 1.0f - (1.0f - dec).pow(6.0f)); GLES30.glUniform1f(uFBGainLoc, gain * 1.5f)
            GLES30.glUniform1f(uFBZoomLoc, ((fx["FB_ZOOM"] ?: 0.5f) - 0.5f) * 0.1f); GLES30.glUniform1f(uFBRotateLoc, ((fx["FB_ROTATE"] ?: 0.5f) - 0.5f) * 10f * (PI.toFloat() / 180f))
            GLES30.glUniform1f(uFBShiftLoc, fx["FB_SHIFT"] ?: 0f); GLES30.glUniform1f(uFBBlurLoc, fx["FB_BLUR"] ?: 0f)
            GLES30.glDisable(GLES30.GL_BLEND); GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4); GLES30.glEnable(GLES30.GL_BLEND); fbIndex = n
        }
        val t = fx["SNAP_TRIG"] ?: 0f; val trs = fx["TRAILS"] ?: 0f
        if (t > 0.5f && isSnapshotArmed && !s && trs > 0.01f) {
            ghostIndex = (ghostIndex + 1) % MAX_GHOSTS; GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, currentFrameFBO); GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, ghostFramebuffers[ghostIndex]); GLES30.glBlitFramebuffer(0,0,TARGET_WIDTH,TARGET_HEIGHT,0,0,TARGET_WIDTH,TARGET_HEIGHT,GLES30.GL_COLOR_BUFFER_BIT,GLES30.GL_NEAREST); isSnapshotArmed = false
        } else if (t <= 0.5f) isSnapshotArmed = true
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, masterFramebuffers[targetFboIdx]); GLES30.glClearColor(0f, 0f, 0f, 0f); GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT); GLES30.glUseProgram(trailProgram); GLES30.glBindVertexArray(trailVao)
        if ((fx["SNAP_BLEND"] ?: 0f) >= 0.5f) GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE) else GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        val cnt = ((fx["SNAP_COUNT"] ?: 0.5f) * 14f + 2f).toInt().coerceIn(2, MAX_GHOSTS); val mde = fx["SNAP_MODE"] ?: 0f
        val dG = { for (i in (cnt - 1) downTo 1) { val idx = (ghostIndex - i + MAX_GHOSTS) % MAX_GHOSTS; GLES30.glUniform1f(uTrailAlphaLocation, trs * exp(-(i.toFloat() * i.toFloat()) / (cnt.toFloat() * 1.5f))); GLES30.glActiveTexture(GLES30.GL_TEXTURE0); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ghostTextures[idx]); GLES30.glUniform1i(uTrailTextureLocation, 0); GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4) } }
        val dC = { GLES30.glUniform1f(uTrailAlphaLocation, 1.0f); GLES30.glActiveTexture(GLES30.GL_TEXTURE0); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (dec > 0.01f || gain > 0.01f) fbTextures[fbIndex] else currentFrameTexture)
            GLES30.glUniform1i(uTrailTextureLocation, 0); GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4) }
        if (mde < 0.5f) { dG(); dC() } else { dC(); dG() }
    }

    private fun renderSource(s: MandalaVisualSource) {
        val p = s.parameters; val hS = ((p["Hue Sweep"]?.value ?: (1f/9f)) * 9f).roundToInt().toFloat()
        GLES30.glUniform1f(uFillModeLocation, 0f); GLES30.glUniform1f(uHueOffsetLocation, p["Hue Offset"]?.value ?: 0f); GLES30.glUniform1f(uHueSweepLocation, hS); GLES30.glUniform1f(uAlphaLocation, s.globalAlpha.value)
        GLES30.glUniform1f(uGlobalScaleLocation, (p["Scale"]?.value ?: 0.125f) * s.globalScale.value * 8f); GLES30.glUniform1f(uGlobalRotationLocation, (p["Rotation"]?.value ?: 0f) * 2f * PI.toFloat()); GLES30.glUniform1f(uAspectRatioLocation, aspectRatio); GLES30.glUniform1f(uDepthLocation, p["Depth"]?.value ?: 0.35f); GLES30.glUniform1f(uMinRLocation, s.minR); GLES30.glUniform1f(uMaxRLocation, s.maxR); GLES30.glUniform1f(uThicknessLocation, p["Thickness"]?.value ?: 0.1f)
        GLES30.glUniform1f(uLayerOffsetLocation, 0f); GLES30.glUniform1f(uLayerAlphaLocation, 1f); GLES30.glUniform1f(uLayerScaleLocation, 1f); GLES30.glUniform1f(uL1Loc, p["L1"]?.value ?: 0f); GLES30.glUniform1f(uL2Loc, p["L2"]?.value ?: 0f); GLES30.glUniform1f(uL3Loc, p["L3"]?.value ?: 0f); GLES30.glUniform1f(uL4Loc, p["L4"]?.value ?: 0f); GLES30.glUniform1f(uALoc, s.recipe.a.toFloat()); GLES30.glUniform1f(uBLoc, s.recipe.b.toFloat()); GLES30.glUniform1f(uCLoc, s.recipe.c.toFloat()); GLES30.glUniform1f(uDLoc, s.recipe.d.toFloat()); GLES30.glBindVertexArray(vao); GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, (resolution + 1) * 2)
    }
}
