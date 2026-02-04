package llm.slop.spirals.database.daos

import kotlinx.coroutines.flow.Flow
import llm.slop.spirals.database.entities.MandalaTag
import llm.slop.spirals.platform.Dao
import llm.slop.spirals.platform.Delete
import llm.slop.spirals.platform.Insert
import llm.slop.spirals.platform.OnConflictStrategy
import llm.slop.spirals.platform.Query

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
