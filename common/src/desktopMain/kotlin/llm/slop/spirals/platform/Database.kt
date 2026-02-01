package llm.slop.spirals.platform

actual annotation class Entity(actual val tableName: String)
actual annotation class PrimaryKey
actual annotation class Dao
actual annotation class Query(actual val value: String)
actual annotation class Insert(actual val onConflict: OnConflictStrategy)
actual annotation class Delete
