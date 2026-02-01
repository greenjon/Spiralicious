package llm.slop.spirals.database.entities

import llm.slop.spirals.platform.Entity
import llm.slop.spirals.platform.PrimaryKey

/**
 * Room Entity for storing a Mandala Patch.
 */
@Entity(tableName = "mandala_patches")
data class MandalaPatchEntity(
    @PrimaryKey val name: String,
    val recipeId: String,
    val jsonSettings: String // Serialized ParameterSettings
)
