package llm.slop.spirals.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import llm.slop.spirals.display.ISpiralRenderer
import javax.swing.JLabel
import javax.swing.JPanel
import java.awt.BorderLayout
import java.awt.Color

@Composable
actual fun SpiralSurface(
    renderer: ISpiralRenderer,
    modifier: Modifier
) {
    SwingPanel(
        background = Color.BLACK,
        factory = {
            JPanel(BorderLayout()).apply {
                add(JLabel("OpenGL Surface Placeholder (Desktop)"), BorderLayout.CENTER)
                // TODO: Initialize AWTGLCanvas or similar with the renderer
            }
        },
        modifier = modifier
    )
}
