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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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

class MainActivity : ComponentActivity() {
    private var spiralSurfaceView: SpiralSurfaceView? = null
    private val visualSource = MandalaVisualSource()
    
    // Global Audio Engine components
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
        var isTabMenuExpanded by remember { mutableStateOf(false) }

        // Settings
        var hideCvLab by remember { mutableStateOf(false) }
        var isSettingsMenuExpanded by remember { mutableStateOf(false) }

        // Global Audio State
        var audioSourceType by remember { mutableStateOf(AudioSourceType.MIC) }
        var currentInternalAudioRecord by remember { mutableStateOf<AudioRecord?>(null) }

        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        var hasMicPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            )
        }

        // Global Audio Lifecycle
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
                    } else {
                        audioEngine.stop()
                    }
                }
                AudioSourceType.INTERNAL -> {
                    if (currentInternalAudioRecord != null) {
                        audioEngine.start(scope, currentInternalAudioRecord)
                    } else {
                        audioEngine.stop()
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { context -> 
                    SpiralSurfaceView(context).also { 
                        spiralSurfaceView = it
                        it.setVisualSource(visualSource)
                    } 
                },
                update = { /* Renderer pulls from visualSource now */ },
                modifier = Modifier.fillMaxSize()
            )

            // UI Overlay
            Column(
                modifier = Modifier.fillMaxWidth()
                    .fillMaxHeight()
                    .align(Alignment.TopStart).statusBarsPadding().padding(16.dp).background(Color.Transparent)
            ) {
                // Header
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
                            if (!hideCvLab) DropdownMenuItem(text = { Text("CV Lab") }, onClick = { currentTab = "CV Lab"; isTabMenuExpanded = false })
                            DropdownMenuItem(text = { Text("Patch Bay") }, onClick = { currentTab = "Patch Bay"; isTabMenuExpanded = false })
                        }
                    }
                    
                    Box {
                        IconButton(onClick = { isSettingsMenuExpanded = true }) { Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White) }
                        DropdownMenu(expanded = isSettingsMenuExpanded, onDismissRequest = { isSettingsMenuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(if (hideCvLab) "Show CV Lab" else "Hide CV Lab") },
                                onClick = { hideCvLab = !hideCvLab; isSettingsMenuExpanded = false }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Export Tags") },
                                onClick = { onShare(vm.getExportData()); isSettingsMenuExpanded = false }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                when (currentTab) {
                    "Patch Bay" -> {
                        InstrumentEditorScreen(visualSource, vm)
                    }
                    "CV Lab" -> {
                        if (!hideCvLab) {
                            CvLabScreen(
                                audioEngine = audioEngine,
                                sourceManager = sourceManager!!,
                                audioSourceType = audioSourceType,
                                onAudioSourceTypeChange = { audioSourceType = it },
                                hasMicPermission = hasMicPermission,
                                onMicPermissionGranted = { hasMicPermission = true },
                                onInternalAudioRecordCreated = { currentInternalAudioRecord = it }
                            )
                        } else {
                            currentTab = "Patch Bay"
                        }
                    }
                }
            }
        }
    }

    override fun onPause() { super.onPause(); spiralSurfaceView?.onPause(); audioEngine.stop() }
    override fun onResume() { super.onResume(); spiralSurfaceView?.onResume() }
}
