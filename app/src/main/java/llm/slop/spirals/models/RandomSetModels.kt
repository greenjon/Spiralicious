package llm.slop.spirals.models

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Standardized beat division values to be used throughout the app.
 * Ensures consistent options: 1/16, 1/8, 1/4, 1/2, 1, 2, 4, 8, 16, 32, 64, 128, 256
 */
val STANDARD_BEAT_VALUES = listOf(0.0625f, 0.125f, 0.25f, 0.5f, 1f, 2f, 4f, 8f, 16f, 32f, 64f, 128f, 256f)

/**
 * RSet (Random Set) - Generative patch templates for infinite mandala variations.
 * 
 * Philosophy: The RSet template IS the creative work - thoughtfully defining constraints
 * that produce a consistent aesthetic. Templates become reusable building blocks.
 * 
 * Usage: Load an RSet into a Mixer slot. Each time "Next" is triggered, a fresh mandala
 * is generated matching the template's constraints.
 */

@Serializable
data class RandomSet(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    
    // Recipe Constraints
    val recipeFilter: RecipeFilter = RecipeFilter.ALL,
    val petalCount: Int? = null, // For PETALS_EXACT filter
    val petalMin: Int? = null, // For PETALS_RANGE filter
    val petalMax: Int? = null, // For PETALS_RANGE filter
    val specificRecipeIds: List<String>? = null, // For SPECIFIC_IDS filter
    val autoHueSweep: Boolean = true, // Auto-set hue sweep to match petals
    
    // Per-Parameter Constraints (null = use defaults)
    val l1Constraints: ArmConstraints? = null,
    val l2Constraints: ArmConstraints? = null,
    val l3Constraints: ArmConstraints? = null,
    val l4Constraints: ArmConstraints? = null,
    
    val rotationConstraints: RotationConstraints? = null,
    val hueOffsetConstraints: HueOffsetConstraints? = null,
    
    // Feedback (if/when implemented)
    val feedbackMode: FeedbackMode = FeedbackMode.NONE
)

@Serializable
enum class RecipeFilter {
    ALL,
    FAVORITES_ONLY,
    PETALS_EXACT,
    PETALS_RANGE,
    SPECIFIC_IDS
}

@Serializable
data class ArmConstraints(
    // Base length range (0-100 scale)
    val baseLengthMin: Int = 0,
    val baseLengthMax: Int = 100,
    
    // Movement sources
    val enableBeat: Boolean = false,
    val enableLfo: Boolean = false,
    
    // Waveform selection
    val allowSine: Boolean = true,
    val allowTriangle: Boolean = true,
    val allowSquare: Boolean = false,
    
    // Weight/intensity range (-100 to 100)
    val weightMin: Int = -100,
    val weightMax: Int = 100,
    
    // Beat division range (discrete values as floats)
    val beatDivMin: Float = 0.0625f, // 1/16
    val beatDivMax: Float = 32.0f,
    
    // LFO speed control
    val lfoSpeedMode: LfoSpeedMode = LfoSpeedMode.MEDIUM,
    val lfoTimeMin: Float = 1.0f, // seconds, within speed mode
    val lfoTimeMax: Float = 60.0f  // seconds, within speed mode
)

@Serializable
enum class LfoSpeedMode {
    FAST,    // 0.1s - 10s
    MEDIUM,  // 1s - 15min (900s)
    SLOW     // 10s - 24h (86400s)
}

@Serializable
data class RotationConstraints(
    // Direction
    val enableClockwise: Boolean = true,
    val enableCounterClockwise: Boolean = true,
    
    // Speed control
    val speedSource: SpeedSource = SpeedSource.BEAT,
    
    // Beat division range (when speedSource == BEAT)
    val beatDivMin: Float = 0.0625f, // 1/16
    val beatDivMax: Float = 256.0f,
    
    // LFO time range (when speedSource == LFO)
    val lfoSpeedMode: LfoSpeedMode = LfoSpeedMode.MEDIUM,
    val lfoTimeMin: Float = 5.0f,
    val lfoTimeMax: Float = 30.0f
)

@Serializable
data class HueOffsetConstraints(
    // Direction
    val enableForward: Boolean = true,
    val enableReverse: Boolean = true,
    
    // Speed control
    val speedSource: SpeedSource = SpeedSource.BEAT,
    
    // Beat division range (when speedSource == BEAT)
    val beatDivMin: Float = 0.0625f, // 1/16
    val beatDivMax: Float = 256.0f,
    
    // LFO time range (when speedSource == LFO)
    val lfoSpeedMode: LfoSpeedMode = LfoSpeedMode.MEDIUM,
    val lfoTimeMin: Float = 10.0f,
    val lfoTimeMax: Float = 60.0f
)

@Serializable
enum class SpeedSource {
    BEAT,
    LFO
}

@Serializable
enum class FeedbackMode {
    NONE,
    LIGHT,   // Preset ranges
    MEDIUM,  // Preset ranges
    HEAVY,   // Preset ranges
    CUSTOM   // User-defined ranges (future)
}
