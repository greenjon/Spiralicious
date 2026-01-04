package llm.slop.spirals

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Room Entity for storing a Mandala Patch.
 */
@Entity(tableName = "mandala_patches")
data class MandalaPatchEntity(
    @PrimaryKey val name: String,
    val recipeId: String,
    val jsonSettings: String // Serialized ParameterSettings
)

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
