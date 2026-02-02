package llm.slop.spirals

import llm.slop.spirals.models.mandala.MandalaRatio
import llm.slop.spirals.modulation.Lfo
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL30.*
import java.nio.FloatBuffer

class DesktopSpiralRenderer : ISpiralRenderer {

    // Shader Programs
    private var mandalaProgram: Int = 0
    private var feedbackProgram: Int = 0
    private var screenProgram: Int = 0

    // FBOs and Textures
    private var fboA: Int = 0
    private var texA: Int = 0
    private var fboB: Int = 0
    private var texB: Int = 0

    // Geometry
    private var fullScreenVao: Int = 0
    private var mandalaVao: Int = 0
    private var mandalaVbo: Int = 0
    private var mandalaVertexCount: Int = 0
    private var currentRatio: MandalaRatio? = null

    // Modulation Engine
    private var startTime: Long = 0L
    private val lfos = mutableMapOf<String, Lfo>()
    private val lfoValues = mutableMapOf<String, Float>()

    // Screen dimensions
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0

    // --- Core Rendering Lifecycle ---

    override fun onSurfaceCreated() {
        GL.createCapabilities()
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        // Initialize Modulation Engine
        startTime = System.currentTimeMillis()
        lfos["lfo1"] = Lfo(rate = 0.1f) // A slow sine wave
        lfos["lfo2"] = Lfo(rate = 0.13f, phase = 0.5f) // A slightly different LFO

        // Load Shaders
        mandalaProgram = createProgram("/mandala_vertex.glsl", "/mandala_fragment.glsl")
        feedbackProgram = createProgram("/quad_vertex.glsl", "/feedback_fragment.glsl")
        screenProgram = createProgram("/quad_vertex.glsl", "/screen_fragment.glsl")

        // Setup Geometry
        setupFullScreenQuad()
        setupMandalaBuffers()
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        if (width == 0 || height == 0) return
        glViewport(0, 0, width, height)
        screenWidth = width
        screenHeight = height

        cleanupFbos()
        val (fbo1, tex1) = createFbo(width, height)
        fboA = fbo1; texA = tex1
        val (fbo2, tex2) = createFbo(width, height)
        fboB = fbo2; texB = tex2
    }

