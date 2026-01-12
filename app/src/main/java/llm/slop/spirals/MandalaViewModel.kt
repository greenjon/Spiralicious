package llm.slop.spirals

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import llm.slop.spirals.models.MixerPatch
import java.util.UUID

data class NavLayer(
    val id: String,
    val name: String,
    val type: LayerType,
    val data: Any? = null 
)

enum class LayerType { MIXER, SET, MANDALA }

class MandalaViewModel(application: Application) : AndroidViewModel(application) {
    private val db = MandalaDatabase.getDatabase(application)
    private val tagDao = db.mandalaTagDao()
    private val patchDao = db.mandalaPatchDao()
    private val setDao = db.mandalaSetDao()
    private val mixerDao = db.mixerPatchDao()

    private val _currentPatch = MutableStateFlow<PatchData?>(null)
    val currentPatch: StateFlow<PatchData?> = _currentPatch.asStateFlow()

    private val _navStack = MutableStateFlow<List<NavLayer>>(listOf(NavLayer("root", "Mixer 1", LayerType.MIXER)))
    val navStack = _navStack.asStateFlow()

    val allPatches = patchDao.getAllPatches().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allSets = setDao.getAllSets().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allMixerPatches = mixerDao.getAllMixerPatches().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun generateNextName(type: LayerType): String {
        val (prefix, list) = when (type) {
            LayerType.MIXER -> "Mix" to allMixerPatches.value.map { it.name }
            LayerType.SET -> "Set" to allSets.value.map { it.name }
            LayerType.MANDALA -> "Man" to allPatches.value.map { it.name }
        }
        
        val regex = Regex("${prefix}(\\d+)")
        val maxNum = list.mapNotNull { 
            regex.find(it)?.groupValues?.get(1)?.toIntOrNull() 
        }.maxOrNull() ?: 0
        
        return "$prefix${(maxNum + 1).toString().padStart(3, '0')}"
    }

    fun pushLayer(layer: NavLayer) {
        _navStack.value += layer
    }

    fun createAndPushLayer(type: LayerType, parentData: Any? = null) {
        val name = generateNextName(type)
        val id = UUID.randomUUID().toString()
        pushLayer(NavLayer(id, name, type, parentData))
    }

    fun popToLayer(index: Int, save: Boolean = true) {
        if (index < 0 || index >= _navStack.value.size) return
        val layersToPop = _navStack.value.subList(index + 1, _navStack.value.size).reversed()
        
        if (save) {
            layersToPop.forEach { layer ->
                // Actual saving logic would go here, triggered by an external save signal or data capture
                // For now, we assume the UI handles the "dirty" state and we just confirm the final pop
            }
        }
        
        _navStack.value = _navStack.value.take(index + 1)
    }

    fun setCurrentPatch(patch: PatchData?) {
        _currentPatch.value = patch
    }

    val tags: StateFlow<Map<String, List<String>>> = tagDao.getAllTags()
        .map { list -> list.groupBy({ it.id }, { it.tag }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

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
