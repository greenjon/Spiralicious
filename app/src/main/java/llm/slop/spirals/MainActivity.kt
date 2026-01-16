package llm.slop.spirals

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.media.AudioFormat
import android.media.AudioRecord
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import llm.slop.spirals.cv.audio.AudioEngine
import llm.slop.spirals.cv.audio.AudioSourceType
import llm.slop.spirals.ui.*
import llm.slop.spirals.ui.components.MandalaParameterMatrix
import llm.slop.spirals.ui.components.OscilloscopeView
import llm.slop.spirals.ui.components.EditorBreadcrumbs
import llm.slop.spirals.ui.components.PatchManagerOverlay
import llm.slop.spirals.ui.theme.AppAccent
import llm.slop.spirals.ui.theme.AppBackground
import llm.slop.spirals.ui.theme.AppText
import llm.slop.spirals.ui.theme.SpiralsTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import llm.slop.spirals.cv.CvRegistry
import llm.slop.spirals.models.MixerPatch
import llm.slop.spirals.models.ShowPatch
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {

    private lateinit var audioEngine: AudioEngine
    private var spiralSurfaceView: SpiralSurfaceView? = null
    private var sourceManager: MandalaVisualSource? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission result handled via State in Compose
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        audioEngine = AudioEngine(applicationContext)
        val manager = MandalaVisualSource()
        sourceManager = manager
        
        val surfaceView = SpiralSurfaceView(applicationContext)
        spiralSurfaceView = surfaceView
        val renderer = surfaceView.renderer
        renderer.visualSource = manager

        setContent {
            SpiralsTheme {
                val vm: MandalaViewModel = viewModel()
                val navStack by vm.navStack.collectAsState()
                val currentLayer = navStack.lastOrNull() ?: return@SpiralsTheme
                val currentPatch by vm.currentPatch.collectAsState()
                val scope = rememberCoroutineScope()
                
                var showCvLab by remember { mutableStateOf(false) }
                var showSettings by remember { mutableStateOf(false) }
                var showManager by remember { mutableStateOf(false) }

                var hasMicPermission by remember { 
                    mutableStateOf(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) 
                }
                
                var audioSourceType by remember { mutableStateOf(AudioSourceType.MIC) }
                var showRenameDialog by remember { mutableStateOf(false) }
                var showOpenMixerDialog by remember { mutableStateOf(false) }
                var showOpenPatchDialog by remember { mutableStateOf(false) }
                var showOpenSetDialog by remember { mutableStateOf(false) }
                var showOpenShowDialog by remember { mutableStateOf(false) }
                var showDeleteConfirm by remember { mutableStateOf(false) }

                // 1. Start the CV Sync Registry immediately
                LaunchedEffect(Unit) {
                    CvRegistry.startSync(this)
                }

                // 2. Automatically start/stop Audio Engine based on source selection
                LaunchedEffect(audioSourceType, hasMicPermission) {
                    if (audioSourceType == AudioSourceType.MIC || audioSourceType == AudioSourceType.UNPROCESSED) {
                        if (hasMicPermission) {
                            val sampleRate = 44100
                            val encoding = AudioFormat.ENCODING_PCM_FLOAT
                            val channelConfig = AudioFormat.CHANNEL_IN_MONO
                            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
                            
                            val record = audioEngine.sourceManager.buildAudioRecord(
                                type = audioSourceType,
                                sampleRate = sampleRate,
                                encoding = encoding,
                                channelConfig = channelConfig,
                                bufferSize = bufferSize
                            )
                            audioEngine.start(this, record)
                        }
                    }
                }

                LaunchedEffect(hasMicPermission) {
                    if (!hasMicPermission) {
                        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }

                CompositionLocalProvider(LocalSpiralRenderer provides renderer) {
                    val previewContent = @Composable {
                        AndroidView(
                            factory = { surfaceView },
                            modifier = Modifier.fillMaxSize(),
                            update = {}
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AppBackground)
                            .systemBarsPadding()
                    ) {
                        var showHeaderMenu by remember { mutableStateOf(false) }

                        EditorBreadcrumbs(
                            stack = navStack,
                            onLayerClick = { index -> vm.popToLayer(index) },
                            actions = {
                                IconButton(onClick = { showHeaderMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = AppText)
                                }
                                
                                if (showHeaderMenu) {
                                    DropdownMenu(
                                        expanded = showHeaderMenu, 
                                        onDismissRequest = { showHeaderMenu = false },
                                        containerColor = AppBackground
                                    ) {
                                        val hasActiveData = currentLayer.data != null
                                        val disabledColor = AppText.copy(alpha = 0.3f)
                                        val canSwitch = navStack.size == 1
                                        val editorName = when(currentLayer.type) {
                                            LayerType.MIXER -> "Mixer"
                                            LayerType.SET -> "Set"
                                            LayerType.MANDALA -> "Mandala"
                                            LayerType.SHOW -> "Show"
                                        }

                                        // --- TOP GROUP ---
                                        DropdownMenuItem(
                                            text = { Text("Manage", color = AppAccent) },
                                            onClick = { showManager = true; showHeaderMenu = false }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("New $editorName", color = if (canSwitch) AppAccent else disabledColor) },
                                            onClick = { 
                                                if (canSwitch) {
                                                    vm.startNewPatch(currentLayer.type)
                                                    showHeaderMenu = false
                                                }
                                            },
                                            enabled = canSwitch
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Open $editorName", color = if (canSwitch) AppAccent else disabledColor) },
                                            onClick = { 
                                                if (canSwitch) {
                                                    when(currentLayer.type) {
                                                        LayerType.MIXER -> showOpenMixerDialog = true
                                                        LayerType.SET -> showOpenSetDialog = true
                                                        LayerType.MANDALA -> showOpenPatchDialog = true
                                                        LayerType.SHOW -> showOpenShowDialog = true
                                                    }
                                                    showHeaderMenu = false
                                                }
                                            },
                                            enabled = canSwitch
                                        )

                                        HorizontalDivider(color = AppText.copy(alpha = 0.1f))

                                        // --- DATA GROUP ---
                                        DropdownMenuItem(
                                            text = { Text("Save", color = if (hasActiveData) AppText else disabledColor) }, 
                                            onClick = { vm.saveLayer(currentLayer); showHeaderMenu = false },
                                            enabled = hasActiveData
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Rename", color = if (hasActiveData) AppText else disabledColor) }, 
                                            onClick = { showRenameDialog = true; showHeaderMenu = false },
                                            enabled = hasActiveData
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Clone", color = if (hasActiveData) AppText else disabledColor) }, 
                                            onClick = { vm.cloneLayer(navStack.lastIndex); showHeaderMenu = false },
                                            enabled = hasActiveData
                                        )

                                        HorizontalDivider(color = AppText.copy(alpha = 0.1f))

                                        // --- DELETE GROUP ---
                                        DropdownMenuItem(
                                            text = { Text("Delete", color = if (hasActiveData) Color.Red else disabledColor) }, 
                                            onClick = { showDeleteConfirm = true; showHeaderMenu = false },
                                            enabled = hasActiveData
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Discard Changes", color = Color.Red) }, 
                                            onClick = { 
                                                vm.popToLayer(navStack.size - 2, save = false)
                                                showHeaderMenu = false 
                                            }
                                        )

                                        HorizontalDivider(color = AppText.copy(alpha = 0.1f))

                                        // --- SWITCH GROUP ---
                                        DropdownMenuItem(
                                            text = { Text("Switch to...", color = AppText) },
                                            onClick = { /* Section Header */ },
                                            enabled = false
                                        )
                                        
                                        LayerType.entries.filter { it != currentLayer.type }.forEach { type ->
                                            val label = when(type) {
                                                LayerType.MIXER -> "Mixer Editor"
                                                LayerType.SET -> "Set Editor"
                                                LayerType.MANDALA -> "Mandala Editor"
                                                LayerType.SHOW -> "Show Editor"
                                            }
                                            DropdownMenuItem(
                                                text = { 
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text("  • ", color = AppAccent)
                                                        Text(label, color = AppAccent)
                                                    }
                                                },
                                                onClick = { vm.createAndResetStack(type); showHeaderMenu = false },
                                                enabled = canSwitch
                                            )
                                        }

                                        HorizontalDivider(color = AppText.copy(alpha = 0.1f))

                                        // --- BOTTOM GROUP ---
                                        DropdownMenuItem(
                                            text = { Text("CV Lab", color = AppAccent) }, 
                                            onClick = { showCvLab = true; showHeaderMenu = false }
                                        )
                                        DropdownMenuItem(text = { Text("Settings", color = AppText) }, onClick = { 
                                            showSettings = true
                                            showHeaderMenu = false 
                                        })
                                    }
                                }
                            }
                        )

                        Box(modifier = Modifier.weight(1f)) {
                            when (currentLayer.type) {
                                LayerType.SHOW -> {
                                    ShowEditorScreen(
                                        vm = vm,
                                        onNavigateToMixerEditor = { nested ->
                                            if (nested) vm.createAndPushLayer(LayerType.MIXER)
                                            else vm.createAndResetStack(LayerType.MIXER)
                                        },
                                        previewContent = previewContent,
                                        showManager = showManager,
                                        onHideManager = { showManager = false }
                                    )
                                }
                                LayerType.MIXER -> {
                                    MixerEditorScreen(
                                        vm = vm, 
                                        onClose = { /* Root layer usually doesn't close */ },
                                        onNavigateToSetEditor = { nested ->
                                            if (nested) vm.createAndPushLayer(LayerType.SET)
                                            else vm.createAndResetStack(LayerType.SET)
                                        },
                                        onNavigateToMandalaEditor = { nested ->
                                            if (nested) vm.createAndPushLayer(LayerType.MANDALA)
                                            else vm.createAndResetStack(LayerType.MANDALA)
                                        },
                                        onShowCvLab = { showCvLab = true },
                                        previewContent = previewContent,
                                        showManager = showManager,
                                        onHideManager = { showManager = false }
                                    )
                                }
                                LayerType.SET -> {
                                    MandalaSetEditorScreen(
                                        vm = vm, 
                                        onClose = { vm.popToLayer(navStack.size - 2) },
                                        onNavigateToMixerEditor = { /* Navigation handled via breadcrumbs */ },
                                        onShowCvLab = { showCvLab = true },
                                        previewContent = previewContent,
                                        visualSource = manager,
                                        showManager = showManager,
                                        onHideManager = { showManager = false }
                                    )
                                }
                                LayerType.MANDALA -> {
                                    MandalaEditorScreen(
                                        vm = vm,
                                        visualSource = manager,
                                        isDirty = PatchMapper.isDirty(manager, currentPatch),
                                        lastLoadedPatch = currentPatch,
                                        onPatchLoaded = { vm.setCurrentPatch(it) },
                                        onInteraction = { /* Generic interaction trigger */ },
                                        onNavigateToSetEditor = { /* Navigation handled via breadcrumbs */ },
                                        onNavigateToMixerEditor = { /* Navigation handled via breadcrumbs */ },
                                        onShowCvLab = { showCvLab = true },
                                        previewContent = previewContent,
                                        showHeader = false,
                                        showManager = showManager,
                                        onHideManager = { showManager = false }
                                    )
                                }
                            }
                        }
                    }
                }

                // Overlays
                AnimatedVisibility(
                    visible = showCvLab,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    CvLabScreen(
                        audioEngine = audioEngine,
                        sourceManager = audioEngine.sourceManager,
                        audioSourceType = audioSourceType,
                        onAudioSourceTypeChange = { audioSourceType = it },
                        hasMicPermission = hasMicPermission,
                        onMicPermissionGranted = { hasMicPermission = true },
                        onInternalAudioRecordCreated = { record ->
                            audioEngine.start(scope, record)
                        },
                        onClose = { showCvLab = false }
                    )
                }

                if (showSettings) {
                    SettingsOverlay(
                        currentMode = vm.getStartupMode(),
                        onModeChange = { vm.setStartupMode(it) },
                        onClose = { showSettings = false }
                    )
                }
                
                if (showRenameDialog) {
                    RenamePatchDialog(
                        initialName = currentLayer.name,
                        onRename = { newName ->
                            vm.renameLayer(navStack.lastIndex, currentLayer.name, newName)
                            showRenameDialog = false
                        },
                        onDismiss = { showRenameDialog = false }
                    )
                }

                if (showDeleteConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm = false },
                        title = { Text("Delete '${currentLayer.name}'?", color = AppText) },
                        text = { Text("This action cannot be undone.", color = AppText) },
                        confirmButton = {
                            TextButton(onClick = { 
                                vm.deleteLayerAndPop(navStack.lastIndex)
                                showDeleteConfirm = false 
                            }) {
                                Text("DELETE", color = Color.Red)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteConfirm = false }) {
                                Text("CANCEL", color = AppText)
                            }
                        },
                        containerColor = AppBackground
                    )
                }

                if (showOpenMixerDialog) {
                    OpenMixerDialog(
                        vm = vm,
                        onMixerSelected = { mixer ->
                            vm.createAndResetStack(LayerType.MIXER)
                            vm.updateLayerData(0, MixerLayerContent(mixer))
                            vm.updateLayerName(0, mixer.name)
                            showOpenMixerDialog = false
                        },
                        onDismiss = { showOpenMixerDialog = false }
                    )
                }

                if (showOpenPatchDialog) {
                    OpenPatchDialog(
                        vm = vm,
                        onPatchSelected = { patch ->
                            vm.createAndResetStack(LayerType.MANDALA)
                            vm.updateLayerData(0, MandalaLayerContent(patch))
                            vm.updateLayerName(0, patch.name)
                            vm.setCurrentPatch(patch)
                            showOpenPatchDialog = false
                        },
                        onDismiss = { showOpenPatchDialog = false }
                    )
                }

                if (showOpenSetDialog) {
                    OpenSetDialog(
                        vm = vm,
                        onSetSelected = { setEntity ->
                            vm.createAndResetStack(LayerType.SET)
                            val set = MandalaSet(
                                id = setEntity.id,
                                name = setEntity.name,
                                orderedMandalaIds = Json.decodeFromString(setEntity.jsonOrderedMandalaIds),
                                selectionPolicy = SelectionPolicy.valueOf(setEntity.selectionPolicy)
                            )
                            vm.updateLayerData(0, SetLayerContent(set))
                            vm.updateLayerName(0, set.name)
                            showOpenSetDialog = false
                        },
                        onDismiss = { showOpenSetDialog = false }
                    )
                }

                if (showOpenShowDialog) {
                    OpenShowDialog(
                        vm = vm,
                        onShowSelected = { showEntity ->
                            vm.createAndResetStack(LayerType.SHOW)
                            val show = Json.decodeFromString<ShowPatch>(showEntity.jsonSettings)
                            vm.updateLayerData(0, ShowLayerContent(show))
                            vm.updateLayerName(0, show.name)
                            showOpenShowDialog = false
                        },
                        onDismiss = { showOpenShowDialog = false }
                    )
                }
            }
        }
    }

    @Composable
    fun SettingsOverlay(
        currentMode: StartupMode,
        onModeChange: (StartupMode) -> Unit,
        onClose: () -> Unit
    ) {
        Dialog(onDismissRequest = onClose) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = MaterialTheme.shapes.medium,
                color = AppBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, AppText.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Settings", style = MaterialTheme.typography.headlineSmall, color = AppText)
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = AppText)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text("Start app with...", style = MaterialTheme.typography.titleMedium, color = AppText)
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    StartupMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onModeChange(mode) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentMode == mode,
                                onClick = { onModeChange(mode) },
                                colors = RadioButtonDefaults.colors(selectedColor = AppAccent, unselectedColor = AppText.copy(alpha = 0.6f))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = when(mode) {
                                    StartupMode.LAST_WORKSPACE -> "Last Workspace"
                                    StartupMode.MIXER -> "Mixer Editor"
                                    StartupMode.SET -> "Set Editor"
                                    StartupMode.MANDALA -> "Mandala Editor"
                                    StartupMode.SHOW -> "Show Editor"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = AppText
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun MandalaEditorScreen(
        vm: MandalaViewModel,
        visualSource: MandalaVisualSource,
        isDirty: Boolean,
        lastLoadedPatch: PatchData?,
        onPatchLoaded: (PatchData) -> Unit,
        onInteraction: () -> Unit,
        onNavigateToSetEditor: () -> Unit,
        onNavigateToMixerEditor: () -> Unit,
        onShowCvLab: () -> Unit,
        previewContent: @Composable () -> Unit,
        showHeader: Boolean = true,
        showManager: Boolean = false,
        onHideManager: () -> Unit = {}
    ) {
        var focusedParameterId by remember { mutableStateOf("L1") }
        var recipeExpanded by remember { mutableStateOf(false) }
        var showOpenDialog by remember { mutableStateOf(false) }
        val patchName by remember(lastLoadedPatch) { mutableStateOf(lastLoadedPatch?.name ?: "New Patch") }
        val allPatches by vm.allPatches.collectAsState(initial = emptyList())

        val renderer = LocalSpiralRenderer.current
        DisposableEffect(renderer) {
            renderer?.visualSource = visualSource
            renderer?.mixerPatch = null
            // Reset alpha when entering editor
            visualSource.globalAlpha.baseValue = 1f
            onDispose {
                renderer?.visualSource = null
            }
        }

        // Apply loaded patch to visual source when it changes
        LaunchedEffect(lastLoadedPatch) {
            lastLoadedPatch?.let { 
                PatchMapper.applyToVisualSource(it, visualSource)
            }
        }

        // Keep ViewModel updated with current work-in-progress for cascade saving
        LaunchedEffect(visualSource.recipe, visualSource.parameters.values.map { it.value }) {
            val patchData = PatchMapper.fromVisualSource(patchName, visualSource)
            val stack = vm.navStack.value
            val index = stack.indexOfLast { it.type == LayerType.MANDALA }
            if (index != -1 && stack[index].data != null) { // Only update if we have actual data
                val realDirty = PatchMapper.isDirty(visualSource, lastLoadedPatch)
                vm.updateLayerData(index, MandalaLayerContent(patchData), isDirty = realDirty)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                    Column(modifier = Modifier.wrapContentHeight().fillMaxWidth().padding(horizontal = 8.dp)) {
                        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16 / 9f).background(Color.Black).border(1.dp, AppText.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                            previewContent()
                            
                            Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                                Surface(color = AppBackground.copy(alpha = 0.7f), shape = MaterialTheme.shapes.extraSmall, modifier = Modifier.clickable { recipeExpanded = true }) {
                                    Text(text = "${visualSource.recipe.a}, ${visualSource.recipe.b}, ${visualSource.recipe.c}, ${visualSource.recipe.d} (${visualSource.recipe.petals}P)", style = MaterialTheme.typography.labelSmall, color = AppAccent, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp) )
                                }
                                DropdownMenu(expanded = recipeExpanded, onDismissRequest = { recipeExpanded = false }, containerColor = AppBackground, tonalElevation = 0.dp) {
                                    MandalaLibrary.MandalaRatios.forEach { ratio ->
                                        DropdownMenuItem(text = { Text("${ratio.a}, ${ratio.b}, ${ratio.c}, ${ratio.d} (${ratio.petals}P)", color = AppText) }, onClick = { 
                                            visualSource.recipe = ratio
                                            onInteraction()
                                            recipeExpanded = false
                                        })
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        var monitorTick by remember { mutableIntStateOf(0) }
                        LaunchedEffect(Unit) {
                            while (true) {
                                monitorTick++
                                delay(16)
                            }
                        }

                        val focusedParam = remember(focusedParameterId, visualSource) {
                            visualSource.parameters[focusedParameterId] ?: visualSource.globalAlpha
                        }
                        
                        Box(modifier = Modifier.fillMaxWidth().height(60.dp).border(1.dp, AppText.copy(alpha = 0.1f))) {
                            key(monitorTick) {
                                OscilloscopeView(history = focusedParam.history, modifier = Modifier.fillMaxSize())
                            }
                            Surface(color = AppBackground.copy(alpha = 0.8f), modifier = Modifier.align(Alignment.TopStart).padding(4.dp), shape = MaterialTheme.shapes.extraSmall) {
                                Text(text = focusedParameterId, style = MaterialTheme.typography.labelSmall, color = AppAccent, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        MandalaParameterMatrix(labels = visualSource.parameters.keys.toList(), parameters = visualSource.parameters.values.toList(), focusedParameterId = focusedParameterId, onFocusRequest = { focusedParameterId = it }, onInteractionFinished = onInteraction)
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        InstrumentEditorScreen(source = visualSource, vm = vm, focusedId = focusedParameterId, onFocusChange = { focusedParameterId = it }, onInteractionFinished = onInteraction)
                    }
                }
            }

            if (showManager) {
                PatchManagerOverlay(
                    title = "Manage Mandalas",
                    patches = allPatches.map { it.name to it.name },
                    selectedId = patchName,
                    onSelect = { id ->
                        val entity = allPatches.find { it.name == id }
                        entity?.let {
                            val data = PatchMapper.fromJson(it.jsonSettings)
                            if (data != null) {
                                PatchMapper.applyToVisualSource(data, visualSource)
                                vm.setCurrentPatch(data)
                                // Update layer name in stack
                                val stack = vm.navStack.value
                                val idx = stack.indexOfLast { it.type == LayerType.MANDALA }
                                if (idx != -1) {
                                    vm.updateLayerData(idx, MandalaLayerContent(data))
                                    vm.updateLayerName(idx, data.name)
                                }
                            }
                        }
                    },
                    onRename = { newName ->
                        vm.renamePatch(LayerType.MANDALA, patchName, newName)
                    },
                    onClone = { id ->
                        vm.cloneSavedPatch(LayerType.MANDALA, id)
                    },
                    onDelete = { id ->
                        vm.deleteSavedPatch(LayerType.MANDALA, id)
                    },
                    onClose = onHideManager
                )
            }
        }

        if (showOpenDialog) {
            OpenPatchDialog(vm, onPatchSelected = { 
                PatchMapper.applyToVisualSource(it, visualSource)
                vm.setCurrentPatch(it)
                showOpenDialog = false 
            }, onDismiss = { showOpenDialog = false })
        }
    }

    override fun onPause() { super.onPause(); spiralSurfaceView?.onPause(); audioEngine.stop() }
    override fun onResume() { super.onResume(); spiralSurfaceView?.onResume() }
}

@Composable
fun VisualSourceHeader(
    title: String,
    onMenuClick: () -> Unit,
    menuContent: @Composable BoxScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = AppText,
            modifier = Modifier.padding(4.dp)
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        Box {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = AppText)
            }
            menuContent()
        }
    }
}

