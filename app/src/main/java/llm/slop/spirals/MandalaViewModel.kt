package llm.slop.spirals

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MandalaViewModel(application: Application) : AndroidViewModel(application) {
    private val db = MandalaDatabase.getDatabase(application)
    private val tagDao = db.mandalaTagDao()
    private val patchDao = db.mandalaPatchDao()
    private val setDao = db.mandalaSetDao()
    private val mixerDao = db.mixerPatchDao()

    val tags: StateFlow<Map<String, List<String>>> = tagDao.getAllTags()
        .map { list -> list.groupBy({ it.id }, { it.tag }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val allPatches = patchDao.getAllPatches()
    val allSets = setDao.getAllSets()
    val allMixerPatches = mixerDao.getAllMixerPatches()

    fun savePatch(patchData: PatchData) {
        viewModelScope.launch {
            val json = PatchMapper.toJson(patchData)
            patchDao.insertPatch(MandalaPatchEntity(patchData.name, patchData.recipeId, json))
        }
    }

    fun deletePatch(name: String) {
        viewModelScope.launch { patchDao.deleteByName(name) }
    }

    fun saveSet(mandalaSet: MandalaSet) {
        viewModelScope.launch {
            val entity = MandalaSetEntity(
                id = mandalaSet.id,
                name = mandalaSet.name,
                jsonOrderedMandalaIds = Json.encodeToString(mandalaSet.orderedMandalaIds),
                selectionPolicy = mandalaSet.selectionPolicy.name
            )
            setDao.insertSet(entity)
        }
    }

    fun deleteSet(id: String) {
        viewModelScope.launch { setDao.deleteById(id) }
    }

    fun saveMixerPatch(mixerPatch: MixerPatch) {
        viewModelScope.launch {
            val json = Json.encodeToString(mixerPatch)
            mixerDao.insertMixerPatch(MixerPatchEntity(mixerPatch.id, mixerPatch.name, json))
        }
    }

    fun deleteMixerPatch(id: String) {
        viewModelScope.launch { mixerDao.deleteById(id) }
    }

    fun toggleTag(id: String, tag: String) {
        viewModelScope.launch {
            val allForId = tagDao.getAllTagsForId(id)
            val existing = allForId.find { it.tag == tag }
            if (existing != null) {
                tagDao.deleteTag(existing)
            } else {
                val ratings = listOf("trash", "1", "2", "3")
                if (tag in ratings) {
                    allForId.filter { it.tag in ratings }.forEach { tagDao.deleteTag(it) }
                }
                tagDao.insertTag(MandalaTag(id, tag))
            }
        }
    }

    fun getExportData(): String {
        val currentTags = tags.value
        if (currentTags.isEmpty()) return "No tags recorded."
        val sb = StringBuilder("ID,Tags\n")
        currentTags.forEach { (id, tagsList) -> sb.append("$id,${tagsList.joinToString("|")}\n") }
        return sb.toString()
    }
}
