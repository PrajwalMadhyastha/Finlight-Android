// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/TagViewModel.kt
// REASON: FIX - The `deleteTag` function has been hardened. It now also checks
// if a tag is linked to a historical trip record before allowing deletion. This
// prevents accidental deletion of trip data and provides clearer user feedback.
// =================================================================================
package io.pm.finlight

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.repository.TripRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TagViewModel(application: Application) : AndroidViewModel(application) {
    private val tagRepository: TagRepository
    private val tripRepository: TripRepository // --- NEW: Add TripRepository
    private val tagDao: TagDao
    private val _uiEvent = Channel<String>()
    val uiEvent = _uiEvent.receiveAsFlow()

    val allTags: StateFlow<List<Tag>>

    init {
        val database = AppDatabase.getInstance(application)
        tagDao = database.tagDao()
        val transactionDao = database.transactionDao()
        tagRepository = TagRepository(tagDao, transactionDao)
        tripRepository = TripRepository(database.tripDao()) // --- NEW: Initialize TripRepository

        allTags = tagRepository.allTags.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    /**
     * Called from the 'Manage Tags' screen. Inserts a new tag into the database.
     */
    fun addTag(tagName: String) {
        if (tagName.isNotBlank()) {
            viewModelScope.launch {
                // Check if a tag with this name already exists
                val existingTag = tagDao.findByName(tagName)
                if (existingTag != null) {
                    _uiEvent.send("A tag named '$tagName' already exists.")
                } else {
                    tagRepository.insert(Tag(name = tagName))
                    _uiEvent.send("Tag '$tagName' created.")
                }
            }
        }
    }

    fun updateTag(tag: Tag) {
        if (tag.name.isNotBlank()) {
            viewModelScope.launch {
                tagRepository.update(tag)
            }
        }
    }

    fun deleteTag(tag: Tag) {
        viewModelScope.launch {
            // --- UPDATED: Add a check for trip usage ---
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
        }
    }
}
