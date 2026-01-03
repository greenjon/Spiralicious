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
    private val dao = db.mandalaTagDao()

    val tags: StateFlow<Map<String, List<String>>> = dao.getAllTags()
        .map { list -> 
            list.groupBy({ it.id }, { it.tag })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun toggleTag(id: String, tag: String) {
        viewModelScope.launch {
            val allForId = dao.getAllTagsForId(id)
            val existing = allForId.find { it.tag == tag }
            if (existing != null) {
                dao.deleteTag(existing)
            } else {
                // For "trash", "1", "2", "3", they are mutually exclusive ratings
                val ratings = listOf("trash", "1", "2", "3")
                if (tag in ratings) {
                    allForId.filter { it.tag in ratings }.forEach { dao.deleteTag(it) }
                }
                dao.insertTag(MandalaTag(id, tag))
            }
        }
    }

    fun getExportData(): String {
        val currentTags = tags.value
        if (currentTags.isEmpty()) return "No tags recorded."
        
        val sb = StringBuilder("ID,Tags\n")
        currentTags.forEach { (id, tagsList) ->
            sb.append("$id,${tagsList.joinToString("|")}\n")
        }
        return sb.toString()
    }
}
