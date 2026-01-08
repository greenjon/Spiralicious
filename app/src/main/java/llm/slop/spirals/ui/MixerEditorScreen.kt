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
import androidx.compose.ui.unit.dp
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
    var focusedMixerGroup by remember { mutableStateOf("F") } // "A", "B", "F"
    var focusedSlotIndex by remember { mutableIntStateOf(0) }
    var monitorSource by remember { mutableStateOf("F") } // "1", "2", "3", "4", "A", "B", "F"

    var showMenu by remember { mutableStateOf(false) }
    var showOpenDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showSetPicker by remember { mutableStateOf(false) }

    val renderer = LocalSpiralRenderer.current
    LaunchedEffect(renderer, currentPatch, monitorSource, allSets, allPatches) {
        if (renderer == null) return@LaunchedEffect
        
        renderer.mixerPatch = currentPatch
        renderer.monitorSource = monitorSource
        
        // Load sources for each slot
        currentPatch.slots.forEachIndexed { index, slot ->
            val setEntity = allSets.find { it.id == slot.mandalaSetId }
            if (setEntity != null) {
                val orderedIds = Json.decodeFromString<List<String>>(setEntity.jsonOrderedMandalaIds)
                val firstMandalaId = orderedIds.firstOrNull()
                if (firstMandalaId != null) {
                    val patchEntity = allPatches.find { it.name == firstMandalaId }
                    patchEntity?.let { pe ->
                        val patchData = PatchMapper.fromJson(pe.jsonSettings)
                        patchData?.let { pd ->
                            val source = renderer.getSlotSource(index)
                            PatchMapper.applyToVisualSource(pd, source)
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            renderer?.mixerPatch = null
            renderer?.monitorSource = "F"
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

            // Main Preview Window
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
            Box(modifier = Modifier.fillMaxWidth().height(40.dp).border(1.dp, AppText.copy(alpha = 0.1f))) {
                val monIdx = monitorSource.toIntOrNull()?.minus(1)
                val targetSource = if (monIdx != null && monIdx in 0..3) {
                    renderer?.getSlotSource(monIdx)
                } else {
                    renderer?.getSlotSource(0)
                }
                
                if (targetSource != null) {
                    OscilloscopeView(history = targetSource.globalAlpha.history, modifier = Modifier.fillMaxSize())
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // MIXER SECTION (A/B/F)
            MixerControlPanel(
                selectedGroup = focusedMixerGroup,
                onGroupSelected = { focusedMixerGroup = it },
                currentPatch = currentPatch,
                onPatchUpdate = { currentPatch = it },
                renderer = renderer
            )

            Spacer(modifier = Modifier.height(12.dp))

            // SLOT SECTION (1-4)
            SlotControlPanel(
                selectedIndex = focusedSlotIndex,
                onIndexSelected = { focusedSlotIndex = it },
                currentPatch = currentPatch,
                onPatchUpdate = { currentPatch = it },
                renderer = renderer,
                allSets = allSets,
                onOpenSetPicker = { showSetPicker = true }
            )
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
}

@Composable
fun MixerControlPanel(
    selectedGroup: String,
    onGroupSelected: (String) -> Unit,
    currentPatch: MixerPatch,
    onPatchUpdate: (MixerPatch) -> Unit,
    renderer: SpiralRenderer?
) {
    val groupData = when(selectedGroup) {
        "A" -> currentPatch.mixerA
        "B" -> currentPatch.mixerB
        else -> currentPatch.mixerF
    }

    Row(modifier = Modifier.fillMaxWidth().height(140.dp)) {
        // Left Side: Mini Preview + Buttons
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(80.dp).background(Color.Black).border(1.dp, AppText.copy(alpha = 0.2f))) {
                // For Phase 1, we show the source of slot 1 for A, slot 3 for B, or empty for Final in mini monitors
                val miniSourceIndex = when(selectedGroup) {
                    "A" -> 0
                    "B" -> 2
                    else -> null
                }
                if (miniSourceIndex != null) {
                    MiniMandalaPreview(renderer?.getSlotSource(miniSourceIndex))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("A", "B", "F").forEach { g ->
                    val isSel = selectedGroup == g
                    Surface(
                        color = if (isSel) AppAccent else AppBackground,
                        modifier = Modifier.size(24.dp).clickable { onGroupSelected(g) }.border(1.dp, AppText.copy(alpha = 0.1f)),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(g, style = MaterialTheme.typography.labelSmall, color = if (isSel) Color.White else AppText)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Right Side: Knobs + Mode
        Column(modifier = Modifier.weight(3f)) {
            var modeExpanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { modeExpanded = true }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(groupData.mode.name, style = MaterialTheme.typography.labelSmall, color = AppText)
                }
                DropdownMenu(expanded = modeExpanded, onDismissRequest = { modeExpanded = false }, containerColor = AppBackground) {
                    MixerMode.values().forEach { m ->
                        DropdownMenuItem(text = { Text(m.name, style = MaterialTheme.typography.labelSmall) }, onClick = {
                            val newGroup = groupData.copy(mode = m)
                            onPatchUpdate(updateGroup(currentPatch, selectedGroup, newGroup))
                            modeExpanded = false
                        })
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                val balDisabled = groupData.mode == MixerMode.XFADE
                MixerKnob(
                    label = "Bal",
                    value = groupData.balance.baseValue,
                    onValueChange = { newVal ->
                        val newGroup = groupData.copy(balance = groupData.balance.copy(baseValue = newVal))
                        onPatchUpdate(updateGroup(currentPatch, selectedGroup, newGroup))
                    },
                    modifier = if (balDisabled) Modifier.alpha(0.3f) else Modifier,
                    enabled = !balDisabled
                )

                MixerKnob(
                    label = "Mix",
                    value = groupData.mix.baseValue,
                    onValueChange = { newVal ->
                        val newGroup = groupData.copy(mix = groupData.mix.copy(baseValue = newVal))
                        onPatchUpdate(updateGroup(currentPatch, selectedGroup, newGroup))
                    }
                )

                MixerKnob(
                    label = "Gain",
                    value = groupData.gain.baseValue,
                    onValueChange = { newVal ->
                        val newGroup = groupData.copy(gain = groupData.gain.copy(baseValue = newVal))
                        onPatchUpdate(updateGroup(currentPatch, selectedGroup, newGroup))
                    }
                )
            }
        }
    }
}

@Composable
fun SlotControlPanel(
    selectedIndex: Int,
    onIndexSelected: (Int) -> Unit,
    currentPatch: MixerPatch,
    onPatchUpdate: (MixerPatch) -> Unit,
    renderer: SpiralRenderer?,
    allSets: List<MandalaSetEntity>,
    onOpenSetPicker: () -> Unit
) {
    val slot = currentPatch.slots[selectedIndex]

    Row(modifier = Modifier.fillMaxWidth().height(160.dp)) {
        // Left Side: Mini Preview + Buttons
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(80.dp).background(Color.Black).border(1.dp, AppText.copy(alpha = 0.2f))) {
                MiniMandalaPreview(renderer?.getSlotSource(selectedIndex))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0..3).forEach { i ->
                    val isSel = selectedIndex == i
                    Surface(
                        color = if (isSel) AppAccent else AppBackground,
                        modifier = Modifier.size(24.dp).clickable { onIndexSelected(i) }.border(1.dp, AppText.copy(alpha = 0.1f)),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = if (isSel) Color.White else AppText)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Right Side: Controls
        Column(modifier = Modifier.weight(3f)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                var sourceExpanded by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { sourceExpanded = true }) {
                        Text(if (slot.sourceIsSet) "Mandala Set" else "Mandala", style = MaterialTheme.typography.labelSmall, color = AppAccent)
                    }
                    DropdownMenu(expanded = sourceExpanded, onDismissRequest = { sourceExpanded = false }, containerColor = AppBackground) {
                        DropdownMenuItem(text = { Text("Mandala") }, onClick = { 
                            onPatchUpdate(updateSlot(currentPatch, selectedIndex, slot.copy(sourceIsSet = false)))
                            sourceExpanded = false 
                        })
                        DropdownMenuItem(text = { Text("Mandala Set") }, onClick = { 
                            onPatchUpdate(updateSlot(currentPatch, selectedIndex, slot.copy(sourceIsSet = true)))
                            sourceExpanded = false 
                        })
                    }
                }

                Switch(
                    checked = slot.enabled,
                    onCheckedChange = { onPatchUpdate(updateSlot(currentPatch, selectedIndex, slot.copy(enabled = it))) },
                    modifier = Modifier.graphicsLayer { scaleX = 0.7f; scaleY = 0.7f }
                )
            }

            if (slot.sourceIsSet) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val setName = allSets.find { it.id == slot.mandalaSetId }?.name ?: "No Set"
                    Text(setName, style = MaterialTheme.typography.bodySmall, color = AppText, modifier = Modifier.weight(1f), maxLines = 1)
                    IconButton(onClick = { /* Previous */ }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null) }
                    IconButton(onClick = { /* Next */ }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.KeyboardArrowRight, contentDescription = null) }
                }
                OutlinedButton(onClick = onOpenSetPicker, modifier = Modifier.height(28.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Pick Set", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                KnobView(
                    currentValue = slot.gain.baseValue,
                    onValueChange = { onPatchUpdate(updateSlot(currentPatch, selectedIndex, slot.copy(gain = slot.gain.copy(baseValue = it)))) },
                    onInteractionFinished = {},
                    knobSize = 40.dp,
                    showValue = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Gain", style = MaterialTheme.typography.labelSmall, color = AppText)
            }
        }
    }
}

@Composable
fun MiniMandalaPreview(visualSource: MandalaVisualSource?, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            SpiralSurfaceView(ctx).apply {
                visualSource?.let { setVisualSource(it) }
            }
        },
        modifier = modifier.fillMaxSize(),
        update = { view ->
            visualSource?.let { view.setVisualSource(it) }
        }
    )
}

@Composable
fun MixerKnob(label: String, value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        KnobView(
            currentValue = value,
            onValueChange = if (enabled) onValueChange else ({}),
            onInteractionFinished = {},
            knobSize = 40.dp,
            showValue = true,
            focused = enabled
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = AppText)
    }
}

private fun updateGroup(patch: MixerPatch, group: String, data: MixerGroupData): MixerPatch {
    return when(group) {
        "A" -> patch.copy(mixerA = data)
        "B" -> patch.copy(mixerB = data)
        else -> patch.copy(mixerF = data)
    }
}

private fun updateSlot(patch: MixerPatch, index: Int, data: MixerSlotData): MixerPatch {
    val newSlots = patch.slots.toMutableList()
    newSlots[index] = data
    return patch.copy(slots = newSlots)
}
