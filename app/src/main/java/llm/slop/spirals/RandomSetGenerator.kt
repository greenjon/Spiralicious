package llm.slop.spirals

import android.content.Context
import llm.slop.spirals.cv.CvModulator
import llm.slop.spirals.cv.ModulationOperator
import llm.slop.spirals.cv.Waveform
import llm.slop.spirals.models.RandomSet
import llm.slop.spirals.models.RecipeFilter
import llm.slop.spirals.models.STANDARD_BEAT_VALUES

/**
 * RandomSetGenerator - Generates ephemeral mandala patches from RSet templates.
 * 
 * PHILOSOPHY:
 * The RSet template IS the creative work. This generator respects the constraints
 * defined in the template and produces infinite variations within those guardrails.
 * 
 * PHASE 1 IMPLEMENTATION:
 * - Recipe filtering (ALL, FAVORITES_ONLY, PETALS_EXACT, PETALS_RANGE)
 * - Auto hue sweep (matches recipe petals)
 * - Default randomization for arms, rotation, hue offset (using existing logic)
 * - Feedback mode presets
 * 
 * PHASE 2 (Future):
 * - Granular arm constraints (base length ranges, waveform selection, weight ranges)
 * - Rotation and hue offset constraints (direction, speed control)
 * - Custom feedback ranges
 * 
 * NOTE TO FUTURE AI: When implementing Phase 2, use the constraint objects in the
 * RandomSet data class. The structure is already in place, just needs the logic here.
 */
class RandomSetGenerator(private val context: Context) {
    
    private val tagManager = RecipeTagManager(context)
    
    /**
     * Generates a mandala configuration from an RSet template.
     * Applies the configuration directly to the provided visual source.
     * 
     * @param rset The Random Set template containing constraints
     * @param visualSource The visual source to configure
     */
    fun generateFromRSet(rset: RandomSet, visualSource: MandalaVisualSource) {
        val random = kotlin.random.Random.Default
        
        // 1. Select recipe based on filter
        val recipe = selectRecipe(rset)
        visualSource.recipe = recipe
        
        // 2. Auto-set hue sweep if enabled
        if (rset.autoHueSweep) {
            visualSource.parameters["Hue Sweep"]?.let { param ->
                param.baseValue = recipe.petals / 9.0f
                param.modulators.clear()
            }
        }
        
        // 3. Apply constraints to L1-L4
        applyArmConstraints("L1", rset.l1Constraints, visualSource, random)
        applyArmConstraints("L2", rset.l2Constraints, visualSource, random)
        applyArmConstraints("L3", rset.l3Constraints, visualSource, random)
        applyArmConstraints("L4", rset.l4Constraints, visualSource, random)
        
        // 4. Apply rotation constraints
        applyRotationConstraints(rset.rotationConstraints, visualSource, random)
        
        // 5. Apply hue offset constraints
        applyHueOffsetConstraints(rset.hueOffsetConstraints, visualSource, random)
        
        // 6. Apply feedback mode
        applyFeedbackMode(rset.feedbackMode, visualSource)
    }
    
    /**
     * Applies arm parameter constraints with full Phase 2 support.
     */
    private fun applyArmConstraints(
        paramName: String,
        constraints: llm.slop.spirals.models.ArmConstraints?,
        visualSource: MandalaVisualSource,
        random: kotlin.random.Random
    ) {
        visualSource.parameters[paramName]?.let { param ->
            // Use constraints if provided, otherwise use defaults
            val c = constraints ?: llm.slop.spirals.models.ArmConstraints()
            
            // Base value from range
            param.baseValue = (random.nextInt(c.baseLengthMin, c.baseLengthMax + 1) / 100f)
            param.modulators.clear()
            
            // Add modulator if Beat or LFO is enabled
            if (c.enableBeat || c.enableLfo) {
                // Select waveform from allowed options
                val allowedWaveforms = mutableListOf<Waveform>()
                if (c.allowSine) allowedWaveforms.add(Waveform.SINE)
                if (c.allowTriangle) allowedWaveforms.add(Waveform.TRIANGLE)
                if (c.allowSquare) allowedWaveforms.add(Waveform.SQUARE)
                
                // Fall back to SINE if no waveforms selected
                val waveform = if (allowedWaveforms.isNotEmpty()) {
                    allowedWaveforms.random(random)
                } else {
                    Waveform.SINE
                }
                
                // Weight from range (convert to 0-1 scale)
                val weight = random.nextInt(c.weightMin, c.weightMax + 1) / 100f
                
                // Choose beat or LFO based on what's enabled
                val useBeat = when {
                    c.enableBeat && c.enableLfo -> random.nextBoolean()
                    c.enableBeat -> true
                    else -> false
                }
                
                if (useBeat) {
                    // Beat subdivision from range
                    // Use the standardized beat values and pick one in the valid range
                val validValues = STANDARD_BEAT_VALUES.filter { it in c.beatDivMin..c.beatDivMax }
                val subdivision = if (validValues.isNotEmpty()) {
                    validValues.random(random)
                } else {
                    // Fallback to nearest allowed value
                    STANDARD_BEAT_VALUES.minByOrNull { kotlin.math.abs(it - c.beatDivMin) } ?: 1f
                }
                    
                    param.modulators.add(
                        CvModulator(
                            sourceId = "beatPhase",
                            operator = ModulationOperator.ADD,
                            waveform = waveform,
                            slope = 0.5f,
                            weight = weight,
                            phaseOffset = random.nextFloat(),
                            subdivision = subdivision
                        )
                    )
                } else {
                    // LFO time from range (convert to frequency)
                    val timeSeconds = random.nextInt(c.lfoTimeMin.toInt(), c.lfoTimeMax.toInt() + 1).toFloat()
                    
                    param.modulators.add(
                        CvModulator(
                            sourceId = "lfo1",
                            operator = ModulationOperator.ADD,
                            waveform = waveform,
                            slope = 0.5f,
                            weight = weight,
                            phaseOffset = random.nextFloat(),
                            subdivision = timeSeconds
                        )
                    )
                }
            }
        }
    }
    
