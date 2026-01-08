package llm.slop.spirals.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
    onNavigateToMixerEditor: () -> Unit,
    onShowCvLab: () -> Unit,
    previewContent: @Composable () -> Unit,
    visualSource: MandalaVisualSource
) {
    val allSets by vm.allSets.collectAsState(initial = emptyList())
    val allPatches by vm.allPatches.collectAsState(initial = emptyList())

    var currentSet by remember { mutableStateOf<MandalaSet?>(null) }
    var focusedMandalaId by remember { mutableStateOf<String?>(null) }
    var showMandalaPicker by remember { mutableStateOf(false) }
    
    var showMenu by remember { mutableStateOf(false) }
    var showOpenDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    // Logic to clear the preview when no set or mandala is selected
    LaunchedEffect(currentSet, focusedMandalaId) {
        if (currentSet == null || focusedMandalaId == null) {
            visualSource.globalAlpha.baseValue = 0f
        } else {
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

    fun selectSet(setId: String) {
        val entity = allSets.find { it.id == setId } ?: return
        currentSet = MandalaSet(
            id = entity.id,
            name = entity.name,
            orderedMandalaIds = Json.decodeFromString(entity.jsonOrderedMandalaIds),
            selectionPolicy = SelectionPolicy.valueOf(entity.selectionPolicy)
        )
        focusedMandalaId = currentSet?.orderedMandalaIds?.firstOrNull()
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
                val breadcrumb = "Mandala Set: ${currentSet?.name ?: "..."}"
                val focusedName = if(focusedMandalaId != null) " › $focusedMandalaId" else ""
                Text(
                    text = breadcrumb + focusedName, 
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
                            text = { Text("New Set", color = AppText) },
                            onClick = { 
                                currentSet = MandalaSet(name = "New Set", orderedMandalaIds = mutableListOf())
                                focusedMandalaId = null
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
                            onClick = { onClose(); showMenu = false },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = AppAccent) }
                        )
                        DropdownMenuItem(
                            text = { Text("Mixer Editor", color = AppAccent) },
                            onClick = { onNavigateToMixerEditor(); showMenu = false },
                            leadingIcon = { Icon(Icons.Default.List, contentDescription = null, tint = AppAccent) }
                        )
                        HorizontalDivider(color = AppText.copy(alpha = 0.1f))
                        DropdownMenuItem(
                            text = { Text("Open Set", color = AppText) },
                            onClick = { showOpenDialog = true; showMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Save Set", color = AppText) },
                            onClick = { 
                                currentSet?.let { vm.saveSet(it) }
                                showMenu = false 
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename Set", color = AppText) },
                            onClick = { showRenameDialog = true; showMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Set", color = Color.Red) },
                            onClick = { 
                                currentSet?.let { vm.deleteSet(it.id) }
                                currentSet = null
                                focusedMandalaId = null
                                showMenu = false 
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Always show preview window area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16 / 9f)
                    .background(Color.Black)
            ) {
                previewContent()
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (currentSet == null) {
                Column {
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
                // Set Editor View
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { showMandalaPicker = true }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Mandala")
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        // Policy Selector Dropdown
                        var policyExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { policyExpanded = true },
                                shape = MaterialTheme.shapes.extraSmall,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(currentSet?.selectionPolicy?.name ?: "Policy", style = MaterialTheme.typography.labelSmall, color = AppText)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AppText)
                            }
                            DropdownMenu(
                                expanded = policyExpanded,
                                onDismissRequest = { policyExpanded = false },
                                containerColor = AppBackground
                            ) {
                                SelectionPolicy.values().forEach { policy ->
                                    DropdownMenuItem(
                                        text = { Text(policy.name, style = MaterialTheme.typography.labelSmall) },
                                        onClick = { 
                                            currentSet = currentSet?.copy(selectionPolicy = policy)
                                            policyExpanded = false 
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    SetChipList(
                        chipIds = currentSet!!.orderedMandalaIds,
                        onChipTapped = { focusedMandalaId = it },
                        onChipReordered = { newOrder -> 
                            currentSet = currentSet?.copy(orderedMandalaIds = newOrder.toMutableList())
                        }
                    )
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
                    currentSet = currentSet?.copy(
                        orderedMandalaIds = (currentSet?.orderedMandalaIds ?: mutableListOf()).toMutableList().apply { add(patchName) }
                    )
                    focusedMandalaId = patchName
                }
            )
        }
    }

    if (showOpenDialog) {
        Dialog(onDismissRequest = { showOpenDialog = false }) {
            Surface(shape = MaterialTheme.shapes.medium, color = AppBackground) {
                Column(modifier = Modifier.padding(16.dp).fillMaxHeight(0.7f)) {
                    Text("Saved Sets", style = MaterialTheme.typography.titleLarge, color = AppText)
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        allSets.forEach { entity ->
                            Row(modifier = Modifier.fillMaxWidth().clickable { 
                                selectSet(entity.id)
                                showOpenDialog = false 
                            }.padding(12.dp)) {
                                Text(entity.name, style = MaterialTheme.typography.bodyLarge, color = AppText)
                            }
                        }
                    }
                    if (allSets.isEmpty()) Text("No sets saved yet.", color = AppText.copy(alpha = 0.5f))
                }
            }
        }
    }

    if (showRenameDialog) {
        var name by remember { mutableStateOf(currentSet?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Set", color = AppText) },
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
                    currentSet = currentSet?.copy(name = name)
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
