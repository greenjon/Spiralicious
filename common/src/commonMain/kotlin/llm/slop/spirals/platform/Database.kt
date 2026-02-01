package llm.slop.spirals.platform

expect annotation class Entity(val tableName: String)
expect annotation class PrimaryKey
expect annotation class Dao
expect annotation class Query(val value: String)
expect annotation class Insert(val onConflict: OnConflictStrategy)
expect annotation class Delete

enum class OnConflictStrategy {
    REPLACE
}
