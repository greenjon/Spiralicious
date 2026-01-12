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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MandalaParameterMatrix(
    labels: List<String>,
    parameters: List<ModulatableParameter>,
    focusedParameterId: String?,
    onFocusRequest: (String) -> Unit,
    onInteractionFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Row 1: Primary Geometry (Arms + Scale)
    val row1Ids = listOf("L1", "L2", "L3", "L4", "Scale")
    val row1Params = row1Ids.mapNotNull { id ->
        val idx = labels.indexOf(id)
        if (idx != -1) id to parameters[idx] else null
    }

    // Row 2: Secondary Geometry & Color
    val row2Ids = listOf("Rotation", "Thickness", "Hue Offset", "Hue Sweep", "Depth")
    val row2Params = row2Ids.mapNotNull { id ->
        val idx = labels.indexOf(id)
        if (idx != -1) id to parameters[idx] else null
    }

    // Row 3: Snapshot & Trails
    val row3Ids = listOf("Trails", "Snap Count", "Snap Mode", "Snap Blend", "Snap Trigger")
    val row3Params = row3Ids.mapNotNull { id ->
        val idx = labels.indexOf(id)
        if (idx != -1) id to parameters[idx] else null
    }

    // Row 4: Feedback Engine
    val row4Ids = listOf("FB Decay", "FB Gain", "FB Zoom", "FB Rotate", "FB Shift", "FB Blur")
    val row4Params = row4Ids.mapNotNull { id ->
        val idx = labels.indexOf(id)
        if (idx != -1) id to parameters[idx] else null
    }

    // Capture any parameters not explicitly grouped above
    val handledIds = row1Ids + row2Ids + row3Ids + row4Ids
    val extraParams = labels.indices
        .filter { !handledIds.contains(labels[it]) }
        .map { labels[it] to parameters[it] }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ROW 1
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            row1Params.forEach { (id, param) ->
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

        Spacer(modifier = Modifier.height(12.dp))

        // ROW 2
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            row2Params.forEach { (id, param) ->
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

        Spacer(modifier = Modifier.height(12.dp))

        // ROW 3
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            row3Params.forEach { (id, param) ->
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

        Spacer(modifier = Modifier.height(12.dp))

        // ROW 4: Feedback
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            row4Params.forEach { (id, param) ->
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

        if (extraParams.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                extraParams.forEach { (id, param) ->
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
                        "Snap Count" -> (it * 14f + 2f).roundToInt().toString()
                        "Snap Mode" -> if (it < 0.5f) "BHND" else "ABOV"
                        "Snap Blend" -> if (it < 0.5f) "NORM" else "ADD"
                        "FB Zoom" -> "%.1f%%".format((it - 0.5f) * 10f)
                        "FB Rotate" -> "%.1f°".format((it - 0.5f) * 10f)
                        "FB Shift" -> "%.0f°".format(it * 360f)
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
