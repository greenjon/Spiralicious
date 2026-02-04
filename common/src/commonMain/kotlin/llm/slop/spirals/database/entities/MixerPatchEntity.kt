package llm.slop.spirals.database.entities

import llm.slop.spirals.platform.Entity
import llm.slop.spirals.platform.PrimaryKey

@Entity(tableName = "mixer_patches")
data class MixerPatchEntity(
    @PrimaryKey val id: String,
    val name: String,
    val jsonSettings: String // Serialized MixerPatch
)