    override fun onDrawFrame() {
        val ratio = currentRatio ?: return

        // 1. Update Modulation Sources
        val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0f
        lfos.forEach { (name, lfo) ->
            lfoValues[name] = lfo.getValue(elapsedSeconds)
        }

        // STAGE 1: Draw the fresh Mandala into FBO A
        glBindFramebuffer(GL_FRAMEBUFFER, fboA)
        glViewport(0, 0, screenWidth, screenHeight)
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        glClear(GL_COLOR_BUFFER_BIT)
        glUseProgram(mandalaProgram)
        glBindVertexArray(mandalaVao)
        glDrawArrays(GL_LINE_STRIP, 0, mandalaVertexCount)

        // STAGE 2: Run Feedback/Mixer stage
        glBindFramebuffer(GL_FRAMEBUFFER, fboB)
        glUseProgram(feedbackProgram)
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, texA) // newFrame
        glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D, texB) // lastFrame
        glUniform1i(glGetUniformLocation(feedbackProgram, "u_newFrame"), 0)
        glUniform1i(glGetUniformLocation(feedbackProgram, "u_lastFrame"), 1)

        val finalScale = ratio.feedbackScale.getValue(lfoValues)
        val finalRotation = ratio.feedbackRotation.getValue(lfoValues)
        val finalAmount = ratio.feedbackAmount.getValue(lfoValues)
        val matrix = createFeedbackMatrix(finalScale, finalRotation)
        glUniformMatrix3fv(glGetUniformLocation(feedbackProgram, "u_feedbackMatrix"), false, matrix)
        glUniform1f(glGetUniformLocation(feedbackProgram, "u_feedbackAmount"), finalAmount)

        glBindVertexArray(fullScreenVao)
        glDrawArrays(GL_TRIANGLES, 0, 6)

        // STAGE 3: Draw final result to the screen
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        glUseProgram(screenProgram)
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, texB)
        glUniform1i(glGetUniformLocation(screenProgram, "u_screenTexture"), 0)
        glBindVertexArray(fullScreenVao)
        glDrawArrays(GL_TRIANGLES, 0, 6)

        // "Pong" the buffers
        val tempFbo = fboA; fboA = fboB; fboB = tempFbo
        val tempTex = texA; texA = texB; texB = tempTex

        glBindVertexArray(0)
        glUseProgram(0)
    }

    // --- ISpiralRenderer Interface & Helpers ---

    override fun setRatio(ratio: MandalaRatio) {
        this.currentRatio = ratio
        val vertices = generateMandalaVertices(ratio)
        this.mandalaVertexCount = vertices.size / 2
        glBindBuffer(GL_ARRAY_BUFFER, mandalaVbo)
        glBufferSubData(GL_ARRAY_BUFFER, 0, vertices)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
    }

    override fun clearFeedback() {
        glBindFramebuffer(GL_FRAMEBUFFER, fboA); glClear(GL_COLOR_BUFFER_BIT)
        glBindFramebuffer(GL_FRAMEBUFFER, fboB); glClear(GL_COLOR_BUFFER_BIT)
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }
    
    // ... all other helper and setup functions remain the same ...
    fun cleanup() {
        cleanupFbos()
        glDeleteVertexArrays(fullScreenVao)
        glDeleteVertexArrays(mandalaVao)
        glDeleteBuffers(mandalaVbo)
        glDeleteProgram(mandalaProgram)
        glDeleteProgram(feedbackProgram)
        glDeleteProgram(screenProgram)
    }
    private fun createFeedbackMatrix(scale: Float, rotation: Float): FloatArray {
        val cosR = kotlin.math.cos(rotation); val sinR = kotlin.math.sin(rotation)
        return floatArrayOf(scale*cosR, scale*sinR, 0f, -scale*sinR, scale*cosR, 0f, -0.5f*scale*cosR+0.5f*scale*sinR+0.5f, -0.5f*scale*sinR-0.5f*scale*cosR+0.5f, 1f)
    }
    private fun generateMandalaVertices(ratio: MandalaRatio): FloatArray {
        val vertices = mutableListOf<Float>(); val iter = ratio.iter.coerceIn(1, 10000); val m = ratio.m.toFloat(); val n = ratio.n.toFloat(); val p = ratio.p; val r = ratio.r; val baseScale = ratio.baseScale
        for (i in 0..iter) {
            val t = (i.toFloat()/iter.toFloat())*2.0f*Math.PI.toFloat()*p; val x = (m-n)*kotlin.math.cos(t)+r*kotlin.math.cos(((m-n)/n)*t); val y = (m-n)*kotlin.math.sin(t)-r*kotlin.math.sin(((m-n)/n)*t)
            vertices.add(x*baseScale); vertices.add(y*baseScale)
        }
        return vertices.toFloatArray()
    }
    private fun setupMandalaBuffers() {
        mandalaVao=glGenVertexArrays(); mandalaVbo=glGenBuffers(); glBindVertexArray(mandalaVao); glBindBuffer(GL_ARRAY_BUFFER, mandalaVbo); glBufferData(GL_ARRAY_BUFFER, 10000L*2*4, GL_DYNAMIC_DRAW); glVertexAttribPointer(0, 2, GL_FLOAT, false, 2*4, 0); glEnableVertexAttribArray(0); glBindVertexArray(0)
    }
    private fun createFbo(width: Int, height: Int): Pair<Int, Int> {
        val fboId = glGenFramebuffers(); val textureId = glGenTextures()
        glBindFramebuffer(GL_FRAMEBUFFER, fboId); glBindTexture(GL_TEXTURE_2D, textureId)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, null as FloatBuffer?); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, textureId, 0)
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) { throw RuntimeException("Framebuffer is not complete!") }
        glBindFramebuffer(GL_FRAMEBUFFER, 0); glBindTexture(GL_TEXTURE_2D, 0)
        return Pair(fboId, textureId)
    }
    private fun cleanupFbos() { glDeleteFramebuffers(fboA); glDeleteFramebuffers(fboB); glDeleteTextures(texA); glDeleteTextures(texB) }
    private fun setupFullScreenQuad() {
        val quadVertices = floatArrayOf(-1.0f,1.0f,0.0f,1.0f,-1.0f,-1.0f,0.0f,0.0f,1.0f,-1.0f,1.0f,0.0f,-1.0f,1.0f,0.0f,1.0f,1.0f,-1.0f,1.0f,0.0f,1.0f,1.0f,1.0f,1.0f)
        fullScreenVao=glGenVertexArrays(); val vbo=glGenBuffers(); glBindVertexArray(fullScreenVao); glBindBuffer(GL_ARRAY_BUFFER, vbo); glBufferData(GL_ARRAY_BUFFER, quadVertices, GL_STATIC_DRAW); glVertexAttribPointer(0, 2, GL_FLOAT, false, 16, 0); glEnableVertexAttribArray(0); glVertexAttribPointer(1, 2, GL_FLOAT, false, 16, 8); glEnableVertexAttribArray(1); glBindVertexArray(0)
    }
    private fun readResource(path: String): String { return this::class.java.getResource(path)?.readText() ?: throw RuntimeException("Cannot load resource: $path") }
    private fun createProgram(vsPath: String, fsPath: String): Int {
        val vsSrc = readResource(vsPath); val fsSrc = readResource(fsPath)
        val vs = glCreateShader(GL_VERTEX_SHADER).also { glShaderSource(it, vsSrc); glCompileShader(it); if (glGetShaderi(it, GL_COMPILE_STATUS)==GL_FALSE) { println("VS Error: "+glGetShaderInfoLog(it)) } }
        val fs = glCreateShader(GL_FRAGMENT_SHADER).also { glShaderSource(it, fsSrc); glCompileShader(it); if (glGetShaderi(it, GL_COMPILE_STATUS)==GL_FALSE) { println("FS Error: "+glGetShaderInfoLog(it)) } }
        return glCreateProgram().also { glAttachShader(it, vs); glAttachShader(it, fs); glLinkProgram(it); if (glGetProgrami(it, GL_LINK_STATUS)==GL_FALSE) { println("Link Error: "+glGetProgramInfoLog(it)) }; glDeleteShader(vs); glDeleteShader(fs) }
    }
}
