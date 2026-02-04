package llm.slop.spirals

import llm.slop.spirals.database.daos.*

actual abstract class MandalaDatabase {
    actual abstract fun mandalaTagDao(): MandalaTagDao
    actual abstract fun mandalaPatchDao(): MandalaPatchDao
    actual abstract fun mandalaSetDao(): MandalaSetDao
    actual abstract fun mixerPatchDao(): MixerPatchDao
    actual abstract fun showPatchDao(): ShowPatchDao
    actual abstract fun randomSetDao(): RandomSetDao
}
