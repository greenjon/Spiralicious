package llm.slop.spirals.database

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

// The 'actual' implementation for Desktop
actual class DatabaseDriver {
    actual fun createDatabaseBuilder(): RoomDatabase.Builder<MandalaDatabase> {
        val dbFile = File(System.getProperty("user.home"), "mandala.db")
        return Room.databaseBuilder<MandalaDatabase>(
            name = dbFile.absolutePath,
        ).setDriver(BundledSQLiteDriver()) // Use the bundled driver
    }
}