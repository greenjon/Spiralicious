package llm.slop.spirals.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import llm.slop.spirals.*
import llm.slop.spirals.models.*
import llm.slop.spirals.ui.components.*
import llm.slop.spirals.ui.theme.AppBackground
import llm.slop.spirals.ui.theme.AppText
import llm.slop.spirals.ui.theme.AppAccent
import kotlinx.serialization.json.Json
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixerEditorScreen(
    vm: MandalaViewModel = viewModel(),
    onClose: () -> Unit,
    onNavigateToSetEditor: () -> Unit,
    onNavigateToMandalaEditor: () -> Unit,
    onShowCvLab: () -> Unit,
    previewContent: @Composable () -> Unit
) {
    val allMixerPatches by vm.allMixerPatches.collectAsState(initial = emptyList())
    val allSets by vm.allSets.collectAsState(initial = emptyList())
    val allPatches by vm.allPatches.collectAsState(initial = emptyList())

    var currentPatch by remember { mutableStateOf(MixerPatch(name = "New Mixer", slots = List(4) { MixerSlotData() })) }
    var monitorSource by remember { mutableStateOf("F") } 
    
    // UI state for focusing parameters for CV patching
    var focusedParameterId by remember { mutableStateOf("PN1") }

    var showMenu by remember { mutableStateOf(false) }
    var showOpenDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showSetPickerForSlot by remember { mutableStateOf<Int?>(null) }
    var showMandalaPickerForSlot by remember { mutableStateOf<Int?>(null) }

    val mainRenderer = LocalSpiralRenderer.current

    // A simple tick that forces the UI to recompose at 60Hz so the scopes update
    var frameTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            frameTick++
            delay(16)
        }
    }

    LaunchedEffect(mainRenderer, currentPatch, monitorSource, allSets, allPatches) {
        if (mainRenderer == null) return@LaunchedEffect
        mainRenderer.mixerPatch = currentPatch
        mainRenderer.monitorSource = monitorSource
        
        currentPatch.slots.forEachIndexed { index, slot ->
            val patchEntity = if (slot.sourceType == VideoSourceType.MANDALA_SET) {
                val setEntity = allSets.find { it.id == slot.mandalaSetId }
                setEntity?.let { se ->
                    val orderedIds = Json.decodeFromString<List<String>>(se.jsonOrderedMandalaIds)
                    if (orderedIds.isNotEmpty()) {
                        val safeIndex = slot.currentIndex.baseValue.toInt().coerceIn(0, orderedIds.size - 1)
                        allPatches.find { it.name == orderedIds[safeIndex] }
                    } else null
                }
            } else if (slot.sourceType == VideoSourceType.MANDALA) {
                allPatches.find { it.name == slot.selectedMandalaId }
            } else null

            val source = mainRenderer.getSlotSource(index)
            patchEntity?.let { pe ->
                val patchData = PatchMapper.fromJson(pe.jsonSettings)
                patchData?.let { pd ->
                    PatchMapper.applyToVisualSource(pd, source)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val breadcrumb = "Mixer: ${currentPatch.name} › ${if (monitorSource in listOf("A", "B", "F")) "Group $monitorSource" else "Slot $monitorSource"}"
            Text(
                text = breadcrumb,
                style = MaterialTheme.typography.titleMedium,
                color = AppText,
                modifier = Modifier.padding(4.dp)
            )

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = AppText)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, containerColor = AppBackground) {
                    DropdownMenuItem(text = { Text("New Mixer") }, onClick = { 
                        currentPatch = MixerPatch(name = "New Mixer", slots = List(4) { MixerSlotData() })
                        showMenu = false 
                    })
                    DropdownMenuItem(text = { Text("Open Mixer") }, onClick = { showOpenDialog = true; showMenu = false })
                    DropdownMenuItem(text = { Text("Save Mixer") }, onClick = { vm.saveMixerPatch(currentPatch); showMenu = false })
                    DropdownMenuItem(text = { Text("Rename Mixer") }, onClick = { showRenameDialog = true; showMenu = false })
                    HorizontalDivider(color = AppText.copy(alpha = 0.1f))
                    DropdownMenuItem(text = { Text("CV Lab") }, onClick = { onShowCvLab(); showMenu = false })
                    DropdownMenuItem(text = { Text("Mandala Editor") }, onClick = { onNavigateToMandalaEditor(); showMenu = false })
                    DropdownMenuItem(text = { Text("Set Editor") }, onClick = { onNavigateToSetEditor(); showMenu = false })
                    DropdownMenuItem(text = { Text("Close", color = Color.Red) }, onClick = { onClose(); showMenu = false })
                }
            }
        }

        // Main Preview Window
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .aspectRatio(16 / 9f)
                .background(Color.Black)
                .border(1.dp, AppText.copy(alpha = 0.1f))
        ) {
            previewContent()

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("1", "2", "3", "4", "A", "B", "F").forEach { src ->
                    val isSelected = monitorSource == src
                    Surface(
                        color = if (isSelected) AppAccent else AppBackground.copy(alpha = 0.7f),
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier.clickable { monitorSource = src }
                    ) {
                        Text(
                            text = src,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) Color.White else AppText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Oscilloscope
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(60.dp).border(1.dp, AppText.copy(alpha = 0.1f))) {
            key(frameTick) {
                val targetParam = mainRenderer?.getMixerParam(focusedParameterId)
                if (targetParam != null) {
                    OscilloscopeView(history = targetParam.history, modifier = Modifier.fillMaxSize())
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Strips Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 4.dp)
        ) {
            Column(modifier = Modifier.weight(3f)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    SourceStrip(0, currentPatch, { currentPatch = it }, mainRenderer, { showSetPickerForSlot = 0 }, { showMandalaPickerForSlot = 0 }, allSets, "1", Alignment.TopEnd, focusedParameterId, { focusedParameterId = it }, Modifier.weight(1f))
                    MonitorStrip("A", currentPatch, { currentPatch = it }, mainRenderer, false, true, {}, focusedParameterId, { focusedParameterId = it }, Modifier.weight(1f))
                    SourceStrip(1, currentPatch, { currentPatch = it }, mainRenderer, { showSetPickerForSlot = 1 }, { showMandalaPickerForSlot = 1 }, allSets, "2", Alignment.TopStart, focusedParameterId, { focusedParameterId = it }, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    SourceStrip(2, currentPatch, { currentPatch = it }, mainRenderer, { showSetPickerForSlot = 2 }, { showMandalaPickerForSlot = 2 }, allSets, "3", Alignment.TopEnd, focusedParameterId, { focusedParameterId = it }, Modifier.weight(1f))
                    MonitorStrip("B", currentPatch, { currentPatch = it }, mainRenderer, false, true, {}, focusedParameterId, { focusedParameterId = it }, Modifier.weight(1f))
                    SourceStrip(3, currentPatch, { currentPatch = it }, mainRenderer, { showSetPickerForSlot = 3 }, { showMandalaPickerForSlot = 3 }, allSets, "4", Alignment.TopStart, focusedParameterId, { focusedParameterId = it }, Modifier.weight(1f))
                }
            }
            MonitorStrip("F", currentPatch, { currentPatch = it }, mainRenderer, false, true, {}, focusedParameterId, { focusedParameterId = it }, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // CV Patching Area
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            MixerCvEditor(
                patch = currentPatch,
                focusedId = focusedParameterId,
                onPatchUpdate = { currentPatch = it }
            )
        }
    }

    // Picker Dialogs
    if (showSetPickerForSlot != null) {
        PickerDialog(
            title = "Select Mandala Set",
            items = allSets.map { it.name to it.id },
            onSelect = { id ->
                val idx = showSetPickerForSlot!!
                val newSlots = currentPatch.slots.toMutableList()
                newSlots[idx] = newSlots[idx].copy(mandalaSetId = id, currentIndex = ModulatableParameterData(0f), sourceType = VideoSourceType.MANDALA_SET)
                currentPatch = currentPatch.copy(slots = newSlots)
                showSetPickerForSlot = null
            },
            onDismiss = { showSetPickerForSlot = null },
            onCreateNew = {
                onNavigateToSetEditor()
                showSetPickerForSlot = null
            }
        )
    }

    if (showMandalaPickerForSlot != null) {
        PickerDialog(
            title = "Select Mandala",
            items = allPatches.map { it.name to it.name },
            onSelect = { id ->
                val idx = showMandalaPickerForSlot!!
                val newSlots = currentPatch.slots.toMutableList()
                newSlots[idx] = newSlots[idx].copy(selectedMandalaId = id, sourceType = VideoSourceType.MANDALA)
                currentPatch = currentPatch.copy(slots = newSlots)
                showMandalaPickerForSlot = null
            },
            onDismiss = { showMandalaPickerForSlot = null },
            onCreateNew = {
                onNavigateToMandalaEditor()
                showMandalaPickerForSlot = null
            }
        )
    }

    if (showOpenDialog) {
        PickerDialog(
            title = "Open Mixer Patch",
            items = allMixerPatches.map { it.name to it.jsonSettings },
            onSelect = { json ->
                currentPatch = Json.decodeFromString(json)
                showOpenDialog = false
            },
            onDismiss = { showOpenDialog = false }
        )
    }

    if (showRenameDialog) {
        var newName by remember { mutableStateOf(currentPatch.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Mixer") },
            text = { TextField(value = newName, onValueChange = { newName = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { currentPatch = currentPatch.copy(name = newName); showRenameDialog = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } },
            containerColor = AppBackground, titleContentColor = AppText, textContentColor = AppText
        )
    }
}

@Composable
fun PickerDialog(
    title: String,
    items: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreateNew: (() -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium, color = AppBackground, modifier = Modifier.fillMaxHeight(0.7f).fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = AppText)
                    if (onCreateNew != null) {
                        IconButton(onClick = onCreateNew) {
                            Icon(Icons.Default.Add, contentDescription = "Create New", tint = AppAccent)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (onCreateNew != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCreateNew() }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = AppAccent, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Create new...", style = MaterialTheme.typography.bodyLarge, color = AppAccent)
                        }
                        HorizontalDivider(color = AppText.copy(alpha = 0.1f))
                    }

                    items.forEach { (name, value) ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(value) }.padding(12.dp)) {
                            Text(name, style = MaterialTheme.typography.bodyLarge, color = AppText)
                        }
                    }
                    if (items.isEmpty() && onCreateNew == null) {
                        Text("No items found.", color = AppText.copy(alpha = 0.5f), modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
    }
}
