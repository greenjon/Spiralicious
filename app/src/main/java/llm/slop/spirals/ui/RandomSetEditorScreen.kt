package llm.slop.spirals.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import llm.slop.spirals.*
import llm.slop.spirals.models.*
import llm.slop.spirals.ui.components.PatchManagerOverlay
import llm.slop.spirals.ui.theme.AppBackground
import llm.slop.spirals.ui.theme.AppText
import llm.slop.spirals.ui.theme.AppAccent
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RandomSetEditorScreen(
    vm: MandalaViewModel,
    onClose: () -> Unit,
    previewContent: @Composable () -> Unit,
    showManager: Boolean = false,
    onHideManager: () -> Unit = {}
) {
    val allRandomSets by vm.allRandomSets.collectAsState(initial = emptyList())
    
    // Initialize from layer data if available
    val navStack by vm.navStack.collectAsState()
    val layer = navStack.lastOrNull { it.type == LayerType.RANDOM_SET }
    
    var currentRSet by remember { mutableStateOf((layer?.data as? RandomSetLayerContent)?.randomSet) }
    var selectedTab by remember { mutableStateOf(0) }
    
    // Update local state if nav data changes (e.g. from Manage overlay)
    LaunchedEffect(layer?.data) {
        (layer?.data as? RandomSetLayerContent)?.randomSet?.let {
            if (it.id != (currentRSet?.id ?: "")) {
                currentRSet = it
            }
        }
    }
    
    // Update ViewModel when RSet changes
    LaunchedEffect(currentRSet) {
        val layerIndex = navStack.indexOfFirst { it.type == LayerType.RANDOM_SET }
        if (layerIndex != -1 && currentRSet != null) {
            vm.updateLayerData(layerIndex, RandomSetLayerContent(currentRSet!!), isDirty = true)
            vm.updateLayerName(layerIndex, currentRSet!!.name)
        }
    }
    
    fun selectRSet(rsetId: String) {
        val entity = allRandomSets.find { it.id == rsetId } ?: return
        val newRSet = Json.decodeFromString<RandomSet>(entity.jsonSettings)
        currentRSet = newRSet
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground)
        ) {
            // Preview section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.4f)
                    .background(Color.Black)
            ) {
                previewContent()
                
                // Info overlay
                if (currentRSet != null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Random Set Template",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = currentRSet!!.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }
                }
            }
            
            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = AppBackground,
                contentColor = AppAccent
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Recipe") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Arms") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Motion") }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Color") }
                )
                Tab(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    text = { Text("FX") }
                )
            }
            
            // Tab content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.6f)
            ) {
                if (currentRSet != null) {
                    when (selectedTab) {
                        0 -> RecipeTab(
                            rset = currentRSet!!,
                            onUpdate = { currentRSet = it }
                        )
                        1 -> ArmsTab(
                            rset = currentRSet!!,
                            onUpdate = { currentRSet = it }
                        )
                        2 -> MotionTab(
                            rset = currentRSet!!,
                            onUpdate = { currentRSet = it }
                        )
                        3 -> ColorTab(
                            rset = currentRSet!!,
                            onUpdate = { currentRSet = it }
                        )
                        4 -> FXTab(
                            rset = currentRSet!!,
                            onUpdate = { currentRSet = it }
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No Random Set loaded",
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppText.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
        
        // Manager overlay
        if (showManager && layer != null) {
            PatchManagerOverlay(
                title = "Random Sets",
                patches = allRandomSets.map { it.name to it.id },
                selectedId = currentRSet?.id,
                onSelect = { name ->
                    val entity = allRandomSets.find { it.name == name }
                    entity?.let { selectRSet(it.id) }
                },
                onOpen = { name ->
                    val entity = allRandomSets.find { it.name == name }
                    entity?.let { 
                        selectRSet(it.id)
                        onHideManager()
                    }
                },
                onCreateNew = {
                    vm.startNewPatch(LayerType.RANDOM_SET)
                    onHideManager()
                },
                onRename = { name ->
                    // Show rename dialog - for now just skip
                },
                onClone = { name ->
                    vm.cloneSavedPatch(LayerType.RANDOM_SET, name)
                },
                onDelete = { name ->
                    vm.deleteSavedPatch(LayerType.RANDOM_SET, name)
                }
            )
        }
    }
}

@Composable
fun RecipeTab(
    rset: RandomSet,
    onUpdate: (RandomSet) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Recipe Filter",
            style = MaterialTheme.typography.titleMedium,
            color = AppText,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        RecipeFilterOption("All Recipes", RecipeFilter.ALL, rset.recipeFilter) {
            onUpdate(rset.copy(recipeFilter = RecipeFilter.ALL))
        }
        
        RecipeFilterOption("Favorites Only", RecipeFilter.FAVORITES_ONLY, rset.recipeFilter) {
            onUpdate(rset.copy(recipeFilter = RecipeFilter.FAVORITES_ONLY))
        }
        
        // Petal count exact
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { 
                    onUpdate(rset.copy(
                        recipeFilter = RecipeFilter.PETALS_EXACT,
                        petalCount = rset.petalCount ?: 5
                    )) 
                }
                .padding(vertical = 8.dp)
        ) {
            RadioButton(
                selected = rset.recipeFilter == RecipeFilter.PETALS_EXACT,
                onClick = { 
                    onUpdate(rset.copy(
                        recipeFilter = RecipeFilter.PETALS_EXACT,
                        petalCount = rset.petalCount ?: 5
                    )) 
                }
            )
            Text("Specific Petal Count:", modifier = Modifier.padding(start = 8.dp), color = AppText)
            Spacer(modifier = Modifier.width(16.dp))
            if (rset.recipeFilter == RecipeFilter.PETALS_EXACT) {
                OutlinedTextField(
                    value = (rset.petalCount ?: 5).toString(),
                    onValueChange = { 
                        it.toIntOrNull()?.let { count ->
                            onUpdate(rset.copy(petalCount = count))
                        }
                    },
                    modifier = Modifier.width(80.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = AppBackground,
                        unfocusedContainerColor = AppBackground,
                        focusedTextColor = AppText,
                        unfocusedTextColor = AppText
                    )
                )
            }
        }
        
        // Petal range
        RecipeFilterOption("Petal Range", RecipeFilter.PETALS_RANGE, rset.recipeFilter) {
            onUpdate(rset.copy(
                recipeFilter = RecipeFilter.PETALS_RANGE,
                petalMin = rset.petalMin ?: 3,
                petalMax = rset.petalMax ?: 9
            ))
        }
        
        if (rset.recipeFilter == RecipeFilter.PETALS_RANGE) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 48.dp, top = 8.dp)
            ) {
                Text("Min:", color = AppText)
                OutlinedTextField(
                    value = (rset.petalMin ?: 3).toString(),
                    onValueChange = { 
                        it.toIntOrNull()?.let { min ->
                            onUpdate(rset.copy(petalMin = min))
                        }
                    },
                    modifier = Modifier
                        .width(80.dp)
                        .padding(horizontal = 8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = AppBackground,
                        unfocusedContainerColor = AppBackground,
                        focusedTextColor = AppText,
                        unfocusedTextColor = AppText
                    )
                )
                Text("Max:", color = AppText)
                OutlinedTextField(
                    value = (rset.petalMax ?: 9).toString(),
                    onValueChange = { 
                        it.toIntOrNull()?.let { max ->
                            onUpdate(rset.copy(petalMax = max))
                        }
                    },
                    modifier = Modifier
                        .width(80.dp)
                        .padding(horizontal = 8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = AppBackground,
                        unfocusedContainerColor = AppBackground,
                        focusedTextColor = AppText,
                        unfocusedTextColor = AppText
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = rset.autoHueSweep,
                onCheckedChange = { onUpdate(rset.copy(autoHueSweep = it)) }
            )
            Text(
                text = "Auto-set Hue Sweep to petals",
                modifier = Modifier.padding(start = 8.dp),
                color = AppText
            )
        }
    }
}

