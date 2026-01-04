package llm.slop.spirals

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import llm.slop.spirals.cv.ModulatableParameter
import kotlin.math.cos
import kotlin.math.sin

/**
 * Optimized VisualSource that avoids allocations in the render loop.
 */
class MandalaVisualSource : VisualSource {
    override val parameters = mapOf(
        "L1" to ModulatableParameter(0.4f),
        "L2" to ModulatableParameter(0.3f),
        "L3" to ModulatableParameter(0.2f),
        "L4" to ModulatableParameter(0.1f),
        "SpinRate" to ModulatableParameter(0.5f),
        "Scale" to ModulatableParameter(0.8f),
        "Thickness" to ModulatableParameter(0.1f),
        "Hue" to ModulatableParameter(0.0f),
        "Saturation" to ModulatableParameter(1.0f)
    )

    override val globalAlpha = ModulatableParameter(1.0f)
    override val globalScale = ModulatableParameter(1.0f)

    var recipe: MandalaRatio = MandalaLibrary.MandalaRatios.first()
    
    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    // Pre-allocated buffers to avoid GC pressure
    private val hsvBuffer = FloatArray(3)

    override fun render(canvas: Canvas, width: Int, height: Int) {
        // Use the already-evaluated '.value' to avoid redundant math in draw thread
        val l1 = parameters["L1"]!!.value * (width / 2f)
        val l2 = parameters["L2"]!!.value * (width / 2f)
        val l3 = parameters["L3"]!!.value * (width / 2f)
        val l4 = parameters["L4"]!!.value * (width / 2f)
        val spinRate = parameters["SpinRate"]!!.value * 5.0f
        val scale = parameters["Scale"]!!.value * globalScale.value
        val thickness = parameters["Thickness"]!!.value * 20f
        val hue = parameters["Hue"]!!.value * 360f
        val sat = parameters["Saturation"]!!.value
        val alpha = globalAlpha.value

        paint.strokeWidth = thickness
        
        // Populate pre-allocated buffer
        hsvBuffer[0] = hue
        hsvBuffer[1] = sat
        hsvBuffer[2] = 1.0f
        
        val baseColor = Color.HSVToColor(hsvBuffer)
        paint.color = Color.argb(
            (alpha * 255).toInt(),
            Color.red(baseColor),
            Color.green(baseColor),
            Color.blue(baseColor)
        )

        val cx = width / 2f
        val cy = height / 2f

        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(scale, scale)

        var lastX = 0f
        var lastY = 0f
        
        val dt = 0.01
        for (i in 0..1000) {
            val t = i * dt
            val angle1 = t * recipe.a * spinRate
            val angle2 = t * recipe.b * spinRate
            val angle3 = t * recipe.c * spinRate
            val angle4 = t * recipe.d * spinRate

            val x = (l1 * cos(angle1) + l2 * cos(angle2) + l3 * cos(angle3) + l4 * cos(angle4)).toFloat()
            val y = (l1 * sin(angle1) + l2 * sin(angle2) + l3 * sin(angle3) + l4 * sin(angle4)).toFloat()

            if (i > 0) {
                canvas.drawLine(lastX, lastY, x, y, paint)
            }
            lastX = x
            lastY = y
        }

        canvas.restore()
    }
}
