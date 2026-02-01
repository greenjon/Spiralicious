package llm.slop.spirals.database

import llm.slop.spirals.database.dao.*
import kotlinx.coroutines.flow.flowOf

actual fun getDatabase(): MandalaDatabase {
    return object : MandalaDatabase {
        override fun mandalaTagDao(): MandalaTagDao = object : MandalaTagDao {
            override fun getAllTagsForId(id: String): List<llm.slop.spirals.database.entities.MandalaTag> = emptyList()
            override fun getAllTags(): kotlinx.coroutines.flow.Flow<List<llm.slop.spirals.database.entities.MandalaTag>> = flowOf(emptyList())
            override suspend fun insertTag(tag: llm.slop.spirals.database.entities.MandalaTag) {}
            override suspend fun deleteTag(tag: llm.slop.spirals.database.entities.MandalaTag) {}
        }
        override fun mandalaPatchDao(): MandalaPatchDao = object : MandalaPatchDao {
            override fun getAllPatches(): kotlinx.coroutines.flow.Flow<List<llm.slop.spirals.database.entities.MandalaPatchEntity>> = flowOf(emptyList())
            override suspend fun insertPatch(patch: llm.slop.spirals.database.entities.MandalaPatchEntity) {}
            override suspend fun deletePatch(patch: llm.slop.spirals.database.entities.MandalaPatchEntity) {}
            override suspend fun deleteByName(name: String) {}
        }
        override fun mandalaSetDao(): MandalaSetDao = object : MandalaSetDao {
            override fun getAllSets(): kotlinx.coroutines.flow.Flow<List<llm.slop.spirals.database.entities.MandalaSetEntity>> = flowOf(emptyList())
            override suspend fun insertSet(set: llm.slop.spirals.database.entities.MandalaSetEntity) {}
            override suspend fun deleteById(id: String) {}
        }
        override fun mixerPatchDao(): MixerPatchDao = object : MixerPatchDao {
            override fun getAllMixerPatches(): kotlinx.coroutines.flow.Flow<List<llm.slop.spirals.database.entities.MixerPatchEntity>> = flowOf(emptyList())
            override suspend fun insertMixerPatch(patch: llm.slop.spirals.database.entities.MixerPatchEntity) {}
            override suspend fun deleteById(id: String) {}
        }
        override fun showPatchDao(): ShowPatchDao = object : ShowPatchDao {
            override fun getAllShowPatches(): kotlinx.coroutines.flow.Flow<List<llm.slop.spirals.database.entities.ShowPatchEntity>> = flowOf(emptyList())
            override suspend fun insertShowPatch(patch: llm.slop.spirals.database.entities.ShowPatchEntity) {}
            override suspend fun deleteById(id: String) {}
        }
        override fun randomSetDao(): RandomSetDao = object : RandomSetDao {
            override fun getAllRandomSets(): kotlinx.coroutines.flow.Flow<List<llm.slop.spirals.database.entities.RandomSetEntity>> = flowOf(emptyList())
            override suspend fun insertRandomSet(set: llm.slop.spirals.database.entities.RandomSetEntity) {}
            override suspend fun deleteById(id: String) {}
        }
    }
}
