package llm.slop.spirals

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import llm.slop.spirals.models.mandala.MandalaRatio
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import java.awt.BorderLayout
import java.awt.Canvas
import javax.swing.JPanel

fun main() {
    System.setProperty("skiko.renderApi", "OPENGL")
    application {
        val renderer = remember { DesktopSpiralRenderer() }
        var isRunning by remember { mutableStateOf(true) }

        DisposableEffect(Unit) {
            onDispose {
                isRunning = false
                renderer.cleanup()
            }
        }

        LaunchedEffect(renderer) {
            // Use the first recipe from your curated library
            MandalaLibrary.MandalaRatios.firstOrNull()?.let {
                renderer.setRatio(it)
            }
        }

        Window(
            onCloseRequest = {
                isRunning = false
                exitApplication()
            },
            title = "Spirals - Linux Desktop"
        ) {
            CompositionLocalProvider(LocalSpiralRenderer provides renderer) {
                SwingPanel(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        JPanel(BorderLayout()).apply {
                            val canvas = Canvas()
                            add(canvas, BorderLayout.CENTER)

                            Thread {
                                setupAndRunGL(canvas, renderer) { isRunning }
                            }.apply {
                                name = "Spiral-Render-Thread"
                                start()
                            }
                        }
                    }
                )
            }
        }
    }
}

private fun setupAndRunGL(canvas: java.awt.Canvas, renderer: ISpiralRenderer, keepRunning: () -> Boolean) {
    if (!glfwInit()) throw IllegalStateException("Unable to initialize GLFW")
    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
    val windowHandle = glfwCreateWindow(1, 1, "GL_CONTEXT", 0, 0)
    if (windowHandle == 0L) throw RuntimeException("Failed to create GLFW window")
    glfwMakeContextCurrent(windowHandle)
    GL.createCapabilities()

    renderer.onSurfaceCreated()

    var lastWidth = 0
    var lastHeight = 0

    while (keepRunning()) {
        val w = canvas.width
        val h = canvas.height
        if (w > 0 && h > 0) {
            if (w != lastWidth || h != lastHeight) {
                renderer.onSurfaceChanged(w, h)
                lastWidth = w
                lastHeight = h
            }
            renderer.onDrawFrame()
            glfwSwapBuffers(windowHandle)
        }
        glfwPollEvents()
        Thread.sleep(16)
    }

    // renderer.cleanup() is called in DisposableEffect
    glfwDestroyWindow(windowHandle)
    glfwTerminate()
}
