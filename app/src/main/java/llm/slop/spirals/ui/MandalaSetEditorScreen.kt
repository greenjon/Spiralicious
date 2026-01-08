package llm.slop.spirals.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import llm.slop.spirals.MandalaSet
import llm.slop.spirals.MandalaViewModel
import llm.slop.spirals.SelectionPolicy
import llm.slop.spirals.PatchMapper
import llm.slop.spirals.MandalaVisualSource
import llm.slop.spirals.ui.components.MandalaPicker
import llm.slop.spirals.ui.components.SetChipList
import llm.slop.spirals.ui.theme.AppBackground
import llm.slop.spirals.ui.theme.AppText
import llm.slop.spirals.ui.theme.AppAccent
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MandalaSetEditorScreen(
    vm: MandalaViewModel = viewModel(), 
    onClose: () -> Unit,
    previewContent: @Composable () -> Unit,
    visualSource: MandalaVisualSource
) {
    val allSets by vm.allSets.collectAsState(initial = emptyList())
    val allPatches by vm.allPatches.collectAsState(initial = emptyList())

    var currentSet by remember { mutableStateOf<MandalaSet?>(null) }
    var focusedMandalaId by remember { mutableStateOf<String?>(null) }
    var showMandalaPicker by remember { mutableStateOf(false) }

    // Logic to clear the preview when no set or mandala is selected
    LaunchedEffect(currentSet, focusedMandalaId) {
        if (currentSet == null || focusedMandalaId == null) {
            // "Clear" the visual source. Assuming 0 alpha hides it effectively.
            visualSource.globalAlpha.baseValue = 0f
        } else {
            // Find the patch and apply it
            val patchEntity = allPatches.find { it.name == focusedMandalaId }
            patchEntity?.let { entity ->
                val patchData = PatchMapper.fromJson(entity.jsonSettings)
                patchData?.let { data ->
                    PatchMapper.applyToVisualSource(data, visualSource)
                    visualSource.globalAlpha.baseValue = 1f
                }
            }
        }
    }

    // When a set is selected, load it into the editor state
    fun selectSet(setId: String) {
        val entity = allSets.find { it.id == setId } ?: return
        currentSet = MandalaSet(
            id = entity.id,
            name = entity.name,
            orderedMandalaIds = Json.decodeFromString(entity.jsonOrderedMandalaIds),
            selectionPolicy = SelectionPolicy.valueOf(entity.selectionPolicy)
        )
        // If set has mandalas, focus the first one
        focusedMandalaId = currentSet?.orderedMandalaIds?.firstOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val breadcrumb = "Mandala Set: ${currentSet?.name ?: "..."}"
                val focusedName = if(focusedMandalaId != null) " › $focusedMandalaId" else ""
                Text(breadcrumb + focusedName, style = MaterialTheme.typography.headlineSmall, color = AppText)
                
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = AppText)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (currentSet == null) {
                // Set Selection / Creation View
                Column {
                    Button(onClick = { 
                        currentSet = MandalaSet(name = "New Set", orderedMandalaIds = mutableListOf())
                        focusedMandalaId = null
                    }) {
                        Text("Create New Set")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Existing Sets:", style = MaterialTheme.typography.titleMedium, color = AppText)
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        allSets.forEach { setEntity ->
                            Text(
                                text = setEntity.name,
                                color = AppAccent,
                                modifier = Modifier.clickable { selectSet(setEntity.id) }.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            } else {
                 // Preview Window
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16 / 9f)
                        .background(Color.Black)
                ) {
                    previewContent()
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                // Set Editor View
                Column {
                    // Add Mandala Button
                    Button(onClick = { showMandalaPicker = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Mandala")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Chip List
                    SetChipList(
                        chipIds = currentSet!!.orderedMandalaIds,
                        onChipTapped = { focusedMandalaId = it },
                        onChipReordered = { newOrder -> 
                            currentSet?.let { it.orderedMandalaIds.clear(); it.orderedMandalaIds.addAll(newOrder) }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Policy Selector
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Policy: ", color = AppText, style = MaterialTheme.typography.labelSmall)
                        SelectionPolicy.values().forEach { policy ->
                            Button(
                                onClick = { currentSet?.let { it.selectionPolicy = policy } },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (currentSet?.selectionPolicy == policy) AppAccent else AppText.copy(alpha = 0.1f),
                                    contentColor = if (currentSet?.selectionPolicy == policy) Color.White else AppText
                                ),
                                shape = MaterialTheme.shapes.extraSmall,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            ) {
                                Text(policy.name, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // Save Button
                    Button(onClick = { currentSet?.let { vm.saveSet(it) }; onClose() }) {
                        Text("Save & Close")
                    }
                }
            }
        }
    }

    if (showMandalaPicker) {
        ModalBottomSheet(onDismissRequest = { showMandalaPicker = false }) {
            MandalaPicker(
                patches = allPatches,
                onPatchSelected = { patchName ->
                    focusedMandalaId = patchName
                },
                onPatchAdded = { patchName -> 
                    currentSet?.orderedMandalaIds?.add(patchName)
                    focusedMandalaId = patchName
                    showMandalaPicker = false
                }
            )
        }
    }
}
