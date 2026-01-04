package llm.slop.spirals.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import llm.slop.spirals.MandalaVisualSource
import llm.slop.spirals.cv.*

@Composable
fun InstrumentEditorScreen(visualSource: MandalaVisualSource) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text("Visual Patch Bay", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))

        // Global Parameters
        Text("Global Controls", style = MaterialTheme.typography.titleMedium, color = Color.Cyan)
        ParameterStrip("Alpha", visualSource.globalAlpha)
        ParameterStrip("Global Scale", visualSource.globalScale)
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.DarkGray)

        // Geometry Parameters
        Text("Geometry", style = MaterialTheme.typography.titleMedium, color = Color.Cyan)
        visualSource.parameters.forEach { (name, param) ->
            ParameterStrip(name, param)
        }
        
        Spacer(modifier = Modifier.height(100.dp)) // Padding for bottom controls
    }
}

@Composable
fun ParameterStrip(name: String, parameter: ModulatableParameter) {
    var showModDialog by remember { mutableStateOf(false) }
    var baseValue by remember { mutableFloatStateOf(parameter.baseValue) }
    
    // Pulse/Meter state
    var currentValue by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while(true) {
            currentValue = parameter.history.getSamples().lastOrNull() ?: 0f
            delay(16)
        }
    }

    Column(modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = Color.White, modifier = Modifier.width(80.dp), style = MaterialTheme.typography.labelMedium)
            
            Slider(
                value = baseValue,
                onValueChange = { 
                    baseValue = it
                    parameter.baseValue = it 
                },
                modifier = Modifier.weight(1f)
            )
            
            IconButton(onClick = { showModDialog = true }) {
                Icon(
                    Icons.Default.Add, 
                    contentDescription = "Add Modulator", 
                    tint = if (parameter.modulators.isNotEmpty()) Color.Green else Color.Gray
                )
            }
        }
        
        // Post-Modulation Meter
        Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(Color.DarkGray)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(currentValue.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(Color.Green)
            )
        }
    }

    if (showModDialog) {
        ModulatorConfigDialog(parameter) { showModDialog = false }
    }
}

@Composable
fun ModulatorConfigDialog(parameter: ModulatableParameter, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Modulators for Parameter", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

                parameter.modulators.forEachIndexed { index, mod ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(mod.sourceId, modifier = Modifier.weight(1f))
                        Text(mod.operator.name, modifier = Modifier.padding(horizontal = 8.dp))
                        Text("%.2f".format(mod.weight))
                        IconButton(onClick = { parameter.modulators.removeAt(index); onDismiss(); /* Refresh */ }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Red)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                var selectedSource by remember { mutableStateOf("amp") }
                var selectedOp by remember { mutableStateOf(ModulationOperator.ADD) }
                var weight by remember { mutableFloatStateOf(0.5f) }

                Text("Add New Modulator", style = MaterialTheme.typography.labelLarge)
                
                // Simplified selection for now
                Row {
                    val sources = listOf("amp", "bass", "mid", "high", "accent", "beatPhase")
                    var expanded by remember { mutableStateOf(false) }
                    TextButton(onClick = { expanded = true }) { Text(selectedSource) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        sources.forEach { s ->
                            DropdownMenuItem(text = { Text(s) }, onClick = { selectedSource = s; expanded = false })
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Op: ")
                    TextButton(onClick = { selectedOp = if (selectedOp == ModulationOperator.ADD) ModulationOperator.MUL else ModulationOperator.ADD }) {
                        Text(selectedOp.name)
                    }
                }

                Text("Weight: %.2f".format(weight))
                Slider(value = weight, onValueChange = { weight = it }, valueRange = -1f..1f)

                Button(
                    onClick = {
                        parameter.modulators.add(CvModulator(selectedSource, selectedOp, weight))
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Attach Modulator")
                }
            }
        }
    }
}
