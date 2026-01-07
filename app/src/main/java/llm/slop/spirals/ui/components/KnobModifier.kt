package llm.slop.spirals.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * A custom modifier that implements rotary knob logic via vertical dragging.
 */
fun Modifier.knobInput(
    value: Float,
    config: KnobConfig,
    onValueChange: (Float) -> Unit,
    onInteractionFinished: () -> Unit = {}
): Modifier = this.then(
    Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            var lastY = down.position.y
            var lastTime = System.currentTimeMillis()
            var smoothedVelocity = 0f
            var currentValue = value

            while (true) {
                val event: PointerEvent = awaitPointerEvent()
                val type = event.type

                if (type == PointerEventType.Move) {
                    val currentY = event.changes.first().position.y
                    val currentTime = System.currentTimeMillis()
                    
                    val deltaY = currentY - lastY
                    val deltaTimeSec = (currentTime - lastTime) / 1000f
                    
                    val (nextValue, newVelocity) = RotaryKnobMath.computeNewValue(
                        deltaY = deltaY,
                        deltaTimeSec = deltaTimeSec,
                        currentValue = currentValue,
                        prevVelocity = smoothedVelocity,
                        config = config
                    )
                    
                    currentValue = nextValue
                    smoothedVelocity = newVelocity
                    onValueChange(nextValue)
                    
                    lastY = currentY
                    lastTime = currentTime
                    event.changes.first().consume()
                } else if (type == PointerEventType.Release || type == PointerEventType.Exit) {
                    onInteractionFinished()
                    break
                }
            }
        }
    }
)
