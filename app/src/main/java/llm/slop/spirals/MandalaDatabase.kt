package llm.slop.spirals

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MandalaTag::class], version = 1, exportSchema = false)
abstract class MandalaDatabase : RoomDatabase() {
    abstract fun mandalaTagDao(): MandalaTagDao

    companion object {
        @Volatile
        private var INSTANCE: MandalaDatabase? = null

        fun getDatabase(context: Context): MandalaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MandalaDatabase::class.java,
                    "mandala_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
