package llm.slop.spirals.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import llm.slop.spirals.*
import llm.slop.spirals.models.*
import llm.slop.spirals.ui.components.PatchManagerOverlay
import llm.slop.spirals.ui.components.SetChipList
import llm.slop.spirals.ui.components.KnobView
import llm.slop.spirals.ui.components.OscilloscopeView
import llm.slop.spirals.ui.theme.AppBackground
import llm.slop.spirals.ui.theme.AppAccent
import llm.slop.spirals.ui.theme.AppText
import llm.slop.spirals.cv.ModulatableParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import android.util.Log
import kotlin.random.Random

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

    var focusedTriggerId by remember { mutableStateOf("SHOW_NEXT") }
    var frameTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameTick++ }
        }
    }

    val mainRenderer = LocalSpiralRenderer.current
    val generator = remember { RandomSetGenerator(vm.getApplication()) }

    // Background monitoring for CV-based triggering
    var lastModPrev by remember { mutableFloatStateOf(0f) }
    var lastModNext by remember { mutableFloatStateOf(0f) }
    var lastModRand by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(frameTick) {
        val modPrev = mainRenderer?.getMixerParam("SHOW_PREV")?.value ?: 0f
        val modNext = mainRenderer?.getMixerParam("SHOW_NEXT")?.value ?: 0f
        val modRand = mainRenderer?.getMixerParam("SHOW_RANDOM")?.value ?: 0f

        if (modPrev > 0.5f && lastModPrev <= 0.5f) {
            vm.triggerPrevMixer(currentShow.randomSetIds.size)
        }
        if (modNext > 0.5f && lastModNext <= 0.5f) {
            vm.triggerNextMixer(currentShow.randomSetIds.size)
        }
        if (modRand > 0.5f && lastModRand <= 0.5f) {
            if (currentShow.randomSetIds.isNotEmpty()) {
                vm.jumpToShowIndex(Random.nextInt(currentShow.randomSetIds.size))
            }
        }
        
        lastModPrev = modPrev
        lastModNext = modNext
        lastModRand = modRand
    }

    // Update local state if nav data changes
    LaunchedEffect(layer?.data) {
        (layer?.data as? ShowLayerContent)?.show?.let {
            if (it.id != currentShow.id) {
                currentShow = it
            }
        }
    }

    // Pass the active currentShow to the mainRenderer
    LaunchedEffect(mainRenderer, currentShow) {
        mainRenderer?.showPatch = currentShow
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

            Spacer(modifier = Modifier.height(4.dp))

            // Oscilloscope View
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(60.dp).border(1.dp, AppText.copy(alpha = 0.1f))) {
                key(frameTick) {
                    val targetParam = mainRenderer?.getMixerParam(focusedTriggerId)
                    if (targetParam != null) {
                        OscilloscopeView(history = targetParam.history, modifier = Modifier.fillMaxSize())
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Performance Controls (Prev/Next/Random)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // PREV Trigger
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally, 
                    modifier = Modifier.clickable { focusedTriggerId = "SHOW_PREV" }
                ) {
                    Text(
                        text = "PREV", 
                        style = MaterialTheme.typography.labelSmall, 
                        color = if (focusedTriggerId == "SHOW_PREV") AppAccent else AppText
                    )
                    val modulatedPrev = if (frameTick >= 0) mainRenderer?.getMixerParam("SHOW_PREV")?.value ?: currentShow.prevTrigger.baseValue else 0f
                    KnobView(
                        baseValue = currentShow.prevTrigger.baseValue,
                        modulatedValue = modulatedPrev,
                        onValueChange = { currentShow = currentShow.copy(prevTrigger = currentShow.prevTrigger.copy(baseValue = it)) },
                        onInteractionFinished = {
                            if (currentShow.prevTrigger.baseValue > 0.5f) {
                                vm.triggerPrevMixer(currentShow.randomSetIds.size)
                            }
                        },
                        focused = focusedTriggerId == "SHOW_PREV" || currentShow.prevTrigger.modulators.isNotEmpty(),
                        tick = frameTick.toLong()
                    )
                }

                // RANDOM Trigger
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally, 
                    modifier = Modifier.clickable { focusedTriggerId = "SHOW_RANDOM" }
                ) {
                    Text(
                        text = "RAND", 
                        style = MaterialTheme.typography.labelSmall, 
                        color = if (focusedTriggerId == "SHOW_RANDOM") AppAccent else AppText
                    )
                    val modulatedRand = if (frameTick >= 0) mainRenderer?.getMixerParam("SHOW_RANDOM")?.value ?: currentShow.randomTrigger.baseValue else 0f
                    KnobView(
                        baseValue = currentShow.randomTrigger.baseValue,
                        modulatedValue = modulatedRand,
                        onValueChange = { currentShow = currentShow.copy(randomTrigger = currentShow.randomTrigger.copy(baseValue = it)) },
                        onInteractionFinished = {
                            if (currentShow.randomTrigger.baseValue > 0.5f && currentShow.randomSetIds.isNotEmpty()) {
                                vm.jumpToShowIndex(Random.nextInt(currentShow.randomSetIds.size))
                            }
                        },
                        focused = focusedTriggerId == "SHOW_RANDOM" || currentShow.randomTrigger.modulators.isNotEmpty(),
                        tick = frameTick.toLong()
                    )
                }

                // NEXT Trigger
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally, 
                    modifier = Modifier.clickable { focusedTriggerId = "SHOW_NEXT" }
                ) {
                    Text(
                        text = "NEXT", 
                        style = MaterialTheme.typography.labelSmall, 
                        color = if (focusedTriggerId == "SHOW_NEXT") AppAccent else AppText
                    )
                    val modulatedNext = if (frameTick >= 0) mainRenderer?.getMixerParam("SHOW_NEXT")?.value ?: currentShow.nextTrigger.baseValue else 0f
                    KnobView(
                        baseValue = currentShow.nextTrigger.baseValue,
                        modulatedValue = modulatedNext,
                        onValueChange = { currentShow = currentShow.copy(nextTrigger = currentShow.nextTrigger.copy(baseValue = it)) },
                        onInteractionFinished = {
                            if (currentShow.nextTrigger.baseValue > 0.5f) {
                                vm.triggerNextMixer(currentShow.randomSetIds.size)
                            }
                        },
                        focused = focusedTriggerId == "SHOW_NEXT" || currentShow.nextTrigger.modulators.isNotEmpty(),
                        tick = frameTick.toLong()
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. CV Editor for focused parameter
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                ShowCvEditor(
                    show = currentShow,
                    focusedId = focusedTriggerId,
                    onShowUpdate = { currentShow = it }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4. RandomSet sequence (Set Editor style)
            Column(modifier = Modifier.padding(horizontal = 12.dp).height(120.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    var showRandomSetPicker by remember { mutableStateOf(false) }

                    Button(onClick = { showRandomSetPicker = true }, modifier = Modifier.height(36.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add RandomSet", fontSize = 12.sp)
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

                Spacer(modifier = Modifier.height(8.dp))

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

@Composable
fun ShowCvEditor(
    show: ShowPatch,
    focusedId: String,
    onShowUpdate: (ShowPatch) -> Unit
) {
    val scrollState = rememberScrollState()
    
    val focusedParamData = remember(show, focusedId) {
        when (focusedId) {
            "SHOW_PREV" -> show.prevTrigger
            "SHOW_NEXT" -> show.nextTrigger
            "SHOW_RANDOM" -> show.randomTrigger
            else -> null
        }
    }

    if (focusedParamData == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Select a trigger to patch CV", color = AppText.copy(alpha = 0.5f))
        }
        return
    }

    val tempParam = remember(focusedId, focusedParamData) {
        ModulatableParameter(baseValue = focusedParamData.baseValue).apply {
            modulators.addAll(focusedParamData.modulators)
        }
    }
    
    var refreshCount by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Column(modifier = Modifier.weight(1f).verticalScroll(scrollState)) {
            key(focusedId, refreshCount) {
                tempParam.modulators.forEachIndexed { index, mod ->
                    ModulatorRow(
                        mod = mod,
                        onUpdate = { updatedMod ->
                            tempParam.modulators[index] = updatedMod
                            syncShowParam(show, focusedId, tempParam, onShowUpdate)
                        },
                        onInteractionFinished = { syncShowParam(show, focusedId, tempParam, onShowUpdate) },
                        onRemove = {
                            tempParam.modulators.removeAt(index)
                            refreshCount++
                            syncShowParam(show, focusedId, tempParam, onShowUpdate)
                        }
                    )
                    HorizontalDivider(color = AppText.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))
                }

                ModulatorRow(
                    mod = null,
                    onUpdate = { newMod ->
                        tempParam.modulators.add(newMod)
                        refreshCount++
                        syncShowParam(show, focusedId, tempParam, onShowUpdate)
                    },
                    onInteractionFinished = { syncShowParam(show, focusedId, tempParam, onShowUpdate) },
                    onRemove = {}
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

private fun syncShowParam(show: ShowPatch, id: String, param: ModulatableParameter, onUpdate: (ShowPatch) -> Unit) {
    val data = ModulatableParameterData(baseValue = param.baseValue, modulators = param.modulators.toList())
    val newShow = when (id) {
        "SHOW_PREV" -> show.copy(prevTrigger = data)
        "SHOW_NEXT" -> show.copy(nextTrigger = data)
        "SHOW_RANDOM" -> show.copy(randomTrigger = data)
        else -> show
    }
    onUpdate(newShow)
}
