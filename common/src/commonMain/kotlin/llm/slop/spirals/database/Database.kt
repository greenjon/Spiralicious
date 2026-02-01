package llm.slop.spirals.database

import llm.slop.spirals.database.dao.*

interface MandalaDatabase {
    fun mandalaTagDao(): MandalaTagDao
    fun mandalaPatchDao(): MandalaPatchDao
    fun mandalaSetDao(): MandalaSetDao
    fun mixerPatchDao(): MixerPatchDao
    fun showPatchDao(): ShowPatchDao
    fun randomSetDao(): RandomSetDao
}

expect fun getDatabase(): MandalaDatabase
