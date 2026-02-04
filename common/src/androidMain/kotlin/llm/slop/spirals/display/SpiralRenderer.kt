package llm.slop.spirals.display

import android.content.Context
import android.opengl.GLES30.*
import android.opengl.GLSurfaceView
import llm.slop.spirals.models.mandala.MandalaRatio
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class SpiralRenderer(private val context: Context) : ISpiralRenderer, GLSurfaceView.Renderer {

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
    
    private var fboA: Int = 0
    private var texA: Int = 0
    private var fboB: Int = 0
    private var texB: Int = 0
    
    private var fullScreenVao: Int = 0
    private var mandalaVao: Int = 0
    private var mandalaVbo: Int = 0
    private var mandalaVertexCount: Int = 0
    
    private var currentRatio: MandalaRatio? = null
    private val pendingRatio = AtomicReference<MandalaRatio?>(null)

    var width: Int = 0
    var height: Int = 0

    // Bridge methods for GLSurfaceView.Renderer
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        onSurfaceCreated()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        onSurfaceChanged(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        onDrawFrame()
    }

    override fun onSurfaceCreated() {
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        
        mandalaProgram = ShaderHelper.createProgram(
            readShaderResource("mandala_vertex.glsl"),
            readShaderResource("mandala_fragment.glsl")
        )
        feedbackProgram = ShaderHelper.createProgram(
            readShaderResource("quad_vertex.glsl"),
            readShaderResource("feedback_fragment.glsl")
        )
        screenProgram = ShaderHelper.createProgram(
            readShaderResource("quad_vertex.glsl"),
            readShaderResource("screen_fragment.glsl")
        )
        
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
        this.width = width
        this.height = height
        cleanupFbos()
        val (fbo1, tex1) = createFbo(width, height)
        fboA = fbo1; texA = tex1
        val (fbo2, tex2) = createFbo(width, height)
        fboB = fbo2; texB = tex2
    }
    
    override fun onDrawFrame() {
        pendingRatio.getAndSet(null)?.let {
            applyRatio(it)
        }
        val ratio = currentRatio ?: return
        
        // STAGE 1: Draw the fresh Mandala into FBO A
        glBindFramebuffer(GL_FRAMEBUFFER, fboA)
        glViewport(0, 0, this.width, this.height)
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
        glUniform1f(glGetUniformLocation(mandalaProgram, "uAspectRatio"), this.width.toFloat() / this.height.toFloat())
        glUniform1f(glGetUniformLocation(mandalaProgram, "uGlobalScale"), ratio.baseScale)
        glUniform1f(glGetUniformLocation(mandalaProgram, "uThickness"), 0.1f)
        
        glBindVertexArray(mandalaVao)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, mandalaVertexCount)
        
        // STAGE 2: Run Feedback
        glBindFramebuffer(GL_FRAMEBUFFER, fboB)
        glViewport(0, 0, this.width, this.height)
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        glClear(GL_COLOR_BUFFER_BIT)
        
        glUseProgram(feedbackProgram)
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, texA)
        glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D, texB)
        glUniform1i(glGetUniformLocation(feedbackProgram, "uTextureLive"), 0)
        glUniform1i(glGetUniformLocation(feedbackProgram, "uTextureHistory"), 1)
        
        glUniform1f(glGetUniformLocation(feedbackProgram, "uZoom"), ratio.feedbackScale)
        glUniform1f(glGetUniformLocation(feedbackProgram, "uRotate"), ratio.feedbackRotation)
        glUniform1f(glGetUniformLocation(feedbackProgram, "uGain"), ratio.feedbackAmount)
        
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
        
        glBindVertexArray(0)
        glUseProgram(0)
    }
    
    override fun setRatio(ratio: MandalaRatio) {
        pendingRatio.set(ratio)
    }
    
    override fun clearFeedback() {
        // TODO: This needs to be thread-safe with the rendering thread
        glBindFramebuffer(GL_FRAMEBUFFER, fboA); glClear(GL_COLOR_BUFFER_BIT)
        glBindFramebuffer(GL_FRAMEBUFFER, fboB); glClear(GL_COLOR_BUFFER_BIT)
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }
    
    private fun applyRatio(ratio: MandalaRatio) {
        this.currentRatio = ratio
        val vertices = generateMandalaVertices()
        this.mandalaVertexCount = vertices.size / 2
        
        val buffer = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buffer.put(vertices).position(0)
        
        glBindBuffer(GL_ARRAY_BUFFER, mandalaVbo)
        glBufferSubData(GL_ARRAY_BUFFER, 0, vertices.size * 4, buffer)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
    }
    
    private fun generateMandalaVertices(): FloatArray {
        val numSegments = 2048
        val vertices = mutableListOf<Float>()
        for (i in 0..numSegments) {
            val phase = i.toFloat() / numSegments.toFloat()
            vertices.add(phase); vertices.add(-1.0f)
            vertices.add(phase); vertices.add(1.0f)
        }
        return vertices.toFloatArray()
    }
    
    private fun setupFullScreenQuad() {
        val vao = IntArray(1)
        glGenVertexArrays(1, vao, 0)
        fullScreenVao = vao[0]
        
        val vbo = IntArray(1)
        glGenBuffers(1, vbo, 0)
        
        glBindVertexArray(fullScreenVao)
        glBindBuffer(GL_ARRAY_BUFFER, vbo[0])
        
        val vertices = floatArrayOf(
            -1f, -1f, 1f, -1f, -1f, 1f,
            1f, -1f, 1f, 1f, -1f, 1f
        )
        val buffer = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buffer.put(vertices).position(0)
        
        glBufferData(GL_ARRAY_BUFFER, vertices.size * 4, buffer, GL_STATIC_DRAW)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 8, 0)
        glEnableVertexAttribArray(0)
        glBindVertexArray(0)
    }
    
    private fun setupMandalaBuffers() {
        val vao = IntArray(1)
        glGenVertexArrays(1, vao, 0)
        mandalaVao = vao[0]
        
        val vbo = IntArray(1)
        glGenBuffers(1, vbo, 0)
        mandalaVbo = vbo[0]
        
        glBindVertexArray(mandalaVao)
        glBindBuffer(GL_ARRAY_BUFFER, mandalaVbo)
        
        val vertices = generateMandalaVertices()
        mandalaVertexCount = vertices.size / 2
        
        val buffer = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buffer.put(vertices).position(0)
        
        glBufferData(GL_ARRAY_BUFFER, (vertices.size * 4).toLong(), buffer, GL_DYNAMIC_DRAW)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 8, 0)
        glEnableVertexAttribArray(0)
        glBindVertexArray(0)
    }
    
    private fun createFbo(width: Int, height: Int): Pair<Int, Int> {
        val fbo = IntArray(1)
        glGenFramebuffers(1, fbo, 0)
        
        val texture = IntArray(1)
        glGenTextures(1, texture, 0)
        
        glBindFramebuffer(GL_FRAMEBUFFER, fbo[0])
        glBindTexture(GL_TEXTURE_2D, texture[0])
        
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, null)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture[0], 0)
        
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            // Handle error
        }
        
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        return Pair(fbo[0], texture[0])
    }
    
    private fun cleanupFbos() {
        if (fboA != 0) glDeleteFramebuffers(1, intArrayOf(fboA), 0)
        if (texA != 0) glDeleteTextures(1, intArrayOf(texA), 0)
        if (fboB != 0) glDeleteFramebuffers(1, intArrayOf(fboB), 0)
        if (texB != 0) glDeleteTextures(1, intArrayOf(texB), 0)
    }
}
