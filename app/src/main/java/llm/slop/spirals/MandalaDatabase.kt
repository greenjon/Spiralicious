package llm.slop.spirals

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MandalaTag::class, MandalaPatchEntity::class, MandalaSetEntity::class, MixerPatchEntity::class, ShowPatchEntity::class], version = 6, exportSchema = false)
abstract class MandalaDatabase : RoomDatabase() {
    abstract fun mandalaTagDao(): MandalaTagDao
    abstract fun mandalaPatchDao(): MandalaPatchDao
    abstract fun mandalaSetDao(): MandalaSetDao
    abstract fun mixerPatchDao(): MixerPatchDao
    abstract fun showPatchDao(): ShowPatchDao

    companion object {
        @Volatile
        private var INSTANCE: MandalaDatabase? = null

        fun getDatabase(context: Context): MandalaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MandalaDatabase::class.java,
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
