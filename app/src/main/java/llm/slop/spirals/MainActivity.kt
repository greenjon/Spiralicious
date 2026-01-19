package llm.slop.spirals

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.media.AudioFormat
import android.media.AudioRecord
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import llm.slop.spirals.ui.components.RecipePickerDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import llm.slop.spirals.cv.ModulationRegistry
import llm.slop.spirals.defaults.DefaultsConfig
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
                
                // Auto-show Manage overlay when opening from menu
                LaunchedEffect(currentLayer.id, currentLayer.openedFromMenu) {
                    if (currentLayer.openedFromMenu && !showManager) {
                        showManager = true
                        // Clear the flag after showing once
                        val index = navStack.indexOfLast { it.id == currentLayer.id }
                        if (index != -1) {
                            vm.clearOpenedFromMenuFlag(index)
                        }
                    }
                }
                
                var audioSourceType by remember { mutableStateOf(AudioSourceType.MIC) }
                var showRenameDialog by remember { mutableStateOf(false) }
                var showDeleteConfirm by remember { mutableStateOf(false) }
                var showExitConfirm by remember { mutableStateOf(false) }

                // Back button handler
                BackHandler {
                    when {
                        // 1. Library open + has active data → Close Library
                        showManager && currentLayer.data != null -> {
                            showManager = false
                        }
                        // 2. Library open + no active data → Exit with confirmation
                        showManager && currentLayer.data == null -> {
                            showExitConfirm = true
                        }
                        // 3. CV Lab open → Close CV Lab
                        showCvLab -> {
                            showCvLab = false
                        }
                        // 4. Settings open → Close Settings
                        showSettings -> {
                            showSettings = false
                        }
                        // 5. Any dialog open → Close it
                        showRenameDialog -> {
                            showRenameDialog = false
                        }
                        showDeleteConfirm -> {
                            showDeleteConfirm = false
                        }
                        showExitConfirm -> {
                            showExitConfirm = false
                        }
                        // 6. In child editor → Pop to parent with cascade save+link
                        navStack.size > 1 -> {
                            vm.popToLayer(navStack.size - 2, save = true)
                        }
                        // 7. At root → Exit with confirmation
                        else -> {
                            showExitConfirm = true
                        }
                    }
                }

                // 1. Start the CV Sync Registry immediately
                LaunchedEffect(Unit) {
                    ModulationRegistry.startSync(this)
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
                                        val isAtRoot = navStack.size == 1
                                        val editorName = when(currentLayer.type) {
                                            LayerType.MIXER -> "Mixer"
                                            LayerType.SET -> "Set"
                                            LayerType.MANDALA -> "Mandala"
                                            LayerType.SHOW -> "Show"
                                            LayerType.RANDOM_SET -> "RSet"
                                        }

                                        // --- TOP GROUP ---
                                        DropdownMenuItem(
                                            text = { Text("Library", color = if (isAtRoot) AppAccent else disabledColor) },
                                            onClick = { 
                                                if (isAtRoot) {
                                                    showManager = true
                                                    showHeaderMenu = false
                                                }
                                            },
                                            enabled = isAtRoot
                                        )
                                        DropdownMenuItem(
                                            text = { Text("New $editorName", color = if (isAtRoot) AppAccent else disabledColor) },
                                            onClick = { 
                                                if (isAtRoot) {
                                                    vm.startNewPatch(currentLayer.type)
                                                    showHeaderMenu = false
                                                }
                                            },
                                            enabled = isAtRoot
                                        )

                                        HorizontalDivider(color = AppText.copy(alpha = 0.1f))

                                        // --- CURRENT ITEM ACTIONS ---
                                        DropdownMenuItem(
                                            text = { Text("Rename", color = if (hasActiveData) AppText else disabledColor) }, 
                                            onClick = { showRenameDialog = true; showHeaderMenu = false },
                                            enabled = hasActiveData
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete", color = if (hasActiveData && isAtRoot) Color.Red else disabledColor) }, 
                                            onClick = { 
                                                if (isAtRoot) {
                                                    showDeleteConfirm = true
                                                    showHeaderMenu = false
                                                }
                                            },
                                            enabled = hasActiveData && isAtRoot
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
                                                LayerType.RANDOM_SET -> "RSet Editor"
                                            }
                                            DropdownMenuItem(
                                                text = { 
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text("  • ", color = if (isAtRoot) AppAccent else disabledColor)
                                                        Text(label, color = if (isAtRoot) AppAccent else disabledColor)
                                                    }
                                                },
                                                onClick = { 
                                                    if (isAtRoot) {
                                                        vm.createAndResetStack(type, openedFromMenu = true)
                                                        showHeaderMenu = false
                                                    }
                                                },
                                                enabled = isAtRoot
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
                                LayerType.RANDOM_SET -> {
                                    RandomSetEditorScreen(
                                        vm = vm,
                                        onClose = { vm.popToLayer(navStack.size - 2) },
                                        previewContent = previewContent,
                                        visualSource = manager,
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

                if (showExitConfirm) {
                    AlertDialog(
                        onDismissRequest = { showExitConfirm = false },
                        title = { Text("Exit Spirals?", color = AppText) },
                        text = { Text("All changes have been saved.", color = AppText) },
                        confirmButton = {
                            TextButton(onClick = { 
                                finish()
                            }) {
                                Text("EXIT", color = AppText)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showExitConfirm = false }) {
                                Text("STAY", color = AppAccent)
                            }
                        },
                        containerColor = AppBackground
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
        val context = LocalContext.current
        val defaultsConfig = remember { DefaultsConfig.getInstance(context) }
        var showGlobalDefaults by remember { mutableStateOf(false) }
        
        if (showGlobalDefaults) {
            Dialog(
                onDismissRequest = { showGlobalDefaults = false },
                properties = androidx.compose.ui.window.DialogProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false
                )
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = AppBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppText.copy(alpha = 0.1f))
                ) {
                    llm.slop.spirals.ui.settings.GlobalDefaultsScreen(
                        defaultsConfig = defaultsConfig,
                        onClose = { showGlobalDefaults = false }
                    )
                }
            }
        } else {
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
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider(color = AppText.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Button(
                            onClick = { showGlobalDefaults = true },
                            colors = ButtonDefaults.buttonColors(containerColor = AppAccent),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Randomization Defaults")
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
        
        // Get the current layer name from the nav stack (handles renames)
        val navStack by vm.navStack.collectAsState()
        val currentLayer = navStack.lastOrNull { it.type == LayerType.MANDALA }
        val patchName = currentLayer?.name ?: lastLoadedPatch?.name ?: "New Patch"
        
        val allPatches by vm.allPatches.collectAsState(initial = emptyList())
        var recipeSortMode by remember { mutableStateOf(llm.slop.spirals.ui.components.RecipeSortMode.PETALS) }

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
        LaunchedEffect(patchName, visualSource.recipe, visualSource.parameters.values.map { it.value }) {
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
                            }
                            
                            // Randomize button (center-left)
                            IconButton(
                                onClick = {
                                    randomizeMandala(visualSource)
                                    onInteraction()
                                },
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(8.dp)
                                    .size(48.dp)
                                    .background(AppBackground.copy(alpha = 0.7f), MaterialTheme.shapes.small)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Randomize",
                                    tint = AppAccent,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            
                            // Star/Trash buttons on the right side
                            Column(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val tagManager = remember { RecipeTagManager(applicationContext) }
                                var refreshTrigger by remember { mutableIntStateOf(0) }
                                val isFavorite = remember(visualSource.recipe.id, refreshTrigger) { 
                                    tagManager.isFavorite(visualSource.recipe.id) 
                                }
                                val isTrash = remember(visualSource.recipe.id, refreshTrigger) { 
                                    tagManager.isTrash(visualSource.recipe.id) 
                                }
                                
                                IconButton(
                                    onClick = {
                                        tagManager.toggleFavorite(visualSource.recipe.id)
                                        refreshTrigger++
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(AppBackground.copy(alpha = 0.7f), MaterialTheme.shapes.small)
                                ) {
                                    Icon(
                                        if (isFavorite) Icons.Filled.Star else Icons.Default.Star,
                                        contentDescription = "Toggle Favorite",
                                        tint = if (isFavorite) androidx.compose.ui.graphics.Color(0xFFFFD700) else AppText.copy(alpha = 0.5f),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        tagManager.toggleTrash(visualSource.recipe.id)
                                        refreshTrigger++
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(AppBackground.copy(alpha = 0.7f), MaterialTheme.shapes.small)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Toggle Trash",
                                        tint = if (isTrash) androidx.compose.ui.graphics.Color.Red else AppText.copy(alpha = 0.5f),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            
                            // Navigation arrows (lower right)
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val tagManager = remember { RecipeTagManager(applicationContext) }
                                val sortedRecipes = remember(recipeSortMode) {
                                    val favorites = tagManager.getFavorites()
                                    val trash = tagManager.getTrash()
                                    when (recipeSortMode) {
                                        llm.slop.spirals.ui.components.RecipeSortMode.PETALS -> MandalaLibrary.MandalaRatios.sortedBy { it.petals }
                                        llm.slop.spirals.ui.components.RecipeSortMode.FAVORITES -> {
                                            val faves = MandalaLibrary.MandalaRatios.filter { favorites.contains(it.id) }
                                                .sortedWith(compareBy({ it.petals }, { it.id }))
                                            val rest = MandalaLibrary.MandalaRatios.filter { !favorites.contains(it.id) }
                                                .sortedWith(compareBy({ it.petals }, { it.id }))
                                            faves + rest
                                        }
                                        llm.slop.spirals.ui.components.RecipeSortMode.TO_DELETE -> {
                                            val trashItems = MandalaLibrary.MandalaRatios.filter { trash.contains(it.id) }
                                                .sortedBy { it.id }
                                            val rest = MandalaLibrary.MandalaRatios.filter { !trash.contains(it.id) }
                                                .sortedBy { it.id }
                                            trashItems + rest
                                        }
                                        llm.slop.spirals.ui.components.RecipeSortMode.SHAPE_RATIO -> MandalaLibrary.MandalaRatios.sortedBy { it.shapeRatio }
                                        llm.slop.spirals.ui.components.RecipeSortMode.MULTIPLICITY -> MandalaLibrary.MandalaRatios.sortedBy { it.multiplicityClass }
                                        llm.slop.spirals.ui.components.RecipeSortMode.FREQ_COUNT -> MandalaLibrary.MandalaRatios.sortedBy { it.independentFreqCount }
                                        llm.slop.spirals.ui.components.RecipeSortMode.HIERARCHY -> MandalaLibrary.MandalaRatios.sortedBy { it.hierarchyDepth }
                                        llm.slop.spirals.ui.components.RecipeSortMode.DOMINANCE -> MandalaLibrary.MandalaRatios.sortedBy { it.dominanceRatio }
                                        llm.slop.spirals.ui.components.RecipeSortMode.RADIAL_VAR -> MandalaLibrary.MandalaRatios.sortedBy { it.radialVariance }
                                    }
                                }
                                val currentIndex = sortedRecipes.indexOfFirst { it.id == visualSource.recipe.id }
                                
                                IconButton(
                                    onClick = {
                                        if (currentIndex > 0) {
                                            visualSource.recipe = sortedRecipes[currentIndex - 1]
                                            onInteraction()
                                        }
                                    },
                                    enabled = currentIndex > 0,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowLeft,
                                        contentDescription = "Previous Recipe",
                                        tint = if (currentIndex > 0) AppAccent else AppText.copy(alpha = 0.3f)
                                    )
                                }
                                
                                IconButton(
                                    onClick = {
                                        if (currentIndex < sortedRecipes.size - 1) {
                                            visualSource.recipe = sortedRecipes[currentIndex + 1]
                                            onInteraction()
                                        }
                                    },
                                    enabled = currentIndex < sortedRecipes.size - 1,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowRight,
                                        contentDescription = "Next Recipe",
                                        tint = if (currentIndex < sortedRecipes.size - 1) AppAccent else AppText.copy(alpha = 0.3f)
                                    )
                                }
                            }
                            
                            if (recipeExpanded) {
                                RecipePickerDialog(
                                    currentRecipe = visualSource.recipe,
                                    initialSortMode = recipeSortMode,
                                    onRecipeSelected = { ratio ->
                                        visualSource.recipe = ratio
                                        onInteraction()
                                        recipeExpanded = false
                                    },
                                    onSortModeChanged = { mode ->
                                        recipeSortMode = mode
                                    },
                                    onDismiss = { recipeExpanded = false }
                                )
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
                        // Preview instantly on tap
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
                    onOpen = { id ->
                        // Open and close overlay
                        val entity = allPatches.find { it.name == id }
                        entity?.let {
                            val data = PatchMapper.fromJson(it.jsonSettings)
                            if (data != null) {
                                PatchMapper.applyToVisualSource(data, visualSource)
                                vm.setCurrentPatch(data)
                                val stack = vm.navStack.value
                                val idx = stack.indexOfLast { it.type == LayerType.MANDALA }
                                if (idx != -1) {
                                    vm.updateLayerData(idx, MandalaLayerContent(data))
                                    vm.updateLayerName(idx, data.name)
                                }
                                onHideManager()
                            }
                        }
                    },
                    onCreateNew = {
                        vm.startNewPatch(LayerType.MANDALA)
                        onHideManager()
                    },
                    onRename = { newName ->
                        vm.renamePatch(LayerType.MANDALA, patchName, newName)
                    },
                    onClone = { id ->
                        vm.cloneSavedPatch(LayerType.MANDALA, id)
                    },
                    onDelete = { id ->
                        vm.deleteSavedPatch(LayerType.MANDALA, id)
                    }
                )
            }
        }

    }

    private fun randomizeMandala(visualSource: MandalaVisualSource) {
        val random = kotlin.random.Random.Default
        
        // 1. Random recipe
        visualSource.recipe = MandalaLibrary.MandalaRatios.random()
        
        // 2. Hue Sweep = petals (scaled by 9.0 in render code)
        visualSource.parameters["Hue Sweep"]?.let { param ->
            param.baseValue = visualSource.recipe.petals / 9.0f
            param.modulators.clear()
        }
        
        // 3. L1-L4 (arm lengths)
        listOf("L1", "L2", "L3", "L4").forEach { paramName ->
            visualSource.parameters[paramName]?.let { param ->
                param.baseValue = 0.2f // Base 20%
                param.modulators.clear()
                param.modulators.add(
                    llm.slop.spirals.cv.CvModulator(
                        sourceId = "beatPhase",
                        operator = llm.slop.spirals.cv.ModulationOperator.ADD,
                        waveform = if (random.nextBoolean()) llm.slop.spirals.cv.Waveform.SINE else llm.slop.spirals.cv.Waveform.TRIANGLE,
                        slope = 0.5f,
                        weight = random.nextFloat() * 0.5f + 0.1f, // 10-60% -> 0.1-0.6
                        phaseOffset = random.nextFloat(),
                        subdivision = random.nextInt(8, 33).toFloat() // 8-32
                    )
                )
            }
        }
        
        // 4. Rotation
        visualSource.parameters["Rotation"]?.let { param ->
            param.baseValue = 0f
            param.modulators.clear()
            param.modulators.add(
                llm.slop.spirals.cv.CvModulator(
                    sourceId = "beatPhase",
                    operator = llm.slop.spirals.cv.ModulationOperator.ADD,
                    waveform = llm.slop.spirals.cv.Waveform.TRIANGLE,
                    slope = if (random.nextBoolean()) 0f else 1f, // 0 or 100
                    weight = 1.0f, // 100%
                    phaseOffset = random.nextFloat(),
                    subdivision = random.nextInt(4, 129).toFloat() // 4-128
                )
            )
        }
        
        // 5. Hue Offset
        visualSource.parameters["Hue Offset"]?.let { param ->
            param.baseValue = 0f
            param.modulators.clear()
            param.modulators.add(
                llm.slop.spirals.cv.CvModulator(
                    sourceId = "beatPhase",
                    operator = llm.slop.spirals.cv.ModulationOperator.ADD,
                    waveform = llm.slop.spirals.cv.Waveform.TRIANGLE,
                    slope = if (random.nextBoolean()) 0f else 1f, // 0 or 100
                    weight = 1.0f, // 100%
                    phaseOffset = random.nextFloat(),
                    subdivision = random.nextInt(4, 17).toFloat() // 4-16
                )
            )
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

