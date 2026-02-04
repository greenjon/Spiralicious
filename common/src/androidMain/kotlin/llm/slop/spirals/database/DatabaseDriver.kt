package llm.slop.spirals.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

// The 'actual' implementation for Android
actual class DatabaseDriver(private val context: Context) {
    actual fun createDatabaseBuilder(): RoomDatabase.Builder<MandalaDatabase> {
        val dbFile = context.getDatabasePath("mandala.db")
        return Room.databaseBuilder<MandalaDatabase>(
            context = context.applicationContext,
            name = dbFile.absolutePath
        )
    }
}