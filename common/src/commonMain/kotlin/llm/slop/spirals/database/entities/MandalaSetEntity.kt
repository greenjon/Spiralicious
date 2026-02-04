package llm.slop.spirals.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mandala_sets")
data class MandalaSetEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val jsonOrderedMandalaIds: String,
    val selectionPolicy: String
)