package llm.slop.spirals

import org.lwjgl.opengl.GL30.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object ShaderHelper {
    private const val TAG = "ShaderHelper"

    fun createProgram(vertexShaderSource: String, fragmentShaderSource: String): Int {
        val vertexShaderId = compileShader(GL_VERTEX_SHADER, vertexShaderSource)
        val fragmentShaderId = compileShader(GL_FRAGMENT_SHADER, fragmentShaderSource)

        if (vertexShaderId == 0 || fragmentShaderId == 0) {
            return 0
        }

        return linkProgram(vertexShaderId, fragmentShaderId)
    }

    private fun compileShader(type: Int, shaderCode: String): Int {
        val shaderObjectId = glCreateShader(type)
        if (shaderObjectId == 0) {
            println("$TAG: Could not create new shader.")
            return 0
        }

        glShaderSource(shaderObjectId, shaderCode)
        glCompileShader(shaderObjectId)

        val compileStatus = IntArray(1)
        glGetShaderiv(shaderObjectId, GL_COMPILE_STATUS, compileStatus)

        if (compileStatus[0] == 0) {
            println("$TAG: Results of compiling shader:\n$shaderCode\n:${glGetShaderInfoLog(shaderObjectId)}")
            glDeleteShader(shaderObjectId)
            return 0
        }

        return shaderObjectId
    }

    private fun linkProgram(vertexShaderId: Int, fragmentShaderId: Int): Int {
        val programObjectId = glCreateProgram()
        if (programObjectId == 0) {
            println("$TAG: Could not create new program")
            return 0
        }

        glAttachShader(programObjectId, vertexShaderId)
        glAttachShader(programObjectId, fragmentShaderId)
        glLinkProgram(programObjectId)

        val linkStatus = IntArray(1)
        glGetProgramiv(programObjectId, GL_LINK_STATUS, linkStatus)

        if (linkStatus[0] == 0) {
            println("$TAG: Results of linking program:\n${glGetProgramInfoLog(programObjectId)}")
            glDeleteProgram(programObjectId)
            return 0
        }

        return programObjectId
    }
}
