package llm.slop.spirals

import llm.slop.spirals.database.daos.*

expect abstract class MandalaDatabase {
    abstract fun mandalaTagDao(): MandalaTagDao
    abstract fun mandalaPatchDao(): MandalaPatchDao
    abstract fun mandalaSetDao(): MandalaSetDao
    abstract fun mixerPatchDao(): MixerPatchDao
    abstract fun showPatchDao(): ShowPatchDao
    abstract fun randomSetDao(): RandomSetDao
}
