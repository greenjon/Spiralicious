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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import llm.slop.spirals.*
import llm.slop.spirals.ui.components.KnobView
import llm.slop.spirals.ui.components.OscilloscopeView
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
    var monitorSource by remember { mutableStateOf("F") } // "1", "2", "3", "4", "A", "B", "F"
    var viewSet1A2 by remember { mutableStateOf(true) }
    
    var showMenu by remember { mutableStateOf(false) }
    var showOpenDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showSetPickerForSlot by remember { mutableStateOf<Int?>(null) }
    var showMandalaPickerForSlot by remember { mutableStateOf<Int?>(null) }

    val mainRenderer = LocalSpiralRenderer.current

    // Sync state to main renderer and sources
    LaunchedEffect(mainRenderer, currentPatch, monitorSource, allSets, allPatches) {
        if (mainRenderer == null) return@LaunchedEffect
        mainRenderer.mixerPatch = currentPatch
        mainRenderer.monitorSource = monitorSource
        
        currentPatch.slots.forEachIndexed { index, slot ->
            val patchEntity = if (slot.sourceIsSet) {
                val setEntity = allSets.find { it.id == slot.mandalaSetId }
                setEntity?.let { se ->
                    val orderedIds = Json.decodeFromString<List<String>>(se.jsonOrderedMandalaIds)
                    if (orderedIds.isNotEmpty()) {
                        val safeIndex = slot.currentIndex.coerceIn(0, orderedIds.size - 1)
                        allPatches.find { it.name == orderedIds[safeIndex] }
                    } else null
                }
            } else {
                allPatches.find { it.name == slot.selectedMandalaId }
            }

            patchEntity?.let { pe ->
                val patchData = PatchMapper.fromJson(pe.jsonSettings)
                patchData?.let { pd ->
                    val source = mainRenderer.getSlotSource(index)
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
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

        // Main Preview Window (The video monitor)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .aspectRatio(16 / 9f)
                .background(Color.Black)
                .border(1.dp, AppText.copy(alpha = 0.1f))
        ) {
            previewContent()

            // Monitor Selector Overlaid (7 buttons)
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

        // Oscilloscope tied to monitorSource
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(40.dp).border(1.dp, AppText.copy(alpha = 0.1f))) {
            val monIdx = monitorSource.toIntOrNull()?.minus(1)
            val targetSource = if (monIdx != null && monIdx in 0..3) {
                mainRenderer?.getSlotSource(monIdx)
            } else {
                // For A, B, F we'll just show slot 0 for now as a representative history, 
                // or we could combine them, but keeping it simple as per original
                mainRenderer?.getSlotSource(0)
            }
            
            if (targetSource != null) {
                OscilloscopeView(history = targetSource.globalAlpha.history, modifier = Modifier.fillMaxSize())
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Strip Header with Toggle and Labels
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Toggle Switch
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("1A2", color = if (viewSet1A2) AppAccent else AppText.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Switch(
                    checked = !viewSet1A2,
                    onCheckedChange = { viewSet1A2 = !it },
                    modifier = Modifier.padding(horizontal = 6.dp).graphicsLayer { scaleX = 0.7f; scaleY = 0.7f }
                )
                Text("3B4", color = if (!viewSet1A2) AppAccent else AppText.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Labels
            val labels = if (viewSet1A2) listOf("Source 1", "Mixer A", "Source 2", "Final Mixer")
                         else listOf("Source 3", "Mixer B", "Source 4", "Final Mixer")
            
            Row(modifier = Modifier.weight(1f)) {
                labels.forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppText.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Strips Section
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp)) {
            if (viewSet1A2) {
                SourceStrip(0, currentPatch, { currentPatch = it }, mainRenderer, { showSetPickerForSlot = 0 }, { showMandalaPickerForSlot = 0 }, allSets, Modifier.weight(1f))
                MonitorStrip("A", currentPatch, { currentPatch = it }, mainRenderer, Modifier.weight(1f))
                SourceStrip(1, currentPatch, { currentPatch = it }, mainRenderer, { showSetPickerForSlot = 1 }, { showMandalaPickerForSlot = 1 }, allSets, Modifier.weight(1f))
            } else {
                SourceStrip(2, currentPatch, { currentPatch = it }, mainRenderer, { showSetPickerForSlot = 2 }, { showMandalaPickerForSlot = 2 }, allSets, Modifier.weight(1f))
                MonitorStrip("B", currentPatch, { currentPatch = it }, mainRenderer, Modifier.weight(1f))
                SourceStrip(3, currentPatch, { currentPatch = it }, mainRenderer, { showSetPickerForSlot = 3 }, { showMandalaPickerForSlot = 3 }, allSets, Modifier.weight(1f))
            }
            MonitorStrip("F", currentPatch, { currentPatch = it }, mainRenderer, Modifier.weight(1f))
        }
    }

    // Dialogs
    if (showSetPickerForSlot != null) {
        PickerDialog("Select Mandala Set", allSets.map { it.name to it.id }, { id ->
            val idx = showSetPickerForSlot!!
            val newSlots = currentPatch.slots.toMutableList()
            newSlots[idx] = newSlots[idx].copy(mandalaSetId = id, currentIndex = 0, sourceIsSet = true)
            currentPatch = currentPatch.copy(slots = newSlots)
            showSetPickerForSlot = null
        }, { showSetPickerForSlot = null })
    }

    if (showMandalaPickerForSlot != null) {
        PickerDialog("Select Mandala", allPatches.map { it.name to it.name }, { id ->
            val idx = showMandalaPickerForSlot!!
            val newSlots = currentPatch.slots.toMutableList()
            newSlots[idx] = newSlots[idx].copy(selectedMandalaId = id, sourceIsSet = false)
            currentPatch = currentPatch.copy(slots = newSlots)
            showMandalaPickerForSlot = null
        }, { showMandalaPickerForSlot = null })
    }

    if (showOpenDialog) {
        PickerDialog("Open Mixer Patch", allMixerPatches.map { it.name to it.jsonSettings }, { json ->
            currentPatch = Json.decodeFromString(json)
            showOpenDialog = false
        }, { showOpenDialog = false })
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
fun SourceStrip(
    index: Int,
    patch: MixerPatch,
    onPatchChange: (MixerPatch) -> Unit,
    mainRenderer: SpiralRenderer?,
    onPickSet: () -> Unit,
    onPickMandala: () -> Unit,
    allSets: List<MandalaSetEntity>,
    modifier: Modifier = Modifier
) {
    val slot = patch.slots[index]
    Column(
        modifier = modifier.padding(1.dp).fillMaxHeight().border(1.dp, AppText.copy(alpha = 0.1f)).padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Preview Window
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(Color.Black).border(1.dp, AppText.copy(alpha = 0.2f))) {
            StripPreview(monitorSource = "${index + 1}", patch = patch, mainRenderer = mainRenderer)
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Source
        Text(
            text = if (slot.sourceIsSet) "Set" else "Man",
            style = MaterialTheme.typography.labelSmall,
            color = AppAccent,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable {
                val newSlots = patch.slots.toMutableList()
                newSlots[index] = slot.copy(sourceIsSet = !slot.sourceIsSet)
                onPatchChange(patch.copy(slots = newSlots))
            }
        )
        
        val displayName = if (slot.sourceIsSet) {
            allSets.find { it.id == slot.mandalaSetId }?.name ?: "Pick Set"
        } else {
            slot.selectedMandalaId ?: "Pick Man"
        }
        
        Button(
            onClick = { if (slot.sourceIsSet) onPickSet() else onPickMandala() },
            modifier = Modifier.fillMaxWidth().height(24.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppText.copy(alpha = 0.1f)),
            shape = MaterialTheme.shapes.extraSmall
        ) {
            Text(displayName, style = MaterialTheme.typography.labelSmall, color = AppText, maxLines = 1, textAlign = TextAlign.Center, fontSize = 8.sp)
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // on/off
                Surface(
                    color = if (slot.enabled) AppAccent else AppText.copy(alpha = 0.2f),
                    modifier = Modifier.size(32.dp, 20.dp).clickable {
                        val newSlots = patch.slots.toMutableList()
                        newSlots[index] = slot.copy(enabled = !slot.enabled)
                        onPatchChange(patch.copy(slots = newSlots))
                    },
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(if (slot.enabled) "ON" else "OFF", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                // arrows
                Row {
                    IconButton(
                        onClick = {
                            if (slot.sourceIsSet) {
                                val set = allSets.find { it.id == slot.mandalaSetId }
                                set?.let {
                                    val ids = Json.decodeFromString<List<String>>(it.jsonOrderedMandalaIds)
                                    if (ids.isNotEmpty()) {
                                        val nextIdx = if (slot.currentIndex <= 0) ids.size - 1 else slot.currentIndex - 1
                                        val newSlots = patch.slots.toMutableList()
                                        newSlots[index] = slot.copy(currentIndex = nextIdx)
                                        onPatchChange(patch.copy(slots = newSlots))
                                    }
                                }
                            }
                        },
                        modifier = Modifier.size(18.dp),
                        enabled = slot.sourceIsSet
                    ) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, tint = AppText, modifier = Modifier.size(14.dp))
                    }
                    IconButton(
                        onClick = {
                            if (slot.sourceIsSet) {
                                val set = allSets.find { it.id == slot.mandalaSetId }
                                set?.let {
                                    val ids = Json.decodeFromString<List<String>>(it.jsonOrderedMandalaIds)
                                    if (ids.isNotEmpty()) {
                                        val nextIdx = (slot.currentIndex + 1) % ids.size
                                        val newSlots = patch.slots.toMutableList()
                                        newSlots[index] = slot.copy(currentIndex = nextIdx)
                                        onPatchChange(patch.copy(slots = newSlots))
                                    }
                                }
                            }
                        },
                        modifier = Modifier.size(18.dp),
                        enabled = slot.sourceIsSet
                    ) {
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = AppText, modifier = Modifier.size(14.dp))
                    }
                }
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                KnobView(
                    currentValue = slot.gain.baseValue,
                    onValueChange = {
                        val newSlots = patch.slots.toMutableList()
                        newSlots[index] = slot.copy(gain = slot.gain.copy(baseValue = it))
                        onPatchChange(patch.copy(slots = newSlots))
                    },
                    onInteractionFinished = {},
                    knobSize = 28.dp
                )
                Text("GAIN", style = MaterialTheme.typography.labelSmall, fontSize = 7.sp, color = AppText)
            }
        }
    }
}

@Composable
fun MonitorStrip(
    group: String,
    patch: MixerPatch,
    onPatchChange: (MixerPatch) -> Unit,
    mainRenderer: SpiralRenderer?,
    modifier: Modifier = Modifier
) {
    val groupData = when(group) {
        "A" -> patch.mixerA
        "B" -> patch.mixerB
        else -> patch.mixerF
    }
    
    Column(
        modifier = modifier.padding(1.dp).fillMaxHeight().border(1.dp, AppText.copy(alpha = 0.1f)).padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(Color.Black).border(1.dp, AppText.copy(alpha = 0.2f))) {
            StripPreview(monitorSource = group, patch = patch, mainRenderer = mainRenderer)
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        var modeExpanded by remember { mutableStateOf(false) }
        Box {
            Text(
                text = groupData.mode.name,
                style = MaterialTheme.typography.labelSmall,
                color = AppAccent,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { modeExpanded = true }.padding(2.dp)
            )
            DropdownMenu(expanded = modeExpanded, onDismissRequest = { modeExpanded = false }, containerColor = AppBackground) {
                MixerMode.values().forEach { m ->
                    DropdownMenuItem(text = { Text(m.name, fontSize = 10.sp) }, onClick = {
                        val newGroup = groupData.copy(mode = m)
                        onPatchChange(updateGroup(patch, group, newGroup))
                        modeExpanded = false
                    })
                }
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("BAL", style = MaterialTheme.typography.labelSmall, fontSize = 7.sp, color = AppText)
                KnobView(
                    currentValue = groupData.balance.baseValue,
                    onValueChange = {
                        val newGroup = groupData.copy(balance = groupData.balance.copy(baseValue = it))
                        onPatchChange(updateGroup(patch, group, newGroup))
                    },
                    onInteractionFinished = {},
                    knobSize = 24.dp,
                    focused = groupData.mode != MixerMode.XFADE
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("MIX", style = MaterialTheme.typography.labelSmall, fontSize = 7.sp, color = AppText)
                KnobView(
                    currentValue = groupData.mix.baseValue,
                    onValueChange = {
                        val newGroup = groupData.copy(mix = groupData.mix.copy(baseValue = it))
                        onPatchChange(updateGroup(patch, group, newGroup))
                    },
                    onInteractionFinished = {},
                    knobSize = 24.dp
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            KnobView(
                currentValue = groupData.gain.baseValue,
                onValueChange = {
                    val newGroup = groupData.copy(gain = groupData.gain.copy(baseValue = it))
                    onPatchChange(updateGroup(patch, group, newGroup))
                },
                onInteractionFinished = {},
                knobSize = 32.dp
            )
            Text("GAIN", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = AppText, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun StripPreview(monitorSource: String, patch: MixerPatch, mainRenderer: SpiralRenderer?) {
    AndroidView(
        factory = { ctx ->
            SpiralSurfaceView(ctx).apply {
                setMixerState(patch, monitorSource)
                mainRenderer?.let { mr ->
                    (0..3).forEach { i ->
                        renderer.setSlotSource(i, mr.getSlotSource(i))
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { view ->
            view.setMixerState(patch, monitorSource)
            mainRenderer?.let { mr ->
                (0..3).forEach { i ->
                    view.renderer.setSlotSource(i, mr.getSlotSource(i))
                }
            }
        }
    )
}

@Composable
fun PickerDialog(title: String, items: List<Pair<String, String>>, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium, color = AppBackground, modifier = Modifier.fillMaxHeight(0.7f).fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = AppText)
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    items.forEach { (name, value) ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(value) }.padding(12.dp)) {
                            Text(name, style = MaterialTheme.typography.bodyLarge, color = AppText)
                        }
                    }
                }
            }
        }
    }
}

private fun updateGroup(patch: MixerPatch, group: String, data: MixerGroupData): MixerPatch {
    return when(group) {
        "A" -> patch.copy(mixerA = data)
        "B" -> patch.copy(mixerB = data)
        else -> patch.copy(mixerF = data)
    }
}
