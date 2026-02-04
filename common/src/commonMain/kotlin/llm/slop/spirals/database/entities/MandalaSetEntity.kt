package llm.slop.spirals.database.entities

import llm.slop.spirals.platform.Entity
import llm.slop.spirals.platform.PrimaryKey

@Entity(tableName = "mandala_sets")
data class MandalaSetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val jsonOrderedMandalaIds: String, // Serialized List<String>
    val selectionPolicy: String
)
