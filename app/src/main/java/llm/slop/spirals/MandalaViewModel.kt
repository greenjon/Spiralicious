package llm.slop.spirals

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import llm.slop.spirals.models.MixerPatch
import llm.slop.spirals.models.ShowPatch
import llm.slop.spirals.models.MixerSlotData
import java.util.UUID

class MandalaViewModel(application: Application) : AndroidViewModel(application) {
    private val db = MandalaDatabase.getDatabase(application)
    private val tagDao = db.mandalaTagDao()
    private val patchDao = db.mandalaPatchDao()
    private val setDao = db.mandalaSetDao()
    private val mixerDao = db.mixerPatchDao()
    private val showDao = db.showPatchDao()
    private val appConfig = AppConfig(application)

    // 1. Declare Data Flows first
    val allPatches = patchDao.getAllPatches().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allSets = setDao.getAllSets().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allMixerPatches = mixerDao.getAllMixerPatches().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allShowPatches = showDao.getAllShowPatches().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _currentPatch = MutableStateFlow<PatchData?>(null)
    val currentPatch: StateFlow<PatchData?> = _currentPatch.asStateFlow()

    // Show state
    private val _currentShowIndex = MutableStateFlow(0)
    val currentShowIndex = _currentShowIndex.asStateFlow()

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
            StartupMode.SHOW -> LayerType.SHOW
            else -> LayerType.MIXER
        }
        
        return listOf(NavLayer(UUID.randomUUID().toString(), getGenericName(type), type, isDirty = false))
    }

    fun getGenericName(type: LayerType): String = when(type) {
        LayerType.MIXER -> "Mixer Editor"
        LayerType.SET -> "Set Editor"
        LayerType.MANDALA -> "Mandala Editor"
        LayerType.SHOW -> "Show Editor"
    }

    fun generateNextName(type: LayerType): String {
        val (prefix, list) = when (type) {
            LayerType.MIXER -> "Mix" to allMixerPatches.value.map { it.name }
            LayerType.SET -> "Set" to allSets.value.map { it.name }
            LayerType.MANDALA -> "Man" to allPatches.value.map { it.name }
            LayerType.SHOW -> "Show" to allShowPatches.value.map { it.name }
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

    fun createAndPushLayer(type: LayerType, parentData: LayerContent? = null) {
        val name = generateNextName(type)
        val id = UUID.randomUUID().toString()
        pushLayer(NavLayer(id, name, type, isDirty = false, data = parentData))
    }

    fun createAndResetStack(type: LayerType) {
        val name = getGenericName(type)
        val id = UUID.randomUUID().toString()
        _navStack.value = listOf(NavLayer(id, name, type, isDirty = false))
        saveWorkspaceIfEnabled()
    }

    fun startNewPatch(type: LayerType) {
        val name = generateNextName(type)
        val id = UUID.randomUUID().toString()
        val data: LayerContent? = when(type) {
            LayerType.MIXER -> MixerLayerContent(MixerPatch(id = id, name = name, slots = List(4) { MixerSlotData() }))
            LayerType.SET -> SetLayerContent(MandalaSet(id = id, name = name, orderedMandalaIds = mutableListOf()))
            LayerType.SHOW -> ShowLayerContent(ShowPatch(id = id, name = name))
            LayerType.MANDALA -> MandalaLayerContent(PatchData(name = name, recipeId = MandalaLibrary.MandalaRatios.first().id, parameters = emptyList()))
        }
        
        val newLayer = NavLayer(id, name, type, isDirty = true, data = data)
        
        // If we are at root and it is generic, replace it
        if (_navStack.value.size == 1 && _navStack.value[0].type == type && _navStack.value[0].data == null) {
            _navStack.value = listOf(newLayer)
        } else {
            pushLayer(newLayer)
        }
        
        if (type == LayerType.MANDALA) {
            _currentPatch.value = (data as? MandalaLayerContent)?.patch
        }
    }

    /**
     * Updates the data associated with a specific layer in the stack.
     * Useful for capturing "Work in progress" before a pop or save.
     */
    fun updateLayerData(index: Int, data: LayerContent?, isDirty: Boolean? = null) {
        if (index < 0 || index >= _navStack.value.size) return
        val current = _navStack.value.toMutableList()
        val updatedLayer = current[index]
        val newLayer = updatedLayer.copy(
            data = data,
            isDirty = isDirty ?: updatedLayer.isDirty
        )
        current[index] = newLayer
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
        
        val newStack = if (index == -1) {
            emptyList()
        } else {
            _navStack.value.take(index + 1)
        }
        
        _navStack.value = if (newStack.isEmpty()) {
            // If the stack is emptied, default back to a Mixer hub
            val id = UUID.randomUUID().toString()
            listOf(NavLayer(id, getGenericName(LayerType.MIXER), LayerType.MIXER, isDirty = false))
        } else {
            newStack
        }
        saveWorkspaceIfEnabled()
    }

    fun saveLayer(layer: NavLayer) {
        val data = layer.data ?: return
        viewModelScope.launch {
            when (data) {
                is MandalaLayerContent -> {
                    savePatch(data.patch.copy(name = layer.name))
                }
                is SetLayerContent -> {
                    saveSet(data.set.copy(name = layer.name))
                }
                is MixerLayerContent -> {
                    saveMixerPatch(data.mixer.copy(name = layer.name))
                }
                is ShowLayerContent -> {
                    saveShowPatch(data.show.copy(name = layer.name))
                }
            }
            // Clear dirty flag for this layer in the stack
            val index = _navStack.value.indexOfFirst { it.id == layer.id }
            if (index != -1) {
                _navStack.update { stack ->
                    stack.toMutableList().apply {
                        this[index] = this[index].copy(isDirty = false)
                    }
                }
            }
        }
    }

    fun renameLayer(index: Int, oldName: String, newName: String) {
        if (index < 0 || index >= _navStack.value.size) return
        val layer = _navStack.value[index]
        
        viewModelScope.launch {
            // Delete old entry
            when (val data = layer.data) {
                is MandalaLayerContent -> deletePatch(oldName)
                is SetLayerContent -> deleteSet(data.set.id)
                is MixerLayerContent -> deleteMixerPatch(data.mixer.id)
                is ShowLayerContent -> deleteShowPatch(data.show.id)
                null -> { /* no data to delete */ }
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
        
        val newName = NamingUtils.generateCloneName(layer.name, getExistingNames(layer.type))
        val newId = UUID.randomUUID().toString()
        
        val newData: LayerContent = when (data) {
            is MandalaLayerContent -> MandalaLayerContent(data.patch.copy(name = newName))
            is SetLayerContent -> SetLayerContent(data.set.copy(id = newId, name = newName))
            is MixerLayerContent -> MixerLayerContent(data.mixer.copy(id = newId, name = newName))
            is ShowLayerContent -> ShowLayerContent(data.show.copy(id = newId, name = newName))
        }
        
        val newLayer = NavLayer(newId, newName, layer.type, isDirty = true, data = newData)
        pushLayer(newLayer)
        saveLayer(newLayer)
    }

    private fun getExistingNames(type: LayerType): List<String> {
        return when (type) {
            LayerType.MIXER -> allMixerPatches.value.map { it.name }
            LayerType.SET -> allSets.value.map { it.name }
            LayerType.MANDALA -> allPatches.value.map { it.name }
            LayerType.SHOW -> allShowPatches.value.map { it.name }
        }
    }

    fun deleteLayerAndPop(index: Int) {
        if (index < 0 || index >= _navStack.value.size) return
        val layer = _navStack.value[index]
        
        viewModelScope.launch {
            when (val data = layer.data) {
                is MandalaLayerContent -> deletePatch(layer.name)
                is SetLayerContent -> deleteSet(data.set.id)
                is MixerLayerContent -> deleteMixerPatch(data.mixer.id)
                is ShowLayerContent -> deleteShowPatch(data.show.id)
                null -> { /* no data to delete */ }
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

    fun saveShowPatch(showPatch: ShowPatch) {
        viewModelScope.launch {
            val json = Json.encodeToString(showPatch)
            showDao.insertShowPatch(ShowPatchEntity(showPatch.id, showPatch.name, json))
        }
    }

    fun deleteShowPatch(id: String) {
        viewModelScope.launch { showDao.deleteById(id) }
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

    fun renamePatch(type: LayerType, oldName: String, newName: String) {
        viewModelScope.launch {
            when (type) {
                LayerType.MANDALA -> {
                    val entity = allPatches.value.find { it.name == oldName }
                    if (entity != null) {
                        patchDao.deleteByName(oldName)
                        patchDao.insertPatch(entity.copy(name = newName))
                    }
                }
                LayerType.SET -> {
                    val entity = allSets.value.find { it.name == oldName }
                    if (entity != null) {
                        setDao.insertSet(entity.copy(name = newName))
                    }
                }
                LayerType.MIXER -> {
                    val entity = allMixerPatches.value.find { it.name == oldName }
                    if (entity != null) {
                        mixerDao.insertMixerPatch(entity.copy(name = newName))
                    }
                }
                LayerType.SHOW -> {
                    val entity = allShowPatches.value.find { it.name == oldName }
                    if (entity != null) {
                        showDao.insertShowPatch(entity.copy(name = newName))
                    }
                }
            }
        }
    }

    fun cloneSavedPatch(type: LayerType, name: String) {
        viewModelScope.launch {
            val newName = NamingUtils.generateCloneName(name, getExistingNames(type))
            when (type) {
                LayerType.MANDALA -> {
                    val entity = allPatches.value.find { it.name == name }
                    if (entity != null) {
                        patchDao.insertPatch(entity.copy(name = newName))
                    }
                }
                LayerType.SET -> {
                    val entity = allSets.value.find { it.name == name }
                    if (entity != null) {
                        val newId = UUID.randomUUID().toString()
                        setDao.insertSet(entity.copy(id = newId, name = newName))
                    }
                }
                LayerType.MIXER -> {
                    val entity = allMixerPatches.value.find { it.name == name }
                    if (entity != null) {
                        val newId = UUID.randomUUID().toString()
                        val mixer = Json.decodeFromString<MixerPatch>(entity.jsonSettings)
                        val newMixer = mixer.copy(id = newId, name = newName)
                        mixerDao.insertMixerPatch(MixerPatchEntity(newId, newName, Json.encodeToString(newMixer)))
                    }
                }
                LayerType.SHOW -> {
                    val entity = allShowPatches.value.find { it.name == name }
                    if (entity != null) {
                        val newId = UUID.randomUUID().toString()
                        val show = Json.decodeFromString<ShowPatch>(entity.jsonSettings)
                        val newShow = show.copy(id = newId, name = newName)
                        showDao.insertShowPatch(ShowPatchEntity(newId, newName, Json.encodeToString(newShow)))
                    }
                }
            }
        }
    }

    fun deleteSavedPatch(type: LayerType, name: String) {
        viewModelScope.launch {
            when (type) {
                LayerType.MANDALA -> patchDao.deleteByName(name)
                LayerType.SET -> {
                    val entity = allSets.value.find { it.name == name }
                    if (entity != null) setDao.deleteById(entity.id)
                }
                LayerType.MIXER -> {
                    val entity = allMixerPatches.value.find { it.name == name }
                    if (entity != null) mixerDao.deleteById(entity.id)
                }
                LayerType.SHOW -> {
                    val entity = allShowPatches.value.find { it.name == name }
                    if (entity != null) showDao.deleteById(entity.id)
                }
            }
        }
    }

    fun jumpToShowIndex(index: Int) {
        _currentShowIndex.value = index
    }

    fun triggerNextMixer(size: Int) {
        if (size == 0) return
        _currentShowIndex.value = (_currentShowIndex.value + 1) % size
    }

    fun triggerPrevMixer(size: Int) {
        if (size == 0) return
        _currentShowIndex.value = if (_currentShowIndex.value <= 0) size - 1 else _currentShowIndex.value - 1
    }
}