@Composable
fun RenamePatchDialog(initialName: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename", color = AppText) },
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
                onRename(name)
            }) {
                Text("RENAME", color = AppAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
            }) {
                Text("CANCEL", color = AppText)
            }
        },
        containerColor = AppBackground
    )
}

@Composable
fun OpenPatchDialog(vm: MandalaViewModel, onPatchSelected: (PatchData) -> Unit, onDismiss: () -> Unit) {
    val allPatches by vm.allPatches.collectAsState(initial = emptyList())
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium, color = AppBackground) {
            Column(modifier = Modifier.padding(16.dp).fillMaxHeight(0.7f)) {
                Text("Saved Patches", style = MaterialTheme.typography.titleLarge, color = AppText)
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    allPatches.forEach { entity ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { 
                            val data = PatchMapper.fromJson(entity.jsonSettings)
                            if (data != null) onPatchSelected(data)
                        }.padding(12.dp)) {
                            Text(entity.name, style = MaterialTheme.typography.bodyLarge, color = AppText)
                        }
                    }
                }
                if (allPatches.isEmpty()) Text("No patches saved yet.", color = AppText.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun OpenMixerDialog(vm: MandalaViewModel, onMixerSelected: (MixerPatch) -> Unit, onDismiss: () -> Unit) {
    val allMixers by vm.allMixerPatches.collectAsState(initial = emptyList())
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium, color = AppBackground) {
            Column(modifier = Modifier.padding(16.dp).fillMaxHeight(0.7f)) {
                Text("Saved Mixers", style = MaterialTheme.typography.titleLarge, color = AppText)
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    allMixers.forEach { entity ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { 
                            val mixer = Json.decodeFromString<MixerPatch>(entity.jsonSettings)
                            onMixerSelected(mixer)
                        }.padding(12.dp)) {
                            Text(entity.name, style = MaterialTheme.typography.bodyLarge, color = AppText)
                        }
                    }
                }
                if (allMixers.isEmpty()) Text("No mixers saved yet.", color = AppText.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun OpenSetDialog(vm: MandalaViewModel, onSetSelected: (MandalaSetEntity) -> Unit, onDismiss: () -> Unit) {
    val allSets by vm.allSets.collectAsState(initial = emptyList())
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium, color = AppBackground) {
            Column(modifier = Modifier.padding(16.dp).fillMaxHeight(0.7f)) {
                Text("Saved Sets", style = MaterialTheme.typography.titleLarge, color = AppText)
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    allSets.forEach { entity ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { 
                            onSetSelected(entity)
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

@Composable
fun OpenShowDialog(vm: MandalaViewModel, onShowSelected: (ShowPatchEntity) -> Unit, onDismiss: () -> Unit) {
    val allShows by vm.allShowPatches.collectAsState(initial = emptyList())
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium, color = AppBackground) {
            Column(modifier = Modifier.padding(16.dp).fillMaxHeight(0.7f)) {
                Text("Saved Shows", style = MaterialTheme.typography.titleLarge, color = AppText)
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    allShows.forEach { entity ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { 
                            onShowSelected(entity)
                        }.padding(12.dp)) {
                            Text(entity.name, style = MaterialTheme.typography.bodyLarge, color = AppText)
                        }
                    }
                }
                if (allShows.isEmpty()) Text("No shows saved yet.", color = AppText.copy(alpha = 0.5f))
            }
        }
    }
}
