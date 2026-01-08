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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import llm.slop.spirals.*
import llm.slop.spirals.ui.components.KnobView
import llm.slop.spirals.ui.theme.AppBackground
import llm.slop.spirals.ui.theme.AppText
import llm.slop.spirals.ui.theme.AppAccent
import kotlinx.serialization.json.Json

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
    var focusedSlotIndex by remember { mutableIntStateOf(0) }
    var monitorSource by remember { mutableStateOf("A") } // "1", "2", "3", "4", "A"

    var showMenu by remember { mutableStateOf(false) }
    var showOpenDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showSetPicker by remember { mutableStateOf(false) }

    // Sync state to Renderer
    val renderer = (LocalSpiralRenderer.current) 
    LaunchedEffect(currentPatch, monitorSource) {
        renderer?.mixerPatch = currentPatch
        renderer?.monitorSource = monitorSource
        
        // Phase 1: Load the first mandala of each set into the renderer's slot sources
        currentPatch.slots.forEachIndexed { index, slot ->
            val setEntity = allSets.find { it.id == slot.mandalaSetId }
            if (setEntity != null) {
                val orderedIds: List<String> = Json.decodeFromString(setEntity.jsonOrderedMandalaIds)
                val firstMandalaId = orderedIds.firstOrNull()
                if (firstMandalaId != null) {
                    val patchEntity = allPatches.find { it.name == firstMandalaId }
                    patchEntity?.let { pe ->
                        val patchData = PatchMapper.fromJson(pe.jsonSettings)
                        patchData?.let { pd ->
                            renderer?.getSlotSource(index)?.let { source ->
                                PatchMapper.applyToVisualSource(pd, source)
                            }
                        }
                    }
                }
            }
        }
    }

    // Cleanup when leaving mixer
    DisposableEffect(Unit) {
        onDispose {
            renderer?.mixerPatch = null
            renderer?.monitorSource = "1"
        }
    }

    fun selectPatch(id: String) {
        val entity = allMixerPatches.find { it.id == id } ?: return
        currentPatch = Json.decodeFromString(entity.jsonSettings)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val breadcrumb = "Mixer: ${currentPatch.name} › Slot ${focusedSlotIndex + 1}"
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
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = AppBackground
                    ) {
                        DropdownMenuItem(
                            text = { Text("New Mixer", color = AppText) },
                            onClick = {
                                currentPatch = MixerPatch(name = "New Mixer", slots = List(4) { MixerSlotData() })
                                showMenu = false
                            }
                        )
                        HorizontalDivider(color = AppText.copy(alpha = 0.1f))
                        DropdownMenuItem(
                            text = { Text("CV Lab", color = AppAccent) },
                            onClick = { onShowCvLab(); showMenu = false },
                            leadingIcon = { Icon(Icons.Default.Build, contentDescription = null, tint = AppAccent) }
                        )
                        DropdownMenuItem(
                            text = { Text("Mandala Editor", color = AppAccent) },
                            onClick = { onNavigateToMandalaEditor(); showMenu = false },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = AppAccent) }
                        )
                        DropdownMenuItem(
                            text = { Text("Set Editor", color = AppAccent) },
                            onClick = { onNavigateToSetEditor(); showMenu = false },
                            leadingIcon = { Icon(Icons.Default.List, contentDescription = null, tint = AppAccent) }
                        )
                        HorizontalDivider(color = AppText.copy(alpha = 0.1f))
                        DropdownMenuItem(
                            text = { Text("Open Mixer", color = AppText) },
                            onClick = { showOpenDialog = true; showMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Save Mixer", color = AppText) },
                            onClick = {
                                vm.saveMixerPatch(currentPatch)
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename Mixer", color = AppText) },
                            onClick = { showRenameDialog = true; showMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Mixer", color = Color.Red) },
                            onClick = {
                                vm.deleteMixerPatch(currentPatch.id)
                                currentPatch = MixerPatch(name = "New Mixer", slots = List(4) { MixerSlotData() })
                                showMenu = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Preview Window
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16 / 9f)
                    .background(Color.Black)
                    .border(1.dp, AppText.copy(alpha = 0.1f))
            ) {
                previewContent()

                // Monitor Selector Overlaid
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("1", "2", "3", "4", "A").forEach { src ->
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

            Spacer(modifier = Modifier.height(16.dp))

            // Slot Strip (Always Visible)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                currentPatch.slots.forEachIndexed { index, slot ->
                    key(index) {
                        val isFocused = focusedSlotIndex == index
                        val setName = allSets.find { it.id == slot.mandalaSetId }?.name ?: "Empty"
                        
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(2.dp)
                                .border(1.dp, if (isFocused) AppAccent else AppText.copy(alpha = 0.1f))
                                .clickable { focusedSlotIndex = index }
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Slot ${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isFocused) AppAccent else AppText
                            )
                            Text(
                                text = setName,
                                style = MaterialTheme.typography.bodySmall,
                                color = AppText,
                                maxLines = 1
                            )
                            Switch(
                                checked = slot.enabled,
                                onCheckedChange = { enabled ->
                                    currentPatch = currentPatch.copy(
                                        slots = currentPatch.slots.toMutableList().also {
                                            it[index] = it[index].copy(enabled = enabled)
                                        }
                                    )
                                },
                                modifier = Modifier.graphicsLayer {
                                    scaleX = 0.6f
                                    scaleY = 0.6f
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Slot Editor (Focused View)
            val focusedSlot = currentPatch.slots[focusedSlotIndex]
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { showSetPicker = true }) {
                        Text("Select Set")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (focusedSlot.mandalaSetId != null) {
                        OutlinedButton(onClick = onNavigateToSetEditor) {
                            Text("Edit Set")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Mixing Controls: Opacity
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KnobView(
                        currentValue = focusedSlot.opacityBaseValue,
                        onValueChange = { newVal ->
                            currentPatch = currentPatch.copy(
                                slots = currentPatch.slots.toMutableList().also {
                                    it[focusedSlotIndex] = it[focusedSlotIndex].copy(opacityBaseValue = newVal)
                                }
                            )
                        },
                        onInteractionFinished = {},
                        knobSize = 48.dp,
                        showValue = true,
                        focused = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Opacity", style = MaterialTheme.typography.labelSmall, color = AppText)
                        IconButton(onClick = { /* CV Binding Modal */ }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Settings, contentDescription = "CV Binding", tint = AppAccent, modifier = Modifier.size(16.dp))
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(32.dp))
                    
                    // Blend Mode Dropdown
                    var blendExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { blendExpanded = true }) {
                            Text(focusedSlot.blendMode, style = MaterialTheme.typography.labelSmall, color = AppText)
                        }
                        DropdownMenu(expanded = blendExpanded, onDismissRequest = { blendExpanded = false }, containerColor = AppBackground) {
                            listOf("Normal", "Add", "Multiply", "Screen").forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode, style = MaterialTheme.typography.labelSmall) },
                                    onClick = {
                                        currentPatch = currentPatch.copy(
                                            slots = currentPatch.slots.toMutableList().also {
                                                it[focusedSlotIndex] = it[focusedSlotIndex].copy(blendMode = mode)
                                            }
                                        )
                                        blendExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Advance Controls
                Text("Advance Mode", style = MaterialTheme.typography.titleSmall, color = AppText)
                var advanceExpanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { advanceExpanded = true }) {
                        Text(focusedSlot.advanceMode.name, style = MaterialTheme.typography.labelSmall, color = AppText)
                    }
                    DropdownMenu(expanded = advanceExpanded, onDismissRequest = { advanceExpanded = false }, containerColor = AppBackground) {
                        AdvanceMode.values().forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.name, style = MaterialTheme.typography.labelSmall) },
                                onClick = {
                                    currentPatch = currentPatch.copy(
                                        slots = currentPatch.slots.toMutableList().also {
                                            it[focusedSlotIndex] = it[focusedSlotIndex].copy(advanceMode = mode)
                                        }
                                    )
                                    advanceExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSetPicker) {
        Dialog(onDismissRequest = { showSetPicker = false }) {
            Surface(shape = MaterialTheme.shapes.medium, color = AppBackground) {
                Column(modifier = Modifier.padding(16.dp).fillMaxHeight(0.7f)) {
                    Text("Select Mandala Set", style = MaterialTheme.typography.titleLarge, color = AppText)
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        allSets.forEach { setEntity ->
                            Row(modifier = Modifier.fillMaxWidth().clickable {
                                currentPatch = currentPatch.copy(
                                    slots = currentPatch.slots.toMutableList().also {
                                        it[focusedSlotIndex] = it[focusedSlotIndex].copy(mandalaSetId = setEntity.id)
                                    }
                                )
                                showSetPicker = false
                            }.padding(12.dp)) {
                                Text(setEntity.name, style = MaterialTheme.typography.bodyLarge, color = AppText)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showOpenDialog) {
        Dialog(onDismissRequest = { showOpenDialog = false }) {
            Surface(shape = MaterialTheme.shapes.medium, color = AppBackground) {
                Column(modifier = Modifier.padding(16.dp).fillMaxHeight(0.7f)) {
                    Text("Open Mixer Patch", style = MaterialTheme.typography.titleLarge, color = AppText)
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        allMixerPatches.forEach { entity ->
                            Row(modifier = Modifier.fillMaxWidth().clickable {
                                selectPatch(entity.id)
                                showOpenDialog = false
                            }.padding(12.dp)) {
                                Text(entity.name, style = MaterialTheme.typography.bodyLarge, color = AppText)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        var name by remember { mutableStateOf(currentPatch.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Mixer", color = AppText) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppAccent,
                        unfocusedTextColor = AppText,
                        focusedTextColor = AppText,
                        cursorColor = AppAccent
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    currentPatch = currentPatch.copy(name = name)
                    showRenameDialog = false
                }) {
                    Text("RENAME", color = AppAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("CANCEL", color = AppText)
                }
            },
            containerColor = AppBackground
        )
    }
}

// CompositionLocal to allow screens to access the renderer
val LocalSpiralRenderer = staticCompositionLocalOf<SpiralRenderer?> { null }
