package llm.slop.spirals.database.entities

import llm.slop.spirals.platform.Entity
import llm.slop.spirals.platform.PrimaryKey

@Entity(tableName = "show_patches")
data class ShowPatchEntity(
    @PrimaryKey val id: String,
    val name: String,
    val jsonSettings: String // Serialized ShowPatch
)
