package llm.slop.spirals

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "show_patches")
data class ShowPatchEntity(
    @PrimaryKey val id: String,
    val name: String,
    val jsonSettings: String // Serialized ShowPatch
)

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
