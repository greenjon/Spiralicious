package llm.slop.spirals.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import llm.slop.spirals.LayerType
import llm.slop.spirals.MandalaDatabase
import llm.slop.spirals.ShowLayerContent
import llm.slop.spirals.database.repositories.MixerRepository
import llm.slop.spirals.database.repositories.ShowRepository
import llm.slop.spirals.models.ShowPatch
import llm.slop.spirals.navigation.NavigationViewModel

/**
 * ViewModel for the Show Editor screen.
 * 
 * This ViewModel manages the state and logic specifically for editing Show patches.
 * It interacts with the NavigationViewModel for navigation and the ShowRepository for data access.
 */
class ShowEditorViewModel(
    application: Application,
    private val navigationViewModel: NavigationViewModel
) : AndroidViewModel(application) {
    private val database = MandalaDatabase.getDatabase(application)
    private val showRepository = ShowRepository(database)
    private val mixerRepository = MixerRepository(database)
    
    // The current show being edited
    private val _currentShow = MutableStateFlow<ShowPatch?>(null)
    val currentShow: StateFlow<ShowPatch?> = _currentShow.asStateFlow()
    
    // Currently selected mixer index in the show
    private val _currentShowIndex = MutableStateFlow(0)
    val currentShowIndex = _currentShowIndex.asStateFlow()
    
    // All available shows for selection
    val allShows = showRepository.getAll()
    
    // All available mixers that can be added to the show
    val allMixers = mixerRepository.getAll()
    
    /**
     * Sets the current show being edited.
     * 
     * @param show The show to edit
     */
    fun setCurrentShow(show: ShowPatch?) {
        _currentShow.value = show
    }
    
    /**
     * Updates the current show and the navigation layer.
     * 
     * @param show The updated show
     * @param isDirty Whether to mark the show as dirty
     */
    fun updateShow(show: ShowPatch, isDirty: Boolean = true) {
        _currentShow.value = show
        
        // Update the navigation layer data
        val navStack = navigationViewModel.navStack.value
        val index = navStack.indexOfLast { it.type == LayerType.SHOW }
        if (index >= 0) {
            navigationViewModel.updateLayerData(index, ShowLayerContent(show), isDirty)
        }
    }
    
    /**
     * Adds a mixer to the show.
     * 
     * @param mixerName The name of the mixer to add
     */
    fun addMixerToShow(mixerName: String) {
        val show = _currentShow.value ?: return
        if (!show.mixerNames.contains(mixerName)) {
            val updatedMixerNames = show.mixerNames + mixerName
            val updatedShow = show.copy(mixerNames = updatedMixerNames)
            updateShow(updatedShow)
        }
    }
    
    /**
     * Removes a mixer from the show.
     * 
     * @param mixerName The name of the mixer to remove
     */
    fun removeMixerFromShow(mixerName: String) {
        val show = _currentShow.value ?: return
        val updatedMixerNames = show.mixerNames.filter { it != mixerName }
        if (updatedMixerNames.size != show.mixerNames.size) {
            val updatedShow = show.copy(mixerNames = updatedMixerNames)
            updateShow(updatedShow)
        }
    }
    
    /**
     * Reorders mixers in the show.
     * 
     * @param fromIndex The original index
     * @param toIndex The target index
     */
    fun reorderMixers(fromIndex: Int, toIndex: Int) {
        val show = _currentShow.value ?: return
        val mixerNames = show.mixerNames.toMutableList()
        
        if (fromIndex < mixerNames.size && toIndex < mixerNames.size) {
            val item = mixerNames.removeAt(fromIndex)
            mixerNames.add(toIndex, item)
            val updatedShow = show.copy(mixerNames = mixerNames)
            updateShow(updatedShow)
        }
    }
    
    /**
     * Saves the current show to the database.
     */
    fun saveShow() {
        viewModelScope.launch {
            val show = _currentShow.value ?: return@launch
            showRepository.save(show)
            
            // Update the navigation layer to mark it as not dirty
            val navStack = navigationViewModel.navStack.value
            val index = navStack.indexOfLast { it.type == LayerType.SHOW }
            if (index >= 0) {
                navigationViewModel.updateLayerData(index, ShowLayerContent(show), false)
            }
        }
    }
    
    /**
     * Deletes a show by ID.
     * 
     * @param id The ID of the show to delete
     */
    fun deleteShow(id: String) {
        viewModelScope.launch {
            showRepository.deleteById(id)
        }
    }
    
    /**
     * Renames a show.
     * 
     * @param id The ID of the show to rename
     * @param newName The new name for the show
     */
    fun renameShow(id: String, newName: String) {
        viewModelScope.launch {
            val show = _currentShow.value ?: return@launch
            if (show.id == id) {
                val updatedShow = show.copy(name = newName)
                showRepository.save(updatedShow)
                _currentShow.value = updatedShow
                
                // Update the navigation layer
                val navStack = navigationViewModel.navStack.value
                val index = navStack.indexOfLast { it.type == LayerType.SHOW }
                if (index >= 0) {
                    navigationViewModel.updateLayerName(index, newName)
                    navigationViewModel.updateLayerData(index, ShowLayerContent(updatedShow), true)
                }
            } else {
                showRepository.renamePatch(id, newName)
            }
        }
    }
    
    /**
     * Clones a show.
     * 
     * @param id The ID of the show to clone
     * @param newName The name for the cloned show
     */
    fun cloneShow(id: String, newName: String) {
        viewModelScope.launch {
            val newId = showRepository.clonePatch(id, newName)
            if (newId != null) {
                // If we cloned the current show, load the new one
                if (id == _currentShow.value?.id) {
                    showRepository.getById(newId)?.let { newShow ->
                        _currentShow.value = newShow
                    }
                }
            }
        }
    }
    
    /**
     * Jumps to a specific mixer in the show.
     * 
     * @param index The index of the mixer to jump to
     */
    fun jumpToShowIndex(index: Int) {
        if (index >= 0 && index < (_currentShow.value?.mixerNames?.size ?: 0)) {
            _currentShowIndex.value = index
        }
    }
    
    /**
     * Moves to the next mixer in the show.
     */
    fun triggerNextMixer() {
        val size = _currentShow.value?.mixerNames?.size ?: 0
        if (size > 0) {
            _currentShowIndex.value = (_currentShowIndex.value + 1) % size
        }
    }
    
    /**
     * Moves to the previous mixer in the show.
     */
    fun triggerPrevMixer() {
        val size = _currentShow.value?.mixerNames?.size ?: 0
        if (size > 0) {
            _currentShowIndex.value = if (_currentShowIndex.value <= 0) size - 1 else _currentShowIndex.value - 1
        }
    }
}