package llm.slop.spirals.database.daos

import kotlinx.coroutines.flow.Flow
import llm.slop.spirals.database.entities.ShowPatchEntity
import llm.slop.spirals.platform.Dao
import llm.slop.spirals.platform.Insert
import llm.slop.spirals.platform.OnConflictStrategy
import llm.slop.spirals.platform.Query

@Dao
interface ShowPatchDao {
    @Query("SELECT * FROM show_patches")
    fun getAllShowPatches(): Flow<List<ShowPatchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShowPatch(patch: ShowPatchEntity)

    @Query("DELETE FROM show_patches WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM show_patches WHERE name = :name")
    suspend fun deleteByName(name: String)
}
