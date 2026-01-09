package llm.slop.spirals

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import llm.slop.spirals.cv.ModulatableParameter
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * Optimized VisualSource that avoids allocations in the render loop.
 * Renders exactly one complete closed loop based on integer frequencies.
 */
class MandalaVisualSource : VisualSource {
    // Note: Use a Map that preserves insertion order if you want the UI to follow this order
    override val parameters = linkedMapOf(
        "L1" to ModulatableParameter(0.4f),
        "L2" to ModulatableParameter(0.3f),
        "L3" to ModulatableParameter(0.2f),
        "L4" to ModulatableParameter(0.1f),
        "Scale" to ModulatableParameter(0.125f), // Default 0.125 * 8x = 1.0 Unity
        "Rotation" to ModulatableParameter(0.0f),
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
        val alpha = globalAlpha.value
        if (alpha <= 0f) return // Skip rendering if invisible

        val l1 = parameters["L1"]!!.value * (width / 2f)
        val l2 = parameters["L2"]!!.value * (width / 2f)
        val l3 = parameters["L3"]!!.value * (width / 2f)
        val l4 = parameters["L4"]!!.value * (width / 2f)
        
        // Match the 8x scaling logic from the OpenGL renderer
        val scale = parameters["Scale"]!!.value * globalScale.value * 8.0f
        
        val thickness = parameters["Thickness"]!!.value * 20f
        val hue = parameters["Hue"]!!.value * 360f
        val sat = parameters["Saturation"]!!.value
        val rotationDegrees = parameters["Rotation"]!!.value * 360f

        paint.strokeWidth = thickness
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
        canvas.rotate(rotationDegrees)
        canvas.scale(scale, scale)

        var lastX = 0f
        var lastY = 0f
        
        // Total points for the loop. 
        // 2048 matches the OpenGL resolution for consistency.
        val points = 2048
        val dt = (2.0 * PI) / points
        
        for (i in 0..points) {
            val t = i * dt
            val angle1 = t * recipe.a
            val angle2 = t * recipe.b
            val angle3 = t * recipe.c
            val angle4 = t * recipe.d

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
