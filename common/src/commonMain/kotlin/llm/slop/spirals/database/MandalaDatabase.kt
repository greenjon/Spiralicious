package llm.slop.spirals.database

import androidx.room.Database
import androidx.room.RoomDatabase
import llm.slop.spirals.database.daos.*
import llm.slop.spirals.database.entities.*


@Database(
    entities = [
        MandalaPatchEntity::class,
        MandalaSetEntity::class,
        MixerPatchEntity::class,
        RandomSetEntity::class,
        ShowPatchEntity::class,
        MandalaTag::class,
    ],
    version = 1
)
abstract class MandalaDatabase : RoomDatabase() {
    abstract fun mandalaPatchDao(): MandalaPatchDao
    abstract fun mandalaSetDao(): MandalaSetDao
    abstract fun mixerPatchDao(): MixerPatchDao
    abstract fun randomSetDao(): RandomSetDao
    abstract fun showPatchDao(): ShowPatchDao
    abstract fun mandalaTagDao(): MandalaTagDao
}