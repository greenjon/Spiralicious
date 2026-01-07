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
import androidx.lifecycle.viewmodel.compose.viewModel
import llm.slop.spirals.cv.*
import llm.slop.spirals.cv.audio.*
import llm.slop.spirals.ui.CvLabScreen
import llm.slop.spirals.ui.InstrumentEditorScreen
import llm.slop.spirals.ui.components.ParameterMatrix
import llm.slop.spirals.ui.components.OscilloscopeView
import llm.slop.spirals.ui.theme.AppBackground
import llm.slop.spirals.ui.theme.AppText
import llm.slop.spirals.ui.theme.AppAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
                MandalaScreen { csvData ->
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_SUBJECT, "Mandala Curation Export")
                        putExtra(Intent.EXTRA_TEXT, csvData)
                    }
                    startActivity(Intent.createChooser(intent, "Share Export"))
                }
            }
        }
    }

    @Composable
    fun MandalaScreen(vm: MandalaViewModel = viewModel(), onShare: (String) -> Unit) {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        
        var currentTab by remember { mutableStateOf("Patch Bay") }
        var focusedParameterId by remember { mutableStateOf("L1") }
        var lastLoadedPatch by remember { mutableStateOf<PatchData?>(null) }
        var manualChangeTrigger by remember { mutableIntStateOf(0) }
        var isDirty by remember { mutableStateOf(false) }
        var recipeExpanded by remember { mutableStateOf(false) }

        // Audio State
        var audioSourceType by remember { mutableStateOf(AudioSourceType.MIC) }
        var currentInternalAudioRecord by remember { mutableStateOf<AudioRecord?>(null) }
        var hasMicPermission by remember {
            mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 1. TOP MONITOR AREA
            Column(
                modifier = Modifier
                    .wrapContentHeight()
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                // Header & Patch Management
                var showMenu by remember { mutableStateOf(false) }
                var showOpenDialog by remember { mutableStateOf(false) }
                var showRenameDialog by remember { mutableStateOf(false) }
                var patchName by remember(lastLoadedPatch) { mutableStateOf(lastLoadedPatch?.name ?: "New Patch") }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$patchName${if (isDirty || patchName != (lastLoadedPatch?.name ?: "New Patch")) " *" else ""}",
                        style = MaterialTheme.typography.titleMedium,
                        color = AppText,
                        modifier = Modifier
                            .clickable { showRenameDialog = true }
                            .padding(4.dp)
                    )
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Patch Menu", tint = AppText)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            containerColor = AppBackground
                        ) {
                            DropdownMenuItem(
                                text = { Text("Open", color = AppText) },
                                onClick = { showOpenDialog = true; showMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Save", color = AppText) },
                                onClick = { 
                                    scope.launch { 
                                        val data = PatchMapper.fromVisualSource(patchName, visualSource)
                                        vm.savePatch(data)
                                        lastLoadedPatch = data
                                    }
                                    showMenu = false 
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clone", color = AppText) },
                                onClick = { 
                                    scope.launch { 
                                        val data = PatchMapper.fromVisualSource("$patchName CLONE", visualSource)
                                        vm.savePatch(data)
                                        lastLoadedPatch = data
                                    }
                                    showMenu = false 
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share", color = AppText) },
                                onClick = { 
                                    val data = PatchMapper.fromVisualSource(patchName, visualSource)
                                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { 
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, PatchMapper.toJson(data)) 
                                    }, "Share Patch"))
                                    showMenu = false 
                                },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = AppText) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = Color.Red) },
                                onClick = { 
                                    lastLoadedPatch?.let { vm.deletePatch(it.name) }
                                    lastLoadedPatch = null
                                    showMenu = false 
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                            )
                        }
                    }
                }

                if (showRenameDialog) {
                    RenamePatchDialog(
                        initialName = patchName,
                        onRename = { patchName = it },
                        onDismiss = { showRenameDialog = false }
                    )
                }

                if (showOpenDialog) {
                    OpenPatchDialog(
                        vm, 
                        onPatchSelected = { 
                            PatchMapper.applyToVisualSource(it, visualSource)
                            lastLoadedPatch = it
                            showOpenDialog = false 
                        }, 
                        onDismiss = { showOpenDialog = false }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 2. Mandala Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16 / 9f)
                        .background(Color.Black)
                        .border(1.dp, AppText.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(factory = { ctx -> SpiralSurfaceView(ctx).also { spiralSurfaceView = it; it.setVisualSource(visualSource) } }, modifier = Modifier.fillMaxSize())
                    
                    // Recipe Overlay
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                        Surface(
                            color = AppBackground.copy(alpha = 0.7f),
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier.clickable { recipeExpanded = true }
                        ) {
                            Text(
                                text = "${visualSource.recipe.a}, ${visualSource.recipe.b}, ${visualSource.recipe.c}, ${visualSource.recipe.d} (${visualSource.recipe.petals}P)",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppAccent,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = recipeExpanded,
                            onDismissRequest = { recipeExpanded = false },
                            containerColor = AppBackground,
                            tonalElevation = 0.dp
                        ) {
                            MandalaLibrary.MandalaRatios.forEach { ratio ->
                                DropdownMenuItem(
                                    text = { 
                                        Text("${ratio.a}, ${ratio.b}, ${ratio.c}, ${ratio.d} (${ratio.petals}P)", color = AppText) 
                                    }, 
                                    onClick = { 
                                        visualSource.recipe = ratio
                                        manualChangeTrigger++
                                        recipeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 3. Monitor Row
                val focusedParam = visualSource.parameters[focusedParameterId] ?: visualSource.globalAlpha
                Box(modifier = Modifier.fillMaxWidth().height(60.dp).border(1.dp, AppText.copy(alpha = 0.1f))) {
                    OscilloscopeView(history = focusedParam.history, modifier = Modifier.fillMaxSize())
                    Surface(
                        color = AppBackground.copy(alpha = 0.8f),
                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = focusedParameterId, 
                            style = MaterialTheme.typography.labelSmall, 
                            color = AppAccent,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 4. Parameter Matrix
                ParameterMatrix(
                    labels = visualSource.parameters.keys.toList(),
                    parameters = visualSource.parameters.values.toList(),
                    focusedParameterId = focusedParameterId, 
                    onFocusRequest = { focusedParameterId = it },
                    onInteractionFinished = { manualChangeTrigger++ }
                )
            }

            // 5. BOTTOM CONTROL AREA
            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                TabRow(
                    selectedTabIndex = if (currentTab == "Patch Bay") 0 else 1,
                    containerColor = AppBackground,
                    contentColor = AppAccent
                ) {
                    Tab(
                        selected = currentTab == "Patch Bay", 
                        onClick = { currentTab = "Patch Bay" },
                        unselectedContentColor = AppText.copy(alpha = 0.5f)
                    ) { Text("PATCH", modifier = Modifier.padding(8.dp)) }
                    Tab(
                        selected = currentTab == "CV Lab", 
                        onClick = { currentTab = "CV Lab" },
                        unselectedContentColor = AppText.copy(alpha = 0.5f)
                    ) { Text("DIAG", modifier = Modifier.padding(8.dp)) }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    if (currentTab == "Patch Bay") {
                        InstrumentEditorScreen(
                            source = visualSource, 
                            vm = vm, 
                            focusedId = focusedParameterId, 
                            onFocusChange = { focusedParameterId = it },
                            onInteractionFinished = { manualChangeTrigger++ }
                        ) 
                    } else {
                        CvLabScreen(
                            audioEngine = audioEngine,
                            sourceManager = sourceManager!!,
                            audioSourceType = audioSourceType,
                            onAudioSourceTypeChange = { audioSourceType = it },
                            hasMicPermission = hasMicPermission,
                            onMicPermissionGranted = { hasMicPermission = true },
                            onInternalAudioRecordCreated = { currentInternalAudioRecord = it }
                        )
                    }
                }
            }
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
                TextButton(onClick = { onRename(name); onDismiss() }) {
                    Text("RENAME", color = AppAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("CANCEL", color = AppText)
                }
            },
            containerColor = AppBackground
        )
    }

    override fun onPause() { super.onPause(); spiralSurfaceView?.onPause(); audioEngine.stop() }
    override fun onResume() { super.onResume(); spiralSurfaceView?.onResume() }
}

fun Modifier.minHeight(height: androidx.compose.ui.unit.Dp) = this.defaultMinSize(minHeight = height)
