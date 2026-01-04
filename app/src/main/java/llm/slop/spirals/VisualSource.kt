package llm.slop.spirals

import android.graphics.Canvas
import llm.slop.spirals.cv.ModulatableParameter

/**
 * Interface for renderable visual objects that consume modulatable parameters.
 */
interface VisualSource {
    /**
     * Map of parameter names to their modulatable counterparts.
     */
    val parameters: Map<String, ModulatableParameter>

    /**
     * Top-level parameters for mixing and composition.
     */
    val globalAlpha: ModulatableParameter
    val globalScale: ModulatableParameter

    /**
     * Trigger evaluation of all parameters. 
     * Expected to be called at 120Hz or per frame.
     */
    fun update() {
        parameters.values.forEach { it.evaluate() }
        globalAlpha.evaluate()
        globalScale.evaluate()
    }

    /**
     * Render the visual state to the canvas.
     */
    fun render(canvas: Canvas, width: Int, height: Int)
}
