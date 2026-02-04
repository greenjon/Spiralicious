package llm.slop.spirals.database.daos

import kotlinx.coroutines.flow.Flow
import llm.slop.spirals.database.entities.MixerPatchEntity
import llm.slop.spirals.platform.Dao
import llm.slop.spirals.platform.Insert
import llm.slop.spirals.platform.OnConflictStrategy
import llm.slop.spirals.platform.Query

@Dao
interface MixerPatchDao {
    @Query("SELECT * FROM mixer_patches")
    fun getAllMixerPatches(): Flow<List<MixerPatchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMixerPatch(patch: MixerPatchEntity)

    @Query("DELETE FROM mixer_patches WHERE id = :id")
    suspend fun deleteById(id: String)
}
