package llm.slop.spirals.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import llm.slop.spirals.database.dao.*
import llm.slop.spirals.database.entities.*

@Database(entities = [MandalaTag::class, MandalaPatchEntity::class, MandalaSetEntity::class, MixerPatchEntity::class, ShowPatchEntity::class, RandomSetEntity::class], version = 7, exportSchema = false)
abstract class MandalaDatabaseImpl : RoomDatabase(), MandalaDatabase {
    abstract override fun mandalaTagDao(): MandalaTagDao
    abstract override fun mandalaPatchDao(): MandalaPatchDao
    abstract override fun mandalaSetDao(): MandalaSetDao
    abstract override fun mixerPatchDao(): MixerPatchDao
    abstract override fun showPatchDao(): ShowPatchDao
    abstract override fun randomSetDao(): RandomSetDao

    companion object {
        @Volatile
        private var INSTANCE: MandalaDatabaseImpl? = null

        fun getDatabase(context: Context): MandalaDatabaseImpl {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MandalaDatabaseImpl::class.java,
                    "mandala_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

private lateinit var MIGRATION_CONTEXT: Context

fun getDatabase(): MandalaDatabase {
    return MandalaDatabaseImpl.getDatabase(MIGRATION_CONTEXT)
}

fun setDatabaseContext(context: Context) {
    MIGRATION_CONTEXT = context
}
