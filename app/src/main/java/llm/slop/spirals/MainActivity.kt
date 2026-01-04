package llm.slop.spirals

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import llm.slop.spirals.ui.CvLabScreen
import llm.slop.spirals.ui.InstrumentEditorScreen
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var spiralSurfaceView: SpiralSurfaceView? = null
    // We'll keep a single visual source alive for patching
    private val visualSource = MandalaVisualSource()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        var currentTab by remember { mutableStateOf("Speed") }
        var isTabMenuExpanded by remember { mutableStateOf(false) }

        var selectedPetalFilter by remember { mutableStateOf<Int?>(null) }
        var showOnlyUntagged by remember { mutableStateOf(false) }
        var isPetalMenuExpanded by remember { mutableStateOf(false) }
        
        var currentSortBy by remember { mutableStateOf("shapeRatio") }
        var isSortMenuExpanded by remember { mutableStateOf(false) }

        val tags by vm.tags.collectAsState()
        
        val filteredRatios = remember(selectedPetalFilter, showOnlyUntagged, tags, currentSortBy) {
            val filtered = allRatios.filter { ratio ->
                val petalMatch = selectedPetalFilter == null || ratio.petals == selectedPetalFilter
                val tagMatch = !showOnlyUntagged || !tags.containsKey(ratio.id)
                petalMatch && tagMatch
            }
            
            when (currentSortBy) {
                "multiplicityClass" -> filtered.sortedBy { it.multiplicityClass }
                "independentFreqCount" -> filtered.sortedBy { it.independentFreqCount }
                "twoFoldLikely" -> filtered.sortedBy { if (it.twoFoldLikely) 1 else 0 }
                "hierarchyDepth" -> filtered.sortedBy { it.hierarchyDepth }
                "dominanceRatio" -> filtered.sortedBy { it.dominanceRatio }
                "radialVariance" -> filtered.sortedBy { it.radialVariance }
                else -> filtered.sortedBy { it.shapeRatio }
            }
        }

        val currentRatio = remember(params) {
            allRatios.find { it.a == params.omega1 && it.b == params.omega2 && it.c == params.omega3 && it.d == params.omega4 }
        }

        val currentIndex = remember(params, filteredRatios) {
            filteredRatios.indexOfFirst { it.a == params.omega1 && it.b == params.omega2 && it.c == params.omega3 && it.d == params.omega4 }
        }

        val scope = rememberCoroutineScope()
        val listState = rememberLazyListState()

        val goToNext = {
            if (currentIndex < filteredRatios.size - 1) {
                val nextIndex = currentIndex + 1
                val next = filteredRatios[nextIndex]
                params = params.copy(omega1 = next.a, omega2 = next.b, omega3 = next.c, omega4 = next.d)
                scope.launch {
                    listState.animateScrollToItem(nextIndex)
                }
            }
        }

        val goToPrev = {
            if (currentIndex > 0) {
                val prevIndex = currentIndex - 1
                val prev = filteredRatios[prevIndex]
                params = params.copy(omega1 = prev.a, omega2 = prev.b, omega3 = prev.c, omega4 = prev.d)
                scope.launch {
                    listState.animateScrollToItem(prevIndex)
                }
            }
        }

        // Keep visual source in sync with params for now (legacy bridge)
        LaunchedEffect(params) {
            visualSource.recipe = allRatios.find { it.a == params.omega1 && it.b == params.omega2 } ?: allRatios.first()
            visualSource.parameters["L1"]?.baseValue = params.l1
            visualSource.parameters["L2"]?.baseValue = params.l2
            visualSource.parameters["L3"]?.baseValue = params.l3
            visualSource.parameters["L4"]?.baseValue = params.l4
            visualSource.parameters["Thickness"]?.baseValue = params.thickness * 10f // Scale to 0-1
        }

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
                    .fillMaxHeight(if (currentTab == "CV Lab" || currentTab == "Patch Bay") 1.0f else 0.7f)
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .background(Color.Transparent)
            ) {
                // Tab Header with Dropdown
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
                            listOf("Speed", "Length", "CV Lab", "Patch Bay").forEach { tab ->
                                DropdownMenuItem(
                                    text = { Text(tab) },
                                    onClick = { 
                                        currentTab = tab
                                        isTabMenuExpanded = false 
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = { onShare(vm.getExportData()) }) {
                        Icon(Icons.Default.Share, contentDescription = "Export", tint = Color.White)
                    }
                }

                if (currentTab != "CV Lab" && currentTab != "Patch Bay") {
                    // RECIPE BAR
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "Speeds: ${params.omega1}, ${params.omega2}, ${params.omega3}, ${params.omega4}" + 
                                       (currentRatio?.let { 
                                           "   (${it.petals} petals; ${String.format(Locale.US, "%.1f", it.shapeRatio)} sr)" 
                                       } ?: ""),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Cyan
                            )
                            Text(
                                text = "Lengths: ${String.format(Locale.US, "%.3f, %.3f, %.3f, %.3f", params.l1, params.l2, params.l3, params.l4)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Cyan.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                when (currentTab) {
                    "Speed" -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Petal Filter
                            Box(modifier = Modifier.weight(1.2f).padding(horizontal = 4.dp)) {
                                val label = if (selectedPetalFilter == null) "All Petals" else "${selectedPetalFilter}P"
                                val count = remember(selectedPetalFilter, allRatios) {
                                    if (selectedPetalFilter == null) allRatios.size
                                    else allRatios.count { it.petals == selectedPetalFilter }
                                }
                                OutlinedButton(onClick = { isPetalMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text(text = "$label - $count", style = MaterialTheme.typography.labelSmall)
                                }
                                DropdownMenu(expanded = isPetalMenuExpanded, onDismissRequest = { isPetalMenuExpanded = false }) {
                                    DropdownMenuItem(text = { Text("All (${allRatios.size})") }, onClick = { selectedPetalFilter = null; isPetalMenuExpanded = false })
                                    listOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16).forEach { p ->
                                        val pCount = allRatios.count { it.petals == p }
                                        if (pCount > 0) {
                                            DropdownMenuItem(text = { Text("${p}P ($pCount)") }, onClick = { selectedPetalFilter = p; isPetalMenuExpanded = false })
                                        }
                                    }
                                }
                            }
                            // Sort Menu
                            Box(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                                OutlinedButton(onClick = { isSortMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text(text = "Sort: $currentSortBy", style = MaterialTheme.typography.labelSmall)
                                }
                                DropdownMenu(expanded = isSortMenuExpanded, onDismissRequest = { isSortMenuExpanded = false }) {
                                    val sortOptions = listOf(
                                        "shapeRatio", "multiplicityClass", "independentFreqCount", 
                                        "twoFoldLikely", "hierarchyDepth", "dominanceRatio", "radialVariance"
                                    )
                                    sortOptions.forEach { option ->
                                        DropdownMenuItem(text = { Text(option) }, onClick = { currentSortBy = option; isSortMenuExpanded = false })
                                    }
                                }
                            }
                            // Untagged Toggle
                            FilterChip(
                                selected = showOnlyUntagged,
                                onClick = { showOnlyUntagged = !showOnlyUntagged },
                                label = { Text("Untagged", style = MaterialTheme.typography.labelSmall) }
                            )
                        }

                        // Ratio List with Scrub Bar
                        Row(modifier = Modifier.weight(1f)) {
                            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                                itemsIndexed(filteredRatios) { index, ratio ->
                                    val currentTags = tags[ratio.id] ?: emptyList()
                                    val displayValue = when (currentSortBy) {
                                        "multiplicityClass" -> ratio.multiplicityClass.toString()
                                        "independentFreqCount" -> ratio.independentFreqCount.toString()
                                        "twoFoldLikely" -> ratio.twoFoldLikely.toString()
                                        "hierarchyDepth" -> ratio.hierarchyDepth.toString()
                                        "dominanceRatio" -> String.format(Locale.US, "%.2f", ratio.dominanceRatio)
                                        "radialVariance" -> String.format(Locale.US, "%.2f", ratio.radialVariance)
                                        else -> String.format(Locale.US, "%.2f", ratio.shapeRatio)
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                params = params.copy(omega1 = ratio.a, omega2 = ratio.b, omega3 = ratio.c, omega4 = ratio.d)
                                            }
                                            .padding(4.dp)
                                            .background(if (currentIndex == index) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.2f))
                                    ) {
                                        Text(
                                            text = "($displayValue) ${ratio.a}, ${ratio.b}, ${ratio.c}, ${ratio.d}",
                                            color = if (currentTags.isNotEmpty()) Color.Yellow else Color.White,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (currentTags.isNotEmpty()) {
                                            Text(text = currentTags.joinToString(","), color = Color.Yellow, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                            
                            // VERTICAL SCRUB BAR
                            if (filteredRatios.isNotEmpty()) {
                                Slider(
                                    value = if (filteredRatios.size > 1) {
                                        val firstVisible = listState.firstVisibleItemIndex
                                        firstVisible.toFloat() / (filteredRatios.size - 1)
                                    } else 0f,
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
                    "CV Lab" -> {
                        CvLabScreen()
                    }
                    "Patch Bay" -> {
                        InstrumentEditorScreen(visualSource)
                    }
                }
            }

            if (currentTab != "CV Lab" && currentTab != "Patch Bay") {
                // BOTTOM CONTROLS
                val currentId = currentRatio?.id

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Prev Button
                    IconButton(
                        onClick = goToPrev,
                        enabled = currentIndex > 0
                    ) { Icon(Icons.Default.ArrowBack, contentDescription = "Prev", tint = Color.White) }

                    // Tag Buttons
                    if (currentId != null) {
                        val currentTags = tags[currentId] ?: emptyList()
                        Row(modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.medium)) {
                            TagButton(icon = Icons.Default.Delete, label = "trash", active = "trash" in currentTags) { 
                                vm.toggleTag(currentId, "trash")
                                goToNext() 
                            }
                            TagButton(label = "1", active = "1" in currentTags) { 
                                vm.toggleTag(currentId, "1")
                                goToNext() 
                            }
                            TagButton(label = "2", active = "2" in currentTags) { 
                                vm.toggleTag(currentId, "2")
                                goToNext() 
                            }
                            TagButton(label = "3", active = "3" in currentTags) { 
                                vm.toggleTag(currentId, "3")
                                goToNext() 
                            }
                            TagButton(label = "OVAL", active = "oval" in currentTags) { 
                                vm.toggleTag(currentId, "oval")
                            }
                            TagButton(label = "BRAID", active = "braid" in currentTags) { 
                                vm.toggleTag(currentId, "braid")
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    // Next Button
                    IconButton(
                        onClick = goToNext,
                        enabled = currentIndex < filteredRatios.size - 1
                    ) { Icon(Icons.Default.ArrowForward, contentDescription = "Next", tint = Color.White) }
                }
            }
        }
    }

    @Composable
    fun TagButton(icon: androidx.compose.ui.graphics.vector.ImageVector? = null, label: String = "", active: Boolean, onClick: () -> Unit) {
        IconButton(onClick = onClick) {
            if (icon != null) {
                Icon(icon, contentDescription = label, tint = if (active) Color.Red else Color.Gray)
            } else {
                Text(text = label, color = if (active) Color.Green else Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    @Composable
    fun LengthSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float> = 0f..1f, onValueChange: (Float) -> Unit) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(text = "$label: ${String.format(Locale.US, "%.3f", value)}", color = Color.White, style = MaterialTheme.typography.labelSmall)
            Slider(value = value, onValueChange = onValueChange, valueRange = range, modifier = Modifier.height(20.dp))
        }
    }

    override fun onPause() { super.onPause(); spiralSurfaceView?.onPause() }
    override fun onResume() { super.onResume(); spiralSurfaceView?.onResume() }
}
