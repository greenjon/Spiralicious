package llm.slop.spirals

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : ComponentActivity() {
    private var spiralSurfaceView: SpiralSurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                MandalaScreen()
            }
        }
    }

    @Composable
    fun MandalaScreen() {
        var params by remember { mutableStateOf(MandalaParams()) }

        Box(modifier = Modifier.fillMaxSize()) {
            // OpenGL Surface
            AndroidView(
                factory = { context ->
                    SpiralSurfaceView(context).also {
                        spiralSurfaceView = it
                    }
                },
                update = { view ->
                    view.setParams(params)
                },
                modifier = Modifier.fillMaxSize()
            )

            // UI Overlay
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Omega Controls
                ParameterSlider(
                    label = "Omega 1: ${params.omega1}",
                    value = params.omega1.toFloat(),
                    range = -20f..20f,
                    onValueChange = { params = params.copy(omega1 = it.toInt()) }
                )
                ParameterSlider(
                    label = "Omega 2: ${params.omega2}",
                    value = params.omega2.toFloat(),
                    range = -20f..20f,
                    onValueChange = { params = params.copy(omega2 = it.toInt()) }
                )
                ParameterSlider(
                    label = "Omega 3: ${params.omega3}",
                    value = params.omega3.toFloat(),
                    range = -20f..20f,
                    onValueChange = { params = params.copy(omega3 = it.toInt()) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Length Controls
                ParameterSlider(
                    label = "Length 1: ${String.format("%.2f", params.l1)}",
                    value = params.l1,
                    range = 0f..0.5f,
                    onValueChange = { params = params.copy(l1 = it) }
                )
                ParameterSlider(
                    label = "Length 2: ${String.format("%.2f", params.l2)}",
                    value = params.l2,
                    range = 0f..0.5f,
                    onValueChange = { params = params.copy(l2 = it) }
                )
                ParameterSlider(
                    label = "Length 3: ${String.format("%.2f", params.l3)}",
                    value = params.l3,
                    range = 0f..0.5f,
                    onValueChange = { params = params.copy(l3 = it) }
                )
            }
        }
    }

    @Composable
    fun ParameterSlider(
        label: String,
        value: Float,
        range: ClosedFloatingPointRange<Float>,
        onValueChange: (Float) -> Unit
    ) {
        Column {
            Text(text = label, color = Color.White, style = MaterialTheme.typography.bodySmall)
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                modifier = Modifier.height(32.dp)
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
