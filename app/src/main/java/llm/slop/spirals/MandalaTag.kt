package llm.slop.spirals

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mandala_tags")
data class MandalaTag(
    @PrimaryKey val speeds: String, // e.g., "26-23-14-14"
    val tag: String? = null // "trash", "1", "2", "3", "?" or null
)
