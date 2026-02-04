package llm.slop.spirals.database.daos

import kotlinx.coroutines.flow.Flow
import llm.slop.spirals.database.entities.RandomSetEntity
import llm.slop.spirals.platform.Dao
import llm.slop.spirals.platform.Delete
import llm.slop.spirals.platform.Insert
import llm.slop.spirals.platform.OnConflictStrategy
import llm.slop.spirals.platform.Query

@Dao
interface RandomSetDao {
    @Query("SELECT * FROM random_sets")
    fun getAllRandomSets(): Flow<List<RandomSetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRandomSet(randomSet: RandomSetEntity)

    @Delete
    suspend fun deleteRandomSet(randomSet: RandomSetEntity)

    @Query("DELETE FROM random_sets WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM random_sets WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RandomSetEntity?
}
