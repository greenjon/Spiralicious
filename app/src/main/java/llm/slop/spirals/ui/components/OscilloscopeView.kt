package llm.slop.spirals.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun OscilloscopeView(
    samples: FloatArray,
    isUnipolar: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Pre-allocate the Path object to avoid GC pressure in the draw loop
    val sharedPath = remember { Path() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        val width = size.width
        val height = size.height
        
        // Draw baseline
        val baselineY = if (isUnipolar) height - 2f else height / 2f
        drawLine(
            color = Color.DarkGray,
            start = Offset(0f, baselineY),
            end = Offset(width, baselineY),
            strokeWidth = 1f
        )

        if (samples.isEmpty()) return@Canvas

        // Reuse the same Path instance
        sharedPath.reset()
        val stepX = width / (samples.size - 1)

        for (i in samples.indices) {
            val x = i * stepX
            val sample = samples[i]
            
            // Perceptual scaling for parameters that exceed 1.0 (like Scale/Gain)
            // We'll normalize them to fit in the window while keeping them visible
            val displayValue = if (sample > 1.0f) {
                // Map 1.0 -> 8.0 down to a visible upper range
                0.5f + (sample / 16.0f) 
            } else {
                sample
            }

            val y = if (isUnipolar) {
                height - (displayValue.coerceIn(0f, 1f) * height)
            } else {
                val centerY = height / 2f
                centerY - (displayValue.coerceIn(-1f, 1f) * centerY)
            }
            
            if (i == 0) {
                sharedPath.moveTo(x, y)
            } else {
                sharedPath.lineTo(x, y)
            }
        }

        drawPath(
            path = sharedPath,
            color = Color.Green,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}
