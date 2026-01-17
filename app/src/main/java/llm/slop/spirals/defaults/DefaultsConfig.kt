package llm.slop.spirals.defaults

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import llm.slop.spirals.models.STANDARD_BEAT_VALUES
import llm.slop.spirals.models.SpeedSource

/**
 * Manages default settings for randomization and editors.
 * 
 * This class provides access to user-configurable defaults that are used 
 * when specific constraints aren't provided. Settings are persisted using
 * SharedPreferences and provide factory defaults as fallbacks.
 */
class DefaultsConfig(context: Context) {

    companion object {
        private const val PREFS_NAME = "spirals_defaults_config"
        
        // Prefix constants for organizing preferences
        private const val PREFIX_RANDOMSET = "defaults_randomset_"
        private const val PREFIX_ARM = "${PREFIX_RANDOMSET}arm_"
        private const val PREFIX_ROTATION = "${PREFIX_RANDOMSET}rotation_"
        private const val PREFIX_HUE = "${PREFIX_RANDOMSET}hue_"
        
        // Singleton instance
        @Volatile
        private var instance: DefaultsConfig? = null
        
        fun getInstance(context: Context): DefaultsConfig {
            return instance ?: synchronized(this) {
                instance ?: DefaultsConfig(context).also { instance = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // Random Set Defaults - Arms
    
    fun getArmDefaults(): ArmDefaults {
        return ArmDefaults(
            baseLengthMin = prefs.getInt("${PREFIX_ARM}base_length_min", 0),
            baseLengthMax = prefs.getInt("${PREFIX_ARM}base_length_max", 100),
            beatProbability = prefs.getFloat("${PREFIX_ARM}beat_probability", 0.5f),
            lfoProbability = prefs.getFloat("${PREFIX_ARM}lfo_probability", 0.5f),
            beatDivMin = prefs.getFloat("${PREFIX_ARM}beat_div_min", STANDARD_BEAT_VALUES.first()),
            beatDivMax = prefs.getFloat("${PREFIX_ARM}beat_div_max", 32f),
            sineProbability = prefs.getFloat("${PREFIX_ARM}sine_probability", 0.33f),
            triangleProbability = prefs.getFloat("${PREFIX_ARM}triangle_probability", 0.34f),
            squareProbability = prefs.getFloat("${PREFIX_ARM}square_probability", 0.33f),
            weightMin = prefs.getInt("${PREFIX_ARM}weight_min", -100),
            weightMax = prefs.getInt("${PREFIX_ARM}weight_max", 100),
            lfoTimeMin = prefs.getFloat("${PREFIX_ARM}lfo_time_min", 1.0f),
            lfoTimeMax = prefs.getFloat("${PREFIX_ARM}lfo_time_max", 60.0f)
        )
    }
    
    fun saveArmDefaults(defaults: ArmDefaults) {
        prefs.edit {
            putInt("${PREFIX_ARM}base_length_min", defaults.baseLengthMin)
            putInt("${PREFIX_ARM}base_length_max", defaults.baseLengthMax)
            putFloat("${PREFIX_ARM}beat_probability", defaults.beatProbability)
            putFloat("${PREFIX_ARM}lfo_probability", defaults.lfoProbability)
            putFloat("${PREFIX_ARM}beat_div_min", defaults.beatDivMin)
            putFloat("${PREFIX_ARM}beat_div_max", defaults.beatDivMax)
            putFloat("${PREFIX_ARM}sine_probability", defaults.sineProbability)
            putFloat("${PREFIX_ARM}triangle_probability", defaults.triangleProbability)
            putFloat("${PREFIX_ARM}square_probability", defaults.squareProbability)
            putInt("${PREFIX_ARM}weight_min", defaults.weightMin)
            putInt("${PREFIX_ARM}weight_max", defaults.weightMax)
            putFloat("${PREFIX_ARM}lfo_time_min", defaults.lfoTimeMin)
            putFloat("${PREFIX_ARM}lfo_time_max", defaults.lfoTimeMax)
        }
    }
    
    // Random Set Defaults - Rotation
    
    fun getRotationDefaults(): RotationDefaults {
        return RotationDefaults(
            clockwiseProbability = prefs.getFloat("${PREFIX_ROTATION}clockwise_probability", 0.5f),
            counterClockwiseProbability = prefs.getFloat("${PREFIX_ROTATION}counter_clockwise_probability", 0.5f),
            beatProbability = prefs.getFloat("${PREFIX_ROTATION}beat_probability", 0.7f),
            lfoProbability = prefs.getFloat("${PREFIX_ROTATION}lfo_probability", 0.3f),
            beatDivMin = prefs.getFloat("${PREFIX_ROTATION}beat_div_min", 4f),
            beatDivMax = prefs.getFloat("${PREFIX_ROTATION}beat_div_max", 128f),
            lfoTimeMin = prefs.getFloat("${PREFIX_ROTATION}lfo_time_min", 5.0f),
            lfoTimeMax = prefs.getFloat("${PREFIX_ROTATION}lfo_time_max", 30.0f)
        )
    }
    
    fun saveRotationDefaults(defaults: RotationDefaults) {
        prefs.edit {
            putFloat("${PREFIX_ROTATION}clockwise_probability", defaults.clockwiseProbability)
            putFloat("${PREFIX_ROTATION}counter_clockwise_probability", defaults.counterClockwiseProbability)
            putFloat("${PREFIX_ROTATION}beat_probability", defaults.beatProbability)
            putFloat("${PREFIX_ROTATION}lfo_probability", defaults.lfoProbability)
            putFloat("${PREFIX_ROTATION}beat_div_min", defaults.beatDivMin)
            putFloat("${PREFIX_ROTATION}beat_div_max", defaults.beatDivMax)
            putFloat("${PREFIX_ROTATION}lfo_time_min", defaults.lfoTimeMin)
            putFloat("${PREFIX_ROTATION}lfo_time_max", defaults.lfoTimeMax)
        }
    }
    
    // Random Set Defaults - Hue Offset
    
    fun getHueOffsetDefaults(): HueOffsetDefaults {
        return HueOffsetDefaults(
            forwardProbability = prefs.getFloat("${PREFIX_HUE}forward_probability", 0.5f),
            reverseProbability = prefs.getFloat("${PREFIX_HUE}reverse_probability", 0.5f),
            beatProbability = prefs.getFloat("${PREFIX_HUE}beat_probability", 0.8f),
            lfoProbability = prefs.getFloat("${PREFIX_HUE}lfo_probability", 0.2f),
            beatDivMin = prefs.getFloat("${PREFIX_HUE}beat_div_min", 4f),
            beatDivMax = prefs.getFloat("${PREFIX_HUE}beat_div_max", 16f),
            lfoTimeMin = prefs.getFloat("${PREFIX_HUE}lfo_time_min", 10.0f),
            lfoTimeMax = prefs.getFloat("${PREFIX_HUE}lfo_time_max", 60.0f)
        )
    }
    
    fun saveHueOffsetDefaults(defaults: HueOffsetDefaults) {
        prefs.edit {
            putFloat("${PREFIX_HUE}forward_probability", defaults.forwardProbability)
            putFloat("${PREFIX_HUE}reverse_probability", defaults.reverseProbability)
            putFloat("${PREFIX_HUE}beat_probability", defaults.beatProbability)
            putFloat("${PREFIX_HUE}lfo_probability", defaults.lfoProbability)
            putFloat("${PREFIX_HUE}beat_div_min", defaults.beatDivMin)
            putFloat("${PREFIX_HUE}beat_div_max", defaults.beatDivMax)
            putFloat("${PREFIX_HUE}lfo_time_min", defaults.lfoTimeMin)
            putFloat("${PREFIX_HUE}lfo_time_max", defaults.lfoTimeMax)
        }
    }
    
    // Composite Default Objects
    
    fun getRandomSetDefaults(): RandomSetDefaults {
        return RandomSetDefaults(
            armDefaults = getArmDefaults(),
            rotationDefaults = getRotationDefaults(),
            hueOffsetDefaults = getHueOffsetDefaults()
        )
    }
    
    fun saveRandomSetDefaults(defaults: RandomSetDefaults) {
        saveArmDefaults(defaults.armDefaults)
        saveRotationDefaults(defaults.rotationDefaults)
        saveHueOffsetDefaults(defaults.hueOffsetDefaults)
    }
    
    // Reset to factory defaults
    
    fun resetArmDefaults() {
        saveArmDefaults(ArmDefaults())
    }
    
    fun resetRotationDefaults() {
        saveRotationDefaults(RotationDefaults())
    }
    
    fun resetHueOffsetDefaults() {
        saveHueOffsetDefaults(HueOffsetDefaults())
    }
    
    fun resetAllDefaults() {
        resetArmDefaults()
        resetRotationDefaults()
        resetHueOffsetDefaults()
    }
}