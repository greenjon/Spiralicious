package llm.slop.spirals.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import llm.slop.spirals.cv.ModulatableParameter
import kotlin.math.roundToInt

@Composable
fun ParameterMatrix(
    labels: List<String>,
    parameters: List<ModulatableParameter>,
    focusedParameterId: String?,
    onFocusRequest: (String) -> Unit,
    onInteractionFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        for (row in 0 until 2) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 2) {
                    val index = row * 2 + col
                    if (index < parameters.size) {
                        val id = labels[index]
                        val param = parameters[index]
                        val isFocused = id == focusedParameterId
                        
                        var currentValue by remember(param) { mutableFloatStateOf(param.baseValue) }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(2.dp)
                                .border(
                                    width = 1.dp,
                                    color = if (isFocused) Color.Cyan else Color.Transparent,
                                    shape = MaterialTheme.shapes.extraSmall
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onFocusRequest(id) }
                                )
                                .padding(4.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "$id - ${(currentValue * 100).roundToInt()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isFocused) Color.Cyan else Color.Gray
                                )
                                
                                // High-resolution Knob UI Component (Visual only for now)
                                Box(
                                    modifier = Modifier
                                        .padding(vertical = 4.dp)
                                        .height(32.dp)
                                        .fillMaxWidth()
                                        .background(Color.DarkGray.copy(alpha = 0.3f), MaterialTheme.shapes.extraSmall)
                                        .knobInput(
                                            value = currentValue,
                                            config = KnobConfig(isBipolar = false),
                                            onValueChange = { newValue ->
                                                currentValue = newValue
                                                param.baseValue = newValue
                                                onFocusRequest(id)
                                            },
                                            onInteractionFinished = onInteractionFinished
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Simple visual indicator: a bar that grows with value
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(currentValue)
                                            .background(if (isFocused) Color.Cyan.copy(alpha = 0.4f) else Color.Gray.copy(alpha = 0.2f))
                                            .align(Alignment.CenterStart)
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