@Composable
fun RecipeFilterOption(
    label: String,
    filter: RecipeFilter,
    currentFilter: RecipeFilter,
    onSelect: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp)
    ) {
        RadioButton(
            selected = currentFilter == filter,
            onClick = onSelect
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 8.dp),
            color = AppText
        )
    }
}

@Composable
fun ArmsTab(
    rset: RandomSet,
    onUpdate: (RandomSet) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Arm Constraints",
            style = MaterialTheme.typography.titleMedium,
            color = AppText,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Text(
            text = "Configure constraints for L1-L4 arm parameters. Null values use randomize defaults.",
            style = MaterialTheme.typography.bodySmall,
            color = AppText.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // For Phase 1, show a simple placeholder
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = AppBackground.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Detailed arm controls coming soon",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppText
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Phase 1: Uses default randomization logic\nPhase 2: Will add granular per-arm constraints",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppText.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun MotionTab(
    rset: RandomSet,
    onUpdate: (RandomSet) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Rotation Constraints",
            style = MaterialTheme.typography.titleMedium,
            color = AppText,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Text(
            text = "Configure rotation direction and speed for generated mandalas.",
            style = MaterialTheme.typography.bodySmall,
            color = AppText.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Placeholder for Phase 1
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = AppBackground.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Motion controls coming soon",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppText
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Phase 1: Uses default randomization logic\nPhase 2: Will add rotation and speed constraints",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppText.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun ColorTab(
    rset: RandomSet,
    onUpdate: (RandomSet) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Hue Offset Constraints",
            style = MaterialTheme.typography.titleMedium,
            color = AppText,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Text(
            text = "Configure color cycling behavior for generated mandalas.",
            style = MaterialTheme.typography.bodySmall,
            color = AppText.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Placeholder for Phase 1
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = AppBackground.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Color controls coming soon",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppText
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Phase 1: Uses default randomization logic\nPhase 2: Will add hue offset constraints",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppText.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun FXTab(
    rset: RandomSet,
    onUpdate: (RandomSet) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Feedback Mode",
            style = MaterialTheme.typography.titleMedium,
            color = AppText,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        FeedbackModeOption("None", FeedbackMode.NONE, rset.feedbackMode) {
            onUpdate(rset.copy(feedbackMode = FeedbackMode.NONE))
        }
        
        FeedbackModeOption("Light (subtle trails)", FeedbackMode.LIGHT, rset.feedbackMode) {
            onUpdate(rset.copy(feedbackMode = FeedbackMode.LIGHT))
        }
        
        FeedbackModeOption("Medium (noticeable)", FeedbackMode.MEDIUM, rset.feedbackMode) {
            onUpdate(rset.copy(feedbackMode = FeedbackMode.MEDIUM))
        }
        
        FeedbackModeOption("Heavy (intense)", FeedbackMode.HEAVY, rset.feedbackMode) {
            onUpdate(rset.copy(feedbackMode = FeedbackMode.HEAVY))
        }
    }
}

@Composable
fun FeedbackModeOption(
    label: String,
    mode: FeedbackMode,
    currentMode: FeedbackMode,
    onSelect: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp)
    ) {
        RadioButton(
            selected = currentMode == mode,
            onClick = onSelect
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 8.dp),
            color = AppText
        )
    }
}
