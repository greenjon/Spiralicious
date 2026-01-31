package llm.slop.spirals

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioFormat
import android.media.AudioRecord
import android.os.Bundle
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
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.awaitCancellation
import llm.slop.spirals.cv.ModulationRegistry
import llm.slop.spirals.cv.audio.AudioEngine
import llm.slop.spirals.cv.audio.AudioSourceType
import llm.slop.spirals.display.ExternalDisplayCoordinator
import llm.slop.spirals.ui.*
import llm.slop.spirals.ui.components.EditorBreadcrumbs
import llm.slop.spirals.ui.components.HdmiStatusOverlay
import llm.slop.spirals.ui.components.RenamePatchDialog
import llm.slop.spirals.ui.components.SettingsOverlay
import llm.slop.spirals.ui.screens.MandalaEditorScreen
import llm.slop.spirals.ui.theme.AppAccent
import llm.slop.spirals.ui.theme.AppBackground
import llm.slop.spirals.ui.theme.AppText
import llm.slop.spirals.ui.theme.SpiralsTheme

class MainActivity : ComponentActivity() {

    // 1. Add this property to hold the active key listener logic
    private var onKeyDownListener: ((android.view.KeyEvent) -> Boolean)? = null

    private lateinit var audioEngine: AudioEngine
    private var spiralSurfaceView: SpiralSurfaceView? = null
    private var sourceManager: MandalaVisualSource? = null
    private lateinit var displayCoordinator: ExternalDisplayCoordinator

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission result handled via State in Compose
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        // 2. Wrap Window Callback to intercept keys globally
        // This avoids Restricted API issues with overriding dispatchKeyEvent directly on ComponentActivity
        val originalCallback = window.callback
        if (originalCallback != null) {
            window.callback = object : android.view.Window.Callback by originalCallback {
                override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
                    if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                        if (onKeyDownListener?.invoke(event) == true) {
                            return true
                        }
                    }
                    return originalCallback.dispatchKeyEvent(event)
                }
            }
        }
        
        displayCoordinator = ExternalDisplayCoordinator(this)
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
                val configuration = LocalConfiguration.current
                val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                
                val navStack by vm.navStack.collectAsState()
                val currentLayer = navStack.lastOrNull() ?: return@SpiralsTheme
                val currentPatch by vm.currentPatch.collectAsState()
                val scope = rememberCoroutineScope()
                
                var showCvLab by remember { mutableStateOf(false) }
                var showSettings by remember { mutableStateOf(false) }
                var showManager by remember { mutableStateOf(false) }
                
                var isFullscreenPreview by remember { mutableStateOf(false) }

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
                        isFullscreenPreview -> {
                            isFullscreenPreview = false
                        }
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

                // 3. Update the Activity listener on every composition to capture current state
                SideEffect {
                    onKeyDownListener = { event ->
                        val keyCode = event.keyCode
                        
                        when (keyCode) {
                            android.view.KeyEvent.KEYCODE_F -> {
                                isFullscreenPreview = !isFullscreenPreview
                                true
                            }
                            android.view.KeyEvent.KEYCODE_ESCAPE -> {
                                if (isFullscreenPreview) {
                                    isFullscreenPreview = false
                                    true
                                } else false
                            }
                            // Editor Shortcuts
                            android.view.KeyEvent.KEYCODE_Q, 
                            android.view.KeyEvent.KEYCODE_W, 
                            android.view.KeyEvent.KEYCODE_E, 
                            android.view.KeyEvent.KEYCODE_R -> {
                                if (currentLayer.type == LayerType.SHOW) {
                                    val show = (currentLayer.data as? ShowLayerContent)?.show
                                    when (keyCode) {
                                        android.view.KeyEvent.KEYCODE_Q -> {
                                            renderer.getMixerParam("SHOW_PREV")?.triggerPulse()
                                            show?.let { vm.triggerPrevMixer(it.randomSetIds.size) }
                                        }
                                        android.view.KeyEvent.KEYCODE_E -> {
                                            renderer.getMixerParam("SHOW_NEXT")?.triggerPulse()
                                            show?.let { vm.triggerNextMixer(it.randomSetIds.size) }
                                        }
                                        android.view.KeyEvent.KEYCODE_R -> {
                                            renderer.getMixerParam("SHOW_GENERATE")?.triggerPulse()
                                            vm.triggerShowGenerate()
                                        }
                                        android.view.KeyEvent.KEYCODE_W -> {
                                            renderer.getMixerParam("SHOW_RANDOM")?.triggerPulse()
                                            show?.let { 
                                                if (it.randomSetIds.isNotEmpty()) {
                                                    vm.jumpToShowIndex(kotlin.random.Random.nextInt(it.randomSetIds.size))
                                                }
                                            }
                                        }
                                    }
                                    true // Key Handled
                                } else false
                            }
                            else -> false
                        }
                    }
                }

                CompositionLocalProvider(LocalSpiralRenderer provides renderer) {
                    val previewContent = @Composable {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AndroidView(
                                factory = { 
                                    (surfaceView.parent as? android.view.ViewGroup)?.removeView(surfaceView)
                                    surfaceView 
                                },
                                modifier = Modifier.fillMaxSize(),
                                update = {}
                            )
                            
                            val isHdmiConnected by displayCoordinator.isConnected.collectAsState()
                            HdmiStatusOverlay(
                                isConnected = isHdmiConnected,
                                modifier = Modifier.align(Alignment.BottomStart)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AppBackground)
                    ) {
                        // When in fullscreen, we render previewContent here (at root) to fill the screen.
                        // We also keep the Editor UI in the tree (but invisible) so its state/effects persist.
                        if (isFullscreenPreview) {
                            Box(modifier = Modifier.fillMaxSize().zIndex(2f)) {
                                previewContent()
                            }
                        }

                        // The Editor UI
                        // We pass a dummy/empty preview content to the Editor screens when in fullscreen mode
                        // because the real SurfaceView has been moved to the root.
                        val editorPreviewContent: @Composable () -> Unit = if (isFullscreenPreview) {
                            { Spacer(Modifier.fillMaxSize()) }
                        } else {
                            previewContent
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .then(
                                    if (isLandscape) Modifier.widthIn(max = 800.dp).align(Alignment.TopCenter)
                                    else Modifier.fillMaxWidth()
                                )
                                .systemBarsPadding()
                                .graphicsLayer { 
                                    alpha = if (isFullscreenPreview) 0f else 1f 
                                }
                                .zIndex(if (isFullscreenPreview) 0f else 1f)
                                .pointerInput(isFullscreenPreview) {
                                     if (isFullscreenPreview) {
                                         awaitPointerEventScope {
                                             while (true) {
                                                 val event = awaitPointerEvent()
                                                 event.changes.forEach { it.consume() }
                                             }
                                         }
                                     }
                                }
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
                                                // Save menu item - ALWAYS SHOW when there's data
                                                if (hasActiveData) {
                                                    val isDirty = currentLayer.isDirty
                                                    val textColor = if (isDirty) AppAccent else disabledColor

                                                    DropdownMenuItem(
                                                        text = {
                                                            Text("Save", color = textColor)
                                                        },
                                                        onClick = {
                                                            if (isDirty) {
                                                                vm.saveLayer(currentLayer)
                                                                // Optional: Show toast/snackbar feedback
                                                                showHeaderMenu = false
                                                            }
                                                        },
                                                        enabled = isDirty  // Only clickable when dirty
                                                    )
                                                    HorizontalDivider(color = AppText.copy(alpha = 0.1f))
                                                }

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

                                                HorizontalDivider(color = AppText.copy(alpha = 0.1f))
                                                DropdownMenuItem(
                                                    text = { Text("Exit", color = Color.Red) },
                                                    onClick = {
                                                        showHeaderMenu = false
                                                        showExitConfirm = true
                                                    }
                                                )
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
                                                previewContent = editorPreviewContent,
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
                                                previewContent = editorPreviewContent,
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
                                                previewContent = editorPreviewContent,
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
                                                previewContent = editorPreviewContent,
                                                showHeader = false,
                                                showManager = showManager,
                                                onHideManager = { showManager = false }
                                            )
                                        }
                                        LayerType.RANDOM_SET -> {
                                            RandomSetEditorScreen(
                                                vm = vm,
                                                onClose = { vm.popToLayer(navStack.size - 2) },
                                                previewContent = editorPreviewContent,
                                                visualSource = manager,
                                                showManager = showManager,
                                                onHideManager = { showManager = false }
                                            )
                                        }
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
                        onClose = { showSettings = false },
                        displayCoordinator = displayCoordinator
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
                        text = { 
                            val isAnyDirty = navStack.any { it.isDirty }
                            Text(if (isAnyDirty) "Save changes and exit?" else "Exit the application?", color = AppText) 
                        },
                        confirmButton = {
                            TextButton(onClick = { 
                                // Auto-save all dirty layers before finishing
                                navStack.forEach { layer ->
                                    if (layer.isDirty) {
                                        vm.saveLayer(layer)
                                    }
                                }
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

    override fun onStart() {
        super.onStart()
        displayCoordinator.start()
    }

    override fun onStop() {
        super.onStop()
        displayCoordinator.stop()
    }

    override fun onPause() { super.onPause(); spiralSurfaceView?.onPause(); audioEngine.stop() }
    override fun onResume() { super.onResume(); spiralSurfaceView?.onResume() }
}
