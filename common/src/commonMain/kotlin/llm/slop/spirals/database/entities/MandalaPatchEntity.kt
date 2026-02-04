package llm.slop.spirals.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mandala_patches")
data class MandalaPatchEntity(
    @PrimaryKey
    val name: String,
    val recipeId: String,
    val jsonSettings: String
)