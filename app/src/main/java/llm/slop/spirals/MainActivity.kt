package llm.slop.spirals

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var spiralSurfaceView: SpiralSurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        var params by remember { mutableStateOf(MandalaParams(omega1 = 22, omega2 = 19, omega3 = 19, omega4 = 19, l1 = 0.4f, l2 = 0.3f, l3 = 0.2f, l4 = 0.1f, thickness = 0.005f)) }
        var currentTab by remember { mutableStateOf("Speed") }
        var selectedLobeFilter by remember { mutableStateOf<Int?>(null) }
        var showOnlyUntagged by remember { mutableStateOf(false) }
        var isLobeMenuExpanded by remember { mutableStateOf(false) }

        val allRatios = MandalaLibrary.MandalaRatios
        val tags by vm.tags.collectAsState()
        
        val filteredRatios = remember(selectedLobeFilter, showOnlyUntagged, tags) {
            allRatios.filter { ratio ->
                val lobeMatch = selectedLobeFilter == null || ratio.lobes == selectedLobeFilter
                val tagMatch = !showOnlyUntagged || !tags.containsKey("${ratio.omega1}-${ratio.omega2}-${ratio.omega3}-${ratio.omega4}")
                lobeMatch && tagMatch
            }.sortedBy { it.shapeRatio }
        }

        val currentIndex = remember(params, filteredRatios) {
            filteredRatios.indexOfFirst { it.omega1 == params.omega1 && it.omega2 == params.omega2 && it.omega3 == params.omega3 && it.omega4 == params.omega4 }
        }

        val scope = rememberCoroutineScope()
        val listState = rememberLazyListState()

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { context -> SpiralSurfaceView(context).also { spiralSurfaceView = it } },
                update = { view -> view.setParams(params) },
                modifier = Modifier.fillMaxSize()
            )

            // MAIN UI COLUMN
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.7f)
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(Color.Transparent)
            ) {
                // Tab Header
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { currentTab = "Speed" },
                        colors = ButtonDefaults.buttonColors(containerColor = if (currentTab == "Speed") MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else Color.Gray.copy(alpha = 0.4f)),
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.extraSmall
                    ) { Text("Speed") }
                    Button(
                        onClick = { currentTab = "Length" },
                        colors = ButtonDefaults.buttonColors(containerColor = if (currentTab == "Length") MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else Color.Gray.copy(alpha = 0.4f)),
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.extraSmall
                    ) { Text("Length") }
                    IconButton(onClick = { onShare(vm.getExportData()) }) {
                        Icon(Icons.Default.Share, contentDescription = "Export", tint = Color.White)
                    }
                }

                // RECIPE BAR (Always visible below tabs)
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "Speeds: ${params.omega1}, ${params.omega2}, ${params.omega3}, ${params.omega4} | L: ${params.l1}, ${params.l2}, ${params.l3}, ${params.l4}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Cyan,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                when (currentTab) {
                    "Speed" -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Lobe Filter
                            Box(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                                OutlinedButton(onClick = { isLobeMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text(text = if (selectedLobeFilter == null) "All Lobes" else "${selectedLobeFilter}L", style = MaterialTheme.typography.labelSmall)
                                }
                                DropdownMenu(expanded = isLobeMenuExpanded, onDismissRequest = { isLobeMenuExpanded = false }) {
                                    DropdownMenuItem(text = { Text("All") }, onClick = { selectedLobeFilter = null; isLobeMenuExpanded = false })
                                    listOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16).forEach { l ->
                                        DropdownMenuItem(text = { Text("${l}L") }, onClick = { selectedLobeFilter = l; isLobeMenuExpanded = false })
                                    }
                                }
                            }
                            // Untagged Toggle
                            FilterChip(
                                selected = showOnlyUntagged,
                                onClick = { showOnlyUntagged = !showOnlyUntagged },
                                label = { Text("Untagged Only", style = MaterialTheme.typography.labelSmall) }
                            )
                        }

                        // Ratio List with Scrub Bar
                        Row(modifier = Modifier.weight(1f)) {
                            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                                itemsIndexed(filteredRatios) { index, ratio ->
                                    val speedsKey = "${ratio.omega1}-${ratio.omega2}-${ratio.omega3}-${ratio.omega4}"
                                    val currentTag = tags[speedsKey]
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                params = params.copy(omega1 = ratio.omega1, omega2 = ratio.omega2, omega3 = ratio.omega3, omega4 = ratio.omega4)
                                            }
                                            .padding(4.dp)
                                            .background(if (currentIndex == index) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.2f))
                                    ) {
                                        Text(
                                            text = "(${String.format("%.2f", ratio.shapeRatio)}) ${ratio.omega1}, ${ratio.omega2}, ${ratio.omega3}, ${ratio.omega4}",
                                            color = if (currentTag != null) Color.Yellow else Color.White,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (currentTag != null) {
                                            Text(text = currentTag, color = Color.Yellow, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                            
                            // VERTICAL SCRUB BAR
                            if (filteredRatios.isNotEmpty()) {
                                Slider(
                                    value = if (filteredRatios.size > 1) listState.firstVisibleItemIndex.toFloat() / (filteredRatios.size - 1) else 0f,
                                    onValueChange = { scrollPercent ->
                                        scope.launch {
                                            val targetIndex = (scrollPercent * (filteredRatios.size - 1)).toInt()
                                            listState.scrollToItem(targetIndex)
                                        }
                                    },
                                    modifier = Modifier.width(32.dp).fillMaxHeight().padding(vertical = 16.dp),
                                    valueRange = 0f..1f
                                )
                            }
                        }
                    }
                    "Length" -> {
                        Column(modifier = Modifier.padding(16.dp).background(Color.Black.copy(alpha = 0.3f))) {
                            LengthSlider("L1", params.l1) { params = params.copy(l1 = it) }
                            LengthSlider("L2", params.l2) { params = params.copy(l2 = it) }
                            LengthSlider("L3", params.l3) { params = params.copy(l3 = it) }
                            LengthSlider("L4", params.l4) { params = params.copy(l4 = it) }
                            LengthSlider("Thickness", params.thickness, 0.001f..0.015f) { params = params.copy(thickness = it) }
                        }
                    }
                }
            }

            // BOTTOM CONTROLS
            val speedsKey = "${params.omega1}-${params.omega2}-${params.omega3}-${params.omega4}"
            val currentTag = tags[speedsKey]

            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Prev Button
                IconButton(
                    onClick = {
                        if (currentIndex > 0) {
                            val prev = filteredRatios[currentIndex - 1]
                            params = params.copy(omega1 = prev.omega1, omega2 = prev.omega2, omega3 = prev.omega3, omega4 = prev.omega4)
                        }
                    },
                    enabled = currentIndex > 0
                ) { Icon(Icons.Default.ArrowBack, contentDescription = "Prev", tint = Color.White) }

                // Tag Buttons
                Row(modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.medium)) {
                    TagButton(icon = Icons.Default.Delete, label = "trash", active = currentTag == "trash") { vm.toggleTag(speedsKey, "trash") }
                    TagButton(label = "1", active = currentTag == "1") { vm.toggleTag(speedsKey, "1") }
                    TagButton(label = "2", active = currentTag == "2") { vm.toggleTag(speedsKey, "2") }
                    TagButton(label = "3", active = currentTag == "3") { vm.toggleTag(speedsKey, "3") }
                    TagButton(label = "?", active = currentTag == "?") { vm.toggleTag(speedsKey, "?") }
                }

                // Next Button
                IconButton(
                    onClick = {
                        if (currentIndex < filteredRatios.size - 1) {
                            val next = filteredRatios[currentIndex + 1]
                            params = params.copy(omega1 = next.omega1, omega2 = next.omega2, omega3 = next.omega3, omega4 = next.omega4)
                        }
                    },
                    enabled = currentIndex < filteredRatios.size - 1
                ) { Icon(Icons.Default.ArrowForward, contentDescription = "Next", tint = Color.White) }
            }
        }
    }

    @Composable
    fun TagButton(icon: androidx.compose.ui.graphics.vector.ImageVector? = null, label: String = "", active: Boolean, onClick: () -> Unit) {
        IconButton(onClick = onClick) {
            if (icon != null) {
                Icon(icon, contentDescription = label, tint = if (active) Color.Red else Color.Gray)
            } else {
                Text(text = label, color = if (active) Color.Green else Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    @Composable
    fun LengthSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float> = 0f..1f, onValueChange: (Float) -> Unit) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(text = "$label: ${String.format("%.3f", value)}", color = Color.White, style = MaterialTheme.typography.labelSmall)
            Slider(value = value, onValueChange = onValueChange, valueRange = range, modifier = Modifier.height(20.dp))
        }
    }

    override fun onPause() { super.onPause(); spiralSurfaceView?.onPause() }
    override fun onResume() { super.onResume(); spiralSurfaceView?.onResume() }
}
