package llm.slop.spirals

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.MemoryStack

fun main() = application {
    // This is your Control Panel (Compose)
    Window(onCloseRequest = ::exitApplication, title = "Spirals Controls") {
        val scope = rememberCoroutineScope()

        // Start the OpenGL Window on a separate thread when the app launches
        LaunchedEffect(Unit) {
            scope.launch(Dispatchers.IO) {
                runOpenGLWindow()
            }
        }

        Column(Modifier.fillMaxSize()) {
            Text("Controls for your Mandala go here.")
            Text("Check the other window for the visuals!")
        }
    }
}

// This function runs the raw LWJGL loop (Replacing your Android GLSurfaceView)
private fun runOpenGLWindow() {
    // 1. Initialize GLFW
    if (!glfwInit()) {
        throw IllegalStateException("Unable to initialize GLFW")
    }

    // 2. Configure the Window
    glfwDefaultWindowHints()
    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
    glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)

    // 3. Create the Window
    val windowHandle = glfwCreateWindow(800, 600, "Spirals Renderer", NULL, NULL)
    if (windowHandle == NULL) {
        throw RuntimeException("Failed to create the GLFW window")
    }

    // 4. Center it
    val vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor())
    if (vidMode != null) {
        glfwSetWindowPos(
            windowHandle,
            (vidMode.width() - 800) / 2,
            (vidMode.height() - 600) / 2
        )
    }

    // 5. Make the OpenGL context current
    glfwMakeContextCurrent(windowHandle)
    // Enable V-Sync
    glfwSwapInterval(1)
    glfwShowWindow(windowHandle)

    // CRITICAL: This allows LWJGL to talk to your GPU
    GL.createCapabilities()

    // --- YOUR RENDERER LOGIC GOES HERE ---
    val renderer = DesktopSpiralRenderer()
    renderer.onSurfaceCreated()
    renderer.onSurfaceChanged(800, 600)

    // 6. The Render Loop
    while (!glfwWindowShouldClose(windowHandle)) {
        // Handle Resize using MemoryStack (Safe and efficient)
        MemoryStack.stackPush().use { stack ->
            val pWidth = stack.mallocInt(1)
            val pHeight = stack.mallocInt(1)

            // Get size into the pointers
            glfwGetWindowSize(windowHandle, pWidth, pHeight)

            val w = pWidth.get(0)
            val h = pHeight.get(0)

            if (renderer.width != w || renderer.height != h) {
                renderer.onSurfaceChanged(w, h)
            }
        }

        // Draw
        renderer.onDrawFrame()

        // Swap buffers (Show the frame)
        glfwSwapBuffers(windowHandle)

        // Poll input events
        glfwPollEvents()
    }

    // Cleanup
    renderer.cleanup()
    glfwDestroyWindow(windowHandle)
    glfwTerminate()
}