package llm.slop.spirals.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import llm.slop.spirals.MandalaVisualSource
import llm.slop.spirals.MandalaViewModel
import llm.slop.spirals.cv.*
import kotlinx.coroutines.delay

@Composable
fun InstrumentEditorScreen(
    source: MandalaVisualSource,
    vm: MandalaViewModel,
    focusedId: String,
    onFocusChange: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    val focusedParam = source.parameters[focusedId] ?: source.globalAlpha
    val isArmLength = focusedId.startsWith("L") && focusedId.length == 2
    
    var refreshCount by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(8.dp)
    ) {
        // 1. Parameter Selector
        var selectorExpanded by remember { mutableStateOf(false) }
        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            OutlinedButton(
                onClick = { selectorExpanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Parameter: $focusedId", color = Color.Cyan)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
            }
            DropdownMenu(expanded = selectorExpanded, onDismissRequest = { selectorExpanded = false }) {
                source.parameters.keys.sorted().forEach { id ->
                    DropdownMenuItem(text = { Text(id) }, onClick = { onFocusChange(id); selectorExpanded = false })
                }
            }
        }

        Column(modifier = Modifier.weight(1f).verticalScroll(scrollState)) {
            if (!isArmLength) {
                // Use local state for base value to ensure smooth dragging
                var baseVal by remember(focusedId) { mutableFloatStateOf(focusedParam.baseValue) }
                Text("Base Value: ${"%.2f".format(baseVal)}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Slider(
                    value = baseVal,
                    onValueChange = { 
                        baseVal = it
                        focusedParam.baseValue = it 
                    },
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            Text("Modulation Matrix", style = MaterialTheme.typography.titleSmall, color = Color.Cyan)
            Spacer(modifier = Modifier.height(8.dp))

            key(focusedId, refreshCount) {
                focusedParam.modulators.forEachIndexed { index, mod ->
                    ModulatorRow(
                        mod = mod,
                        onUpdate = { updatedMod ->
                            focusedParam.modulators[index] = updatedMod
                        },
                        onRemove = { 
                            focusedParam.modulators.removeAt(index)
                            refreshCount++
                        }
                    )
                    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
                }

                // Empty row for adding new modulators
                ModulatorRow(
                    mod = null,
                    onUpdate = { newMod ->
                        focusedParam.modulators.add(newMod)
                        refreshCount++
                    },
                    onRemove = {}
                )
            }
            
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun ModulatorRow(
    mod: CvModulator?,
    onUpdate: (CvModulator) -> Unit,
    onRemove: () -> Unit
) {
    val isNew = mod == null
    var sourceId by remember(mod) { mutableStateOf(mod?.sourceId ?: "none") }
    var operator by remember(mod) { mutableStateOf(mod?.operator ?: ModulationOperator.ADD) }
    var weight by remember(mod) { mutableFloatStateOf(mod?.weight ?: 0f) }
    var bypassed by remember(mod) { mutableStateOf(mod?.bypassed ?: false) }
    
    // Pulse Line State
    var pulseValue by remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(sourceId) {
        if (sourceId != "none") {
            while(true) {
                pulseValue = CvRegistry.get(sourceId)
                delay(16)
            }
        } else {
            pulseValue = 0f
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(if (bypassed) Modifier.alpha(0.5f) else Modifier)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Bypass [B] Toggle
            if (!isNew) {
                Surface(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(24.dp)
                        .clickable { 
                            bypassed = !bypassed
                            onUpdate(CvModulator(sourceId, operator, weight, bypassed))
                        },
                    color = if (bypassed) Color.DarkGray else Color.Gray,
                    shape = MaterialTheme.shapes.extraSmall,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (bypassed) Color.Gray else Color.Cyan)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("B", color = if (bypassed) Color.Gray else Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Operator Toggle
            TextButton(
                onClick = { 
                    val newOp = if (operator == ModulationOperator.ADD) ModulationOperator.MUL else ModulationOperator.ADD
                    operator = newOp
                    if (!isNew) onUpdate(CvModulator(sourceId, newOp, weight, bypassed))
                },
                modifier = Modifier.width(48.dp)
            ) {
                Text(if (operator == ModulationOperator.ADD) "+" else "X", color = Color.Cyan)
            }

            // Source Dropdown
            var sourceExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                Text(
                    text = sourceId.uppercase(),
                    modifier = Modifier.clickable { sourceExpanded = true }.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isNew) Color.Gray else Color.White
                )
                DropdownMenu(expanded = sourceExpanded, onDismissRequest = { sourceExpanded = false }) {
                    listOf("none", "amp", "bass", "mid", "high", "accent", "beatPhase").forEach { s ->
                        DropdownMenuItem(text = { Text(s.uppercase()) }, onClick = { 
                            sourceId = s
                            if (s != "none") onUpdate(CvModulator(s, operator, weight, bypassed))
                            sourceExpanded = false
                        })
                    }
                }
            }

            // Weight Value Readout
            Text(
                text = "W: ${"%.2f".format(weight)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // Remove Button
            if (!isNew) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Weight Slider
        if (sourceId != "none") {
            Slider(
                value = weight,
                onValueChange = { 
                    weight = it
                    onUpdate(CvModulator(sourceId, operator, it, bypassed))
                },
                valueRange = -4f..4f,
                modifier = Modifier.height(24.dp)
            )
            
            // Pulse Line
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.DarkGray)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(pulseValue.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(Color.Cyan.copy(alpha = 0.5f))
                )
            }
        }
    }
}
