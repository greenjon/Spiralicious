package llm.slop.spirals.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import llm.slop.spirals.cv.*
import llm.slop.spirals.cv.audio.AudioEngine
import llm.slop.spirals.cv.audio.AudioSourceManager
import llm.slop.spirals.cv.audio.AudioSourceType
import llm.slop.spirals.cv.ui.CvHistoryBuffer
import llm.slop.spirals.ui.components.OscilloscopeView
import kotlinx.coroutines.delay

@Composable
fun CvLabScreen(audioEngine: AudioEngine, sourceManager: AudioSourceManager) {
    val context = LocalContext.current
    var audioSourceType by remember { mutableStateOf(AudioSourceType.MIC) }
    var currentAudioRecord by remember { mutableStateOf<AudioRecord?>(null) }
    
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
    }

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val projection = sourceManager.getMediaProjection(result.resultCode, result.data!!)
            val record = sourceManager.buildAudioRecord(
                AudioSourceType.INTERNAL,
                44100,
                AudioFormat.ENCODING_PCM_FLOAT,
                AudioFormat.CHANNEL_IN_MONO,
                AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT),
                projection
            )
            currentAudioRecord = record
        }
    }

    val historyBuffers = remember { 
        mapOf<String, CvHistoryBuffer>(
            "amp" to CvHistoryBuffer(200),
            "bass" to CvHistoryBuffer(200),
            "mid" to CvHistoryBuffer(200),
            "high" to CvHistoryBuffer(200),
            "accent" to CvHistoryBuffer(200),
            "beatPhase" to CvHistoryBuffer(200)
        )
    }

    val scope = rememberCoroutineScope()
    
    // CV Update & Debug Registry Sync
    LaunchedEffect(Unit) {
        while (true) {
            historyBuffers.forEach { entry ->
                entry.value.add(CvRegistry.get(entry.key))
            }
            delay(16)
        }
    }

    // Audio Engine Lifecycle
    LaunchedEffect(audioSourceType, hasMicPermission, currentAudioRecord) {
        when (audioSourceType) {
            AudioSourceType.MIC, AudioSourceType.UNPROCESSED -> {
                if (hasMicPermission) {
                    val record = sourceManager.buildAudioRecord(
                        audioSourceType, 44100, AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.CHANNEL_IN_MONO,
                        AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
                    )
                    audioEngine.start(scope, record)
                } else { audioEngine.stop() }
            }
            AudioSourceType.INTERNAL -> {
                if (currentAudioRecord != null) { audioEngine.start(scope, currentAudioRecord) }
                else { audioEngine.stop() }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("CV Diagnostic Lab", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Input Selector
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Audio Input: ", color = Color.White, style = MaterialTheme.typography.labelSmall)
            CvSegmentedButton(
                options = listOf("Mic", "Raw", "Internal"),
                selected = when(audioSourceType) {
                    AudioSourceType.MIC -> "Mic"
                    AudioSourceType.UNPROCESSED -> "Raw"
                    AudioSourceType.INTERNAL -> "Internal"
                },
                onSelect = { selected ->
                    when (selected) {
                        "Mic" -> { audioSourceType = AudioSourceType.MIC; if (!hasMicPermission) micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                        "Raw" -> { audioSourceType = AudioSourceType.UNPROCESSED; if (!hasMicPermission) micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                        "Internal" -> { audioSourceType = AudioSourceType.INTERNAL; projectionLauncher.launch(sourceManager.createProjectionIntent()) }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Monitor all registry signals
        DiagnosticScope("AMP (Master)", historyBuffers["amp"]!!)
        DiagnosticScope("BASS (Low)", historyBuffers["bass"]!!)
        DiagnosticScope("MID", historyBuffers["mid"]!!)
        DiagnosticScope("HIGH", historyBuffers["high"]!!)
        DiagnosticScope("ACCENT (Transient)", historyBuffers["accent"]!!)
        DiagnosticScope("BEAT PHASE", historyBuffers["beatPhase"]!!)

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun DiagnosticScope(label: String, buffer: CvHistoryBuffer) {
    var samples by remember { mutableStateOf(floatArrayOf()) }
    LaunchedEffect(Unit) {
        while(true) {
            samples = buffer.getSamples()
            delay(32)
        }
    }
    
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, color = Color.Cyan, style = MaterialTheme.typography.labelSmall)
        OscilloscopeView(samples = samples, modifier = Modifier.height(60.dp))
    }
}

@Composable
fun CvSegmentedButton(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row {
        options.forEach { option ->
            Button(
                onClick = { onSelect(option) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected == option) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f)
                ),
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                Text(option, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
