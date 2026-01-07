package llm.slop.spirals.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun KnobView(
    value: Float,
    onValueChange: (Float) -> Unit,
    onInteractionFinished: () -> Unit,
    modifier: Modifier = Modifier,
    isBipolar: Boolean = false,
    focused: Boolean = false
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .knobInput(
                value = value,
                config = KnobConfig(isBipolar = isBipolar),
                onValueChange = onValueChange,
                onInteractionFinished = onInteractionFinished
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasCenter = center // center property of DrawScope is an Offset
            val radius = size.minDimension / 2f - 4.dp.toPx()
            
            // Background track
            drawCircle(
                color = Color.DarkGray.copy(alpha = 0.5f),
                radius = radius,
                center = canvasCenter,
                style = Stroke(width = 2.dp.toPx())
            )

            val arcTopLeft = Offset(canvasCenter.x - radius, canvasCenter.y - radius)
            val arcSize = Size(radius * 2f, radius * 2f)

            if (isBipolar) {
                // Bipolar: Start/End at 6 o'clock (90°). 0 is at 12 o'clock (270°)
                // Sweep from 12 o'clock
                val sweep = value * 180f
                drawArc(
                    color = if (focused) Color.Cyan else Color.LightGray,
                    startAngle = 270f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            } else {
                // Unipolar: 7 o'clock (120°) to 5 o'clock (60°)
                // Total sweep is 300 degrees
                drawArc(
                    color = if (focused) Color.Cyan else Color.LightGray,
                    startAngle = 120f,
                    sweepAngle = 300f * value,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}
