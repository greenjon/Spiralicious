package llm.slop.spirals

import llm.slop.spirals.models.mandala.MandalaRatio
import llm.slop.spirals.models.ModulatableParameterData
import kotlin.math.max
import kotlin.math.abs

interface VisualSource {
    val parameters: MutableMap<String, ModulatableParameter>
    val globalAlpha: ModulatableParameter
    val globalScale: ModulatableParameter

    fun update(modSources: Map<String, Float>)
}

class MandalaVisualSource : VisualSource {
    override val parameters = linkedMapOf(
        "L1" to ModulatableParameter(ModulatableParameterData(0.4f)),
        "L2" to ModulatableParameter(ModulatableParameterData(0.3f)),
        "L3" to ModulatableParameter(ModulatableParameterData(0.2f)),
        "L4" to ModulatableParameter(ModulatableParameterData(0.1f)),
        "Scale" to ModulatableParameter(ModulatableParameterData(0.125f)),
        "Rotation" to ModulatableParameter(ModulatableParameterData(0.0f)),
        "Thickness" to ModulatableParameter(ModulatableParameterData(0.1f)),
        "Hue Offset" to ModulatableParameter(ModulatableParameterData(0.0f)),
        "Hue Sweep" to ModulatableParameter(ModulatableParameterData(1.0f / 9.0f)),
        "Depth" to ModulatableParameter(ModulatableParameterData(0.35f)),
        "FB Gain" to ModulatableParameter(ModulatableParameterData(0.0f)),
        "FB Zoom" to ModulatableParameter(ModulatableParameterData(0.5f)),
        "FB Rotate" to ModulatableParameter(ModulatableParameterData(0.5f)),
        "FB Shift" to ModulatableParameter(ModulatableParameterData(0.0f)),
        "FB Blur" to ModulatableParameter(ModulatableParameterData(0.0f))
    )

    override var globalAlpha = ModulatableParameter(ModulatableParameterData(1.0f))
    override var globalScale = ModulatableParameter(ModulatableParameterData(1.0f))

    var recipe: MandalaRatio = MandalaLibrary.MandalaRatios.first()
    
    val evaluatedValues = mutableMapOf<String, Float>()
    var evaluatedGlobalAlpha: Float = 1.0f
    var evaluatedGlobalScale: Float = 1.0f

    var minR: Float = 0f
        private set
    var maxR: Float = 1f
        private set

    var isDirty = true
    private var lastRecipeId = ""

    override fun update(modSources: Map<String, Float>) {
        parameters.forEach { (k, v) -> evaluatedValues[k] = v.getValue(modSources) }
        evaluatedGlobalAlpha = globalAlpha.getValue(modSources)
        evaluatedGlobalScale = globalScale.getValue(modSources)

        val l1 = abs(evaluatedValues["L1"] ?: 0f)
        val l2 = abs(evaluatedValues["L2"] ?: 0f)
        val l3 = abs(evaluatedValues["L3"] ?: 0f)
        val l4 = abs(evaluatedValues["L4"] ?: 0f)

        maxR = max(0.001f, l1 + l2 + l3 + l4)
        minR = 0f

        if (recipe.id != lastRecipeId) {
            lastRecipeId = recipe.id
            isDirty = true
        }
    }

    fun copy(): MandalaVisualSource {
        val newSource = MandalaVisualSource()
        this.parameters.forEach { (k, v) ->
             newSource.parameters[k] = ModulatableParameter(v.data)
        }
        newSource.globalAlpha = ModulatableParameter(this.globalAlpha.data)
        newSource.globalScale = ModulatableParameter(this.globalScale.data)
        newSource.recipe = this.recipe.copy()
        return newSource
    }
}
