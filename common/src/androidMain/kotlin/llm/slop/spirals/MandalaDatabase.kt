package llm.slop.spirals

import androidx.room.Database
import androidx.room.RoomDatabase
import llm.slop.spirals.database.daos.*
import llm.slop.spirals.database.entities.*

@Database(entities = [MandalaTag::class, MandalaPatchEntity::class, MandalaSetEntity::class, MixerPatchEntity::class, ShowPatchEntity::class, RandomSetEntity::class], version = 7, exportSchema = false)
actual abstract class MandalaDatabase : RoomDatabase() {
    actual abstract fun mandalaTagDao(): MandalaTagDao
    actual abstract fun mandalaPatchDao(): MandalaPatchDao
    actual abstract fun mandalaSetDao(): MandalaSetDao
    actual abstract fun mixerPatchDao(): MixerPatchDao
    actual abstract fun showPatchDao(): ShowPatchDao
    actual abstract fun randomSetDao(): RandomSetDao
}
