package llm.slop.spirals

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
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
import llm.slop.spirals.ui.CvLabScreen
import llm.slop.spirals.ui.InstrumentEditorScreen
import llm.slop.spirals.ui.MixerEditorScreen
import llm.slop.spirals.ui.MandalaSetEditorScreen
import llm.slop.spirals.ui.components.MandalaParameterMatrix
import llm.slop.spirals.ui.components.OscilloscopeView
import llm.slop.spirals.ui.components.EditorBreadcrumbs
import llm.slop.spirals.ui.theme.AppAccent
import llm.slop.spirals.ui.theme.AppBackground
import llm.slop.spirals.ui.theme.AppText
import llm.slop.spirals.ui.theme.SpiralsTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import llm.slop.spirals.cv.CvModulator
import llm.slop.spirals.cv.Waveform
import llm.slop.spirals.cv.ModulationOperator
import llm.slop.spirals.cv.CvRegistry
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private lateinit var audioEngine: AudioEngine
    private var spiralSurfaceView: SpiralSurfaceView? = null
    private var sourceManager: MandalaVisualSource? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        audioEngine = AudioEngine(this)
        sourceManager = MandalaVisualSource()
        
        spiralSurfaceView = SpiralSurfaceView(this)
        val renderer = spiralSurfaceView!!.renderer
        renderer.visualSource = sourceManager

        setContent {
            SpiralsTheme {
                val vm: MandalaViewModel = viewModel()
                val navStack by vm.navStack.collectAsState()
                val currentLayer = navStack.last()
                val currentPatch by vm.currentPatch.collectAsState()
                val scope = rememberCoroutineScope()
                
                var showCvLab by remember { mutableStateOf(false) }
                var hasMicPermission by remember { 
                    mutableStateOf(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) 
                }
                
                var audioSourceType by remember { mutableStateOf(AudioSourceType.MIC) }

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
                            factory = { spiralSurfaceView!! },
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
                            onMenuClick = { showHeaderMenu = true }
                        )

                        Box(modifier = Modifier.weight(1f)) {
                            when (currentLayer.type) {
                                LayerType.MIXER -> MixerEditorScreen(
                                    vm = vm, 
                                    onClose = { /* Root layer usually doesn't close */ },
                                    onNavigateToSetEditor = { 
                                        vm.pushLayer(NavLayer("new_set", "New Set", LayerType.SET))
                                    },
                                    onNavigateToMandalaEditor = { 
                                        vm.pushLayer(NavLayer("new_mandala", "New Patch", LayerType.MANDALA))
                                    },
                                    onShowCvLab = { showCvLab = true },
                                    previewContent = previewContent
                                )
                                LayerType.SET -> MandalaSetEditorScreen(
                                    vm = vm, 
                                    onClose = { vm.popToLayer(navStack.size - 2) },
                                    onNavigateToMixerEditor = { /* Navigation handled via breadcrumbs */ },
                                    onShowCvLab = { showCvLab = true },
                                    previewContent = previewContent,
                                    visualSource = sourceManager!!
                                )
                                LayerType.MANDALA -> MandalaEditorScreen(
                                    vm = vm,
                                    visualSource = sourceManager!!,
                                    isDirty = PatchMapper.isDirty(sourceManager!!, currentPatch),
                                    lastLoadedPatch = currentPatch,
                                    onPatchLoaded = { vm.setCurrentPatch(it) },
                                    onInteraction = { /* Generic interaction trigger */ },
                                    onNavigateToSetEditor = { /* Navigation handled via breadcrumbs */ },
                                    onNavigateToMixerEditor = { /* Navigation handled via breadcrumbs */ },
                                    onShowCvLab = { showCvLab = true },
                                    previewContent = previewContent,
                                    showHeader = false // Hide local header, use breadcrumbs
                                )
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
        showHeader: Boolean = true
    ) {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        var focusedParameterId by remember { mutableStateOf("L1") }
        var recipeExpanded by remember { mutableStateOf(false) }
        var showMenu by remember { mutableStateOf(false) }
        var showOpenDialog by remember { mutableStateOf(false) }
        var showRenameDialog by remember { mutableStateOf(false) }
        var patchName by remember(lastLoadedPatch) { mutableStateOf(lastLoadedPatch?.name ?: "New Patch") }

        val renderer = LocalSpiralRenderer.current
        DisposableEffect(renderer) {
            renderer?.visualSource = visualSource
            renderer?.mixerPatch = null
            onDispose {
                renderer?.visualSource = null
            }
        }

        val isModified = remember(isDirty, patchName, lastLoadedPatch) {
            isDirty || patchName != (lastLoadedPatch?.name ?: "New Patch")
        }
        val headerTitle = remember(patchName, isModified) {
            "$patchName${if (isModified) " *" else ""}"
        }

        Column(modifier = Modifier.fillMaxSize()) {
            if (showHeader) {
                VisualSourceHeader(
                    title = headerTitle,
                    onMenuClick = { showMenu = true },
                    menuContent = {
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, containerColor = AppBackground) {
                            DropdownMenuItem(text = { Text("New", color = AppText) }, onClick = { 
                                val defaultRecipe = MandalaLibrary.MandalaRatios.first()
                                visualSource.recipe = defaultRecipe
                                onPatchLoaded(PatchData("New Patch", defaultRecipe.id, emptyList()))
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
                            DropdownMenuItem(text = { Text("Copy to Clipboard", color = AppText) }, onClick = { 
                                val data = PatchMapper.fromVisualSource(patchName, visualSource)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Mandala Patch", PatchMapper.toJson(data)))
                                Toast.makeText(context, "Patch copied to clipboard", Toast.LENGTH_SHORT).show()
                                showMenu = false 
                            }, leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = AppText) })
                            DropdownMenuItem(text = { Text("Share", color = AppText) }, onClick = { 
                                val data = PatchMapper.fromVisualSource(patchName, visualSource)
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, PatchMapper.toJson(data)) }, "Share Patch"))
                                showMenu = false 
                            }, leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = AppText) })
                            DropdownMenuItem(text = { Text("Delete", color = Color.Red) }, onClick = { 
                                lastLoadedPatch?.let { vm.deletePatch(it.name) }
                                onPatchLoaded(PatchData("New Patch", MandalaLibrary.MandalaRatios.first().id, emptyList()))
                                showMenu = false 
                            }, leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) })
                        }
                    }
                )
            }

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

        if (showRenameDialog) {
            RenamePatchDialog(patchName, onRename = { newName ->
                patchName = newName
                lastLoadedPatch?.let { 
                    val updated = it.copy(name = newName)
                    vm.savePatch(updated)
                    vm.setCurrentPatch(updated)
                }
                showRenameDialog = false
            }, onDismiss = { showRenameDialog = false })
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
        title = { Text("Rename Patch", color = AppText) },
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
