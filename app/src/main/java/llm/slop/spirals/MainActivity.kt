package llm.slop.spirals

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : ComponentActivity() {
    private var spiralSurfaceView: SpiralSurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                MandalaScreen()
            }
        }
    }

    @Composable
    fun MandalaScreen() {
        var params by remember { mutableStateOf(MandalaParams(omega1 = 20, omega2 = 17, omega3 = 11)) }
        var currentTab by remember { mutableStateOf("Speed") }
        var selectedLobeFilter by remember { mutableStateOf<Int?>(null) }
        var isLobeMenuExpanded by remember { mutableStateOf(false) }

        val allRatios = MandalaLibrary.MandalaRatios
        val lobesOptions = remember { allRatios.map { it.lobes }.distinct().sorted() }
        val filteredRatios = remember(selectedLobeFilter) {
            if (selectedLobeFilter == null) allRatios else allRatios.filter { it.lobes == selectedLobeFilter }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { context -> SpiralSurfaceView(context).also { spiralSurfaceView = it } },
                update = { view -> view.setParams(params) },
                modifier = Modifier.fillMaxSize()
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth() // Changed from fillMaxWidth(0.4f)
                    .fillMaxHeight(0.6f)
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.7f))
            ) {
                // Tab Header
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { currentTab = "Speed" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentTab == "Speed") MaterialTheme.colorScheme.primary else Color.Gray
                        ),
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text("Speed")
                    }
                    Button(
                        onClick = { currentTab = "Length" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentTab == "Length") MaterialTheme.colorScheme.primary else Color.Gray
                        ),
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text("Length")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                when (currentTab) {
                    "Speed" -> {
                        // Lobe Filter Dropdown
                        Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                            OutlinedButton(
                                onClick = { isLobeMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = if (selectedLobeFilter == null) "All Lobes" else "Lobes: $selectedLobeFilter", style = MaterialTheme.typography.labelSmall)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = isLobeMenuExpanded,
                                onDismissRequest = { isLobeMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("All Lobes") },
                                    onClick = {
                                        selectedLobeFilter = null
                                        isLobeMenuExpanded = false
                                    }
                                )
                                lobesOptions.forEach { lobeCount ->
                                    DropdownMenuItem(
                                        text = { Text("$lobeCount Lobes") },
                                        onClick = {
                                            selectedLobeFilter = lobeCount
                                            isLobeMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Ratio List
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(filteredRatios) { ratio ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            params = params.copy(
                                                omega1 = ratio.omega1,
                                                omega2 = ratio.omega2,
                                                omega3 = ratio.omega3
                                            )
                                        }
                                        .padding(8.dp)
                                        .background(if (params.omega1 == ratio.omega1 && params.omega2 == ratio.omega2 && params.omega3 == ratio.omega3) 
                                            Color.White.copy(alpha = 0.2f) else Color.Transparent)
                                ) {
                                    Text(
                                        text = "${ratio.omega1} : ${ratio.omega2} : ${ratio.omega3}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                    "Length" -> {
                        Column(modifier = Modifier.padding(16.dp)) {
                            LengthSlider("L1", params.l1) { params = params.copy(l1 = it) }
                            LengthSlider("L2", params.l2) { params = params.copy(l2 = it) }
                            LengthSlider("L3", params.l3) { params = params.copy(l3 = it) }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun LengthSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(text = "$label: ${String.format("%.2f", value)}", color = Color.White, style = MaterialTheme.typography.labelSmall)
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = 0f..1f,
                modifier = Modifier.height(24.dp)
            )
        }
    }

    override fun onPause() {
        super.onPause()
        spiralSurfaceView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        spiralSurfaceView?.onResume()
    }
}
