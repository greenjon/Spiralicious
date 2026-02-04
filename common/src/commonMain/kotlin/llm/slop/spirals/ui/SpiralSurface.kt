package llm.slop.spirals.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import llm.slop.spirals.display.ISpiralRenderer

@Composable
expect fun SpiralSurface(
    renderer: ISpiralRenderer,
    modifier: Modifier = Modifier
)
