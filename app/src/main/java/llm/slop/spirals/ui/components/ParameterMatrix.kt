package llm.slop.spirals.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        for (row in 0 until 2) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 2) {
                    val index = row * 2 + col
                    if (index < parameters.size) {
                        val id = labels[index]
                        val isFocused = id == focusedParameterId
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(2.dp)
                                .border(
                                    width = 1.dp,
                                    color = if (isFocused) Color.Cyan else Color.Transparent,
                                    shape = MaterialTheme.shapes.extraSmall
                                )
                                .padding(4.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = id,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isFocused) Color.Cyan else Color.Gray
                                )
                                Slider(
                                    value = parameters[index].baseValue,
                                    onValueChange = { 
                                        parameters[index].baseValue = it
                                        onFocusRequest(id)
                                    },
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
