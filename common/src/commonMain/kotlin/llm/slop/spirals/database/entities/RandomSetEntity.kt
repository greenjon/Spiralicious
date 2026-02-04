package llm.slop.spirals.database.entities

import llm.slop.spirals.platform.Entity
import llm.slop.spirals.platform.PrimaryKey

@Entity(tableName = "random_sets")
data class RandomSetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val jsonSettings: String // Serialized RandomSet
)
