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
    val data: Any? = null,
    val isDirty: Boolean = false
)

enum class LayerType { MIXER, SET, MANDALA }

class MandalaViewModel(application: Application) : AndroidViewModel(application) {
    private val db = MandalaDatabase.getDatabase(application)
    private val tagDao = db.mandalaTagDao()
    private val patchDao = db.mandalaPatchDao()
    private val setDao = db.mandalaSetDao()
    private val mixerDao = db.mixerPatchDao()
    private val appConfig = AppConfig(application)

    // 1. Declare Data Flows first
    val allPatches = patchDao.getAllPatches().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allSets = setDao.getAllSets().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allMixerPatches = mixerDao.getAllMixerPatches().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _currentPatch = MutableStateFlow<PatchData?>(null)
    val currentPatch: StateFlow<PatchData?> = _currentPatch.asStateFlow()

    // 2. Initialize navStack with empty first, then fill in init
    private val _navStack = MutableStateFlow<List<NavLayer>>(emptyList())
    val navStack = _navStack.asStateFlow()

    init {
        // 3. Now it is safe to call functions that use the flows
        _navStack.value = initialStack()
    }

    private fun initialStack(): List<NavLayer> {
        val mode = appConfig.startupMode
        if (mode == StartupMode.LAST_WORKSPACE) {
            val saved = appConfig.loadNavStack()
            if (!saved.isNullOrEmpty()) return saved
        }
        
        val type = when (mode) {
            StartupMode.MIXER -> LayerType.MIXER
            StartupMode.SET -> LayerType.SET
            StartupMode.MANDALA -> LayerType.MANDALA
            else -> LayerType.MIXER
        }
        
        val name = generateNextName(type)
        return listOf(NavLayer(UUID.randomUUID().toString(), name, type))
    }

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
        saveWorkspaceIfEnabled()
    }

    fun createAndPushLayer(type: LayerType, parentData: Any? = null) {
        val name = generateNextName(type)
        val id = UUID.randomUUID().toString()
        pushLayer(NavLayer(id, name, type, parentData))
    }

    /**
     * Updates the data associated with a specific layer in the stack.
     * Useful for capturing "Work in progress" before a pop or save.
     */
    fun updateLayerData(index: Int, data: Any?, isDirty: Boolean? = null) {
        if (index < 0 || index >= _navStack.value.size) return
        val current = _navStack.value.toMutableList()
        current[index] = current[index].copy(
            data = data,
            isDirty = isDirty ?: current[index].isDirty
        )
        _navStack.value = current
    }

    fun updateLayerName(index: Int, newName: String) {
        if (index < 0 || index >= _navStack.value.size) return
        val current = _navStack.value.toMutableList()
        current[index] = current[index].copy(name = newName)
        _navStack.value = current
        saveWorkspaceIfEnabled()
    }

    fun popToLayer(index: Int, save: Boolean = true) {
        if (index < -1) return
        if (index >= _navStack.value.size) return
        
        val layersToPop = _navStack.value.subList(index + 1, _navStack.value.size).reversed()
        
        if (save) {
            layersToPop.forEach { layer ->
                if (layer.isDirty) {
                    saveLayer(layer)
                }
            }
        }
        
        _navStack.value = if (index == -1) {
            emptyList()
        } else {
            _navStack.value.take(index + 1)
        }
        saveWorkspaceIfEnabled()
    }

    fun saveLayer(layer: NavLayer) {
        val data = layer.data ?: return
        viewModelScope.launch {
            when (layer.type) {
                LayerType.MANDALA -> {
                    val patch = data as? PatchData ?: return@launch
                    savePatch(patch.copy(name = layer.name))
                }
                LayerType.SET -> {
                    val set = data as? MandalaSet ?: return@launch
                    saveSet(set.copy(name = layer.name))
                }
                LayerType.MIXER -> {
                    val mixer = data as? MixerPatch ?: return@launch
                    saveMixerPatch(mixer.copy(name = layer.name))
                }
            }
            // Clear dirty flag for this layer in the stack
            val index = _navStack.value.indexOfFirst { it.id == layer.id }
            if (index != -1) {
                val current = _navStack.value.toMutableList()
                current[index] = current[index].copy(isDirty = false)
                _navStack.value = current
            }
        }
    }

    fun renameLayer(index: Int, oldName: String, newName: String) {
        if (index < 0 || index >= _navStack.value.size) return
        val layer = _navStack.value[index]
        
        viewModelScope.launch {
            // Delete old entry
            when (layer.type) {
                LayerType.MANDALA -> deletePatch(oldName)
                LayerType.SET -> {
                    (layer.data as? MandalaSet)?.let { deleteSet(it.id) }
                }
                LayerType.MIXER -> {
                    (layer.data as? MixerPatch)?.let { deleteMixerPatch(it.id) }
                }
            }
            
            // Update name in stack
            updateLayerName(index, newName)
            
            // Save with new name
            saveLayer(_navStack.value[index])
        }
    }

    fun cloneLayer(index: Int) {
        if (index < 0 || index >= _navStack.value.size) return
        val layer = _navStack.value[index]
        val data = layer.data ?: return
        
        val newName = generateNextName(layer.type)
        val newId = UUID.randomUUID().toString()
        
        val newData = when (layer.type) {
            LayerType.MANDALA -> (data as? PatchData)?.copy(name = newName)
            LayerType.SET -> (data as? MandalaSet)?.copy(id = newId, name = newName)
            LayerType.MIXER -> (data as? MixerPatch)?.copy(id = newId, name = newName)
        } ?: return
        
        val newLayer = NavLayer(newId, newName, layer.type, newData, isDirty = true)
        pushLayer(newLayer)
        saveLayer(newLayer)
    }

    fun deleteLayerAndPop(index: Int) {
        if (index < 0 || index >= _navStack.value.size) return
        val layer = _navStack.value[index]
        
        viewModelScope.launch {
            when (layer.type) {
                LayerType.MANDALA -> deletePatch(layer.name)
                LayerType.SET -> (layer.data as? MandalaSet)?.let { deleteSet(it.id) }
                LayerType.MIXER -> (layer.data as? MixerPatch)?.let { deleteMixerPatch(it.id) }
            }
            popToLayer(index - 1, save = false)
        }
    }

    private fun saveWorkspaceIfEnabled() {
        if (appConfig.startupMode == StartupMode.LAST_WORKSPACE) {
            appConfig.saveNavStack(_navStack.value)
        }
    }

    fun getStartupMode(): StartupMode = appConfig.startupMode
    fun setStartupMode(mode: StartupMode) {
        appConfig.startupMode = mode
        saveWorkspaceIfEnabled()
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
