package llm.slop.spirals.platform

import androidx.room.Room
import llm.slop.spirals.MandalaDatabase

private var database: MandalaDatabase? = null

actual fun getDatabase(): MandalaDatabase {
    if (database == null) {
        database = Room.databaseBuilder(
            getAppContext(),
            MandalaDatabase::class.java,
            "mandala_database"
        )
        .fallbackToDestructiveMigration()
        .build()
    }
    return database!!
}
