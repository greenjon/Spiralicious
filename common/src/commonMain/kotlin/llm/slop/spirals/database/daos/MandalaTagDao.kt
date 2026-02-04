package llm.slop.spirals.database.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import llm.slop.spirals.database.entities.MandalaTag

@Dao
interface MandalaTagDao {
    @Query("SELECT * FROM mandala_tags")
    fun getAllTags(): Flow<List<MandalaTag>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(mandalaTag: MandalaTag)

    @Delete
    suspend fun deleteTag(mandalaTag: MandalaTag)

    @Query("SELECT * FROM mandala_tags WHERE id = :id")
    suspend fun getAllTagsForId(id: String): List<MandalaTag>
}