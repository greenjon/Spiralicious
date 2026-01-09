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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
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
import llm.slop.spirals.cv.CvModulator
import llm.slop.spirals.cv.ModulatableParameter

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
    var viewSet1A2 by remember { mutableStateOf(true) }
    
    // UI state for focusing parameters for CV patching
    var focusedParameterId by remember { mutableStateOf("G1") } // e.g. G1, G2, G3, G4, MA_BAL, MB_GAIN, etc.

    var showMenu by remember { mutableStateOf(false) }
    var showOpenDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showSetPickerForSlot by remember { mutableStateOf<Int?>(null) }
    var showMandalaPickerForSlot by remember { mutableStateOf<Int?>(null) }

    val mainRenderer = LocalSpiralRenderer.current

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
                        val safeIndex = slot.currentIndex.baseValue.toInt().coerceIn(0, orderedIds.size - 1)
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
            val monIdx = monitorSource.toIntOrNull()?.minus(1)
            val targetSource = if (monIdx != null && monIdx in 0..3) {
                mainRenderer?.getSlotSource(monIdx)
            } else {
                mainRenderer?.getSlotSource(0)
            }
            
            if (targetSource != null) {
                OscilloscopeView(history = targetSource.globalAlpha.history, modifier = Modifier.fillMaxSize())
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
            if (viewSet1A2) {
                SourceStrip(0, currentPatch, { currentPatch = it }, mainRenderer, { showSetPickerForSlot = 0 }, { showMandalaPickerForSlot = 0 }, allSets, "1", Alignment.TopEnd, focusedParameterId, { focusedParameterId = it }, Modifier.weight(1f))
                MonitorStrip("A", currentPatch, { currentPatch = it }, mainRenderer, true, viewSet1A2, { viewSet1A2 = it }, focusedParameterId, { focusedParameterId = it }, Modifier.weight(1f))
                SourceStrip(1, currentPatch, { currentPatch = it }, mainRenderer, { showSetPickerForSlot = 1 }, { showMandalaPickerForSlot = 1 }, allSets, "2", Alignment.TopStart, focusedParameterId, { focusedParameterId = it }, Modifier.weight(1f))
            } else {
                SourceStrip(2, currentPatch, { currentPatch = it }, mainRenderer, { showSetPickerForSlot = 2 }, { showMandalaPickerForSlot = 2 }, allSets, "3", Alignment.TopEnd, focusedParameterId, { focusedParameterId = it }, Modifier.weight(1f))
                MonitorStrip("B", currentPatch, { currentPatch = it }, mainRenderer, true, viewSet1A2, { viewSet1A2 = it }, focusedParameterId, { focusedParameterId = it }, Modifier.weight(1f))
                SourceStrip(3, currentPatch, { currentPatch = it }, mainRenderer, { showSetPickerForSlot = 3 }, { showMandalaPickerForSlot = 3 }, allSets, "4", Alignment.TopStart, focusedParameterId, { focusedParameterId = it }, Modifier.weight(1f))
            }
            MonitorStrip("F", currentPatch, { currentPatch = it }, mainRenderer, false, viewSet1A2, {}, focusedParameterId, { focusedParameterId = it }, Modifier.weight(1f))
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

    // Dialogs...
    if (showSetPickerForSlot != null) {
        PickerDialog("Select Mandala Set", allSets.map { it.name to it.id }, { id ->
            val idx = showSetPickerForSlot!!
            val newSlots = currentPatch.slots.toMutableList()
            newSlots[idx] = newSlots[idx].copy(mandalaSetId = id, currentIndex = ModulatableParameterData(0f), sourceIsSet = true)
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
    identity: String,
    onOffAlignment: Alignment,
    focusedId: String,
    onFocusChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val slot = patch.slots[index]
    val slotId = "S${index + 1}"
    val gainId = "G${index + 1}"
    val prevNextId = "PN${index + 1}"

    Column(
        modifier = modifier.padding(1.dp).wrapContentHeight().border(1.dp, AppText.copy(alpha = 0.1f)).padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            text = "Source $identity",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = AppText,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 0.dp)
        )

        // Preview Window
        Box(modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(Color.Black)
            .border(1.dp, AppText.copy(alpha = 0.2f))
            .clickable {
                val newSlots = patch.slots.toMutableList()
                newSlots[index] = slot.copy(enabled = !slot.enabled)
                onPatchChange(patch.copy(slots = newSlots))
            }
        ) {
            if (slot.enabled) {
                StripPreview(monitorSource = "${index + 1}", patch = patch, mainRenderer = mainRenderer)
                
                Text(
                    text = "ON",
                    modifier = Modifier.align(onOffAlignment).padding(horizontal = 2.dp, vertical = 0.dp),
                    style = TextStyle(color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, shadow = Shadow(color = Color.Black, blurRadius = 3f))
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "OFF",
                        style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, shadow = Shadow(color = Color.Black, blurRadius = 3f))
                    )
                    Text(
                        text = "Tap to enable",
                        style = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 8.sp, fontWeight = FontWeight.Normal, shadow = Shadow(color = Color.Black, blurRadius = 3f))
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Source Label (Focusable)
        Text(
            text = if (slot.sourceIsSet) "Mandala Set" else "Mandala",
            style = MaterialTheme.typography.labelSmall,
            color = if (focusedId == slotId) AppAccent else AppText.copy(alpha = 0.7f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable {
                onFocusChange(slotId)
                val newSlots = patch.slots.toMutableList()
                newSlots[index] = slot.copy(sourceIsSet = !slot.sourceIsSet)
                onPatchChange(patch.copy(slots = newSlots))
            }.padding(2.dp)
        )
        
        val displayName = if (slot.sourceIsSet) {
            allSets.find { it.id == slot.mandalaSetId }?.name ?: "Pick Set"
        } else {
            slot.selectedMandalaId ?: "Pick Man"
        }
        
        Button(
            onClick = { if (slot.sourceIsSet) onPickSet() else onPickMandala() },
            modifier = Modifier.fillMaxWidth().height(28.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppText.copy(alpha = 0.1f)),
            shape = MaterialTheme.shapes.extraSmall
        ) {
            Text(displayName, style = MaterialTheme.typography.labelSmall, color = AppText, maxLines = 1, textAlign = TextAlign.Center, fontSize = 8.sp)
        }
        
        Spacer(modifier = Modifier.height(4.dp))

        // Arrows Row (Focusable)
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onFocusChange(prevNextId) },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val arrowColor = if (focusedId == prevNextId) AppAccent else AppText
            IconButton(
                onClick = {
                    onFocusChange(prevNextId)
                    if (slot.sourceIsSet) {
                        val set = allSets.find { it.id == slot.mandalaSetId }
                        set?.let {
                            val ids = Json.decodeFromString<List<String>>(it.jsonOrderedMandalaIds)
                            if (ids.isNotEmpty()) {
                                val nextIdx = if (slot.currentIndex.baseValue <= 0) ids.size - 1 else slot.currentIndex.baseValue.toInt() - 1
                                val newSlots = patch.slots.toMutableList()
                                newSlots[index] = slot.copy(currentIndex = slot.currentIndex.copy(baseValue = nextIdx.toFloat()))
                                onPatchChange(patch.copy(slots = newSlots))
                            }
                        }
                    }
                },
                modifier = Modifier.size(36.dp),
                enabled = slot.sourceIsSet
            ) { Icon(Icons.Default.KeyboardArrowLeft, null, tint = if (slot.sourceIsSet) arrowColor else arrowColor.copy(alpha = 0.2f), modifier = Modifier.size(32.dp)) }
            
            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    onFocusChange(prevNextId)
                    if (slot.sourceIsSet) {
                        val set = allSets.find { it.id == slot.mandalaSetId }
                        set?.let {
                            val ids = Json.decodeFromString<List<String>>(it.jsonOrderedMandalaIds)
                            if (ids.isNotEmpty()) {
                                val nextIdx = (slot.currentIndex.baseValue.toInt() + 1) % ids.size
                                val newSlots = patch.slots.toMutableList()
                                newSlots[index] = slot.copy(currentIndex = slot.currentIndex.copy(baseValue = nextIdx.toFloat()))
                                onPatchChange(patch.copy(slots = newSlots))
                            }
                        }
                    }
                },
                modifier = Modifier.size(36.dp),
                enabled = slot.sourceIsSet
            ) { Icon(Icons.Default.KeyboardArrowRight, null, tint = if (slot.sourceIsSet) arrowColor else arrowColor.copy(alpha = 0.2f), modifier = Modifier.size(32.dp)) }
        }
        
        // Gain Knob (Focusable)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onFocusChange(gainId) }) {
            KnobView(
                currentValue = slot.gain.baseValue,
                onValueChange = {
                    onFocusChange(gainId)
                    val newSlots = patch.slots.toMutableList()
                    newSlots[index] = slot.copy(gain = slot.gain.copy(baseValue = it))
                    onPatchChange(patch.copy(slots = newSlots))
                },
                onInteractionFinished = {},
                knobSize = 44.dp,
                showValue = true,
                focused = focusedId == gainId
            )
            Text("GAIN", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = if (focusedId == gainId) AppAccent else AppText)
        }
    }
}

