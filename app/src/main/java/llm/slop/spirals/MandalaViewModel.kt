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

    val tags: StateFlow<Map<String, String>> = dao.getAllTags()
        .map { list -> list.associate { it.speeds to (it.tag ?: "") } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun toggleTag(speeds: String, tag: String) {
        viewModelScope.launch {
            val current = dao.getTagForSpeeds(speeds)
            if (current?.tag == tag) {
                dao.deleteTag(current)
            } else {
                dao.insertTag(MandalaTag(speeds, tag))
            }
        }
    }

    fun getExportData(): String {
        val currentTags = tags.value
        if (currentTags.isEmpty()) return "No tags recorded."
        
        val sb = StringBuilder("Speeds,Tag\n")
        currentTags.forEach { (speeds, tag) ->
            sb.append("$speeds,$tag\n")
        }
        return sb.toString()
    }
}
