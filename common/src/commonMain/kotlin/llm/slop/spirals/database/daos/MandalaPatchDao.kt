package llm.slop.spirals.database.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import llm.slop.spirals.database.entities.MandalaPatchEntity

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