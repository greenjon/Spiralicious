package llm.slop.spirals.database.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import llm.slop.spirals.database.entities.MandalaSetEntity

@Dao
public interface MandalaSetDao {
    @Query("SELECT * FROM mandala_sets")
    public fun getAllSets(): Flow<List<MandalaSetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insertSet(set: MandalaSetEntity)

    @Delete
    public suspend fun deleteSet(set: MandalaSetEntity)

    @Query("DELETE FROM mandala_sets WHERE id = :id")
    public suspend fun deleteById(id: String)
}