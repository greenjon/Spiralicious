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
public abstract class MandalaDatabase : RoomDatabase() {
    public abstract fun mandalaPatchDao(): MandalaPatchDao
    public abstract fun mandalaSetDao(): MandalaSetDao
    public abstract fun mixerPatchDao(): MixerPatchDao
    public abstract fun randomSetDao(): RandomSetDao
    public abstract fun showPatchDao(): ShowPatchDao
    public abstract fun mandalaTagDao(): MandalaTagDao
}