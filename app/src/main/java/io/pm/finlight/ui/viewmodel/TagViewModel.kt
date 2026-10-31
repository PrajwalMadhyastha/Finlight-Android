package io.pm.finlight.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.ITagRepository
import io.pm.finlight.Tag
import io.pm.finlight.data.repository.ITripRepository
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.DispatcherProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TagViewModel(
    private val tagRepository: ITagRepository,
    private val tripRepository: ITripRepository,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) : ViewModel() {
    private val _uiEvent = Channel<String>(Channel.UNLIMITED)
    val uiEvent = _uiEvent.receiveAsFlow()

    val allTags: StateFlow<List<Tag>> =
        tagRepository.allTags.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    /**
     * Called from the 'Manage Tags' screen. Inserts a new tag into the database.
     */
    fun addTag(tagName: String) {
        if (tagName.isNotBlank()) {
            viewModelScope.launch(dispatcherProvider.io) {
                try {
                    // Check if a tag with this name already exists using the repository
                    val existingTag = tagRepository.findByName(tagName)
                    if (existingTag != null) {
                        _uiEvent.send("A tag named '$tagName' already exists.")
                    } else {
                        tagRepository.insert(Tag(name = tagName))
                        _uiEvent.send("Tag '$tagName' created.")
                    }
                } catch (e: Exception) {
                    _uiEvent.send("Error creating tag: ${e.message}")
                }
            }
        }
    }

    fun updateTag(tag: Tag) {
        if (tag.name.isNotBlank()) {
            viewModelScope.launch(dispatcherProvider.io) {
                try {
                    tagRepository.update(tag)
                } catch (e: Exception) {
                    _uiEvent.send("Error updating tag: ${e.message}")
                }
            }
        }
    }

    fun deleteTag(tag: Tag) {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                val isUsedByTransaction = tagRepository.isTagInUse(tag.id)
                val isUsedByTrip = tripRepository.isTagUsedByTrip(tag.id)

                if (isUsedByTransaction) {
                    _uiEvent.send("Cannot delete '${tag.name}'. It is attached to one or more transactions.")
                } else if (isUsedByTrip) {
                    _uiEvent.send("Cannot delete '${tag.name}'. It is linked to a trip in your travel history.")
                } else {
                    tagRepository.delete(tag)
                    _uiEvent.send("Tag '${tag.name}' deleted.")
                }
            } catch (e: Exception) {
                _uiEvent.send("Error deleting tag: ${e.message}")
            }
        }
    }
}
