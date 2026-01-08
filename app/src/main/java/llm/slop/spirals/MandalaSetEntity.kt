package llm.slop.spirals

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "mandala_sets")
data class MandalaSetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val jsonOrderedMandalaIds: String, // Serialized List<String>
    val selectionPolicy: String
)

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
