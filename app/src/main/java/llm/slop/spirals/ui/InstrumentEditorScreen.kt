package llm.slop.spirals.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import llm.slop.spirals.MandalaVisualSource
import llm.slop.spirals.MandalaViewModel
import llm.slop.spirals.cv.*
import llm.slop.spirals.R
import kotlinx.coroutines.delay

@Composable
fun InstrumentEditorScreen(
    source: MandalaVisualSource,
    vm: MandalaViewModel,
    focusedId: String,
    onFocusChange: (String) -> Unit,
    onInteractionFinished: () -> Unit
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
                // Use the map's keys directly without sorting to preserve insertion order
                source.parameters.keys.forEach { id ->
                    DropdownMenuItem(text = { Text(id) }, onClick = { onFocusChange(id); selectorExpanded = false })
                }
            }
        }

        Column(modifier = Modifier.weight(1f).verticalScroll(scrollState)) {
            if (!isArmLength) {
                var baseVal by remember(focusedId) { mutableFloatStateOf(focusedParam.baseValue) }
                Text("Base Value: ${"%.2f".format(baseVal)}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Slider(
                    value = baseVal,
                    onValueChange = { 
                        baseVal = it
                        focusedParam.baseValue = it 
                    },
                    onValueChangeFinished = onInteractionFinished,
                    modifier = Modifier.padding(bottom = 8.dp).padding(horizontal = 24.dp)
                )
                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 16.dp))
            }

            key(focusedId, refreshCount) {
                focusedParam.modulators.forEachIndexed { index, mod ->
                    ModulatorRow(
                        mod = mod,
                        onUpdate = { updatedMod ->
                            focusedParam.modulators[index] = updatedMod
                        },
                        onInteractionFinished = onInteractionFinished,
                        onRemove = { 
                            focusedParam.modulators.removeAt(index)
                            refreshCount++
                            onInteractionFinished()
                        }
                    )
                    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
                }

                ModulatorRow(
                    mod = null,
                    onUpdate = { newMod ->
                        focusedParam.modulators.add(newMod)
                        refreshCount++
                        onInteractionFinished()
                    },
                    onInteractionFinished = onInteractionFinished,
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
    onInteractionFinished: () -> Unit,
    onRemove: () -> Unit
) {
    val isNew = mod == null
    var sourceId by remember(mod) { mutableStateOf(mod?.sourceId ?: "none") }
    var operator by remember(mod) { mutableStateOf(mod?.operator ?: ModulationOperator.ADD) }
    var weight by remember(mod) { mutableFloatStateOf(mod?.weight ?: 0f) }
    var bypassed by remember(mod) { mutableStateOf(mod?.bypassed ?: false) }
    
    // Beat fields
    var waveform by remember(mod) { mutableStateOf(mod?.waveform ?: Waveform.SINE) }
    var subdivision by remember(mod) { mutableFloatStateOf(mod?.subdivision ?: 1.0f) }
    var phaseOffset by remember(mod) { mutableFloatStateOf(mod?.phaseOffset ?: 0.0f) }
    var slope by remember(mod) { mutableFloatStateOf(mod?.slope ?: 0.5f) }

    val isBeat = sourceId == "beatPhase"
    
    var pulseValue by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(sourceId) {
        if (sourceId != "none") {
            while(true) {
                pulseValue = if (sourceId == "beatPhase") {
                    // Use the precision clock for visual feedback
                    (CvRegistry.getSynchronizedTotalBeats() % 1.0).toFloat()
                } else {
                    CvRegistry.get(sourceId)
                }
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
            if (!isNew) {
                Surface(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .height(24.dp)
                        .width(36.dp)
                        .clickable { 
                            bypassed = !bypassed
                            onUpdate(CvModulator(sourceId, operator, weight, bypassed, waveform, subdivision, phaseOffset, slope))
                            onInteractionFinished() 
                        },
                    color = if (bypassed) Color.DarkGray else Color.Gray,
                    shape = MaterialTheme.shapes.extraSmall,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (bypassed) Color.Gray else Color.Cyan)
                ) {
                    Box(contentAlignment = Alignment.Center) { 
                        Text(
                            text = if (bypassed) "OFF" else "ON", 
                            color = if (bypassed) Color.Gray else Color.White, 
                            style = MaterialTheme.typography.labelSmall
                        ) 
                    }
                }
            }

            TextButton(
                onClick = { 
                    val newOp = if (operator == ModulationOperator.ADD) ModulationOperator.MUL else ModulationOperator.ADD
                    operator = newOp
                    if (!isNew) { onUpdate(CvModulator(sourceId, newOp, weight, bypassed, waveform, subdivision, phaseOffset, slope)); onInteractionFinished() }
                },
                modifier = Modifier.width(48.dp)
            ) { Text(if (operator == ModulationOperator.ADD) "+" else "X", color = Color.Cyan) }

            var sourceExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isBeat) "BEAT" else sourceId.uppercase(),
                    modifier = Modifier.clickable { sourceExpanded = true }.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isNew) Color.Gray else Color.White
                )
                DropdownMenu(expanded = sourceExpanded, onDismissRequest = { sourceExpanded = false }) {
                    listOf("none", "amp", "bass", "mid", "high", "accent", "beatPhase").forEach { s ->
                        DropdownMenuItem(text = { Text(if (s == "beatPhase") "BEAT" else s.uppercase()) }, onClick = { 
                            sourceId = s
                            if (s != "none") { onUpdate(CvModulator(s, operator, weight, bypassed, waveform, subdivision, phaseOffset, slope)); onInteractionFinished() }
                            sourceExpanded = false
                        })
                    }
                }
            }

            if (isBeat) {
                IconButton(onClick = {
                    val nextWave = Waveform.values()[(waveform.ordinal + 1) % Waveform.values().size]
                    waveform = nextWave
                    onUpdate(CvModulator(sourceId, operator, weight, bypassed, nextWave, subdivision, phaseOffset, slope))
                    onInteractionFinished()
                }) {
                    Icon(
                        painter = painterResource(id = when(waveform) {
                            Waveform.SINE -> R.drawable.ic_wave_sine
                            Waveform.TRIANGLE -> R.drawable.ic_wave_triangle
                            Waveform.SQUARE -> R.drawable.ic_wave_square
                        }),
                        contentDescription = "Waveform",
                        tint = Color.Cyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                var subExpanded by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { subExpanded = true }) {
                        val subText = when(subdivision) {
                            0.0625f -> "1/16"
                            0.125f -> "1/8"
                            0.25f -> "1/4"
                            0.5f -> "1/2"
                            else -> subdivision.toInt().toString()
                        }
                        Text(subText, color = Color.Cyan)
                    }
                    DropdownMenu(expanded = subExpanded, onDismissRequest = { subExpanded = false }) {
                        listOf(0.0625f, 0.125f, 0.25f, 0.5f, 1f, 2f, 4f, 8f, 16f, 32f, 64f, 128f, 256f).forEach { sub ->
                            DropdownMenuItem(text = {
                                Text(when(sub) {
                                    0.0625f -> "1/16"
                                    0.125f -> "1/8"
                                    0.25f -> "1/4"
                                    0.5f -> "1/2"
                                    else -> sub.toInt().toString()
                                })
                            }, onClick = {
                                subdivision = sub
                                onUpdate(CvModulator(sourceId, operator, weight, bypassed, waveform, sub, phaseOffset, slope))
                                onInteractionFinished()
                                subExpanded = false
                            })
                        }
                    }
                }
            }

            if (!isNew) {
                IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.LightGray, modifier = Modifier.size(16.dp)) }
            }
        }

        if (sourceId != "none") {
            Text("Weight: ${"%.2f".format(weight)}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Slider(
                value = weight, 
                onValueChange = { weight = it; onUpdate(CvModulator(sourceId, operator, it, bypassed, waveform, subdivision, phaseOffset, slope)) }, 
                onValueChangeFinished = onInteractionFinished, 
                valueRange = -1f..1f, 
                modifier = Modifier.height(24.dp).padding(horizontal = 24.dp)
            )
            
            if (isBeat) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    val hasSecondSlider = waveform == Waveform.TRIANGLE || waveform == Waveform.SQUARE
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Phase: ${"%.2f".format(phaseOffset)}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Slider(
                            value = phaseOffset, 
                            onValueChange = { phaseOffset = it; onUpdate(CvModulator(sourceId, operator, weight, bypassed, waveform, subdivision, it, slope)) }, 
                            onValueChangeFinished = onInteractionFinished, 
                            modifier = Modifier.height(24.dp).padding(
                                start = 24.dp, 
                                end = if (hasSecondSlider) 4.dp else 24.dp
                            )
                        )
                    }
                    if (hasSecondSlider) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (waveform == Waveform.TRIANGLE) "Slope: ${"%.2f".format(slope)}" else "Duty: ${"%.2f".format(slope)}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Slider(
                                value = slope, 
                                onValueChange = { slope = it; onUpdate(CvModulator(sourceId, operator, weight, bypassed, waveform, subdivision, phaseOffset, it)) }, 
                                onValueChangeFinished = onInteractionFinished, 
                                modifier = Modifier.height(24.dp).padding(start = 4.dp, end = 24.dp)
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.DarkGray)) {
                Box(modifier = Modifier.fillMaxWidth(pulseValue.coerceIn(0f, 1f)).fillMaxHeight().background(Color.Cyan.copy(alpha = 0.5f)))
            }
        }
    }
}