    /**
     * Applies rotation constraints with full Phase 2 support.
     */
    private fun applyRotationConstraints(
        constraints: llm.slop.spirals.models.RotationConstraints?,
        visualSource: MandalaVisualSource,
        random: kotlin.random.Random
    ) {
        if (constraints == null) {
            // Default: random rotation
            visualSource.parameters["Rotation"]?.let { param ->
                param.baseValue = 0f
                param.modulators.clear()
                
                // Select values 4 and above for default behavior (matching previous range)
                val usableValues = STANDARD_BEAT_VALUES.filter { it >= 4f && it <= 128f }
                
                param.modulators.add(
                    CvModulator(
                        sourceId = "beatPhase",
                        operator = ModulationOperator.ADD,
                        waveform = Waveform.TRIANGLE,
                        slope = if (random.nextBoolean()) 0f else 1f,
                        weight = 1.0f,
                        phaseOffset = random.nextFloat(),
                        subdivision = usableValues.random(random)
                    )
                )
            }
            return
        }
        
        visualSource.parameters["Rotation"]?.let { param ->
            param.baseValue = 0f
            param.modulators.clear()
            
            // Determine direction (slope: 0 = clockwise ramp down, 1 = counter-clockwise ramp up)
            val directions = mutableListOf<Float>()
            if (constraints.enableClockwise) directions.add(0f)
            if (constraints.enableCounterClockwise) directions.add(1f)
            
            val slope = if (directions.isNotEmpty()) {
                directions.random(random)
            } else {
                0f // Default to clockwise if nothing selected
            }
            
            val sourceId: String
            val subdivision: Float
            
            if (constraints.speedSource == llm.slop.spirals.models.SpeedSource.BEAT) {
                sourceId = "beatPhase"
                // Use the standardized beat values and pick one in the valid range
                val validValues = STANDARD_BEAT_VALUES.filter { it in constraints.beatDivMin..constraints.beatDivMax }
                subdivision = if (validValues.isNotEmpty()) {
                    validValues.random(random)
                } else {
                    // Fallback to nearest allowed value
                    STANDARD_BEAT_VALUES.minByOrNull { kotlin.math.abs(it - constraints.beatDivMin) } ?: 1f
                }
            } else {
                sourceId = "lfo1"
                subdivision = random.nextInt(constraints.lfoTimeMin.toInt(), constraints.lfoTimeMax.toInt() + 1).toFloat()
            }
            
            param.modulators.add(
                CvModulator(
                    sourceId = sourceId,
                    operator = ModulationOperator.ADD,
                    waveform = Waveform.TRIANGLE,
                    slope = slope,
                    weight = 1.0f,
                    phaseOffset = random.nextFloat(),
                    subdivision = subdivision
                )
            )
        }
    }
    
