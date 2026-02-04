package llm.slop.spirals.ui

import android.opengl.GLSurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import llm.slop.spirals.display.ISpiralRenderer
import llm.slop.spirals.SpiralSurfaceView

@Composable
actual fun SpiralSurface(
    renderer: ISpiralRenderer,
    modifier: Modifier
) {
    AndroidView(
        factory = { context ->
            SpiralSurfaceView(context).apply {
                if (renderer is GLSurfaceView.Renderer) {
                    setSpiralRenderer(renderer)
                } else {
                    throw IllegalArgumentException("Renderer must implement GLSurfaceView.Renderer on Android")
                }
            }
        },
        modifier = modifier
    )
}
