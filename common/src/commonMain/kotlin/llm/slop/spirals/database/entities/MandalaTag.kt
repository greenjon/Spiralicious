package llm.slop.spirals.database.entities

import llm.slop.spirals.platform.Entity

@Entity(tableName = "mandala_tags", primaryKeys = ["id", "tag"])
data class MandalaTag(
    val id: String,
    val tag: String
)
