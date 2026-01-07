package llm.slop.spirals.ui.components

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

@Composable
fun ParameterMatrix(
    labels: List<String>,
    parameters: List<ModulatableParameter>,
    focusedParameterId: String?,
    onFocusRequest: (String) -> Unit,
    onInteractionFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        parameters.forEachIndexed { index, param ->
            val id = labels[index]
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
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = id,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isFocused) Color.Cyan else Color.Gray
                    )
                    KnobView(
                        currentValue = currentValue,
                        onValueChange = { newValue ->
                            currentValue = newValue
                            param.baseValue = newValue
                            onFocusRequest(id)
                        },
                        onInteractionFinished = onInteractionFinished,
                        modifier = Modifier.padding(vertical = 4.dp),
                        isBipolar = false,
                        focused = isFocused,
                        knobSize = 48.dp, // 50% larger (32 * 1.5)
                        showValue = true
                    )
                }
            }
        }
    }
}
