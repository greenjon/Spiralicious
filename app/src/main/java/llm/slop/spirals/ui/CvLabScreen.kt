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
fun CvLabScreen(
    audioEngine: AudioEngine, 
    sourceManager: AudioSourceManager,
    audioSourceType: AudioSourceType,
    onAudioSourceTypeChange: (AudioSourceType) -> Unit,
    hasMicPermission: Boolean,
    onMicPermissionGranted: () -> Unit,
    onInternalAudioRecordCreated: (AudioRecord) -> Unit
) {
    val context = LocalContext.current
    
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) onMicPermissionGranted()
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
            if (record != null) onInternalAudioRecordCreated(record)
        }
    }

    val historyBuffers = remember { 
        mapOf<String, CvHistoryBuffer>(
            "amp" to CvHistoryBuffer(200),
            "accent" to CvHistoryBuffer(200),
            "onset" to CvHistoryBuffer(200),
            "bassFlux" to CvHistoryBuffer(200),
            "bass" to CvHistoryBuffer(200),
            "mid" to CvHistoryBuffer(200),
            "high" to CvHistoryBuffer(200),
            "beatPhase" to CvHistoryBuffer(200)
        )
    }

    // CV Update & Debug Registry Sync
    LaunchedEffect(Unit) {
        while (true) {
            historyBuffers.forEach { entry ->
                entry.value.add(CvRegistry.get(entry.key))
            }
            delay(16)
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
                        "Mic" -> { 
                            onAudioSourceTypeChange(AudioSourceType.MIC)
                            if (!hasMicPermission) micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) 
                        }
                        "Raw" -> { 
                            onAudioSourceTypeChange(AudioSourceType.UNPROCESSED)
                            if (!hasMicPermission) micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) 
                        }
                        "Internal" -> { 
                            onAudioSourceTypeChange(AudioSourceType.INTERNAL)
                            projectionLauncher.launch(sourceManager.createProjectionIntent()) 
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Monitor all registry signals
        DiagnosticScope("ACCENT (Weighted Flux + Decay)", historyBuffers["accent"]!!)
        DiagnosticScope("ONSET (Raw Multi-band Flux)", historyBuffers["onset"]!!)
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.DarkGray)
        
        DiagnosticScope("AMP (Master Envelope)", historyBuffers["amp"]!!)
        DiagnosticScope("BASS FLUX (Friend 1)", historyBuffers["bassFlux"]!!)
        DiagnosticScope("BEAT PHASE (Flywheel)", historyBuffers["beatPhase"]!!)

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun DiagnosticScope(label: String, buffer: CvHistoryBuffer) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, color = Color.Cyan, style = MaterialTheme.typography.labelSmall)
        OscilloscopeView(history = buffer, modifier = Modifier.height(60.dp))
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
