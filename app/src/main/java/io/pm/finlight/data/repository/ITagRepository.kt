package io.pm.finlight

import kotlinx.coroutines.flow.Flow

interface ITagRepository {
    val allTags: Flow<List<Tag>>

    suspend fun insert(tag: Tag): Long

    suspend fun update(tag: Tag)

    suspend fun delete(tag: Tag)

    suspend fun isTagInUse(tagId: Int): Boolean

    suspend fun findOrCreateTag(tagName: String): Tag

    suspend fun findTagById(id: Int): Tag?

    suspend fun findByName(name: String): Tag?
}