@Composable
fun MonitorStrip(
    group: String,
    patch: MixerPatch,
    onPatchChange: (MixerPatch) -> Unit,
    mainRenderer: SpiralRenderer?,
    hasToggle: Boolean,
    viewSet1A2: Boolean,
    onToggleViewSet: (Boolean) -> Unit,
    focusedId: String,
    onFocusChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val groupData = when(group) {
        "A" -> patch.mixerA
        "B" -> patch.mixerB
        else -> patch.mixerF
    }
    
    val headerText = when(group) {
        "A" -> "Mixer A"
        "B" -> "Mixer B"
        else -> "Final Mixer"
    }

    val modeId = "M${group}_MODE"
    val balId = "M${group}_BAL"
    val mixId = "M${group}_MIX"
    val gainId = "M${group}_GAIN"
    
    Column(
        modifier = modifier.padding(1.dp).wrapContentHeight().border(1.dp, AppText.copy(alpha = 0.1f)).padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            text = headerText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = AppText,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 0.dp)
        )

        Box(modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(Color.Black)
            .border(1.dp, AppText.copy(alpha = 0.2f))
            .clickable {
                if (group == "A") onToggleViewSet(false)
                else if (group == "B") onToggleViewSet(true)
            }
        ) {
            StripPreview(monitorSource = group, patch = patch, mainRenderer = mainRenderer)

            if (hasToggle) {
                Row(modifier = Modifier.align(Alignment.TopCenter), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text(text = "1/2", style = TextStyle(color = if (viewSet1A2) AppAccent else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, shadow = Shadow(color = Color.Black, blurRadius = 3f)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "3/4", style = TextStyle(color = if (!viewSet1A2) AppAccent else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, shadow = Shadow(color = Color.Black, blurRadius = 3f)))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Mode Selector (Focusable)
        var modeExpanded by remember { mutableStateOf(false) }
        Box(modifier = Modifier.clickable { onFocusChange(modeId) }) {
            Text(
                text = MixerMode.values()[groupData.mode.baseValue.toInt()].name,
                style = MaterialTheme.typography.labelSmall,
                color = if (focusedId == modeId) AppAccent else AppText.copy(alpha = 0.7f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onFocusChange(modeId); modeExpanded = true }.padding(2.dp)
            )
            DropdownMenu(expanded = modeExpanded, onDismissRequest = { modeExpanded = false }, containerColor = AppBackground) {
                MixerMode.values().forEachIndexed { index, m ->
                    DropdownMenuItem(text = { Text(m.name, fontSize = 11.sp) }, onClick = {
                        val newGroup = groupData.copy(mode = groupData.mode.copy(baseValue = index.toFloat()))
                        onPatchChange(updateGroup(patch, group, newGroup))
                        modeExpanded = false
                    })
                }
            }
        }
        
        Spacer(modifier = Modifier.height(2.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            // BAL (Focusable)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onFocusChange(balId) }) {
                Text("BAL", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = if (focusedId == balId) AppAccent else AppText)
                KnobView(
                    currentValue = groupData.balance.baseValue,
                    onValueChange = {
                        onFocusChange(balId)
                        val newGroup = groupData.copy(balance = groupData.balance.copy(baseValue = it))
                        onPatchChange(updateGroup(patch, group, newGroup))
                    },
                    onInteractionFinished = {},
                    knobSize = 44.dp,
                    showValue = true,
                    focused = focusedId == balId
                )
            }
            // MIX (Focusable)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onFocusChange(mixId) }) {
                Text("MIX", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = if (focusedId == mixId) AppAccent else AppText)
                KnobView(
                    currentValue = groupData.mix.baseValue,
                    onValueChange = {
                        onFocusChange(mixId)
                        val newGroup = groupData.copy(mix = groupData.mix.copy(baseValue = it))
                        onPatchChange(updateGroup(patch, group, newGroup))
                    },
                    onInteractionFinished = {},
                    knobSize = 44.dp,
                    showValue = true,
                    focused = focusedId == mixId
                )
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // GAIN (Focusable)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onFocusChange(gainId) }) {
            KnobView(
                currentValue = groupData.gain.baseValue,
                onValueChange = {
                    onFocusChange(gainId)
                    val newGroup = groupData.copy(gain = groupData.gain.copy(baseValue = it))
                    onPatchChange(updateGroup(patch, group, newGroup))
                },
                onInteractionFinished = {},
                knobSize = 44.dp,
                showValue = true,
                focused = focusedId == gainId
            )
            Text("GAIN", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = if (focusedId == gainId) AppAccent else AppText, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MixerCvEditor(
    patch: MixerPatch,
    focusedId: String,
    onPatchUpdate: (MixerPatch) -> Unit
) {
    val scrollState = rememberScrollState()
    
    // Resolve the focused parameter from the MixerPatch
    val focusedParamData = remember(patch, focusedId) {
        when {
            focusedId.startsWith("G") -> { // Gain knobs S1-S4
                val idx = (focusedId.last().digitToIntOrNull() ?: 1) - 1
                if (idx in 0..3) patch.slots[idx].gain else null
            }
            focusedId.startsWith("PN") -> { // Prev/Next S1-S4 (mapped to currentIndex)
                val idx = (focusedId.last().digitToIntOrNull() ?: 1) - 1
                if (idx in 0..3) patch.slots[idx].currentIndex else null
            }
            focusedId.startsWith("S") -> { // Slot labels (none for now or could map to something)
                null
            }
            focusedId.startsWith("MA_") -> { // Mixer A params
                when(focusedId.removePrefix("MA_")) {
                    "MODE" -> patch.mixerA.mode
                    "BAL" -> patch.mixerA.balance
                    "MIX" -> patch.mixerA.mix
                    "GAIN" -> patch.mixerA.gain
                    else -> null
                }
            }
            focusedId.startsWith("MB_") -> { // Mixer B params
                when(focusedId.removePrefix("MB_")) {
                    "MODE" -> patch.mixerB.mode
                    "BAL" -> patch.mixerB.balance
                    "MIX" -> patch.mixerB.mix
                    "GAIN" -> patch.mixerB.gain
                    else -> null
                }
            }
            focusedId.startsWith("MF_") -> { // Mixer F params
                when(focusedId.removePrefix("MF_")) {
                    "MODE" -> patch.mixerF.mode
                    "BAL" -> patch.mixerF.balance
                    "MIX" -> patch.mixerF.mix
                    "GAIN" -> patch.mixerF.gain
                    else -> null
                }
            }
            else -> null
        }
    }

    if (focusedParamData == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Select a parameter to patch CV", color = AppText.copy(alpha = 0.5f))
        }
        return
    }

    // Since our Mixer Models use ModulatableParameterData (serializable lists), 
    // we bridge them to the UI by creating a temporary ModulatableParameter object 
    // then syncing it back.
    val tempParam = remember(focusedId, focusedParamData) {
        ModulatableParameter(baseValue = focusedParamData.baseValue).apply {
            modulators.addAll(focusedParamData.modulators)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Column(modifier = Modifier.weight(1f).verticalScroll(scrollState)) {
            tempParam.modulators.forEachIndexed { index, mod ->
                ModulatorRow(
                    mod = mod,
                    onUpdate = { updatedMod ->
                        tempParam.modulators[index] = updatedMod
                        syncMixerParam(patch, focusedId, tempParam, onPatchUpdate)
                    },
                    onInteractionFinished = { syncMixerParam(patch, focusedId, tempParam, onPatchUpdate) },
                    onRemove = {
                        tempParam.modulators.removeAt(index)
                        syncMixerParam(patch, focusedId, tempParam, onPatchUpdate)
                    }
                )
                HorizontalDivider(color = AppText.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))
            }

            ModulatorRow(
                mod = null,
                onUpdate = { newMod ->
                    tempParam.modulators.add(newMod)
                    syncMixerParam(patch, focusedId, tempParam, onPatchUpdate)
                },
                onInteractionFinished = {},
                onRemove = {}
            )
            
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

private fun syncMixerParam(patch: MixerPatch, id: String, param: ModulatableParameter, onUpdate: (MixerPatch) -> Unit) {
    val data = ModulatableParameterData(baseValue = param.baseValue, modulators = param.modulators.toList())
    val newPatch = when {
        id.startsWith("G") -> {
            val idx = (id.last().digitToIntOrNull() ?: 1) - 1
            val newSlots = patch.slots.toMutableList()
            newSlots[idx] = newSlots[idx].copy(gain = data)
            patch.copy(slots = newSlots)
        }
        id.startsWith("PN") -> {
            val idx = (id.last().digitToIntOrNull() ?: 1) - 1
            val newSlots = patch.slots.toMutableList()
            newSlots[idx] = newSlots[idx].copy(currentIndex = data)
            patch.copy(slots = newSlots)
        }
        id.startsWith("MA_") -> {
            val group = patch.mixerA
            val newGroup = when(id.removePrefix("MA_")) {
                "MODE" -> group.copy(mode = data)
                "BAL" -> group.copy(balance = data)
                "MIX" -> group.copy(mix = data)
                "GAIN" -> group.copy(gain = data)
                else -> group
            }
            patch.copy(mixerA = newGroup)
        }
        id.startsWith("MB_") -> {
            val group = patch.mixerB
            val newGroup = when(id.removePrefix("MB_")) {
                "MODE" -> group.copy(mode = data)
                "BAL" -> group.copy(balance = data)
                "MIX" -> group.copy(mix = data)
                "GAIN" -> group.copy(gain = data)
                else -> group
            }
            patch.copy(mixerB = newGroup)
        }
        id.startsWith("MF_") -> {
            val group = patch.mixerF
            val newGroup = when(id.removePrefix("MF_")) {
                "MODE" -> group.copy(mode = data)
                "BAL" -> group.copy(balance = data)
                "MIX" -> group.copy(mix = data)
                "GAIN" -> group.copy(gain = data)
                else -> group
            }
            patch.copy(mixerF = newGroup)
        }
        else -> patch
    }
    onUpdate(newPatch)
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
