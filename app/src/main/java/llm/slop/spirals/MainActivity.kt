package llm.slop.spirals

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.media.AudioFormat
import android.media.AudioRecord
import androidx.activity.ComponentActivity
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
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
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
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 1. TOP MONITOR AREA (Fixed size parts, Header can push everything down)
            Column(
                modifier = Modifier
                    .wrapContentHeight()
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                // Collapsible Header & Patch Management
                var isHeaderExpanded by remember { mutableStateOf(false) }
                var showOpenDialog by remember { mutableStateOf(false) }
                
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().clickable { isHeaderExpanded = !isHeaderExpanded }.padding(bottom = 2.dp),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "${lastLoadedPatch?.name ?: "New Patch"}${if (isDirty) " *" else ""}", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(text = "${visualSource.recipe.a}, ${visualSource.recipe.b}, ${visualSource.recipe.c}, ${visualSource.recipe.d} (${visualSource.recipe.petals}P)", style = MaterialTheme.typography.labelSmall, color = Color.Cyan)
                        }
                        if (isHeaderExpanded) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Change Recipe", style = MaterialTheme.typography.labelLarge, color = Color.Cyan)
                            var petalFilter by remember { mutableStateOf<Int?>(null) }
                            var filterExpanded by remember { mutableStateOf(false) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box {
                                    TextButton(onClick = { filterExpanded = true }) { Text(if (petalFilter == null) "All Petals" else "${petalFilter}P") }
                                    DropdownMenu(expanded = filterExpanded, onDismissRequest = { filterExpanded = false }) {
                                        DropdownMenuItem(text = { Text("All") }, onClick = { petalFilter = null; filterExpanded = false })
                                        listOf(3, 4, 5, 6, 7, 8, 9, 10, 12, 16).forEach { p -> DropdownMenuItem(text = { Text("${p}P") }, onClick = { petalFilter = p; filterExpanded = false }) }
                                    }
                                }
                                var recipeExpanded by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.weight(1f)) {
                                    Button(onClick = { recipeExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text("Select Ratio...") }
                                    DropdownMenu(expanded = recipeExpanded, onDismissRequest = { recipeExpanded = false }) {
                                        val filtered = MandalaLibrary.MandalaRatios.filter { petalFilter == null || it.petals == petalFilter }.take(100)
                                        filtered.forEach { ratio ->
                                            DropdownMenuItem(text = { Text("${ratio.a}, ${ratio.b}, ${ratio.c}, ${ratio.d} (${ratio.petals}P)") }, onClick = { 
                                                visualSource.recipe = ratio
                                                manualChangeTrigger++
                                                recipeExpanded = false
                                                isHeaderExpanded = false 
                                            } )
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.DarkGray)
                            Text("Patch Management", style = MaterialTheme.typography.labelLarge, color = Color.Cyan)
                            var nameInput by remember(lastLoadedPatch) { mutableStateOf(lastLoadedPatch?.name ?: "New Patch") }
                            OutlinedTextField(value = nameInput, onValueChange = { nameInput = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Patch Name") }, textStyle = MaterialTheme.typography.bodySmall)
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Row {
                                    Button(onClick = { scope.launch { val data = PatchMapper.fromVisualSource(nameInput, visualSource); vm.savePatch(data); lastLoadedPatch = data; isHeaderExpanded = false } }) { Text("Save") }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(onClick = { val data = PatchMapper.fromVisualSource(nameInput, visualSource); context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, PatchMapper.toJson(data)) }, "Share Patch")) }) { Icon(Icons.Default.Share, contentDescription = null, tint = Color.White) }
                                }
                                Row {
                                    Button(onClick = { showOpenDialog = true }) { Text("Open") }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Button(onClick = { scope.launch { val data = PatchMapper.fromVisualSource("$nameInput CLONE", visualSource); vm.savePatch(data); lastLoadedPatch = data; isHeaderExpanded = false } }) { Text("Clone") }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(onClick = { lastLoadedPatch?.let { vm.deletePatch(it.name); lastLoadedPatch = null; isHeaderExpanded = false } }) { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                                }
                            }
                        }
                    }
                }

                if (showOpenDialog) {
                    OpenPatchDialog(vm, onPatchSelected = { PatchMapper.applyToVisualSource(it, visualSource); lastLoadedPatch = it; showOpenDialog = false; isHeaderExpanded = false }, onDismiss = { showOpenDialog = false })
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 2. Mandala Preview (Height determined by aspect ratio)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16 / 9f)
                        .background(Color.Black)
                        .border(1.dp, Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(factory = { ctx -> SpiralSurfaceView(ctx).also { spiralSurfaceView = it; it.setVisualSource(visualSource) } }, modifier = Modifier.fillMaxSize())
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 3. Monitor Row
                val focusedParam = visualSource.parameters[focusedParameterId] ?: visualSource.globalAlpha
                Box(modifier = Modifier.fillMaxWidth().height(60.dp).border(1.dp, Color.DarkGray)) {
                    OscilloscopeView(history = focusedParam.history, modifier = Modifier.fillMaxSize())
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = focusedParameterId, 
                            style = MaterialTheme.typography.labelSmall, 
                            color = Color.Cyan,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 4. Arm Length Matrix
                ParameterMatrix(
                    labels = listOf("L1", "L2", "L3", "L4"),
                    parameters = listOf(visualSource.parameters["L1"]!!, visualSource.parameters["L2"]!!, visualSource.parameters["L3"]!!, visualSource.parameters["L4"]!!),
                    focusedParameterId = focusedParameterId, 
                    onFocusRequest = { focusedParameterId = it },
                    onInteractionFinished = { manualChangeTrigger++ }
                )
            }

            // 5. BOTTOM CONTROL AREA (Takes up remaining space)
            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                TabRow(selectedTabIndex = if (currentTab == "Patch Bay") 0 else 1) {
                    Tab(selected = currentTab == "Patch Bay", onClick = { currentTab = "Patch Bay" }) { Text("PATCH", modifier = Modifier.padding(8.dp)) }
                    Tab(selected = currentTab == "CV Lab", onClick = { currentTab = "CV Lab" }) { Text("DIAG", modifier = Modifier.padding(8.dp)) }
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
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(16.dp).fillMaxHeight(0.7f)) {
                    Text("Saved Patches", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        patches.forEach { entity ->
                            Row(modifier = Modifier.fillMaxWidth().clickable { 
                                val patchData = PatchMapper.fromJson(entity.jsonSettings)
                                if (patchData != null) onPatchSelected(patchData) 
                            }.padding(12.dp)) {
                                Text(entity.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    if (patches.isEmpty()) Text("No patches saved yet.")
                }
            }
        }
    }

    override fun onPause() { super.onPause(); spiralSurfaceView?.onPause(); audioEngine.stop() }
    override fun onResume() { super.onResume(); spiralSurfaceView?.onResume() }
}

fun Modifier.minHeight(height: androidx.compose.ui.unit.Dp) = this.defaultMinSize(minHeight = height)
