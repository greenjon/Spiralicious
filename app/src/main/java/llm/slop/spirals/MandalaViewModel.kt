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
import llm.slop.spirals.models.VideoSourceType
import java.util.UUID

/**
 * MandalaViewModel - The central hub for navigation, data persistence, and business logic.
 * 
 * RESPONSIBILITIES:
 * 1. Navigation Stack Management - Maintains the editing path through the hierarchy
 * 2. Data Persistence - Saves/loads patches to/from Room database
 * 3. Cascade System - Implements breadcrumb navigation with auto-save and auto-link
 * 4. Workspace Restoration - Remembers user's editing state between app launches
 * 
 * KEY CONCEPTS:
 * 
 * Navigation Stack (_navStack):
 * - List<NavLayer> representing the editing path
 * - Example: [Show1, Mix001, Set001] = editing Set001 within Mix001 within Show1
 * - Each layer tracks its data, dirty state, and parent relationship
 * 
 * Breadcrumb Cascade System:
 * - When user clicks parent breadcrumb, cascade save/link happens
 * - Walks from current layer down to target, saving and linking each
 * - See popToLayer() and linkChildToParent() for implementation
 * - Enables fast workflow: create child, edit, click parent breadcrumb = auto-linked
 * 
 * Hybrid Auto-Save:
 * - Items saved immediately on creation and changes
 * - Linking to parent happens on breadcrumb navigation
 * - Prevents data loss while avoiding UI clutter
 * 
 * Database Flows:
 * - allPatches, allSets, allMixerPatches, allShowPatches
 * - StateFlows that auto-update UI when database changes
 * - Used by editors to populate pickers and managers
 * 
 * IMPORTANT FUNCTIONS:
 * - createAndPushLayer() - Creates new patch and pushes to stack
 * - popToLayer() - Pops to target with cascade save/link
 * - linkChildToParent() - Adds child to parent's collection
 * - renameLayer() - Renames patch (updates both layer and data)
 * - saveLayer() - Persists layer to database
 * 
 * NOTE TO FUTURE AI: This ViewModel is the backbone of the navigation system.
 * Changes here affect the entire app's UX. Read DESIGN.md before modifying.
 * Test thoroughly with deep hierarchies (Show>Mixer>Set>Mandala).
 * 
 * Feel free to add more documentation as you discover patterns or edge cases!
 */
class MandalaViewModel(application: Application) : AndroidViewModel(application) {
    private val db = MandalaDatabase.getDatabase(application)
    private val tagDao = db.mandalaTagDao()
    private val patchDao = db.mandalaPatchDao()
    private val setDao = db.mandalaSetDao()
    private val mixerDao = db.mixerPatchDao()
    private val showDao = db.showPatchDao()
    private val randomSetDao = db.randomSetDao()
    private val appConfig = AppConfig(application)

