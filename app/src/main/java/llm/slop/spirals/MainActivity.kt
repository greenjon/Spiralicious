package llm.slop.spirals

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import llm.slop.spirals.cv.*
import llm.slop.spirals.cv.audio.*
import llm.slop.spirals.ui.CvLabScreen
import llm.slop.spirals.ui.InstrumentEditorScreen
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var spiralSurfaceView: SpiralSurfaceView? = null
    private val visualSource = MandalaVisualSource()
    
    // Global Audio Engine components
    private val amplitudeCv = AmplitudeCv()
    private val audioEngine = AudioEngine(amplitudeCv)
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
        val allRatios = MandalaLibrary.MandalaRatios
        var params by remember { 
            mutableStateOf(
                allRatios.firstOrNull()?.let { 
                    MandalaParams(omega1 = it.a, omega2 = it.b, omega3 = it.c, omega4 = it.d, l1 = 0.4f, l2 = 0.3f, l3 = 0.2f, l4 = 0.1f, thickness = 0.005f) 
                } ?: MandalaParams(omega1 = 22, omega2 = 19, omega3 = 19, omega4 = 19, l1 = 0.4f, l2 = 0.3f, l3 = 0.2f, l4 = 0.1f, thickness = 0.005f)
            )
        }
        var currentTab by remember { mutableStateOf("Patch Bay") }
        var isTabMenuExpanded by remember { mutableStateOf(false) }

        // Settings
        var hideSpeedScreen by remember { mutableStateOf(false) }
        var hideLengthScreen by remember { mutableStateOf(false) }
        var hideCvLab by remember { mutableStateOf(false) }
        var isSettingsMenuExpanded by remember { mutableStateOf(false) }

        val scope = rememberCoroutineScope()
        val tags by vm.tags.collectAsState()
        
        // Start Audio automatically for performance mode
        LaunchedEffect(Unit) {
            val context = this@MainActivity
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                val record = sourceManager?.buildAudioRecord(
                    AudioSourceType.MIC, 44100, android.media.AudioFormat.ENCODING_PCM_FLOAT, 
                    android.media.AudioFormat.CHANNEL_IN_MONO, 
                    android.media.AudioRecord.getMinBufferSize(44100, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_FLOAT)
                )
                audioEngine.start(scope, record)
            }
        }

        // Bridge to GL Visual Source
        LaunchedEffect(params) {
            visualSource.recipe = allRatios.find { it.a == params.omega1 && it.b == params.omega2 } ?: allRatios.first()
            visualSource.parameters["L1"]?.baseValue = params.l1
            visualSource.parameters["L2"]?.baseValue = params.l2
            visualSource.parameters["L3"]?.baseValue = params.l3
            visualSource.parameters["L4"]?.baseValue = params.l4
            visualSource.parameters["Thickness"]?.baseValue = (params.thickness - 0.001f) / 0.014f
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { context -> 
                    SpiralSurfaceView(context).also { 
                        spiralSurfaceView = it
                        it.setVisualSource(visualSource)
                    } 
                },
                update = { view -> 
                    if (currentTab != "Patch Bay") { view.setParams(params) }
                },
                modifier = Modifier.fillMaxSize()
            )

            Column(
                modifier = Modifier.fillMaxWidth()
                    .fillMaxHeight(if (currentTab == "CV Lab" || currentTab == "Patch Bay") 1.0f else 0.7f)
                    .align(Alignment.TopStart).statusBarsPadding().padding(16.dp).background(Color.Transparent)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        Button(
                            onClick = { isTabMenuExpanded = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text("MODE: $currentTab", style = MaterialTheme.typography.labelLarge, color = Color.White)
                        }
                        DropdownMenu(expanded = isTabMenuExpanded, onDismissRequest = { isTabMenuExpanded = false }) {
                            if (!hideSpeedScreen) DropdownMenuItem(text = { Text("Speed") }, onClick = { currentTab = "Speed"; isTabMenuExpanded = false })
                            if (!hideLengthScreen) DropdownMenuItem(text = { Text("Length") }, onClick = { currentTab = "Length"; isTabMenuExpanded = false })
                            if (!hideCvLab) DropdownMenuItem(text = { Text("CV Lab") }, onClick = { currentTab = "CV Lab"; isTabMenuExpanded = false })
                            DropdownMenuItem(text = { Text("Patch Bay") }, onClick = { currentTab = "Patch Bay"; isTabMenuExpanded = false })
                        }
                    }
                    
                    Box {
                        IconButton(onClick = { isSettingsMenuExpanded = true }) { Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White) }
                        DropdownMenu(expanded = isSettingsMenuExpanded, onDismissRequest = { isSettingsMenuExpanded = false }) {
                            DropdownMenuItem(text = { Text(if (hideSpeedScreen) "Show Speed Screen" else "Hide Speed Screen") }, onClick = { hideSpeedScreen = !hideSpeedScreen; isSettingsMenuExpanded = false })
                            DropdownMenuItem(text = { Text(if (hideLengthScreen) "Show Length Screen" else "Hide Length Screen") }, onClick = { hideLengthScreen = !hideLengthScreen; isSettingsMenuExpanded = false })
                            DropdownMenuItem(text = { Text(if (hideCvLab) "Show CV Lab" else "Hide CV Lab") }, onClick = { hideCvLab = !hideCvLab; isSettingsMenuExpanded = false })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Export Tags") }, onClick = { onShare(vm.getExportData()); isSettingsMenuExpanded = false })
                        }
                    }
                }

                if (currentTab == "Patch Bay") {
                    InstrumentEditorScreen(visualSource)
                } else if (currentTab == "CV Lab") {
                    CvLabScreen(audioEngine, sourceManager!!)
                }
                // ... rest of Speed/Length screens logic
            }
        }
    }

    override fun onPause() { super.onPause(); spiralSurfaceView?.onPause(); audioEngine.stop() }
    override fun onResume() { super.onResume(); spiralSurfaceView?.onResume() }
}
