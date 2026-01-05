package llm.slop.spirals.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import llm.slop.spirals.*
import llm.slop.spirals.cv.*

@Composable
fun InstrumentEditorScreen(visualSource: MandalaVisualSource, vm: MandalaViewModel) {
    val scrollState = rememberScrollState()
    var isCollapsed by remember { mutableStateOf(false) }
    var patchName by remember { mutableStateOf("New Patch") }
    var showLoadDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Petal Filtering State
    var selectedPetalFilter by remember { mutableStateOf<Int?>(null) }
    var petalMenuExpanded by remember { mutableStateOf(false) }
    
    val filteredRatios = remember(selectedPetalFilter) {
        val all = MandalaLibrary.MandalaRatios
        if (selectedPetalFilter == null) all.take(400) // Increased to 400 as requested
        else all.filter { it.petals == selectedPetalFilter }.take(400)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isCollapsed) Color.Transparent else Color.Black.copy(alpha = 0.6f))
            .padding(16.dp)
    ) {
        Text(
            text = "Visual Patch Bay", 
            style = MaterialTheme.typography.headlineSmall, 
            color = Color.White,
            modifier = Modifier.clickable { isCollapsed = !isCollapsed }.padding(bottom = 8.dp)
        )
        
        if (!isCollapsed) {
            Column(modifier = Modifier.verticalScroll(scrollState)) {
                // 1. Recipe Selection & Filtering
                Text("Mandala Recipe", style = MaterialTheme.typography.titleMedium, color = Color.Cyan)
                
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Petal Filter Button
                    Box(modifier = Modifier.weight(0.4f).padding(end = 4.dp)) {
                        OutlinedButton(onClick = { petalMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (selectedPetalFilter == null) "All" else "${selectedPetalFilter}P")
                        }
                        DropdownMenu(expanded = petalMenuExpanded, onDismissRequest = { petalMenuExpanded = false }) {
                            DropdownMenuItem(text = { Text("All Petals") }, onClick = { selectedPetalFilter = null; petalMenuExpanded = false })
                            listOf(3, 4, 5, 6, 7, 8, 9, 10, 12, 16).forEach { p ->
                                DropdownMenuItem(text = { Text("${p} Petals") }, onClick = { selectedPetalFilter = p; petalMenuExpanded = false })
                            }
                        }
                    }

                    // Recipe Dropdown Button
                    var recipeExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(onClick = { recipeExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("${visualSource.recipe.a}, ${visualSource.recipe.b}, ${visualSource.recipe.c}, ${visualSource.recipe.d} (${visualSource.recipe.petals}P)")
                        }
                        DropdownMenu(expanded = recipeExpanded, onDismissRequest = { recipeExpanded = false }) {
                            filteredRatios.forEach { ratio ->
                                DropdownMenuItem(
                                    text = { Text("${ratio.a}, ${ratio.b}, ${ratio.c}, ${ratio.d} (${ratio.petals}P)") },
                                    onClick = { visualSource.recipe = ratio; recipeExpanded = false }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Patch Management
                Text("Patch Management", style = MaterialTheme.typography.titleMedium, color = Color.Cyan)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = patchName,
                        onValueChange = { patchName = it },
                        label = { Text("Patch Name") },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = {
                        val patchData = PatchMapper.fromVisualSource(patchName, visualSource)
                        val json = PatchMapper.toJson(patchData)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, json)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Patch"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                    }
                    Button(onClick = { 
                        scope.launch {
                            val patchData = PatchMapper.fromVisualSource(patchName, visualSource)
                            vm.savePatch(patchData)
                        }
                    }) { Text("Save") }
                }
                Button(onClick = { showLoadDialog = true }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text("Load Patch...")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.DarkGray)

                // 3. Global Parameters
                Text("Global Controls", style = MaterialTheme.typography.titleMedium, color = Color.Cyan)
                ParameterStrip("Alpha", visualSource.globalAlpha)
                ParameterStrip("Global Scale", visualSource.globalScale)
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.DarkGray)

                // 4. Geometry Parameters
                Text("Geometry", style = MaterialTheme.typography.titleMedium, color = Color.Cyan)
                visualSource.parameters.forEach { (name, param) ->
                    ParameterStrip(name, param)
                }
                
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }

    if (showLoadDialog) {
        LoadPatchDialog(vm, onPatchSelected = { patchData ->
            PatchMapper.applyToVisualSource(patchData, visualSource)
            patchName = patchData.name
            showLoadDialog = false
        }, onDismiss = { showLoadDialog = false })
    }
}

@Composable
fun LoadPatchDialog(vm: MandalaViewModel, onPatchSelected: (PatchData) -> Unit, onDismiss: () -> Unit) {
    val patches by vm.allPatches.collectAsState(initial = emptyList())
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(16.dp).fillMaxHeight(0.7f)) {
                Text("Saved Patches", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    patches.forEach { entity ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { 
                            try {
                                val patchData = PatchData(entity.name, entity.recipeId, emptyList())
                                onPatchSelected(patchData) 
                            } catch (e: Exception) { e.printStackTrace() }
                        }.padding(12.dp)) {
                            Text(entity.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                if (patches.isEmpty()) Text("No patches saved yet.")
            }
        }
    }
}

@Composable
fun ParameterStrip(name: String, parameter: ModulatableParameter) {
    var showModDialog by remember { mutableStateOf(false) }
    var baseValue by remember { mutableFloatStateOf(parameter.baseValue) }
    var currentValue by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while(true) {
            currentValue = parameter.value
            delay(16)
        }
    }

    Column(modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = Color.White, modifier = Modifier.width(80.dp), style = MaterialTheme.typography.labelMedium)
            Slider(value = baseValue, onValueChange = { baseValue = it; parameter.baseValue = it }, modifier = Modifier.weight(1f))
            IconButton(onClick = { showModDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Modulator", tint = if (parameter.modulators.isNotEmpty()) Color.Green else Color.Gray)
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(Color.DarkGray)) {
            Box(modifier = Modifier.fillMaxWidth(currentValue.coerceIn(0f, 1f)).fillMaxHeight().background(Color.Green))
        }
    }
    if (showModDialog) { ModulatorConfigDialog(parameter) { showModDialog = false } }
}

@Composable
fun ModulatorConfigDialog(parameter: ModulatableParameter, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Modulators", style = MaterialTheme.typography.titleLarge)
                parameter.modulators.forEachIndexed { index, mod ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${mod.sourceId} ${mod.operator.name} ${"%.2f".format(mod.weight)}", modifier = Modifier.weight(1f))
                        IconButton(onClick = { parameter.modulators.removeAt(index); onDismiss() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Red)
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                var selectedSource by remember { mutableStateOf("amp") }
                var selectedOp by remember { mutableStateOf(ModulationOperator.ADD) }
                var weight by remember { mutableFloatStateOf(0.5f) }
                Row {
                    val sources = listOf("amp", "bass", "mid", "high", "accent", "beatPhase")
                    var expanded by remember { mutableStateOf(false) }
                    TextButton(onClick = { expanded = true }) { Text(selectedSource) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        sources.forEach { s -> DropdownMenuItem(text = { Text(s) }, onClick = { selectedSource = s; expanded = false }) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Op: ")
                    TextButton(onClick = { selectedOp = if (selectedOp == ModulationOperator.ADD) ModulationOperator.MUL else ModulationOperator.ADD }) { Text(selectedOp.name) }
                }
                Text("Weight: %.2f".format(weight))
                Slider(value = weight, onValueChange = { weight = it }, valueRange = -4f..4f)
                Button(onClick = { parameter.modulators.add(CvModulator(selectedSource, selectedOp, weight)); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Attach Modulator")
                }
            }
        }
    }
}
