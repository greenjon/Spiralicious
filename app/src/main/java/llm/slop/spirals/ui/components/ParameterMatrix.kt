package llm.slop.spirals.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import llm.slop.spirals.cv.ModulatableParameter

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
                        
                        var sliderValue by remember(param) { mutableFloatStateOf(param.baseValue) }
                        val interactionSource = remember { MutableInteractionSource() }
                        val isDragged by interactionSource.collectIsDraggedAsState()

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
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$id - ${"%.2f".format(sliderValue)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isFocused) Color.Cyan else Color.Gray
                                )
                                Slider(
                                    value = sliderValue,
                                    onValueChange = { 
                                        // Only update value if we are actively dragging
                                        if (isDragged) {
                                            sliderValue = it
                                            param.baseValue = it
                                        }
                                        // Always request focus on any interaction
                                        onFocusRequest(id)
                                    },
                                    interactionSource = interactionSource,
                                    onValueChangeFinished = onInteractionFinished,
                                    modifier = Modifier.height(24.dp)
                                )
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
