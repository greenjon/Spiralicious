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
        "Hue Offset" to ModulatableParameter(0.0f),
        "Hue Sweep" to ModulatableParameter(1.0f / 9.0f) // Default 1.0, scaled 0-9
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
    
    // Phase 1.2: Store triplets: [x, y, phase]
    private val points = 2048
    val geometryBuffer = FloatArray((points + 1) * 3)

    override fun update() {
        super.update()
        updateGeometry()
    }

    private fun updateGeometry() {
        val l1 = parameters["L1"]!!.value
        val l2 = parameters["L2"]!!.value
        val l3 = parameters["L3"]!!.value
        val l4 = parameters["L4"]!!.value
        
        val dt = (2.0 * PI) / points
        
        for (i in 0..points) {
            val t = i * dt
            val phase = i.toFloat() / points.toFloat()
            
            val angle1 = t * recipe.a
            val angle2 = t * recipe.b
            val angle3 = t * recipe.c
            val angle4 = t * recipe.d

            val x = (l1 * cos(angle1) + l2 * cos(angle2) + l3 * cos(angle3) + l4 * cos(angle4)).toFloat()
            val y = (l1 * sin(angle1) + l2 * sin(angle2) + l3 * sin(angle3) + l4 * sin(angle4)).toFloat()
            
            geometryBuffer[i * 3] = x
            geometryBuffer[i * 3 + 1] = y
            geometryBuffer[i * 3 + 2] = phase
        }
    }

    override fun render(canvas: Canvas, width: Int, height: Int) {
        val alpha = globalAlpha.value
        if (alpha <= 0f) return // Skip rendering if invisible

        // Scale geometry for Canvas - mandala is normalized 0..1 arm lengths
        val canvasScaleFactor = width / 2f
        
        // Match the 8x scaling logic from the OpenGL renderer
        val scale = parameters["Scale"]!!.value * globalScale.value * 8.0f
        
        val thickness = parameters["Thickness"]!!.value * 20f
        val hueOffset = parameters["Hue Offset"]!!.value
        val hueSweep = parameters["Hue Sweep"]!!.value * 9.0f // Scale 0-1 to 0-9
        val rotationDegrees = parameters["Rotation"]!!.value * 360f

        paint.strokeWidth = thickness
        hsvBuffer[1] = 0.8f // Fixed Saturation as per blueprint
        hsvBuffer[2] = 1.0f // Fixed Value

        val cx = width / 2f
        val cy = height / 2f

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(rotationDegrees)
        canvas.scale(scale * canvasScaleFactor, scale * canvasScaleFactor)

        for (i in 0 until points) {
            val x1 = geometryBuffer[i * 3]
            val y1 = geometryBuffer[i * 3 + 1]
            val phase = geometryBuffer[i * 3 + 2]
            
            val x2 = geometryBuffer[(i + 1) * 3]
            val y2 = geometryBuffer[(i + 1) * 3 + 1]

            // For Canvas, we update color per segment to match the new phase coloring
            val currentHue = ((hueOffset + phase * hueSweep) % 1.0f) * 360f
            hsvBuffer[0] = currentHue
            val baseColor = Color.HSVToColor(hsvBuffer)
            paint.color = Color.argb(
                (alpha * 255).toInt(),
                Color.red(baseColor),
                Color.green(baseColor),
                Color.blue(baseColor)
            )
            canvas.drawLine(x1, y1, x2, y2, paint)
        }

        canvas.restore()
    }
}