    // 1. Declare Data Flows first
    val allPatches = patchDao.getAllPatches().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allSets = setDao.getAllSets().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allMixerPatches = mixerDao.getAllMixerPatches().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allShowPatches = showDao.getAllShowPatches().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allRandomSets = randomSetDao.getAllRandomSets().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
        LayerType.RANDOM_SET -> "RSet Editor"
    }

    fun generateNextName(type: LayerType): String {
        val (prefix, list) = when (type) {
            LayerType.MIXER -> "Mix" to allMixerPatches.value.map { it.name }
            LayerType.SET -> "Set" to allSets.value.map { it.name }
            LayerType.MANDALA -> "Man" to allPatches.value.map { it.name }
            LayerType.SHOW -> "Show" to allShowPatches.value.map { it.name }
            LayerType.RANDOM_SET -> "Rand" to allRandomSets.value.map { it.name }
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

    /**
     * Creates a new layer and pushes it onto the navigation stack.
     * 
     * This is the primary way to create new patches from within a parent context.
     * Example: User is editing Mix001 and clicks "Create new Set" on slot 2.
     * 
     * IMPORTANT BEHAVIORS:
     * - Generates a unique name (e.g., "Set001", "Man042")
     * - Creates initial data structure with defaults
     * - Saves immediately to database (hybrid auto-save approach)
     * - Sets createdFromParent=true for auto-linking on breadcrumb navigation
     * - Stores parentSlotIndex for Mixer children (which slot to insert into)
     * 
     * @param type The layer type to create (SHOW, MIXER, SET, or MANDALA)
     * @param parentSlotIndex For Mixer children: which slot (0-3) this belongs to.
     *                        Null for non-Mixer parents or creating at root level.
     * 
     * NOTE TO FUTURE AI: This function is part of the breadcrumb cascade system.
     * The auto-save here ensures work is never lost, while the createdFromParent flag
     * enables auto-linking when user clicks parent breadcrumb. See DESIGN.md for details.
     */
    fun createAndPushLayer(type: LayerType, parentSlotIndex: Int? = null) {
        val name = generateNextName(type)
        val id = UUID.randomUUID().toString()
        
        // Create initial data with auto-save
        val data: LayerContent = when(type) {
            LayerType.MIXER -> MixerLayerContent(MixerPatch(id = id, name = name, slots = List(4) { MixerSlotData() }))
            LayerType.SET -> SetLayerContent(MandalaSet(id = id, name = name, orderedMandalaIds = mutableListOf()))
            LayerType.MANDALA -> MandalaLayerContent(PatchData(name = name, recipeId = MandalaLibrary.MandalaRatios.first().id, parameters = emptyList()))
            LayerType.SHOW -> ShowLayerContent(ShowPatch(id = id, name = name))
            LayerType.RANDOM_SET -> RandomSetLayerContent(llm.slop.spirals.models.RandomSet(id = id, name = name))
        }
        
        val newLayer = NavLayer(
            id = id, 
            name = name, 
            type = type, 
            isDirty = true, 
            data = data,
            parentSlotIndex = parentSlotIndex,
            createdFromParent = true  // Mark as created from parent for auto-linking
        )
        
        pushLayer(newLayer)
        
        // Auto-save immediately (hybrid approach: save on creation, link on navigation)
        saveLayer(newLayer)
        
        // Set current patch if it's a Mandala
        if (type == LayerType.MANDALA) {
            _currentPatch.value = (data as MandalaLayerContent).patch
        }
    }

    fun createAndResetStack(type: LayerType, openedFromMenu: Boolean = false) {
        val name = getGenericName(type)
        val id = UUID.randomUUID().toString()
        _navStack.value = listOf(NavLayer(id, name, type, isDirty = false, openedFromMenu = openedFromMenu))
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
            LayerType.RANDOM_SET -> RandomSetLayerContent(llm.slop.spirals.models.RandomSet(id = id, name = name))
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
     * 
     * ⚠️ IMPORTANT: This is IN-MEMORY ONLY - does NOT save to database!
     * 
     * WHEN THIS IS CALLED:
     * - Every time user edits parameters (via LaunchedEffect in editors)
     * - When loading a patch from Manage overlay (preview)
     * - When setting initial data for a new layer
     * - After linking child to parent (updates parent's data)
     * 
     * PATTERN IN EDITORS:
     * ```kotlin
     * LaunchedEffect(currentPatch, someParameter) {
     *     val patchData = PatchMapper.fromVisualSource(name, visualSource)
     *     vm.updateLayerData(index, MandalaLayerContent(patchData))
     * }
     * ```
     * 
     * This captures work-in-progress so it's available during cascade save.
     * Database write happens later via saveLayer() during navigation.
     * 
     * @param index Index of the layer in navStack to update
     * @param data The new LayerContent (Mandala/Set/Mixer/Show data)
     * @param isDirty Optional dirty flag override
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
    
    fun clearOpenedFromMenuFlag(index: Int) {
        if (index < 0 || index >= _navStack.value.size) return
        val current = _navStack.value.toMutableList()
        current[index] = current[index].copy(openedFromMenu = false)
        _navStack.value = current
    }

    /**
     * Pops the navigation stack to a specific layer, optionally saving and linking children.
     * 
     * This is the CORE of the breadcrumb cascade system. When a user clicks a parent
     * breadcrumb, this function:
     * 1. Walks from current layer DOWN to target layer
     * 2. Saves each child layer
     * 3. Links children to parents if createdFromParent=true
     * 4. Pops everything above the target
     * 
     * EXAMPLE: Stack is [Show1, Mix001, Set001, Man001] and user clicks "Mix001"
     * - Saves Man001 and links to Set001
     * - Saves Set001 and links to Mix001 slot (using parentSlotIndex)
     * - Pops Man001 and Set001, leaving [Show1, Mix001]
     * 
     * @param index The target layer index to pop to (0-based)
     * @param save If true, performs cascade save/link. If false, just pops (for "Discard")
     * 
     * NOTE TO FUTURE AI: This function is critical! Changes here affect the entire
     * navigation UX. Test thoroughly with deep hierarchies. See DESIGN.md for full
     * explanation of the cascade system and why it works this way.
     */
    fun popToLayer(index: Int, save: Boolean = true) {
        if (index < -1) return
        if (index >= _navStack.value.size) return
        
        if (save) {
            // Process layers from current down to target+1, saving and linking
            // IMPORTANT: Walk backwards so children are saved before parents are updated
            for (i in _navStack.value.lastIndex downTo index + 1) {
                val child = _navStack.value[i]
                
                // Save the child layer to database
                if (child.isDirty || child.data != null) {
                    saveLayer(child)
                }
                
                // If created from parent, link it back to parent's collection
                if (child.createdFromParent && i > 0) {
                    val parentIndex = i - 1
                    linkChildToParent(child, parentIndex)
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
    
    /**
     * Links a child layer to its parent's collection.
     * 
     * This is called during breadcrumb cascade to add the child to the parent's
     * appropriate collection. Each parent type has different linking logic:
     * 
     * - SHOW → Adds randomSet.id to show.randomSetIds list
     * - MIXER → Adds set.id OR mandala.name to mixer.slots[parentSlotIndex]
     * - SET → Adds mandala.name to set.orderedMandalaIds list (at end)
     * - MANDALA → Cannot have children
     * 
     * IMPORTANT: Always checks for duplicates before adding to prevent double-linking
     * if user navigates back and forth multiple times.
     * 
     * @param child The child layer to link
     * @param parentIndex Index of the parent layer in the current stack
     * 
     * NOTE TO FUTURE AI: If you add "Edit" functionality (vs only "Create new"), you'll
     * need to set createdFromParent=false to prevent re-linking existing items. The
     * duplicate checks here provide safety, but explicit tracking is cleaner.
     * 
     * CRITICAL: Uses data.name (not layer.name) for linking because renames update data
     * first, and we want the current/correct name. See renameLayer() for details.
     */
    private fun linkChildToParent(child: NavLayer, parentIndex: Int) {
        if (parentIndex < 0 || parentIndex >= _navStack.value.size) return
        val parent = _navStack.value[parentIndex]
        val parentData = parent.data ?: return
        
        when (parent.type) {
            LayerType.SHOW -> {
                // SHOW parent: Add RandomSet to show's randomSetIds list
                val show = (parentData as? ShowLayerContent)?.show ?: return
                val randomSet = (child.data as? RandomSetLayerContent)?.randomSet ?: return

                // Add randomSet to show if not already present (prevents duplicates)
                if (!show.randomSetIds.contains(randomSet.id)) {
                    val updatedShow = show.copy(randomSetIds = show.randomSetIds + randomSet.id)
                    updateLayerData(parentIndex, ShowLayerContent(updatedShow), isDirty = true)
                    saveLayer(_navStack.value[parentIndex])
                }
            }
            LayerType.MIXER -> {
                // MIXER parent: Add Set, Mandala, or RSet to specific slot
                val mixer = (parentData as? MixerLayerContent)?.mixer ?: return
                val slotIndex = child.parentSlotIndex ?: return  // Must have slot index!
                
                when (child.type) {
                    LayerType.SET -> {
                        val set = (child.data as? SetLayerContent)?.set ?: return
                        val newSlots = mixer.slots.toMutableList()
                        
                        // Only update if not already set to this Set (prevents duplicate updates)
                        if (newSlots[slotIndex].mandalaSetId != set.id) {
                            newSlots[slotIndex] = newSlots[slotIndex].copy(
                                mandalaSetId = set.id,
                                sourceType = VideoSourceType.MANDALA_SET
                            )
                            val updatedMixer = mixer.copy(slots = newSlots)
                            updateLayerData(parentIndex, MixerLayerContent(updatedMixer), isDirty = true)
                            saveLayer(_navStack.value[parentIndex])
                        }
                    }
                    LayerType.MANDALA -> {
                        val mandala = (child.data as? MandalaLayerContent)?.patch ?: return
                        val newSlots = mixer.slots.toMutableList()
                        
                        // Only update if not already set to this Mandala
                        if (newSlots[slotIndex].selectedMandalaId != mandala.name) {
                            newSlots[slotIndex] = newSlots[slotIndex].copy(
                                selectedMandalaId = mandala.name,
                                sourceType = VideoSourceType.MANDALA
                            )
                            val updatedMixer = mixer.copy(slots = newSlots)
                            updateLayerData(parentIndex, MixerLayerContent(updatedMixer), isDirty = true)
                            saveLayer(_navStack.value[parentIndex])
                        }
                    }
                    LayerType.RANDOM_SET -> {
                        val randomSet = (child.data as? RandomSetLayerContent)?.randomSet ?: return
                        val newSlots = mixer.slots.toMutableList()
                        
                        // Only update if not already set to this RandomSet
                        if (newSlots[slotIndex].randomSetId != randomSet.id) {
                            newSlots[slotIndex] = newSlots[slotIndex].copy(
                                randomSetId = randomSet.id,
                                sourceType = VideoSourceType.RANDOM_SET
                            )
                            val updatedMixer = mixer.copy(slots = newSlots)
                            updateLayerData(parentIndex, MixerLayerContent(updatedMixer), isDirty = true)
                            saveLayer(_navStack.value[parentIndex])
                        }
                    }
                    else -> { /* Show can't be child of Mixer */ }
                }
            }
            LayerType.SET -> {
                val set = (parentData as? SetLayerContent)?.set ?: return
                val mandala = (child.data as? MandalaLayerContent)?.patch ?: return
                
                // Add mandala to set if not already present
                if (!set.orderedMandalaIds.contains(mandala.name)) {
                    val updatedSet = set.copy(orderedMandalaIds = (set.orderedMandalaIds + mandala.name).toMutableList())
                    updateLayerData(parentIndex, SetLayerContent(updatedSet), isDirty = true)
                    saveLayer(_navStack.value[parentIndex])
                }
            }
            LayerType.MANDALA -> {
                // Mandala can't have children
            }
            LayerType.RANDOM_SET -> {
                // RandomSet can't have children
            }
        }
    }

    /**
     * Saves a layer to the Room database.
     * 
     * CRITICAL: This is where actual persistence happens. Everything else is in-memory.
     * 
     * WHEN THIS IS CALLED:
     * - Immediately after createAndPushLayer() creates a new child
     * - During popToLayer() cascade (saves each layer before linking)
     * - On manual "Save" menu action
     * - During renameLayer() (after deleting old entry)
     * 
     * WHAT IT DOES:
     * 1. Extracts the data from layer.data (Mandala/Set/Mixer/Show)
     * 2. Ensures layer.name is used as the patch name (handles renames correctly)
     * 3. Inserts into Room database (upsert: updates if exists, inserts if new)
     * 4. Clears isDirty flag in the nav stack
     * 
     * NOTE: This does NOT link to parent. That's linkChildToParent()'s job.
     * 
     * @param layer The NavLayer containing data to persist
     */
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
                is RandomSetLayerContent -> {
                    saveRandomSet(data.randomSet.copy(name = layer.name))
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

    /**
     * Renames a layer, updating BOTH the layer name AND the data inside.
     * 
     * CRITICAL BEHAVIOR: This function must update TWO places:
     * 1. layer.name (what breadcrumb displays)
     * 2. data.*.name (patch.name, mixer.name, etc.) - what gets linked to parent
     * 
     * WHY BOTH? Because linkChildToParent uses data.name for linking. If we only
     * updated layer.name, the breadcrumb would show the new name but linking would
     * use the old name, causing the item to "disappear" from the parent.
     * 
     * PROCESS:
     * 1. Delete old database entry (by old name/id)
     * 2. Update data to use new name
     * 3. Update layer name in stack
     * 4. Save with new name (creates new database entry)
     * 
     * @param index Index of layer to rename
     * @param oldName Previous name (for deletion)
     * @param newName New name to apply
     * 
     * NOTE TO FUTURE AI: If you see bugs where renamed items don't appear in parents,
     * check that both layer.name and data.name are being updated. This was a tricky
     * bug that took time to track down. See DESIGN.md for more details.
     */
    fun renameLayer(index: Int, oldName: String, newName: String) {
        if (index < 0 || index >= _navStack.value.size) return
        val layer = _navStack.value[index]
        
        viewModelScope.launch {
            // Delete old entry from database
            when (val data = layer.data) {
                is MandalaLayerContent -> deletePatch(oldName)
                is SetLayerContent -> deleteSet(data.set.id)
                is MixerLayerContent -> deleteMixerPatch(data.mixer.id)
                is ShowLayerContent -> deleteShowPatch(data.show.id)
                is RandomSetLayerContent -> deleteRandomSet(data.randomSet.id)
                null -> { /* no data to delete */ }
            }
            
            // CRITICAL: Update the data to use the new name
            // This ensures linkChildToParent uses the correct name
            val updatedData: LayerContent? = when (val data = layer.data) {
                is MandalaLayerContent -> MandalaLayerContent(data.patch.copy(name = newName))
                is SetLayerContent -> SetLayerContent(data.set.copy(name = newName))
                is MixerLayerContent -> MixerLayerContent(data.mixer.copy(name = newName))
                is ShowLayerContent -> ShowLayerContent(data.show.copy(name = newName))
                is RandomSetLayerContent -> RandomSetLayerContent(data.randomSet.copy(name = newName))
                null -> null
            }
            
            // Update both name and data in stack
            val current = _navStack.value.toMutableList()
            current[index] = current[index].copy(name = newName, data = updatedData)
            _navStack.value = current
            
            // Update currentPatch if this is a Mandala being renamed
            if (layer.type == LayerType.MANDALA && updatedData is MandalaLayerContent) {
                _currentPatch.value = updatedData.patch
            }
            
            // Save with new name (creates new database entry)
            saveLayer(_navStack.value[index])
            saveWorkspaceIfEnabled()
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
            is RandomSetLayerContent -> RandomSetLayerContent(data.randomSet.copy(id = newId, name = newName))
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
            LayerType.RANDOM_SET -> allRandomSets.value.map { it.name }
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
                is RandomSetLayerContent -> deleteRandomSet(data.randomSet.id)
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

    fun saveRandomSet(randomSet: llm.slop.spirals.models.RandomSet) {
        viewModelScope.launch {
            val json = Json.encodeToString(randomSet)
            randomSetDao.insertRandomSet(RandomSetEntity(randomSet.id, randomSet.name, json))
        }
    }

    fun deleteRandomSet(id: String) {
        viewModelScope.launch { randomSetDao.deleteById(id) }
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
                LayerType.RANDOM_SET -> {
                    val entity = allRandomSets.value.find { it.name == oldName }
                    if (entity != null) {
                        randomSetDao.insertRandomSet(entity.copy(name = newName))
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
                LayerType.RANDOM_SET -> {
                    val entity = allRandomSets.value.find { it.name == name }
                    if (entity != null) {
                        val newId = UUID.randomUUID().toString()
                        val randomSet = Json.decodeFromString<llm.slop.spirals.models.RandomSet>(entity.jsonSettings)
                        val newRandomSet = randomSet.copy(id = newId, name = newName)
                        randomSetDao.insertRandomSet(RandomSetEntity(newId, newName, Json.encodeToString(newRandomSet)))
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
                LayerType.RANDOM_SET -> {
                    val entity = allRandomSets.value.find { it.name == name }
                    if (entity != null) randomSetDao.deleteById(entity.id)
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
