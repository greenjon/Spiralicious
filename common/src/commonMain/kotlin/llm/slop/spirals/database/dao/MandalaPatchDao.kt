package llm.slop.spirals.database.dao

import kotlinx.coroutines.flow.Flow
import llm.slop.spirals.database.entities.MandalaPatchEntity
import llm.slop.spirals.platform.Dao
import llm.slop.spirals.platform.Delete
import llm.slop.spirals.platform.Insert
import llm.slop.spirals.platform.OnConflictStrategy
import llm.slop.spirals.platform.Query

@Dao
interface MandalaPatchDao {
    @Query("SELECT * FROM mandala_patches")
    fun getAllPatches(): Flow<List<MandalaPatchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatch(patch: MandalaPatchEntity)

    @Delete
    suspend fun deletePatch(patch: MandalaPatchEntity)

    @Query("DELETE FROM mandala_patches WHERE name = :name")
    suspend fun deleteByName(name: String)
}
