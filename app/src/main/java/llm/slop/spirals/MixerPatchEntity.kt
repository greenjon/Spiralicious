package llm.slop.spirals

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "mixer_patches")
data class MixerPatchEntity(
    @PrimaryKey val id: String,
    val name: String,
    val jsonSettings: String // Serialized MixerPatch
)

@Dao
interface MixerPatchDao {
    @Query("SELECT * FROM mixer_patches")
    fun getAllMixerPatches(): Flow<List<MixerPatchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMixerPatch(patch: MixerPatchEntity)

    @Query("DELETE FROM mixer_patches WHERE id = :id")
    suspend fun deleteById(id: String)
}
