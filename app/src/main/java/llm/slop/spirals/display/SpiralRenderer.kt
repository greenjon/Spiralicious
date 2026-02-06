package llm.slop.spirals.display

import android.content.Context
import android.graphics.Color
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
import llm.slop.spirals.cv.core.ModulatableParameter
import llm.slop.spirals.models.*
import llm.slop.spirals.MandalaVisualSource
import llm.slop.spirals.R

val LocalSpiralRenderer = staticCompositionLocalOf<SpiralRenderer?> { null }

enum class TransitionState {
    IDLE, IMPLODING, EXPLODING
}

/**
 * Data class to hold feedback parameters.
 */
private data class FeedbackParams(
    val decay: Float,
    val gain: Float,
    val zoom: Float,
    val rotate: Float,
    val shift: Float,
    val blur: Float
)

private val NONE_FEEDBACK_PARAMS = FeedbackParams(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
private val LIGHT_FEEDBACK_PARAMS = FeedbackParams(
    decay = 0.01f, 
    gain = 1.002f, 
    zoom = 0.505f,
    rotate = 0.501f,
    shift = 0.001f,
    blur = 0.001f
)
private val HEAVY_FEEDBACK_PARAMS = FeedbackParams(
    decay = 0.08f,
    gain = 1.2f,
    zoom = 1.00f,
    rotate = 1.00f,
    shift = .001f,
    blur = .5f
)

/**
 * Main OpenGL renderer for Spirals app.
 */
class SpiralRenderer(private val context: Context) : GLSurfaceView.Renderer {

    companion object {
        const val TARGET_WIDTH = 1920
        const val TARGET_HEIGHT = 1080

        private fun lerp(a: Float, b: Float, t: Float): Float {
            return a + t * (b - a)
        }
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
    
    // Per-slot feedback: 2 textures per slot (ping-pong) + 2 for final output
    private val slotFBTextures = Array(4) { IntArray(2) }
    private val slotFBFramebuffers = Array(4) { IntArray(2) }
    private val slotFBIndex = IntArray(4) { 0 }
    
    // Final output feedback
    private val finalFBTextures = IntArray(2)
    private val finalFBFramebuffers = IntArray(2)
    private var finalFBIndex = 0
    
    // Temp rendering target
    private var currentFrameFBO: Int = 0
    private var currentFrameTexture: Int = 0

    private val masterTextures = IntArray(7)
    private val masterFramebuffers = IntArray(7)

    private var screenWidth = 0
    private var screenHeight = 0
    
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

    @Volatile
    var showPatch: ShowPatch? = null
        set(value) {
            field = value
            value?.let { 
                syncShowParameters(it)
                // _feedbackMode is removed, now we sync feedbackAmount
                mixerParams["SHOW_FB_AMOUNT"]?.let { param -> syncParam(param, it.feedbackAmount) }
            }
        }
        
    private var clearFeedbackNextFrame = false

    @Volatile
    var monitorSource: String = "F" 
    
    private val slotSources = Array(4) { MandalaVisualSource() }
    private val mixerParams = mutableMapOf<String, ModulatableParameter>()

    // Transition State
    private var transitionState = TransitionState.IDLE
    private var transitionStartTime = 0L
    private var transitionDurationMillis = 0L
    private var transitionFadeOutPercent = 0.5f 
    private var transitionFadeInPercent = 0.5f  
    private var currentTransitionType: TransitionType = TransitionType.NONE 
    private var fromSource: MandalaVisualSource? = null
    private var toSource: MandalaVisualSource? = null


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

        // Show Triggers
        mixerParams["SHOW_PREV"] = ModulatableParameter(0.0f)
        mixerParams["SHOW_NEXT"] = ModulatableParameter(0.0f)
        mixerParams["SHOW_RANDOM"] = ModulatableParameter(0.0f)
        mixerParams["SHOW_GENERATE"] = ModulatableParameter(0.0f)
        mixerParams["SHOW_FB_AMOUNT"] = ModulatableParameter(0.001f) // New: for feedback amount
        mixerParams["SHOW_BG_HUE"] = ModulatableParameter(0.0f)
        mixerParams["SHOW_BG_BRIGHTNESS"] = ModulatableParameter(0.0f)
    }

    fun startTransition(from: MandalaVisualSource, to: MandalaVisualSource, durationBeats: Float, bpm: Float, fadeOutPercent: Float, fadeInPercent: Float, transitionType: TransitionType) {
        if (durationBeats > 0f) {
            this.fromSource = from.copy()
            this.toSource = to.copy()
            this.transitionDurationMillis = (durationBeats / bpm * 60f * 1000f).toLong()
            this.transitionStartTime = System.currentTimeMillis()
            this.transitionState = TransitionState.IMPLODING
            this.transitionFadeOutPercent = fadeOutPercent
            this.transitionFadeInPercent = fadeInPercent
            this.currentTransitionType = transitionType
        }
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

    private fun syncShowParameters(patch: ShowPatch) {
        mixerParams["SHOW_PREV"]?.let { syncParam(it, patch.prevTrigger) }
        mixerParams["SHOW_NEXT"]?.let { syncParam(it, patch.nextTrigger) }
        mixerParams["SHOW_RANDOM"]?.let { syncParam(it, patch.randomTrigger) }
        mixerParams["SHOW_GENERATE"]?.let { syncParam(it, patch.generateTrigger) }
        mixerParams["SHOW_FB_AMOUNT"]?.let { syncParam(it, patch.feedbackAmount) } // New: sync feedback amount
        mixerParams["SHOW_BG_HUE"]?.let { syncParam(it, patch.backgroundHue) }
        mixerParams["SHOW_BG_BRIGHTNESS"]?.let { syncParam(it, patch.backgroundBrightness) }
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
        // Reset all GL resource handles to handle context loss
        program = 0
        trailProgram = 0
        feedbackProgram = 0
        mixerProgram = 0
        blitProgram = 0
        vao = 0
        vbo = 0
        trailVao = 0
        trailVbo = -1
        currentFrameFBO = 0
        currentFrameTexture = 0
        finalFBIndex = 0
        for (i in 0..3) slotFBIndex[i] = 0
        
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
        // Master output textures (one per source + mixer stages)
        for (i in 0..6) { masterTextures[i] = cT(TARGET_WIDTH, TARGET_HEIGHT); masterFramebuffers[i] = cF(masterTextures[i]) }
        
        // Per-slot feedback textures (2 per slot for ping-pong)
        for (slot in 0..3) {
            for (i in 0..1) {
                slotFBTextures[slot][i] = cT(TARGET_WIDTH, TARGET_HEIGHT)
                slotFBFramebuffers[slot][i] = cF(slotFBTextures[slot][i])
            }
        }
        
        // Final output feedback textures
        for (i in 0..1) { 
            finalFBTextures[i] = cT(TARGET_WIDTH, TARGET_HEIGHT)
            finalFBFramebuffers[i] = cF(finalFBTextures[i])
        }
        
        // Temp rendering target
        currentFrameTexture = cT(TARGET_WIDTH, TARGET_HEIGHT)
        currentFrameFBO = cF(currentFrameTexture)
        
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    private fun hsvToRgb(h: Float, s: Float, v: Float): Int {
        val hue = h * 360f
        return Color.HSVToColor(floatArrayOf(hue, s, v))
    }
    
    override fun onDrawFrame(gl: GL10?) {
        // This is the main rendering loop, executed for every frame.

        // 1. Check if GL resources are ready. If not, abort the frame.
        if (program == 0 || screenWidth == 0) return

        // 2. Clear all feedback buffers if a new patch/source was selected.
        // This prevents visual artifacts from a previous state from bleeding into the new one.
        if (clearFeedbackNextFrame) {
            // Clear per-slot feedback buffers
            for (slot in 0..3) {
                for (i in 0..1) {
                    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, slotFBFramebuffers[slot][i])
                    GLES30.glClearColor(0f,0f,0f,0f)
                    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                }
            }
            // Clear final feedback buffers
            for (i in 0..1) {
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, finalFBFramebuffers[i])
                GLES30.glClearColor(0f,0f,0f,0f)
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            }
            clearFeedbackNextFrame = false
        }

        // 3. Update all modulatable parameters for the current frame.
        // This evaluates LFOs, beat clocks, and other modulators.
        visualSource?.update() // For single-mandala mode
        for (i in 0..3) slotSources[i].update() // For the 4 mixer slots
        mixerParams.values.forEach { it.evaluate() }

        // Set global viewport
        GLES30.glViewport(0, 0, TARGET_WIDTH, TARGET_HEIGHT)

        // Handle transition animation
        if (transitionState != TransitionState.IDLE) {
            val elapsedTime = System.currentTimeMillis() - transitionStartTime
            
            val fadeOutMillis = (transitionDurationMillis * transitionFadeOutPercent).toLong()
            val fadeInMillis = (transitionDurationMillis * transitionFadeInPercent).toLong()
            
            var scale = 0.0f 
            var sourceToRender: MandalaVisualSource? = null

            // Imploding phase
            if (elapsedTime <= fadeOutMillis) {
                val progress = if (fadeOutMillis > 0) elapsedTime.toFloat() / fadeOutMillis else 1.0f
                sourceToRender = fromSource
                scale = when (currentTransitionType) {
                    TransitionType.IMPLODE_EXPLODE, TransitionType.IMPLODE_IMPLODE -> (1.0f - progress).coerceAtLeast(0f)
                    TransitionType.EXPLODE_EXPLODE -> (1.0f + progress * 9.0f).coerceAtMost(10f)
                    TransitionType.NONE -> 1.0f
                }
            }
            
            // Exploding phase (starts at transitionDurationMillis - fadeInMillis)
            if (elapsedTime >= (transitionDurationMillis - fadeInMillis)) {
                val fadeInElapsedTime = elapsedTime - (transitionDurationMillis - fadeInMillis)
                val progress = if (fadeInMillis > 0) fadeInElapsedTime.toFloat() / fadeInMillis else 1.0f
                sourceToRender = toSource
                scale = when (currentTransitionType) {
                    TransitionType.IMPLODE_EXPLODE, TransitionType.EXPLODE_EXPLODE -> progress.coerceAtMost(1f)
                    TransitionType.IMPLODE_IMPLODE -> (10.0f - progress * 9.0f).coerceAtLeast(1f)
                    TransitionType.NONE -> 1.0f
                }
            }

            // Transition state management for when animations finish
            if (elapsedTime >= transitionDurationMillis) {
                transitionState = TransitionState.IDLE
            }
            
            // If both fade-out and fade-in are 0%, just use the 'toSource' instantly.
            // Otherwise, render the active source with its calculated scale.
            if (sourceToRender != null) {
                compositeWithFeedback(
                    render = { GLES30.glUseProgram(program); renderSource(sourceToRender, scale) },
                    fbTextures = finalFBTextures,
                    fbFramebuffers = finalFBFramebuffers,
                    fbIndexRef = { finalFBIndex },
                    fbIndexSet = { finalFBIndex = it },
                    targetFboIdx = 6, // Final output always goes to masterFramebuffers[6]
                    explicitFeedbackParams = NONE_FEEDBACK_PARAMS // No feedback during transitions
                )
            }
        } else {
            // --- NORMAL RENDERING LOGIC ---
            val p = mixerPatch
            
            // 4. Determine rendering path: Mixer mode or Single Mandala mode.
            if (p != null) {
                // --- MIXER MODE ---

                // 4a. Render each of the 4 slots into its master texture (`masterTextures[0-3]`).
                // Each slot has its own independent feedback loop, but for now we apply NONE.
                for (i in 0..3) {
                    if (p.slots[i].enabled && p.slots[i].isPopulated()) {
                        val source = slotSources[i]

                        compositeWithFeedback(
                            render = { GLES30.glUseProgram(program); renderSource(source) },
                            fbTextures = slotFBTextures[i],
                            fbFramebuffers = slotFBFramebuffers[i],
                            fbIndexRef = { slotFBIndex[i] },
                            fbIndexSet = { slotFBIndex[i] = it },
                            targetFboIdx = i, // The output is written to masterFramebuffers[i]
                            explicitFeedbackParams = NONE_FEEDBACK_PARAMS // No per-slot feedback for now
                        )
                    } else {
                        // If a slot is disabled or empty, just clear its texture to black.
                        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, masterFramebuffers[i])
                        GLES30.glClearColor(0f, 0f, 0f, 0f)
                        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                    }
                }
                // 4b. Composite the slots into mixer groups A and B.
                // Mix slots 1 & 2 -> Mixer A (output to masterTextures[4])
                renderMixerGroup(4, masterTextures[0], masterTextures[1], "A")
                // Mix slots 3 & 4 -> Mixer B (output to masterTextures[5])
                renderMixerGroup(5, masterTextures[2], masterTextures[3], "B")

                // 4c. Composite the final output. Use global feedback amount.
                compositeFinalMixer(masterTextures[4], masterTextures[5])
            } else {
                // --- SINGLE MANDALA MODE ---
                // This mode is simpler, rendering one mandala with global feedback amount.
                val currentSource = visualSource
                if (currentSource != null) {
                    compositeWithFeedback(
                        render = { GLES30.glUseProgram(program); renderSource(currentSource) },
                        fbTextures = finalFBTextures,
                        fbFramebuffers = finalFBFramebuffers,
                        fbIndexRef = { finalFBIndex },
                        fbIndexSet = { finalFBIndex = it },
                        targetFboIdx = 6 // Final output always goes to masterFramebuffers[6]
                        // explicitFeedbackParams is null, so it will use the interpolated SHOW_FB_AMOUNT
                    )
                }
            }
        }

        // 5. Ensure all rendering commands are submitted before the UI thread tries to read them.
        GLES30.glFlush()

        // 6. Notify HDMI presentation thread if active
        SharedContextManager.notifyFrameReady(masterTextures[6])

        // 7. Blit the selected monitor source texture to the screen.
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, screenWidth, screenHeight)
        val bgHue = mixerParams["SHOW_BG_HUE"]?.value ?: 0f
        val bgBrightness = mixerParams["SHOW_BG_BRIGHTNESS"]?.value ?: 0f
        val bgColor = hsvToRgb(bgHue, 1f, bgBrightness)
        GLES30.glClearColor(Color.red(bgColor) / 255f, Color.green(bgColor) / 255f, Color.blue(bgColor) / 255f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        
        val dTex = getTextureForSource(monitorSource)
        if (dTex != 0) {
            // Use additive blending to make trails appear as light on the background
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
            drawTextureToCurrentBuffer(dTex)
            // Restore standard alpha blending for UI, etc.
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        }
    }

    private fun isSourceStatic(s: MandalaVisualSource?): Boolean {
        if (s == null) return true
        val p = s.parameters
        val l1Value = p["L1"]?.value ?: 0f
        val l2Value = p["L2"]?.value ?: 0f
        val l3Value = p["L3"]?.value ?: 0f
        val l4Value = p["L4"]?.value ?: 0f
        val rotationValue = p["Rotation"]?.value ?: 0f
        
        val res = l1Value == lastL1 && l2Value == lastL2 && l3Value == lastL3 && l4Value == lastL4 && rotationValue == lastRotation
        lastL1 = l1Value
        lastL2 = l2Value
        lastL3 = l3Value
        lastL4 = l4Value
        lastRotation = rotationValue
        return res
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
        compositeWithFeedback(
            render = { 
                GLES30.glUseProgram(mixerProgram); 
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0); 
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tA); 
                GLES30.glUniform1i(uMixTex1Loc, 0); 
                GLES30.glActiveTexture(GLES30.GL_TEXTURE1); 
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tB); 
                GLES30.glUniform1i(uMixTex2Loc, 1); 
                GLES30.glUniform1i(uMixModeLoc, ((mixerParams["MF_MODE"]?.value ?: 0f) * (MixerMode.entries.size - 1)).roundToInt()); 
                GLES30.glUniform1f(uMixBalLoc, mixerParams["MF_BAL"]?.value ?: 0.5f); 
                GLES30.glUniform1f(uMixAlphaLoc, mixerParams["MF_GAIN"]?.value ?: 1f); 
                GLES30.glDisable(GLES30.GL_BLEND); 
                GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4); 
                GLES30.glEnable(GLES30.GL_BLEND) 
            },
            fbTextures = finalFBTextures,
            fbFramebuffers = finalFBFramebuffers,
            fbIndexRef = { finalFBIndex },
            fbIndexSet = { finalFBIndex = it },
            targetFboIdx = 6 // Will use interpolated SHOW_FB_AMOUNT
        )
    }

    /**
     * Composite with persistent feedback.
     * If explicitFeedbackParams is provided, it uses those. Otherwise, for targetFboIdx == 6 (final output),
     * it interpolates feedback parameters based on `SHOW_FB_AMOUNT` from mixerParams.
     * For other targetFboIdx (slots), it defaults to NONE.
     */
    private fun compositeWithFeedback(
        render: () -> Unit,
        fbTextures: IntArray,
        fbFramebuffers: IntArray,
        fbIndexRef: () -> Int,
        fbIndexSet: (Int) -> Unit,
        targetFboIdx: Int,
        explicitFeedbackParams: FeedbackParams? = null
    ) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, currentFrameFBO)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        render()

        val params = explicitFeedbackParams ?: run {
            if (targetFboIdx != 6) { // For individual slots, apply no feedback unless specified
                NONE_FEEDBACK_PARAMS
            } else { // For final output, use interpolated feedback amount
                val feedbackAmount = mixerParams["SHOW_FB_AMOUNT"]?.value ?: 0.001f
                // Normalize 0.001-1.0 range to 0-1 for interpolation
                val t = (feedbackAmount - 0.001f) / (1.0f - 0.001f)
                
                FeedbackParams(
                    decay = lerp(LIGHT_FEEDBACK_PARAMS.decay, HEAVY_FEEDBACK_PARAMS.decay, t),
                    gain = lerp(LIGHT_FEEDBACK_PARAMS.gain, HEAVY_FEEDBACK_PARAMS.gain, t),
                    zoom = lerp(LIGHT_FEEDBACK_PARAMS.zoom, HEAVY_FEEDBACK_PARAMS.zoom, t),
                    rotate = lerp(LIGHT_FEEDBACK_PARAMS.rotate, HEAVY_FEEDBACK_PARAMS.rotate, t),
                    shift = lerp(LIGHT_FEEDBACK_PARAMS.shift, HEAVY_FEEDBACK_PARAMS.shift, t),
                    blur = lerp(LIGHT_FEEDBACK_PARAMS.blur, HEAVY_FEEDBACK_PARAMS.blur, t)
                ).let { rawParams ->
                    // Apply shader-specific mappings for zoom and rotate, others are direct
                    val mappedZoom = ((rawParams.zoom - 0.5f) * 0.1f)
                    val mappedRotate = ((rawParams.rotate - 0.5f) * 10f * (PI.toFloat() / 180f))
                    rawParams.copy(zoom = mappedZoom, rotate = mappedRotate)
                }
            }
        }

        // Only apply feedback if there's significant gain
        if (params.gain > 1.001f) {
            val fbIdx = fbIndexRef()
            val nextIdx = (fbIdx + 1) % 2
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbFramebuffers[nextIdx])
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            GLES30.glUseProgram(feedbackProgram)
            GLES30.glBindVertexArray(trailVao)
            
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, currentFrameTexture)
            GLES30.glUniform1i(uFBTextureLiveLoc, 0)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fbTextures[fbIdx])
            GLES30.glUniform1i(uFBTextureHistoryLoc, 1)
            
            GLES30.glUniform1f(uFBDecayLoc, params.decay)
            GLES30.glUniform1f(uFBGainLoc, params.gain)
            GLES30.glUniform1f(uFBZoomLoc, params.zoom)
            GLES30.glUniform1f(uFBRotateLoc, params.rotate)
            GLES30.glUniform1f(uFBShiftLoc, params.shift)
            GLES30.glUniform1f(uFBBlurLoc, params.blur)
            
            GLES30.glDisable(GLES30.GL_BLEND)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            GLES30.glEnable(GLES30.GL_BLEND)
            fbIndexSet(nextIdx)
        }
        
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, masterFramebuffers[targetFboIdx])
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(trailProgram)
        GLES30.glBindVertexArray(trailVao)
        GLES30.glUniform1f(uTrailAlphaLocation, 1.0f)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (params.gain > 1.001f) fbTextures[fbIndexRef()] else currentFrameTexture)
        GLES30.glUniform1i(uTrailTextureLocation, 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun renderSource(s: MandalaVisualSource, scale: Float = 1.0f) {
        val p = s.parameters
        val hS = ((p["Hue Sweep"]?.value ?: (1f/9f)) * 9f).roundToInt().toFloat()
        
        GLES30.glUniform1f(uFillModeLocation, 0.0f)
        GLES30.glUniform1f(uHueOffsetLocation, p["Hue Offset"]?.value ?: 0f)
        GLES30.glUniform1f(uHueSweepLocation, hS)
        GLES30.glUniform1f(uAlphaLocation, s.globalAlpha.value)
        GLES30.glUniform1f(uGlobalScaleLocation, (p["Scale"]?.value ?: 0.125f) * s.globalScale.value * 8f * scale)
        GLES30.glUniform1f(uGlobalRotationLocation, (p["Rotation"]?.value ?: 0f) * 2f * PI.toFloat())
        GLES30.glUniform1f(uAspectRatioLocation, aspectRatio)
        GLES30.glUniform1f(uDepthLocation, p["Depth"]?.value ?: 0.35f)
        GLES30.glUniform1f(uMinRLocation, s.minR)
        GLES30.glUniform1f(uMaxRLocation, s.maxR)
        GLES30.glUniform1f(uThicknessLocation, p["Thickness"]?.value ?: 0.1f)
        GLES30.glUniform1f(uLayerOffsetLocation, 0f)
        GLES30.glUniform1f(uLayerAlphaLocation, 1f)
        GLES30.glUniform1f(uLayerScaleLocation, 1f)
        GLES30.glUniform1f(uL1Loc, p["L1"]?.value ?: 0f)
        GLES30.glUniform1f(uL2Loc, p["L2"]?.value ?: 0f)
        GLES30.glUniform1f(uL3Loc, p["L3"]?.value ?: 0f)
        GLES30.glUniform1f(uL4Loc, p["L4"]?.value ?: 0f)
        GLES30.glUniform1f(uALoc, s.recipe.a.toFloat())
        GLES30.glUniform1f(uBLoc, s.recipe.b.toFloat())
        GLES30.glUniform1f(uCLoc, s.recipe.c.toFloat())
        GLES30.glUniform1f(uDLoc, s.recipe.d.toFloat())
        
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, (resolution + 1) * 2)
    }
}
