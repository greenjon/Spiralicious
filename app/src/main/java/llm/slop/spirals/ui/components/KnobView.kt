package llm.slop.spirals.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import llm.slop.spirals.ui.theme.AppAccent
import llm.slop.spirals.ui.theme.AppText
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun KnobView(
    baseValue: Float,
    modulatedValue: Float = baseValue,
    onValueChange: (Float) -> Unit,
    onInteractionFinished: () -> Unit,
    modifier: Modifier = Modifier,
    isBipolar: Boolean = false,
    focused: Boolean = false,
    knobSize: Dp = 32.dp,
    showValue: Boolean = false,
    tick: Long = 0L, // Added tick to force recomposition/redraw
    displayTransform: (Float) -> String = { (it * 100).roundToInt().toString() }
) {
    Box(
        modifier = modifier
            .size(knobSize)
            .knobInput(
                value = baseValue,
                config = KnobConfig(isBipolar = isBipolar),
                onValueChange = onValueChange,
                onInteractionFinished = onInteractionFinished
            ),
        contentAlignment = Alignment.Center
    ) {
        // We use the tick here to ensure the Canvas redraws whenever the frame changes
        Canvas(modifier = Modifier.fillMaxSize()) {
            val _t = tick // Explicit read to satisfy Compose dependency tracking
            val canvasCenter = center 
            val radius = this.size.minDimension / 2f - 4.dp.toPx()
            
            // Background track - Thinner circle
            drawCircle(
                color = AppText.copy(alpha = 0.2f),
                radius = radius,
                center = canvasCenter,
                style = Stroke(width = 1.dp.toPx())
            )

            val arcTopLeft = Offset(canvasCenter.x - radius, canvasCenter.y - radius)
            val arcSize = Size(radius * 2f, radius * 2f)

            if (isBipolar) {
                // Sweep from center (12 o'clock / 270°)
                // modulatedValue is expected to be -1.0 to 1.0
                val sweep = modulatedValue * 150f // 150 deg max each side
                
                // Fix: Always use positive sweep angle, adjust start angle for negative values
                val actualStartAngle = if (modulatedValue < 0) 270f + sweep else 270f
                val actualSweep = if (modulatedValue < 0) -sweep else sweep
                
                drawArc(
                    color = if (focused) AppAccent else AppText,
                    startAngle = actualStartAngle,
                    sweepAngle = actualSweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            } else {
                // Sweep from 7 o'clock (120°)
                drawArc(
                    color = if (focused) AppAccent else AppText,
                    startAngle = 120f,
                    sweepAngle = 300f * modulatedValue,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        
        if (showValue) {
            Text(
                text = displayTransform(baseValue),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = (knobSize.value * 0.25f).sp),
                color = if (focused) AppAccent else AppText
            )
        }
    }
}
