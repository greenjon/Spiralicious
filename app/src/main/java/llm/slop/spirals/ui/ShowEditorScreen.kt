package llm.slop.spirals.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import llm.slop.spirals.*
import llm.slop.spirals.models.*
import llm.slop.spirals.ui.components.PatchManagerOverlay
import llm.slop.spirals.ui.components.SetChipList
import llm.slop.spirals.ui.components.KnobView
import llm.slop.spirals.ui.theme.AppBackground
import llm.slop.spirals.ui.theme.AppAccent
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowEditorScreen(
    vm: MandalaViewModel = viewModel(),
    onNavigateToMixerEditor: (Boolean) -> Unit,
    previewContent: @Composable () -> Unit,
    showManager: Boolean = false,
    onHideManager: () -> Unit = {}
) {
    val allMixerPatches by vm.allMixerPatches.collectAsState(initial = emptyList())
    val allShowPatches by vm.allShowPatches.collectAsState(initial = emptyList())
    val currentShowIndex by vm.currentShowIndex.collectAsState()

    // Initialize from layer data
    val navStack by vm.navStack.collectAsState()
    val layer = navStack.lastOrNull { it.type == LayerType.SHOW }
    
    var currentShow by remember { 
        mutableStateOf(layer?.data as? ShowPatch ?: ShowPatch(name = "New Show")) 
    }

    val mainRenderer = LocalSpiralRenderer.current

    // Update local state if nav data changes
    LaunchedEffect(layer?.data) {
        (layer?.data as? ShowPatch)?.let {
            if (it.id != currentShow.id) {
                currentShow = it
            }
        }
    }

    // Apply the active mixer from the show sequence to the renderer
    LaunchedEffect(currentShowIndex, currentShow.mixerNames, allMixerPatches) {
        if (currentShow.mixerNames.isNotEmpty()) {
            val safeIndex = currentShowIndex.coerceIn(0, currentShow.mixerNames.size - 1)
            val mixerName = currentShow.mixerNames[safeIndex]
            val mixerEntity = allMixerPatches.find { it.name == mixerName }
            mixerEntity?.let { entity ->
                try {
                    val mixerPatch = Json.decodeFromString<MixerPatch>(entity.jsonSettings)
                    mainRenderer?.mixerPatch = mixerPatch
                } catch (e: Exception) {}
            }
        }
    }

    // Capture work-in-progress
    LaunchedEffect(currentShow) {
        val index = navStack.indexOfLast { it.type == LayerType.SHOW }
        if (index != -1) {
            vm.updateLayerData(index, currentShow)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground)
        ) {
            // 1. Preview Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16 / 9f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                previewContent()
                
                // Show Index Indicator
                if (currentShow.mixerNames.isNotEmpty()) {
                    Surface(
                        color = AppBackground.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = "${currentShowIndex + 1} / ${currentShow.mixerNames.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppAccent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Performance Controls (Prev/Next)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PREV", style = MaterialTheme.typography.labelSmall, color = AppAccent)
                    KnobView(
                        baseValue = currentShow.prevTrigger.baseValue,
                        onValueChange = { currentShow = currentShow.copy(prevTrigger = currentShow.prevTrigger.copy(baseValue = it)) },
                        onInteractionFinished = {
                            if (currentShow.prevTrigger.baseValue > 0.5f) {
                                vm.triggerPrevMixer(currentShow.mixerNames.size)
                            }
                        }
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NEXT", style = MaterialTheme.typography.labelSmall, color = AppAccent)
                    KnobView(
                        baseValue = currentShow.nextTrigger.baseValue,
                        onValueChange = { currentShow = currentShow.copy(nextTrigger = currentShow.nextTrigger.copy(baseValue = it)) },
                        onInteractionFinished = {
                            if (currentShow.nextTrigger.baseValue > 0.5f) {
                                vm.triggerNextMixer(currentShow.mixerNames.size)
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Mixer sequence (Set Editor style)
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    var showMixerPicker by remember { mutableStateOf(false) }
                    
                    Button(onClick = { showMixerPicker = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Mixer")
                    }

                    if (showMixerPicker) {
                        PickerDialog(
                            title = "Add Mixer to Show",
                            items = allMixerPatches.map { it.name to it.name },
                            onSelect = { name ->
                                currentShow = currentShow.copy(mixerNames = currentShow.mixerNames + name)
                                showMixerPicker = false
                            },
                            onDismiss = { showMixerPicker = false },
                            onCreateNew = {
                                onNavigateToMixerEditor(true)
                                showMixerPicker = false
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                SetChipList(
                    chipIds = currentShow.mixerNames,
                    onChipTapped = { name ->
                        val idx = currentShow.mixerNames.indexOf(name)
                        if (idx != -1) vm.jumpToShowIndex(idx)
                    },
                    onChipReordered = { newList ->
                        currentShow = currentShow.copy(mixerNames = newList)
                    }
                )
            }
        }

        if (showManager) {
            PatchManagerOverlay(
                title = "Manage Shows",
                patches = allShowPatches.map { it.name to it.jsonSettings },
                selectedId = Json.encodeToString<ShowPatch>(currentShow),
                onSelect = { json ->
                    try {
                        val selected = Json.decodeFromString<ShowPatch>(json)
                        currentShow = selected
                        val idx = navStack.indexOfLast { it.type == LayerType.SHOW }
                        if (idx != -1) vm.updateLayerName(idx, selected.name)
                    } catch (e: Exception) {}
                },
                onRename = { newName ->
                    vm.renamePatch(LayerType.SHOW, currentShow.name, newName)
                },
                onClone = { json ->
                    try {
                        val p = Json.decodeFromString<ShowPatch>(json)
                        vm.cloneSavedPatch(LayerType.SHOW, p.name)
                    } catch (e: Exception) {}
                },
                onDelete = { json ->
                    try {
                        val p = Json.decodeFromString<ShowPatch>(json)
                        vm.deleteSavedPatch(LayerType.SHOW, p.name)
                    } catch (e: Exception) {}
                },
                onClose = onHideManager
            )
        }
    }
}
