package llm.slop.spirals

import androidx.compose.ui.geometry.Size
import kotlin.math.max

fun calculateMultipliedSize(size: Size, ratio: Float): Size {
    return if (ratio > 1f) {
        Size(size.width, size.height / ratio)
    } else {
        Size(size.width * ratio, size.height)
    }
}

fun getRatio(size: Size): Float {
    return size.width / size.height
}

fun getMaxSquare(size: Size): Size {
    val maxD = max(size.width, size.height)
    return Size(maxD, maxD)
}
