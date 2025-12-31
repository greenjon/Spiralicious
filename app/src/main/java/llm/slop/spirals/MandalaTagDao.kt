package llm.slop.spirals

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MandalaTagDao {
    @Query("SELECT * FROM mandala_tags")
    fun getAllTags(): Flow<List<MandalaTag>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(mandalaTag: MandalaTag)

    @Delete
    suspend fun deleteTag(mandalaTag: MandalaTag)
    
    @Query("SELECT * FROM mandala_tags WHERE speeds = :speeds LIMIT 1")
    suspend fun getTagForSpeeds(speeds: String): MandalaTag?
}
