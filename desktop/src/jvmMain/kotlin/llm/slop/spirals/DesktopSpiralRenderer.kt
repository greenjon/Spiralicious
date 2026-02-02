package llm.slop.spirals

import llm.slop.spirals.models.mandala.MandalaRatio
import llm.slop.spirals.modulation.Lfo
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL30.*
import java.nio.FloatBuffer

class DesktopSpiralRenderer : ISpiralRenderer {

    // Shader Programs & Uniform Locations
    private var mandalaProgram: Int = 0
    private var feedbackProgram: Int = 0
    private var screenProgram: Int = 0
    private var uALoc: Int = 0
    private var uBLoc: Int = 0
    private var uCLoc: Int = 0
    private var uDLoc: Int = 0
    private var uL1Loc: Int = 0
    private var uL2Loc: Int = 0
    private var uL3Loc: Int = 0
    private var uL4Loc: Int = 0

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

    private var screenWidth: Int = 0
    private var screenHeight: Int = 0

    override fun onSurfaceCreated() {
        GL.createCapabilities()
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        startTime = System.currentTimeMillis()
        lfos["lfo1"] = Lfo(rate = 0.1f)
        lfos["lfo2"] = Lfo(rate = 0.13f, phase = 0.5f)

        mandalaProgram = createProgram("/mandala_vertex.glsl", "/mandala_fragment.glsl")
        feedbackProgram = createProgram("/quad_vertex.glsl", "/feedback_fragment.glsl")
        screenProgram = createProgram("/quad_vertex.glsl", "/screen_fragment.glsl")

        // Get uniform locations for the harmonograph formula
        uALoc = glGetUniformLocation(mandalaProgram, "uA")
        uBLoc = glGetUniformLocation(mandalaProgram, "uB")
        uCLoc = glGetUniformLocation(mandalaProgram, "uC")
        uDLoc = glGetUniformLocation(mandalaProgram, "uD")
        uL1Loc = glGetUniformLocation(mandalaProgram, "uL1")
        uL2Loc = glGetUniformLocation(mandalaProgram, "uL2")
        uL3Loc = glGetUniformLocation(mandalaProgram, "uL3")
        uL4Loc = glGetUniformLocation(mandalaProgram, "uL4")

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

        val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0f
        lfos.forEach { (name, lfo) -> lfoValues[name] = lfo.getValue(elapsedSeconds) }

        // STAGE 1: Draw the fresh Mandala into FBO A
        glBindFramebuffer(GL_FRAMEBUFFER, fboA)
        glViewport(0, 0, screenWidth, screenHeight)
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        glClear(GL_COLOR_BUFFER_BIT)

        glUseProgram(mandalaProgram)
        glUniform1f(uALoc, ratio.arms[0].freq.toFloat())
        glUniform1f(uBLoc, ratio.arms[1].freq.toFloat())
        glUniform1f(uCLoc, ratio.arms[2].freq.toFloat())
        glUniform1f(uDLoc, ratio.arms[3].freq.toFloat())
        glUniform1f(uL1Loc, ratio.arms[0].length)
        glUniform1f(uL2Loc, ratio.arms[1].length)
        glUniform1f(uL3Loc, ratio.arms[2].length)
        glUniform1f(uL4Loc, ratio.arms[3].length)
        // Pass other uniforms like aspect ratio, scale, etc.
        glUniform1f(glGetUniformLocation(mandalaProgram, "uAspectRatio"), screenWidth.toFloat() / screenHeight.toFloat())
        glUniform1f(glGetUniformLocation(mandalaProgram, "uGlobalScale"), ratio.baseScale)
        glUniform1f(glGetUniformLocation(mandalaProgram, "uThickness"), 0.1f) // Example value

        glBindVertexArray(mandalaVao)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, mandalaVertexCount)

        // STAGE 2: Run Feedback
        glBindFramebuffer(GL_FRAMEBUFFER, fboB)
        glUseProgram(feedbackProgram)
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, texA)
        glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D, texB)
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

        // STAGE 3: Draw to screen
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        glUseProgram(screenProgram)
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, texB)
        glUniform1i(glGetUniformLocation(screenProgram, "u_screenTexture"), 0)
        glBindVertexArray(fullScreenVao)
        glDrawArrays(GL_TRIANGLES, 0, 6)

        val tempFbo = fboA; fboA = fboB; fboB = tempFbo
        val tempTex = texA; texA = texB; texB = tempTex
        glBindVertexArray(0); glUseProgram(0)
    }

    override fun setRatio(ratio: MandalaRatio) {
        this.currentRatio = ratio
        val vertices = generateMandalaVertices()
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

    private fun generateMandalaVertices(): FloatArray {
        val numSegments = 2048
        val vertices = mutableListOf<Float>()
        for (i in 0..numSegments) {
            val phase = i.toFloat() / numSegments.toFloat()
            vertices.add(phase); vertices.add(-1.0f) // "Left" side of the line
            vertices.add(phase); vertices.add(1.0f)  // "Right" side of the line
        }
        return vertices.toFloatArray()
    }
    
    // ... all other helper and setup functions are unchanged ...
    fun cleanup() { /* ... */ }
    private fun createFeedbackMatrix(scale: Float, rotation: Float): FloatArray { /* ... */ }
    private fun setupMandalaBuffers() { /* ... */ }
    private fun createFbo(width: Int, height: Int): Pair<Int, Int> { /* ... */ }
    private fun cleanupFbos() { /* ... */ }
    private fun setupFullScreenQuad() { /* ... */ }
    private fun readResource(path: String): String { /* ... */ }
    private fun createProgram(vsPath: String, fsPath: String): Int { /* ... */ }
}
