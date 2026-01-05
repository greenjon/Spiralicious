package llm.slop.spirals

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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
        var currentTab by remember { mutableStateOf("Patch Bay") }
        var focusedParameterId by remember { mutableStateOf("L1") }
        var lastLoadedPatch by remember { mutableStateOf<PatchData?>(null) }

        var audioSourceType by remember { mutableStateOf(AudioSourceType.MIC) }
        var currentInternalAudioRecord by remember { mutableStateOf<AudioRecord?>(null) }

        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        var hasMicPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            )
        }

        LaunchedEffect(Unit) { CvRegistry.startSync(scope) }

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

        Surface(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), color = MaterialTheme.colorScheme.background) {
            if (isLandscape) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        VisualWindow(visualSource, focusedParameterId, lastLoadedPatch, vm, true) { focusedParameterId = it }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        ControlWindow(
                            tab = currentTab, source = visualSource, vm = vm, focusedId = focusedParameterId,
                            audioSourceType = audioSourceType, hasMicPermission = hasMicPermission,
                            onFocusChange = { focusedParameterId = it }, onTabChange = { currentTab = it },
                            onAudioSourceTypeChange = { audioSourceType = it },
                            onMicPermissionGranted = { hasMicPermission = true },
                            onInternalAudioRecordCreated = { currentInternalAudioRecord = it },
                            onPatchLoad = { lastLoadedPatch = it }
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        VisualWindow(visualSource, focusedParameterId, lastLoadedPatch, vm, false) { focusedParameterId = it }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        ControlWindow(
                            tab = currentTab, source = visualSource, vm = vm, focusedId = focusedParameterId,
                            audioSourceType = audioSourceType, hasMicPermission = hasMicPermission,
                            onFocusChange = { focusedParameterId = it }, onTabChange = { currentTab = it },
                            onAudioSourceTypeChange = { audioSourceType = it },
                            onMicPermissionGranted = { hasMicPermission = true },
                            onInternalAudioRecordCreated = { currentInternalAudioRecord = it },
                            onPatchLoad = { lastLoadedPatch = it }
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun VisualWindow(source: MandalaVisualSource, focusedId: String, lastPatch: PatchData?, vm: MandalaViewModel, isLandscape: Boolean, onFocusChange: (String) -> Unit) {
        var isHeaderExpanded by remember { mutableStateOf(false) }
        val isDirty = PatchMapper.isDirty(source, lastPatch)
        val focusedParam = source.parameters[focusedId] ?: source.globalAlpha 
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            // 1. Collapsible Header
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth().clickable { isHeaderExpanded = !isHeaderExpanded }.padding(bottom = 4.dp),
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "${lastPatch?.name ?: "New Patch"}${if (isDirty) " *" else ""}", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(text = "${source.recipe.a}, ${source.recipe.b}, ${source.recipe.c}, ${source.recipe.d} (${source.recipe.petals}P)", style = MaterialTheme.typography.labelSmall, color = Color.Cyan)
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
                                        DropdownMenuItem(text = { Text("${ratio.a}, ${ratio.b}, ${ratio.c}, ${ratio.d} (${ratio.petals}P)") }, onClick = { source.recipe = ratio; recipeExpanded = false; isHeaderExpanded = false })
                                    }
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.DarkGray)
                        Text("Patch Management", style = MaterialTheme.typography.labelLarge, color = Color.Cyan)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            var nameInput by remember { mutableStateOf(lastPatch?.name ?: "New Patch") }
                            OutlinedTextField(value = nameInput, onValueChange = { nameInput = it }, modifier = Modifier.weight(1f), label = { Text("Name") }, textStyle = MaterialTheme.typography.bodySmall)
                            IconButton(onClick = {
                                val data = PatchMapper.fromVisualSource(nameInput, source)
                                val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, PatchMapper.toJson(data)) }
                                context.startActivity(Intent.createChooser(intent, "Share Patch"))
                            }) { Icon(Icons.Default.Share, contentDescription = null, tint = Color.White) }
                            Button(onClick = { scope.launch { vm.savePatch(PatchMapper.fromVisualSource(nameInput, source)); isHeaderExpanded = false } }) { Text("Save") }
                        }
                    }
                }
            }

            // Body Content
            Column(modifier = Modifier.weight(1f)) {
                ParameterMatrix(
                    labels = listOf("L1", "L2", "L3", "L4"),
                    parameters = listOf(source.parameters["L1"]!!, source.parameters["L2"]!!, source.parameters["L3"]!!, source.parameters["L4"]!!),
                    focusedParameterId = focusedId, onFocusRequest = onFocusChange
                )
                
                var focusedSamples by remember { mutableStateOf(floatArrayOf()) }
                LaunchedEffect(focusedId) {
                    while(true) {
                        focusedSamples = focusedParam.history.getSamples()
                        delay(16)
                    }
                }
                Text("Monitor: $focusedId", style = MaterialTheme.typography.labelSmall, color = Color.Cyan, modifier = Modifier.padding(top = 4.dp))
                Box(modifier = Modifier.weight(1f).minHeight(60.dp).padding(vertical = 4.dp).border(1.dp, Color.DarkGray)) {
                    OscilloscopeView(samples = focusedSamples, modifier = Modifier.fillMaxSize())
                }

                // Mandala Preview (Full Width)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16/9f)
                        .background(Color.Black)
                        .border(1.dp, Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(factory = { context -> SpiralSurfaceView(context).also { spiralSurfaceView = it; it.setVisualSource(source) } }, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    @Composable
    fun ControlWindow(
        tab: String, source: MandalaVisualSource, vm: MandalaViewModel, focusedId: String, 
        audioSourceType: AudioSourceType, hasMicPermission: Boolean,
        onFocusChange: (String) -> Unit, onTabChange: (String) -> Unit,
        onAudioSourceTypeChange: (AudioSourceType) -> Unit,
        onMicPermissionGranted: () -> Unit,
        onInternalAudioRecordCreated: (AudioRecord) -> Unit,
        onPatchLoad: (PatchData) -> Unit
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = if (tab == "Patch Bay") 0 else 1) {
                Tab(selected = tab == "Patch Bay", onClick = { onTabChange("Patch Bay") }) { Text("PATCH", modifier = Modifier.padding(8.dp)) }
                Tab(selected = tab == "CV Lab", onClick = { onTabChange("CV Lab") }) { Text("DIAG", modifier = Modifier.padding(8.dp)) }
            }
            Box(modifier = Modifier.weight(1f)) {
                if (tab == "Patch Bay") { InstrumentEditorScreen(source, vm, focusedId, onFocusChange) }
                else { CvLabScreen(audioEngine, sourceManager!!, audioSourceType, onAudioSourceTypeChange, hasMicPermission, onMicPermissionGranted, onInternalAudioRecordCreated) }
            }
        }
    }

    override fun onPause() { super.onPause(); spiralSurfaceView?.onPause(); audioEngine.stop() }
    override fun onResume() { super.onResume(); spiralSurfaceView?.onResume() }
}

fun Modifier.minHeight(height: androidx.compose.ui.unit.Dp) = this.defaultMinSize(minHeight = height)
