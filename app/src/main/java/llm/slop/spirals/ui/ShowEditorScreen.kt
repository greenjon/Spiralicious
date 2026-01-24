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
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowEditorScreen(
    vm: MandalaViewModel = viewModel(),
    onNavigateToMixerEditor: (Boolean) -> Unit,
    previewContent: @Composable () -> Unit,
    showManager: Boolean = false,
    onHideManager: () -> Unit = {}
) {
    val allRandomSets by vm.allRandomSets.collectAsState(initial = emptyList())
    val allShowPatches by vm.allShowPatches.collectAsState(initial = emptyList())
    val currentShowIndex by vm.currentShowIndex.collectAsState()

    // Initialize from layer data
    val navStack by vm.navStack.collectAsState()
    val layer = navStack.lastOrNull { it.type == LayerType.SHOW }
    
    var currentShow by remember { 
        mutableStateOf((layer?.data as? ShowLayerContent)?.show ?: ShowPatch(name = "New Show")) 
    }

    val mainRenderer = LocalSpiralRenderer.current
    val generator = remember { RandomSetGenerator(vm.getApplication()) }

    // Update local state if nav data changes
    LaunchedEffect(layer?.data) {
        (layer?.data as? ShowLayerContent)?.show?.let {
            if (it.id != currentShow.id) {
                currentShow = it
            }
        }
    }

    // Apply the active random set from the show sequence to the renderer
    LaunchedEffect(currentShowIndex, currentShow.randomSetIds, allRandomSets, navStack) {
        Log.d("ShowEditor", "Syncing preview: Index=$currentShowIndex, ShowID=${currentShow.id}, RSetCount=${currentShow.randomSetIds.size}")
        
        if (currentShow.randomSetIds.isNotEmpty()) {
            val safeIndex = currentShowIndex.coerceIn(0, currentShow.randomSetIds.size - 1)
            val randomSetId = currentShow.randomSetIds[safeIndex]
            
            // Fallback logic: check DB first, then NavStack for unsaved/newly created sets
            val randomSetEntity = allRandomSets.find { it.id == randomSetId }
            val randomSet = if (randomSetEntity != null) {
                try { Json.decodeFromString<RandomSet>(randomSetEntity.jsonSettings) } catch (e: Exception) { null }
            } else {
                // Fallback to NavStack - handles cases where DB hasn't updated yet or item is being edited
                navStack.find { it.id == randomSetId && it.type == LayerType.RANDOM_SET }?.let { l ->
                    (l.data as? RandomSetLayerContent)?.randomSet
                }
            }

            Log.d("ShowEditor", "Found RandomSet: ${randomSet?.name ?: "NULL"} (ID=$randomSetId), Renderer=${if (mainRenderer != null) "Ready" else "NULL"}")

            if (randomSet != null && mainRenderer != null) {
                try {
                    // Create a shell MixerPatch that points to this RandomSet
                    val tempMixer = createTemporaryMixerFromRandomSet(randomSet)
                    mainRenderer.mixerPatch = tempMixer
                    mainRenderer.monitorSource = "F"
                    
                    // CRITICAL: Generate the actual mandala configuration for the renderer's visual source.
                    // createTemporaryMixerFromRandomSet puts the RandomSet in slot 0.
                    generator.generateFromRSet(randomSet, mainRenderer.getSlotSource(0))
                    
                    Log.d("ShowEditor", "Renderer updated with RandomSet: ${randomSet.name}")
                } catch (e: Exception) {
                    Log.e("ShowEditor", "Error applying RandomSet to renderer", e)
                }
            } else if (mainRenderer != null) {
                // Clear to avoid stale visuals if we have no valid data for this index
                mainRenderer.mixerPatch = null
            }
        } else {
            // Clear mixerPatch when there are no random sets to prevent stale previews
            mainRenderer?.mixerPatch = null
        }
    }

    // Capture work-in-progress
    LaunchedEffect(currentShow) {
        val index = navStack.indexOfLast { it.type == LayerType.SHOW }
        if (index != -1) {
            vm.updateLayerData(index, ShowLayerContent(currentShow))
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
                if (currentShow.randomSetIds.isNotEmpty()) {
                    Surface(
                        color = AppBackground.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = "${currentShowIndex + 1} / ${currentShow.randomSetIds.size}",
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
                                vm.triggerPrevMixer(currentShow.randomSetIds.size)
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
                                vm.triggerNextMixer(currentShow.randomSetIds.size)
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. RandomSet sequence (Set Editor style)
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    var showRandomSetPicker by remember { mutableStateOf(false) }

                    Button(onClick = { showRandomSetPicker = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add RandomSet")
                    }

                    if (showRandomSetPicker) {
                        PickerDialog(
                            title = "Add RandomSet to Show",
                            items = allRandomSets.map { it.name to it.id },
                            onSelect = { randomSetId ->
                                currentShow = currentShow.copy(randomSetIds = currentShow.randomSetIds + randomSetId)
                                showRandomSetPicker = false
                            },
                            onDismiss = { showRandomSetPicker = false },
                            onCreateNew = {
                                vm.createAndPushLayer(LayerType.RANDOM_SET)
                                showRandomSetPicker = false
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Create mapping from RandomSet IDs to (name, id) pairs for display
                val randomSetItems = remember(currentShow.randomSetIds, allRandomSets) {
                    currentShow.randomSetIds.mapNotNull { randomSetId ->
                        val randomSetEntity = allRandomSets.find { it.id == randomSetId }
                        randomSetEntity?.let { it.name to it.id }
                    }
                }

                SetChipList(
                    chipItems = randomSetItems,
                    onChipTapped = { randomSetId ->
                        val idx = currentShow.randomSetIds.indexOf(randomSetId)
                        if (idx != -1) vm.jumpToShowIndex(idx)
                    },
                    onChipReordered = { newList ->
                        // newList contains IDs in the new order
                        currentShow = currentShow.copy(randomSetIds = newList)
                    }
                )
            }
        }

        if (showManager) {
            val randomSetIds = currentShow.randomSetIds
            val currentRandomSetIndex = if (randomSetIds.isNotEmpty()) currentShowIndex.coerceIn(0, randomSetIds.size - 1) else 0

            PatchManagerOverlay(
                title = "Manage Shows",
                patches = allShowPatches.map { it.name to it.jsonSettings },
                selectedId = Json.encodeToString<ShowPatch>(currentShow),
                onSelect = { json ->
                    // Preview instantly on tap
                    try {
                        val selected = Json.decodeFromString<ShowPatch>(json)
                        currentShow = selected
                        
                        // Force a re-fetch of current state if needed by updating the nav layer
                        val idx = navStack.indexOfLast { it.type == LayerType.SHOW }
                        if (idx != -1) {
                            vm.updateLayerName(idx, selected.name)
                            vm.updateLayerData(idx, ShowLayerContent(selected))
                        }
                        
                        // Reset to first RandomSet when switching shows
                        if (selected.randomSetIds.isNotEmpty()) {
                            vm.jumpToShowIndex(0)
                        } else {
                            mainRenderer?.mixerPatch = null
                        }
                    } catch (e: Exception) {}
                },
                onOpen = { json ->
                    // Open and close overlay
                    try {
                        val selected = Json.decodeFromString<ShowPatch>(json)
                        currentShow = selected
                        val idx = navStack.indexOfLast { it.type == LayerType.SHOW }
                        if (idx != -1) {
                            vm.updateLayerName(idx, selected.name)
                            vm.updateLayerData(idx, ShowLayerContent(selected))
                        }
                        onHideManager()
                    } catch (e: Exception) {}
                },
                onCreateNew = {
                    vm.startNewPatch(LayerType.SHOW)
                    onHideManager()
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
                // Navigation through RandomSets in the show
                navigationLabel = if (randomSetIds.isNotEmpty()) "RandomSet" else null,
                navigationIndex = if (randomSetIds.isNotEmpty()) currentRandomSetIndex else null,
                navigationTotal = if (randomSetIds.isNotEmpty()) randomSetIds.size else null,
                onNavigatePrev = if (randomSetIds.isNotEmpty()) {
                    { vm.triggerPrevMixer(randomSetIds.size) }
                } else null,
                onNavigateNext = if (randomSetIds.isNotEmpty()) {
                    { vm.triggerNextMixer(randomSetIds.size) }
                } else null
            )
        }
    }
}
