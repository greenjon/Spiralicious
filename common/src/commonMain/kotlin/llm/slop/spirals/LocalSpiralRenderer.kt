package llm.slop.spirals

import androidx.compose.runtime.staticCompositionLocalOf

// We use the interface here so the UI doesn't care about the implementation
val LocalSpiralRenderer = staticCompositionLocalOf<ISpiralRenderer> {
    error("No SpiralRenderer provided")
}
