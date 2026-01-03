package llm.slop.spirals.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun OscilloscopeView(
    samples: FloatArray,
    isUnipolar: Boolean = true, // If true, range is 0 to 1. If false, -1 to 1.
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp) // Reduced height to save space
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

        val path = Path()
        val stepX = width / (samples.size - 1)

        for (i in samples.indices) {
            val x = i * stepX
            val y = if (isUnipolar) {
                // Map 0..1 to height..0 (inverted for screen coords)
                height - (samples[i].coerceIn(0f, 1f) * height)
            } else {
                // Map -1..1 to height..0
                val centerY = height / 2f
                centerY - (samples[i].coerceIn(-1f, 1f) * centerY)
            }
            
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = Color.Green,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}