    /**
     * Applies hue offset constraints with full Phase 2 support.
     */
    private fun applyHueOffsetConstraints(
        constraints: llm.slop.spirals.models.HueOffsetConstraints?,
        visualSource: MandalaVisualSource,
        random: kotlin.random.Random
    ) {
        if (constraints == null) {
            // Default: random hue cycling
            visualSource.parameters["Hue Offset"]?.let { param ->
                param.baseValue = 0f
                param.modulators.clear()
                
                // Select values 4 to 16 for default behavior (matching previous range)
                val usableValues = STANDARD_BEAT_VALUES.filter { it >= 4f && it <= 16f }
                
                param.modulators.add(
                    CvModulator(
                        sourceId = "beatPhase",
                        operator = ModulationOperator.ADD,
                        waveform = Waveform.TRIANGLE,
                        slope = if (random.nextBoolean()) 0f else 1f,
                        weight = 1.0f,
                        phaseOffset = random.nextFloat(),
                        subdivision = usableValues.random(random)
                    )
                )
            }
            return
        }
        
        visualSource.parameters["Hue Offset"]?.let { param ->
            param.baseValue = 0f
            param.modulators.clear()
            
            // Determine direction
            val directions = mutableListOf<Float>()
            if (constraints.enableForward) directions.add(1f)
            if (constraints.enableReverse) directions.add(0f)
            
            val slope = if (directions.isNotEmpty()) {
                directions.random(random)
            } else {
                1f // Default to forward
            }
            
            val sourceId: String
            val subdivision: Float
            
            if (constraints.speedSource == llm.slop.spirals.models.SpeedSource.BEAT) {
                sourceId = "beatPhase"
                // Use the standardized beat values and pick one in the valid range
                val validValues = STANDARD_BEAT_VALUES.filter { it in constraints.beatDivMin..constraints.beatDivMax }
                subdivision = if (validValues.isNotEmpty()) {
                    validValues.random(random)
                } else {
                    // Fallback to nearest allowed value
                    STANDARD_BEAT_VALUES.minByOrNull { kotlin.math.abs(it - constraints.beatDivMin) } ?: 1f
                }
            } else {
                sourceId = "lfo1"
                subdivision = random.nextInt(constraints.lfoTimeMin.toInt(), constraints.lfoTimeMax.toInt() + 1).toFloat()
            }
            
            param.modulators.add(
                CvModulator(
                    sourceId = sourceId,
                    operator = ModulationOperator.ADD,
                    waveform = Waveform.TRIANGLE,
                    slope = slope,
                    weight = 1.0f,
                    phaseOffset = random.nextFloat(),
                    subdivision = subdivision
                )
            )
        }
    }
    
    /**
     * Applies feedback mode presets.
     * Note: Feedback parameters may need to be added to MandalaVisualSource for full support.
     */
    private fun applyFeedbackMode(
        mode: llm.slop.spirals.models.FeedbackMode,
        visualSource: MandalaVisualSource
    ) {
        when (mode) {
            llm.slop.spirals.models.FeedbackMode.NONE -> {
                visualSource.parameters["FB Decay"]?.baseValue = 0f
                visualSource.parameters["FB Gain"]?.baseValue = 1f
            }
            llm.slop.spirals.models.FeedbackMode.LIGHT -> {
                visualSource.parameters["FB Decay"]?.baseValue = 0.05f
                visualSource.parameters["FB Gain"]?.baseValue = 0.95f
                visualSource.parameters["FB Zoom"]?.baseValue = 0.51f
                visualSource.parameters["FB Rotate"]?.baseValue = 0.50f
            }
            llm.slop.spirals.models.FeedbackMode.MEDIUM -> {
                visualSource.parameters["FB Decay"]?.baseValue = 0.15f
                visualSource.parameters["FB Gain"]?.baseValue = 0.90f
                visualSource.parameters["FB Zoom"]?.baseValue = 0.52f
                visualSource.parameters["FB Rotate"]?.baseValue = 0.51f
            }
            llm.slop.spirals.models.FeedbackMode.HEAVY -> {
                visualSource.parameters["FB Decay"]?.baseValue = 0.30f
                visualSource.parameters["FB Gain"]?.baseValue = 0.85f
                visualSource.parameters["FB Zoom"]?.baseValue = 0.54f
                visualSource.parameters["FB Rotate"]?.baseValue = 0.52f
            }
            llm.slop.spirals.models.FeedbackMode.CUSTOM -> {
                // For future: user-defined ranges
            }
        }
    }
    
    /**
     * Selects a recipe from the library based on the RSet's recipe filter.
     * 
     * @param rset The Random Set template
     * @return A randomly selected recipe matching the filter criteria
     */
    private fun selectRecipe(rset: RandomSet): Mandala4Arm {
        val allRecipes = MandalaLibrary.MandalaRatios
        
        val filteredRecipes = when (rset.recipeFilter) {
            RecipeFilter.ALL -> {
                allRecipes
            }
            
            RecipeFilter.FAVORITES_ONLY -> {
                val favorites = tagManager.getFavorites()
                if (favorites.isEmpty()) {
                    // Fallback to all if no favorites
                    allRecipes
                } else {
                    allRecipes.filter { it.id in favorites }
                }
            }
            
            RecipeFilter.PETALS_EXACT -> {
                val targetPetals = rset.petalCount ?: 5
                allRecipes.filter { it.petals == targetPetals }
            }
            
            RecipeFilter.PETALS_RANGE -> {
                val min = rset.petalMin ?: 3
                val max = rset.petalMax ?: 9
                allRecipes.filter { it.petals in min..max }
            }
            
            RecipeFilter.SPECIFIC_IDS -> {
                val ids = rset.specificRecipeIds ?: emptyList()
                if (ids.isEmpty()) {
                    allRecipes
                } else {
                    allRecipes.filter { it.id in ids }
                }
            }
        }
        
        // If filter resulted in empty list, fall back to all recipes
        return if (filteredRecipes.isEmpty()) {
            allRecipes.random()
        } else {
            filteredRecipes.random()
        }
    }
}
