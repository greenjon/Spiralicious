package llm.slop.spirals

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.media.AudioFormat
import android.media.AudioRecord
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import llm.slop.spirals.cv.*
import llm.slop.spirals.cv.audio.*
import llm.slop.spirals.ui.*
import llm.slop.spirals.ui.components.MandalaParameterMatrix
import llm.slop.spirals.ui.components.OscilloscopeView
import llm.slop.spirals.ui.theme.AppBackground
import llm.slop.spirals.ui.theme.AppText
import llm.slop.spirals.ui.theme.AppAccent
import kotlinx.coroutines.launch

enum class AppScreen {
    MANDALA_EDITOR,
    MANDALA_SET_EDITOR,
    MIXER_EDITOR
}

class MainActivity : ComponentActivity() {
    private var spiralSurfaceView: SpiralSurfaceView? = null
    private val visualSource = MandalaVisualSource()
    
    private val audioEngine = AudioEngine()
    private var sourceManager: AudioSourceManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceManager = AudioSourceManager(this)
        
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AppBackground.toArgb(), AppBackground.toArgb()),
            navigationBarStyle = SystemBarStyle.light(AppBackground.toArgb(), AppBackground.toArgb())
        )
        
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                background = AppBackground,
                surface = AppBackground,
                surfaceVariant = AppBackground
            )) {
                AppRoot() 
            }
        }
    }

    @Composable
    fun AppRoot() {
        val vm: MandalaViewModel by viewModels()
        var currentScreen by remember { mutableStateOf(AppScreen.MANDALA_EDITOR) }
        
        val scope = rememberCoroutineScope()
        
        var lastLoadedPatch by remember { mutableStateOf<PatchData?>(null) }
        var manualChangeTrigger by remember { mutableIntStateOf(0) }
        var isDirty by remember { mutableStateOf(false) }
        
        // UI Navigation State
        var showCvLab by remember { mutableStateOf(false) }

        // Audio State
        var audioSourceType by remember { mutableStateOf(AudioSourceType.MIC) }
        var currentInternalAudioRecord by remember { mutableStateOf<AudioRecord?>(null) }
        val context = LocalContext.current
        var hasMicPermission by remember {
            mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        }

        val renderer = remember { mutableStateOf<SpiralRenderer?>(null) }

        // Shared preview content
        val previewContent = @Composable { 
            AndroidView(
                factory = { ctx -> 
                    SpiralSurfaceView(ctx).also { 
                        spiralSurfaceView = it
                        renderer.value = it.renderer
                        it.setVisualSource(visualSource)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Global Lifecycle
        LaunchedEffect(Unit) { CvRegistry.startSync(scope) }
        
        // Throttled Dirty Check
        LaunchedEffect(lastLoadedPatch, manualChangeTrigger) {
            isDirty = PatchMapper.isDirty(visualSource, lastLoadedPatch)
        }

        LaunchedEffect(audioSourceType, hasMicPermission, currentInternalAudioRecord) {
            when (audioSourceType) {
                AudioSourceType.MIC, AudioSourceType.UNPROCESSED -> {
                    if (hasMicPermission) {
                        val record = sourceManager?.buildAudioRecord(
                            audioSourceType, 44100, AudioFormat.ENCODING_PCM_FLOAT, 
                            AudioFormat.CHANNEL_IN_MONO, 
                            AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
                        )
                        audioEngine.start(scope, record)
                    } else { audioEngine.stop() }
                }
                AudioSourceType.INTERNAL -> {
                    if (currentInternalAudioRecord != null) {
                        audioEngine.start(scope, currentInternalAudioRecord)
                    } else { audioEngine.stop() }
                }
            }
        }

        CompositionLocalProvider(LocalSpiralRenderer provides renderer.value) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppBackground)
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                 when (currentScreen) {
                    AppScreen.MANDALA_EDITOR -> {
                        MandalaEditorScreen(
                            vm = vm,
                            visualSource = visualSource,
                            isDirty = isDirty,
                            lastLoadedPatch = lastLoadedPatch,
                            onPatchLoaded = { lastLoadedPatch = it },
                            onInteraction = { manualChangeTrigger++ },
                            onNavigateToSetEditor = { currentScreen = AppScreen.MANDALA_SET_EDITOR },
                            onNavigateToMixerEditor = { currentScreen = AppScreen.MIXER_EDITOR },
                            onShowCvLab = { showCvLab = true },
                            previewContent = previewContent
                        )
                    }
                    AppScreen.MANDALA_SET_EDITOR -> {
                        MandalaSetEditorScreen(
                            onClose = { currentScreen = AppScreen.MANDALA_EDITOR },
                            onNavigateToMixerEditor = { currentScreen = AppScreen.MIXER_EDITOR },
                            onShowCvLab = { showCvLab = true },
                            previewContent = previewContent,
                            visualSource = visualSource
                        )
                    }
                    AppScreen.MIXER_EDITOR -> {
                        MixerEditorScreen(
                            vm = vm,
                            onClose = { currentScreen = AppScreen.MANDALA_EDITOR },
                            onNavigateToSetEditor = { currentScreen = AppScreen.MANDALA_SET_EDITOR },
                            onNavigateToMandalaEditor = { currentScreen = AppScreen.MANDALA_EDITOR },
                            onShowCvLab = { showCvLab = true },
                            previewContent = previewContent
                        )
                    }
                }

                // Overlays
                androidx.compose.animation.AnimatedVisibility(
                    visible = showCvLab,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    CvLabScreen(
                        audioEngine = audioEngine,
                        sourceManager = sourceManager!!,
                        audioSourceType = audioSourceType,
                        onAudioSourceTypeChange = { audioSourceType = it },
                        hasMicPermission = hasMicPermission,
                        onMicPermissionGranted = { hasMicPermission = true },
                        onInternalAudioRecordCreated = { currentInternalAudioRecord = it },
                        onClose = { showCvLab = false }
                    )
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
        previewContent: @Composable () -> Unit
    ) {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        var focusedParameterId by remember { mutableStateOf("L1") }
        var recipeExpanded by remember { mutableStateOf(false) }
        var showMenu by remember { mutableStateOf(false) }
        var showOpenDialog by remember { mutableStateOf(false) }
        var showRenameDialog by remember { mutableStateOf(false) }
        var patchName by remember(lastLoadedPatch) { mutableStateOf(lastLoadedPatch?.name ?: "New Patch") }

        // Ensure renderer is pointing to our visualSource when this screen is active
        val renderer = LocalSpiralRenderer.current
        LaunchedEffect(renderer) {
            renderer?.visualSource = visualSource
            renderer?.mixerPatch = null
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$patchName${if (isDirty || patchName != (lastLoadedPatch?.name ?: "New Patch")) " *" else ""}",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppText,
                    modifier = Modifier.padding(4.dp)
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = AppText)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, containerColor = AppBackground) {
                        DropdownMenuItem(text = { Text("New", color = AppText) }, onClick = { 
                            val defaultRecipe = MandalaLibrary.MandalaRatios.first()
                            visualSource.recipe = defaultRecipe
                            onPatchLoaded(PatchData("New Patch", defaultRecipe.id, emptyList<ParameterData>()))
                            showMenu = false 
                        })
                        HorizontalDivider(color = AppText.copy(alpha = 0.1f))
                        DropdownMenuItem(text = { Text("CV Lab", color = AppAccent) }, onClick = { onShowCvLab(); showMenu = false }, leadingIcon = { Icon(Icons.Default.Build, contentDescription = null, tint = AppAccent) })
                        DropdownMenuItem(text = { Text("Mandala Set Editor", color = AppAccent) }, onClick = { onNavigateToSetEditor(); showMenu = false }, leadingIcon = { Icon(Icons.Default.List, contentDescription = null, tint = AppAccent) })
                        DropdownMenuItem(text = { Text("Mixer Editor", color = AppAccent) }, onClick = { onNavigateToMixerEditor(); showMenu = false }, leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = AppAccent) })
                        HorizontalDivider(color = AppText.copy(alpha = 0.1f))
                        DropdownMenuItem(text = { Text("Open", color = AppText) }, onClick = { showOpenDialog = true; showMenu = false })
                        DropdownMenuItem(text = { Text("Save", color = AppText) }, onClick = { 
                            scope.launch { 
                                val data = PatchMapper.fromVisualSource(patchName, visualSource)
                                vm.savePatch(data)
                                onPatchLoaded(data)
                            }
                            showMenu = false 
                        })
                        DropdownMenuItem(text = { Text("Rename", color = AppText) }, onClick = { showRenameDialog = true; showMenu = false })
                        DropdownMenuItem(text = { Text("Clone", color = AppText) }, onClick = { 
                            scope.launch { 
                                val data = PatchMapper.fromVisualSource("$patchName CLONE", visualSource)
                                vm.savePatch(data)
                                onPatchLoaded(data)
                            }
                            showMenu = false 
                        })
                        DropdownMenuItem(text = { Text("Share", color = AppText) }, onClick = { 
                            val data = PatchMapper.fromVisualSource(patchName, visualSource)
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, PatchMapper.toJson(data)) }, "Share Patch"))
                            showMenu = false 
                        }, leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = AppText) })
                        DropdownMenuItem(text = { Text("Delete", color = Color.Red) }, onClick = { 
                            lastLoadedPatch?.let { vm.deletePatch(it.name) }
                            onPatchLoaded(PatchData("New Patch", MandalaLibrary.MandalaRatios.first().id, emptyList<ParameterData>()))
                            showMenu = false 
                        }, leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) })
                    }
                }
            }

            // Main content area
            Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                // Monitor Area
                Column(modifier = Modifier.wrapContentHeight().fillMaxWidth().padding(horizontal = 8.dp)) {
                    // Mandala Preview
                    Box(modifier = Modifier.fillMaxWidth().aspectRatio(16 / 9f).background(Color.Black).border(1.dp, AppText.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                        previewContent()
                        
                        // Recipe Overlay
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

                    // Monitor Row
                    val focusedParam = visualSource.parameters[focusedParameterId] ?: visualSource.globalAlpha
                    Box(modifier = Modifier.fillMaxWidth().height(60.dp).border(1.dp, AppText.copy(alpha = 0.1f))) {
                        OscilloscopeView(history = focusedParam.history, modifier = Modifier.fillMaxSize())
                        Surface(color = AppBackground.copy(alpha = 0.8f), modifier = Modifier.align(Alignment.TopStart).padding(4.dp), shape = MaterialTheme.shapes.extraSmall) {
                            Text(text = focusedParameterId, style = MaterialTheme.typography.labelSmall, color = AppAccent, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Parameter Matrix
                    MandalaParameterMatrix(labels = visualSource.parameters.keys.toList(), parameters = visualSource.parameters.values.toList(), focusedParameterId = focusedParameterId, onFocusRequest = { focusedParameterId = it }, onInteractionFinished = onInteraction)
                }

                // Instrument Editor Area (Filling remaining space)
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    InstrumentEditorScreen(source = visualSource, vm = vm, focusedId = focusedParameterId, onFocusChange = { focusedParameterId = it }, onInteractionFinished = onInteraction)
                }
            }
        }

        // Dialogs
        if (showRenameDialog) {
            RenamePatchDialog(initialName = patchName, onRename = { patchName = it }, onDismiss = { showRenameDialog = false })
        }

        if (showOpenDialog) {
            OpenPatchDialog(vm, onPatchSelected = { 
                PatchMapper.applyToVisualSource(it, visualSource)
                onPatchLoaded(it)
                showOpenDialog = false 
            }, onDismiss = { showOpenDialog = false })
        }
    }

    @Composable
    fun OpenPatchDialog(vm: MandalaViewModel, onPatchSelected: (PatchData) -> Unit, onDismiss: () -> Unit) {
        val patches by vm.allPatches.collectAsState(initial = emptyList())
        Dialog(onDismissRequest = onDismiss) {
            Surface(shape = MaterialTheme.shapes.medium, color = AppBackground) {
                Column(modifier = Modifier.padding(16.dp).fillMaxHeight(0.7f)) {
                    Text("Saved Patches", style = MaterialTheme.typography.titleLarge, color = AppText)
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        patches.forEach { entity ->
                            Row(modifier = Modifier.fillMaxWidth().clickable { 
                                val patchData = PatchMapper.fromJson(entity.jsonSettings)
                                if (patchData != null) onPatchSelected(patchData) 
                            }.padding(12.dp)) {
                                Text(entity.name, style = MaterialTheme.typography.bodyLarge, color = AppText)
                            }
                        }
                    }
                    if (patches.isEmpty()) Text("No patches saved yet.", color = AppText.copy(alpha = 0.5f))
                }
            }
        }
    }

    @Composable
    fun RenamePatchDialog(initialName: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
        var name by remember { mutableStateOf(initialName) }
        AlertDialog(onDismissRequest = onDismiss, title = { Text("Rename Patch", color = AppText) }, text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppAccent, unfocusedTextColor = AppText, focusedTextColor = AppText, cursorColor = AppAccent))
        }, confirmButton = {
            TextButton(onClick = { onRename(name); onDismiss() }) { Text("RENAME", color = AppAccent) }
        }, dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = AppText) }
        }, containerColor = AppBackground)
    }

    override fun onPause() { super.onPause(); spiralSurfaceView?.onPause(); audioEngine.stop() }
    override fun onResume() { super.onResume(); spiralSurfaceView?.onResume() }
}
