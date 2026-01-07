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
    // Row 1: L1 - L4
    val row1Ids = listOf("L1", "L2", "L3", "L4")
    val row1Indices = row1Ids.map { labels.indexOf(it) }.filter { it != -1 }
    val row1Params = row1Indices.map { parameters[it] }

    // Row 2: Everything else (expected to be 5 knobs)
    val remainingIndices = parameters.indices.filter { !row1Indices.contains(it) }
    val remainingLabels = remainingIndices.map { labels[it] }
    val remainingParams = remainingIndices.map { parameters[it] }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // TOP ROW: 4 knobs, 81.5% width, centered
        Row(
            modifier = Modifier.fillMaxWidth(0.815f),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            row1Params.forEachIndexed { index, param ->
                KnobCell(
                    id = row1Ids[index],
                    param = param,
                    isFocused = row1Ids[index] == focusedParameterId,
                    onFocusRequest = onFocusRequest,
                    onInteractionFinished = onInteractionFinished,
                    labelAbove = true
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // BOTTOM ROW: 5 knobs, full width
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            remainingParams.forEachIndexed { index, param ->
                KnobCell(
                    id = remainingLabels[index],
                    param = param,
                    isFocused = remainingLabels[index] == focusedParameterId,
                    onFocusRequest = onFocusRequest,
                    onInteractionFinished = onInteractionFinished,
                    labelAbove = false
                )
            }
        }
    }
}

@Composable
private fun KnobCell(
    id: String,
    param: ModulatableParameter,
    isFocused: Boolean,
    onFocusRequest: (String) -> Unit,
    onInteractionFinished: () -> Unit,
    labelAbove: Boolean,
    modifier: Modifier = Modifier
) {
    var currentValue by remember(param) { mutableFloatStateOf(param.baseValue) }

    Box(
        modifier = modifier
            .padding(2.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onFocusRequest(id) }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (labelAbove) {
                Text(
                    text = id,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isFocused) AppAccent else AppText,
                    maxLines = 1
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
                modifier = Modifier.padding(vertical = 1.dp),
                isBipolar = false,
                focused = isFocused,
                knobSize = 44.dp,
                showValue = true
            )
            if (!labelAbove) {
                Text(
                    text = id,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isFocused) AppAccent else AppText,
                    maxLines = 1
                )
            }
        }
    }
}
