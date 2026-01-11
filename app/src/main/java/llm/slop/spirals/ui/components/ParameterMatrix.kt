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
import kotlin.math.roundToInt

@Composable
fun MandalaParameterMatrix(
    labels: List<String>,
    parameters: List<ModulatableParameter>,
    focusedParameterId: String?,
    onFocusRequest: (String) -> Unit,
    onInteractionFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Row 1: L1 - L4 + Scale, Rotation
    val row1Ids = listOf("L1", "L2", "L3", "L4", "Scale", "Rotation")
    val row1Indices = row1Ids.map { labels.indexOf(it) }.filter { it != -1 }
    val row1Params = row1Indices.map { parameters[it] }

    // Row 2: Everything else
    val remainingIndices = parameters.indices.filter { !row1Ids.contains(labels[it]) }
    val remainingLabels = remainingIndices.map { labels[it] }
    val remainingParams = remainingIndices.map { parameters[it] }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // TOP ROW
        Row(
            modifier = Modifier.fillMaxWidth(),
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

        Spacer(modifier = Modifier.height(8.dp))

        // BOTTOM ROW
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
    val currentOnFocusRequest by rememberUpdatedState(onFocusRequest)

    Box(
        modifier = modifier
            .padding(2.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { currentOnFocusRequest(id) }
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
                    currentOnFocusRequest(id)
                },
                onInteractionFinished = onInteractionFinished,
                modifier = Modifier.padding(vertical = 1.dp),
                isBipolar = false, // Parameters in matrix are typically unipolar 0-1
                focused = isFocused,
                knobSize = 44.dp,
                showValue = true,
                displayTransform = { 
                    when (id) {
                        "Hue Sweep" -> "%.2f".format(it * 9.0f)
                        "Scale" -> "%.2f".format(it * 8.0f)
                        else -> (it * 100f).roundToInt().toString()
                    }
                }
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
