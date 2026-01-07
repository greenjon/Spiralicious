package llm.slop.spirals.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import llm.slop.spirals.cv.ModulatableParameter
import llm.slop.spirals.ui.theme.AppAccent
import llm.slop.spirals.ui.theme.AppText

@Composable
fun ParameterMatrix(
    labels: List<String>,
    parameters: List<ModulatableParameter>,
    focusedParameterId: String?,
    onFocusRequest: (String) -> Unit,
    onInteractionFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val row1Count = 4
    val row1Labels = labels.take(row1Count)
    val row1Params = parameters.take(row1Count)
    
    val row2Labels = labels.drop(row1Count)
    val row2Params = parameters.drop(row1Count)

    Column(modifier = modifier.fillMaxWidth()) {
        // Row 1: L1-L4 (Labels above)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            row1Params.forEachIndexed { index, param ->
                val id = row1Labels[index]
                KnobCell(
                    id = id,
                    param = param,
                    isFocused = id == focusedParameterId,
                    onFocusRequest = onFocusRequest,
                    onInteractionFinished = onInteractionFinished,
                    labelAbove = true
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Row 2: Rest (Labels underneath)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            row2Params.forEachIndexed { index, param ->
                val id = row2Labels[index]
                KnobCell(
                    id = id,
                    param = param,
                    isFocused = id == focusedParameterId,
                    onFocusRequest = onFocusRequest,
                    onInteractionFinished = onInteractionFinished,
                    labelAbove = false
                )
            }
        }
    }
}

@Composable
private fun RowScope.KnobCell(
    id: String,
    param: ModulatableParameter,
    isFocused: Boolean,
    onFocusRequest: (String) -> Unit,
    onInteractionFinished: () -> Unit,
    labelAbove: Boolean
) {
    var currentValue by remember(param) { mutableFloatStateOf(param.baseValue) }

    Box(
        modifier = Modifier
            .weight(1f)
            .padding(2.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onFocusRequest(id) }
            )
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (labelAbove) {
                Text(
                    text = id,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isFocused) AppAccent else AppText
                )
            }
            KnobView(
                currentValue = currentValue,
                onValueChange = { newValue ->
                    currentValue = newValue
                    param.baseValue = newValue
                    onFocusRequest(id)
                },
                onInteractionFinished = onInteractionFinished,
                modifier = Modifier.padding(vertical = 2.dp),
                isBipolar = false,
                focused = isFocused,
                knobSize = 44.dp,
                showValue = true
            )
            if (!labelAbove) {
                Text(
                    text = id,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isFocused) AppAccent else AppText
                )
            }
        }
    }
}
