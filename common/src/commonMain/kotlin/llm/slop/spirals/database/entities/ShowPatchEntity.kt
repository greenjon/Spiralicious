package llm.slop.spirals.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "show_patches")
data class ShowPatchEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val jsonSettings: String
)