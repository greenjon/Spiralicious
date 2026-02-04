package llm.slop.spirals.database

import androidx.room.RoomDatabase

// This is the contract. It says "Every platform MUST provide a way to build the database".
expect class DatabaseDriver {
    fun createDatabaseBuilder(): RoomDatabase.Builder<MandalaDatabase>
}

// A convenient top-level function to get the instance
fun createDatabase(driver: DatabaseDriver): MandalaDatabase {
    return driver.createDatabaseBuilder().build()
}
