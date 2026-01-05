package llm.slop.spirals

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MandalaViewModel(application: Application) : AndroidViewModel(application) {
    private val db = MandalaDatabase.getDatabase(application)
    private val tagDao = db.mandalaTagDao()
    private val patchDao = db.mandalaPatchDao()

    val tags: StateFlow<Map<String, List<String>>> = tagDao.getAllTags()
        .map { list -> list.groupBy({ it.id }, { it.tag }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val allPatches = patchDao.getAllPatches()

    fun savePatch(patchData: PatchData) {
        viewModelScope.launch {
            val json = PatchMapper.toJson(patchData)
            patchDao.insertPatch(MandalaPatchEntity(patchData.name, patchData.recipeId, json))
        }
    }

    fun deletePatch(name: String) {
        viewModelScope.launch { patchDao.deleteByName(name) }
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
