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
import llm.slop.spirals.cv.modifiers.ModifiedCv
import llm.slop.spirals.cv.ui.CvHistoryBuffer
import llm.slop.spirals.ui.components.OscilloscopeView
import kotlinx.coroutines.delay

@Composable
fun CvLabScreen() {
    val context = LocalContext.current
    val clock = remember { CvClock() }
    val beatClock = remember { BeatClock(120f) }
    val beatPhaseCv = remember { BeatPhaseCv(beatClock) }
    val amplitudeCv = remember { AmplitudeCv() }
    val audioEngine = remember { AudioEngine(amplitudeCv) }
    val sourceManager = remember { AudioSourceManager(context) }
    
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

    var selectedSignalType by remember { mutableStateOf("BeatPhase") }
    val selectedSource = remember(selectedSignalType) {
        if (selectedSignalType == "BeatPhase") beatPhaseCv else amplitudeCv
    }

    var exponent by remember { mutableFloatStateOf(1.0f) }
    var gain by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableFloatStateOf(0f) }
    var attackMs by remember { mutableFloatStateOf(20f) }
    var releaseMs by remember { mutableFloatStateOf(150f) }

    val modifiedCv = remember(selectedSource, exponent, gain, offset) {
        ModifiedCv(selectedSource, exponent = exponent, gain = gain, offset = offset)
    }

    val historyBuffer = remember { CvHistoryBuffer(200) }
    var samples by remember { mutableStateOf(floatArrayOf()) }
    var lastValue by remember { mutableFloatStateOf(0f) }
    var rawRms by remember { mutableFloatStateOf(0f) }

    // Update smoothing
    LaunchedEffect(attackMs, releaseMs) {
        amplitudeCv.setSmoothing(attackMs, releaseMs)
    }

    // High-Rate CV Update Loop (120 Hz)
    LaunchedEffect(modifiedCv) {
        val controlRate = 120
        val frameTimeMs = 1000L / controlRate
        val deltaSeconds = 1f / controlRate
        while (true) {
            clock.tick(deltaSeconds)
            val value = modifiedCv.getValue(clock.cvTimeSeconds)
            lastValue = value
            rawRms = audioEngine.debugLastRms
            historyBuffer.add(value)
            samples = historyBuffer.getSamples()
            delay(frameTimeMs)
        }
    }

    // Audio Engine Lifecycle
    val scope = rememberCoroutineScope()
    LaunchedEffect(selectedSignalType, audioSourceType, hasMicPermission, currentAudioRecord) {
        if (selectedSignalType == "Amplitude") {
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
        } else { audioEngine.stop() }
    }

    DisposableEffect(Unit) {
        onDispose { audioEngine.stop(); currentAudioRecord?.release() }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)).padding(16.dp)
    ) {
        Text("CV Laboratory", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        OscilloscopeView(samples = samples)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("CV Val: ${"%.3f".format(lastValue)}", style = MaterialTheme.typography.labelSmall, color = Color.Green)
            Text("Raw RMS: ${"%.3f".format(rawRms)}", style = MaterialTheme.typography.labelSmall, color = Color.Yellow)
        }
        LinearProgressIndicator(
            progress = { rawRms.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = Color.Yellow, trackColor = Color.DarkGray
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Source Controls
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Source: ", color = Color.White)
            var expanded by remember { mutableStateOf(false) }
            Box {
                Button(onClick = { expanded = true }) { Text(selectedSignalType) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("BeatPhase") }, onClick = { selectedSignalType = "BeatPhase"; expanded = false })
                    DropdownMenuItem(text = { Text("Amplitude") }, onClick = { selectedSignalType = "Amplitude"; expanded = false })
                }
            }
        }

        if (selectedSignalType == "Amplitude") {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Audio Input: ", color = Color.White)
                val options = listOf("Mic", "Raw", "Internal")
                val selected = when(audioSourceType) {
                    AudioSourceType.MIC -> "Mic"
                    AudioSourceType.UNPROCESSED -> "Raw"
                    AudioSourceType.INTERNAL -> "Internal"
                }
                CvSegmentedButton(options = options, selected = selected) { 
                    when (it) {
                        "Mic" -> { audioSourceType = AudioSourceType.MIC; if (!hasMicPermission) micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                        "Raw" -> { audioSourceType = AudioSourceType.UNPROCESSED; if (!hasMicPermission) micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                        "Internal" -> { audioSourceType = AudioSourceType.INTERNAL; projectionLauncher.launch(sourceManager.createProjectionIntent()) }
                    }
                }
            }
            Text("Attack: ${attackMs.toInt()}ms", color = Color.White, style = MaterialTheme.typography.labelSmall)
            Slider(value = attackMs, onValueChange = { attackMs = it }, valueRange = 1f..100f)
            Text("Release: ${releaseMs.toInt()}ms", color = Color.White, style = MaterialTheme.typography.labelSmall)
            Slider(value = releaseMs, onValueChange = { releaseMs = it }, valueRange = 10f..500f)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Modifiers
        Text("Exponent (Power): ${"%.2f".format(exponent)}", color = Color.White, style = MaterialTheme.typography.labelSmall)
        Slider(value = exponent, onValueChange = { exponent = it }, valueRange = 0.1f..3.0f)

        Text("Gain: ${"%.2f".format(gain)}", color = Color.White, style = MaterialTheme.typography.labelSmall)
        Slider(value = gain, onValueChange = { gain = it }, valueRange = 0f..10f)

        Text("Offset: ${"%.2f".format(offset)}", color = Color.White, style = MaterialTheme.typography.labelSmall)
        Slider(value = offset, onValueChange = { offset = it }, valueRange = -1f..1f)
        
        if (selectedSignalType == "BeatPhase") {
            var minBpm by remember { mutableFloatStateOf(60f) }
            var maxBpm by remember { mutableFloatStateOf(180f) }
            Text("BPM: ${beatClock.bpm.toInt()}", color = Color.White)
            Slider(value = beatClock.bpm, onValueChange = { beatClock.bpm = it }, valueRange = minBpm.coerceAtMost(maxBpm)..maxBpm.coerceAtLeast(minBpm))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Min BPM: ${minBpm.toInt()}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Slider(value = minBpm, onValueChange = { minBpm = it }, valueRange = 20f..120f)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Max BPM: ${maxBpm.toInt()}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Slider(value = maxBpm, onValueChange = { maxBpm = it }, valueRange = 120f..300f)
                }
            }
        }
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
