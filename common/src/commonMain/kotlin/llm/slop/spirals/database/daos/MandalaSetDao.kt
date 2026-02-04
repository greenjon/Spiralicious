package llm.slop.spirals.database.daos

import kotlinx.coroutines.flow.Flow
import llm.slop.spirals.database.entities.MandalaSetEntity
import llm.slop.spirals.platform.Dao
import llm.slop.spirals.platform.Delete
import llm.slop.spirals.platform.Insert
import llm.slop.spirals.platform.OnConflictStrategy
import llm.slop.spirals.platform.Query

@Dao
interface MandalaSetDao {
    @Query("SELECT * FROM mandala_sets")
    fun getAllSets(): Flow<List<MandalaSetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSet(set: MandalaSetEntity)

    @Delete
    suspend fun deleteSet(set: MandalaSetEntity)

    @Query("DELETE FROM mandala_sets WHERE id = :id")
    suspend fun deleteById(id: String)
}
