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
 */
class RandomSetGenerator(private val context: Context) {
    
    private val tagManager = RecipeTagManager(context)
    private val defaultsConfig = llm.slop.spirals.defaults.DefaultsConfig.getInstance(context)
    
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
     * Applies arm parameter constraints.
     */
    private fun applyArmConstraints(
        paramName: String,
        constraints: llm.slop.spirals.models.ArmConstraints?,
        visualSource: MandalaVisualSource,
        random: kotlin.random.Random
    ) {
        visualSource.parameters[paramName]?.let { param ->
            // Use constraints if provided, otherwise use defaults from settings
            val c = if (constraints != null) {
                constraints
            } else {
                val defaults = defaultsConfig.getArmDefaults()
                llm.slop.spirals.models.ArmConstraints(
                    baseLengthMin = defaults.baseLengthMin,
                    baseLengthMax = defaults.baseLengthMax,
                    enableBeat = defaults.beatProbability > 0,
                    enableLfo = defaults.lfoProbability > 0,
                    enableRandom = defaults.defaultEnableRandom,
                    allowSine = defaults.sineProbability > 0,
                    allowTriangle = defaults.triangleProbability > 0,
                    allowSquare = defaults.squareProbability > 0,
                    beatDivMin = defaults.beatDivMin,
                    beatDivMax = defaults.beatDivMax,
                    weightMin = defaults.weightMin,
                    weightMax = defaults.weightMax,
                    lfoTimeMin = defaults.lfoTimeMin,
                    lfoTimeMax = defaults.lfoTimeMax,
                    randomGlideMin = defaults.randomGlideMin,
                    randomGlideMax = defaults.randomGlideMax
                )
            }
            
            // Base value from range
            param.baseValue = (random.nextInt(c.baseLengthMin, c.baseLengthMax + 1) / 100f)
            param.modulators.clear()
            
            // Pool logic for movement sources
            val availableSources = mutableListOf<String>()
            if (c.enableBeat) availableSources.add("beat")
            if (c.enableLfo) availableSources.add("lfo")
            if (c.enableRandom) availableSources.add("sampleAndHold")
            
            if (availableSources.isNotEmpty()) {
                val chosenSource = availableSources.random(random)
                
                // Weight from range (convert to 0-1 scale)
                val weight = random.nextInt(c.weightMin, c.weightMax + 1) / 100f
                
                // Waveform logic (S&H doesn't use it, but we need a value for CvModulator)
                val waveform = if (chosenSource == "sampleAndHold") {
                    Waveform.SINE // Dummy for S&H
                } else {
                    val allowedWaveforms = mutableListOf<Waveform>()
                    if (c.allowSine) allowedWaveforms.add(Waveform.SINE)
                    if (c.allowTriangle) allowedWaveforms.add(Waveform.TRIANGLE)
                    if (c.allowSquare) allowedWaveforms.add(Waveform.SQUARE)
                    
                    if (allowedWaveforms.isNotEmpty()) {
                        allowedWaveforms.random(random)
                    } else {
                        Waveform.SINE
                    }
                }
                
                when (chosenSource) {
                    "beat" -> {
                        val validValues = STANDARD_BEAT_VALUES.filter { it in c.beatDivMin..c.beatDivMax }
                        val subdivision = if (validValues.isNotEmpty()) {
                            validValues.random(random)
                        } else {
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
                    }
                    "lfo" -> {
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
                    "sampleAndHold" -> {
                        val validValues = STANDARD_BEAT_VALUES.filter { it in c.beatDivMin..c.beatDivMax }
                        val subdivision = if (validValues.isNotEmpty()) {
                            validValues.random(random)
                        } else {
                            STANDARD_BEAT_VALUES.minByOrNull { kotlin.math.abs(it - c.beatDivMin) } ?: 1f
                        }
                        
                        // Glide Assignment: random value between min and max
                        val randomGlide = random.nextFloat() * (c.randomGlideMax - c.randomGlideMin) + c.randomGlideMin
                        
                        param.modulators.add(
                            CvModulator(
                                sourceId = "sampleAndHold",
                                operator = ModulationOperator.ADD,
                                waveform = waveform,
                                slope = randomGlide,
                                weight = weight,
                                phaseOffset = random.nextFloat(),
                                subdivision = subdivision
                            )
                        )
                    }
                }
            }
        }
    }
    
    /**
     * Applies rotation constraints.
     */
    private fun applyRotationConstraints(
        constraints: llm.slop.spirals.models.RotationConstraints?,
        visualSource: MandalaVisualSource,
        random: kotlin.random.Random
    ) {
        if (constraints == null) {
            val defaults = defaultsConfig.getRotationDefaults()
            visualSource.parameters["Rotation"]?.let { param ->
                param.baseValue = 0f
                param.modulators.clear()
                val slope = defaults.getRandomDirection(random)
                val speedSource = defaults.getRandomSpeedSource(random)
                val sourceId = when (speedSource) {
                    llm.slop.spirals.models.SpeedSource.BEAT -> "beatPhase"
                    llm.slop.spirals.models.SpeedSource.LFO -> "lfo1"
                    llm.slop.spirals.models.SpeedSource.RANDOM -> "sampleAndHold"
                }
                
                val subdivision = when (sourceId) {
                    "beatPhase", "sampleAndHold" -> {
                        val validValues = STANDARD_BEAT_VALUES.filter { it in defaults.beatDivMin..defaults.beatDivMax }
                        if (validValues.isNotEmpty()) validValues.random(random) else 4f
                    }
                    else -> random.nextInt(defaults.lfoTimeMin.toInt(), defaults.lfoTimeMax.toInt() + 1).toFloat()
                }
                
                val finalSlope = if (sourceId == "sampleAndHold") {
                    random.nextFloat() * (defaults.randomGlideMax - defaults.randomGlideMin) + defaults.randomGlideMin
                } else slope
                
                param.modulators.add(
                    CvModulator(
                        sourceId = sourceId,
                        operator = ModulationOperator.ADD,
                        waveform = Waveform.TRIANGLE,
                        slope = finalSlope,
                        weight = 1.0f,
                        phaseOffset = random.nextFloat(),
                        subdivision = subdivision
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
            val slope = if (directions.isNotEmpty()) directions.random(random) else 0f
            
            // Pool logic for movement sources
            val availableSources = mutableListOf<String>()
            if (constraints.enableBeat) availableSources.add("beatPhase")
            if (constraints.enableLfo) availableSources.add("lfo1")
            if (constraints.enableRandom) availableSources.add("sampleAndHold")
            
            if (availableSources.isNotEmpty()) {
                val sourceId = availableSources.random(random)
                
                val subdivision = when(sourceId) {
                    "beatPhase", "sampleAndHold" -> {
                        val validValues = STANDARD_BEAT_VALUES.filter { it in constraints.beatDivMin..constraints.beatDivMax }
                        if (validValues.isNotEmpty()) validValues.random(random) 
                        else STANDARD_BEAT_VALUES.minByOrNull { kotlin.math.abs(it - constraints.beatDivMin) } ?: 1f
                    }
                    else -> random.nextInt(constraints.lfoTimeMin.toInt(), constraints.lfoTimeMax.toInt() + 1).toFloat()
                }
                
                val finalSlope = if (sourceId == "sampleAndHold") {
                    random.nextFloat() * (constraints.randomGlideMax - constraints.randomGlideMin) + constraints.randomGlideMin
                } else slope
                
                param.modulators.add(
                    CvModulator(
                        sourceId = sourceId,
                        operator = ModulationOperator.ADD,
                        waveform = Waveform.TRIANGLE,
                        slope = finalSlope,
                        weight = 1.0f,
                        phaseOffset = random.nextFloat(),
                        subdivision = subdivision
                    )
                )
            }
        }
    }
    
    /**
     * Applies hue offset constraints.
     */
    private fun applyHueOffsetConstraints(
        constraints: llm.slop.spirals.models.HueOffsetConstraints?,
        visualSource: MandalaVisualSource,
        random: kotlin.random.Random
    ) {
        if (constraints == null) {
            val defaults = defaultsConfig.getHueOffsetDefaults()
            visualSource.parameters["Hue Offset"]?.let { param ->
                param.baseValue = 0f
                param.modulators.clear()
                val slope = defaults.getRandomDirection(random)
                val speedSource = defaults.getRandomSpeedSource(random)
                val sourceId = when (speedSource) {
                    llm.slop.spirals.models.SpeedSource.BEAT -> "beatPhase"
                    llm.slop.spirals.models.SpeedSource.LFO -> "lfo1"
                    llm.slop.spirals.models.SpeedSource.RANDOM -> "sampleAndHold"
                }
                
                val subdivision = when (sourceId) {
                    "beatPhase", "sampleAndHold" -> {
                        val validValues = STANDARD_BEAT_VALUES.filter { it in defaults.beatDivMin..defaults.beatDivMax }
                        if (validValues.isNotEmpty()) validValues.random(random) else 4f
                    }
                    else -> random.nextInt(defaults.lfoTimeMin.toInt(), defaults.lfoTimeMax.toInt() + 1).toFloat()
                }
                
                val finalSlope = if (sourceId == "sampleAndHold") {
                    random.nextFloat() * (defaults.randomGlideMax - defaults.randomGlideMin) + defaults.randomGlideMin
                } else slope
                
                param.modulators.add(
                    CvModulator(
                        sourceId = sourceId,
                        operator = ModulationOperator.ADD,
                        waveform = Waveform.TRIANGLE,
                        slope = finalSlope,
                        weight = 1.0f,
                        phaseOffset = random.nextFloat(),
                        subdivision = subdivision
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
            val slope = if (directions.isNotEmpty()) directions.random(random) else 1f
            
            // Pool logic for movement sources
            val availableSources = mutableListOf<String>()
            if (constraints.enableBeat) availableSources.add("beatPhase")
            if (constraints.enableLfo) availableSources.add("lfo1")
            if (constraints.enableRandom) availableSources.add("sampleAndHold")
            
            if (availableSources.isNotEmpty()) {
                val sourceId = availableSources.random(random)
                
                val subdivision = when(sourceId) {
                    "beatPhase", "sampleAndHold" -> {
                        val validValues = STANDARD_BEAT_VALUES.filter { it in constraints.beatDivMin..constraints.beatDivMax }
                        if (validValues.isNotEmpty()) validValues.random(random)
                        else STANDARD_BEAT_VALUES.minByOrNull { kotlin.math.abs(it - constraints.beatDivMin) } ?: 1f
                    }
                    else -> random.nextInt(constraints.lfoTimeMin.toInt(), constraints.lfoTimeMax.toInt() + 1).toFloat()
                }
                
                val finalSlope = if (sourceId == "sampleAndHold") {
                    random.nextFloat() * (constraints.randomGlideMax - constraints.randomGlideMin) + constraints.randomGlideMin
                } else slope
                
                param.modulators.add(
                    CvModulator(
                        sourceId = sourceId,
                        operator = ModulationOperator.ADD,
                        waveform = Waveform.TRIANGLE,
                        slope = finalSlope,
                        weight = 1.0f,
                        phaseOffset = random.nextFloat(),
                        subdivision = subdivision
                    )
                )
            }
        }
    }
    
    /**
     * Applies feedback mode presets.
     */
    private fun applyFeedbackMode(
        mode: llm.slop.spirals.models.FeedbackMode,
        visualSource: MandalaVisualSource
    ) {
        when (mode) {
            llm.slop.spirals.models.FeedbackMode.NONE -> {
                visualSource.parameters["FB Decay"]?.baseValue = 0f
                visualSource.parameters["FB Gain"]?.baseValue = 0f
            }
            llm.slop.spirals.models.FeedbackMode.LIGHT -> {
                visualSource.parameters["FB Decay"]?.baseValue = 0.85f
                visualSource.parameters["FB Gain"]?.baseValue = 0.45f
                visualSource.parameters["FB Zoom"]?.baseValue = 0.50f
                visualSource.parameters["FB Rotate"]?.baseValue = 0.50f
            }
            llm.slop.spirals.models.FeedbackMode.MEDIUM -> {
                visualSource.parameters["FB Decay"]?.baseValue = 0.85f
                visualSource.parameters["FB Gain"]?.baseValue = 0.55f
                visualSource.parameters["FB Zoom"]?.baseValue = 0.7f
                visualSource.parameters["FB Rotate"]?.baseValue = 0.55f
            }
            llm.slop.spirals.models.FeedbackMode.HEAVY -> {
                visualSource.parameters["FB Decay"]?.baseValue = 0.85f
                visualSource.parameters["FB Gain"]?.baseValue = 0.6f
                visualSource.parameters["FB Zoom"]?.baseValue = 0.7f
                visualSource.parameters["FB Rotate"]?.baseValue = 0.6f
            }
            llm.slop.spirals.models.FeedbackMode.CUSTOM -> { }
        }
    }
    
    private fun selectRecipe(rset: RandomSet): Mandala4Arm {
        val allRecipes = MandalaLibrary.MandalaRatios
        val filteredRecipes = when (rset.recipeFilter) {
            RecipeFilter.ALL -> allRecipes
            RecipeFilter.FAVORITES_ONLY -> {
                val favorites = tagManager.getFavorites()
                if (favorites.isEmpty()) allRecipes else allRecipes.filter { it.id in favorites }
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
                if (ids.isEmpty()) allRecipes else allRecipes.filter { it.id in ids }
            }
        }
        return if (filteredRecipes.isEmpty()) allRecipes.random() else filteredRecipes.random()
    }
}
