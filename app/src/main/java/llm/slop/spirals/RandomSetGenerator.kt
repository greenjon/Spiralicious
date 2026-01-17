package llm.slop.spirals

import android.content.Context
import llm.slop.spirals.cv.CvModulator
import llm.slop.spirals.cv.ModulationOperator
import llm.slop.spirals.cv.Waveform
import llm.slop.spirals.models.RandomSet
import llm.slop.spirals.models.RecipeFilter

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
        // Phase 1: Use default randomization (Phase 2 will use rset.l1Constraints, etc.)
        listOf("L1", "L2", "L3", "L4").forEach { paramName ->
            visualSource.parameters[paramName]?.let { param ->
                param.baseValue = 0.2f // Base 20%
                param.modulators.clear()
                param.modulators.add(
                    CvModulator(
                        sourceId = "beatPhase",
                        operator = ModulationOperator.ADD,
                        waveform = if (random.nextBoolean()) Waveform.SINE else Waveform.TRIANGLE,
                        slope = 0.5f,
                        weight = random.nextFloat() * 0.5f + 0.1f, // 10-60%
                        phaseOffset = random.nextFloat(),
                        subdivision = random.nextInt(8, 33).toFloat() // 8-32
                    )
                )
            }
        }
        
        // 4. Apply rotation constraints
        // Phase 1: Use default randomization (Phase 2 will use rset.rotationConstraints)
        visualSource.parameters["Rotation"]?.let { param ->
            param.baseValue = 0f
            param.modulators.clear()
            param.modulators.add(
                CvModulator(
                    sourceId = "beatPhase",
                    operator = ModulationOperator.ADD,
                    waveform = Waveform.TRIANGLE,
                    slope = if (random.nextBoolean()) 0f else 1f,
                    weight = 1.0f,
                    phaseOffset = random.nextFloat(),
                    subdivision = random.nextInt(4, 129).toFloat() // 4-128
                )
            )
        }
        
        // 5. Apply hue offset constraints
        // Phase 1: Use default randomization (Phase 2 will use rset.hueOffsetConstraints)
        visualSource.parameters["Hue Offset"]?.let { param ->
            param.baseValue = 0f
            param.modulators.clear()
            param.modulators.add(
                CvModulator(
                    sourceId = "beatPhase",
                    operator = ModulationOperator.ADD,
                    waveform = Waveform.TRIANGLE,
                    slope = if (random.nextBoolean()) 0f else 1f,
                    weight = 1.0f,
                    phaseOffset = random.nextFloat(),
                    subdivision = random.nextInt(4, 17).toFloat() // 4-16
                )
            )
        }
        
        // 6. Apply feedback mode
        // Phase 1: Just set to none (Phase 2 will apply feedback presets)
        // Feedback parameters are not currently exposed in visual source, so skip for now
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
