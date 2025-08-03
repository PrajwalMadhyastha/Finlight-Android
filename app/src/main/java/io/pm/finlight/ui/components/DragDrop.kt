// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/components/DragDrop.kt
// REASON: REFACTOR - The drag-and-drop logic has been refined to trigger a swap
// only when a dragged item's boundary completely passes an adjacent item's
// boundary. This makes the reordering feel more deliberate and correctly
// implements the "float and swap" behavior where other items "pop" into place
// during the drag gesture.
// =================================================================================
package io.pm.finlight.ui.components

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

@Composable
fun rememberDragDropState(
    lazyListState: LazyListState = rememberLazyListState(),
    onMove: (Int, Int) -> Unit,
): DragDropState {
    return remember { DragDropState(lazyListState, onMove) }
}

class DragDropState(
    val lazyListState: LazyListState,
    private val onMove: (Int, Int) -> Unit
) {
    var draggingItemKey by mutableStateOf<Any?>(null)
        private set

    var draggingItemTranslationY by mutableFloatStateOf(0f)
        private set

    private val currentDraggingItemInfo: LazyListItemInfo?
        get() = draggingItemKey?.let { key ->
            lazyListState.layoutInfo.visibleItemsInfo.find { it.key == key }
        }

    fun onDragStart(offset: Offset) {
        lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { item -> offset.y.toInt() in item.offset..(item.offset + item.size) }
            ?.also {
                if (it.index == 0) return // Prevent dragging the hero card
                draggingItemKey = it.key
            }
    }

    fun onDrag(offset: Offset) {
        draggingItemTranslationY += offset.y

        val draggingItem = currentDraggingItemInfo ?: return
        val draggingItemIndex = draggingItem.index

        // The current visual bounds of the dragged item
        val draggedItemTop = draggingItem.offset + draggingItemTranslationY
        val draggedItemBottom = draggedItemTop + draggingItem.size

        // Find a potential target for the swap by checking which item's
        // bounds the center of our dragged item is currently within.
        val targetItem = lazyListState.layoutInfo.visibleItemsInfo
            .find {
                // Not itself, not the hero card
                it.key != draggingItemKey && it.index != 0 &&
                        // Check if the dragged item's center is within the target's bounds
                        (draggedItemTop + draggingItem.size / 2f) in it.offset.toFloat()..(it.offset + it.size).toFloat()
            }

        if (targetItem != null) {
            // Now, check the "complete pass" condition before triggering a move.
            if (draggingItemIndex > targetItem.index) { // Dragging UP
                // Swap if the top of the dragged item has passed the top of the target.
                if (draggedItemTop < targetItem.offset) {
                    onMove(draggingItemIndex, targetItem.index)
                }
            } else { // Dragging DOWN
                // Swap if the bottom of the dragged item has passed the bottom of the target.
                if (draggedItemBottom > targetItem.offset + targetItem.size) {
                    onMove(draggingItemIndex, targetItem.index)
                }
            }
        }
    }


    fun onDragEnd() {
        draggingItemKey = null
        draggingItemTranslationY = 0f
    }

    fun checkForOverScroll(): Float {
        val draggingItem = currentDraggingItemInfo ?: return 0f
        val viewportStartOffset = lazyListState.layoutInfo.viewportStartOffset
        val viewportEndOffset = lazyListState.layoutInfo.viewportEndOffset

        val itemTop = draggingItem.offset + draggingItemTranslationY
        val itemBottom = itemTop + draggingItem.size

        val scrollAmount = 40f

        return when {
            itemBottom > viewportEndOffset -> scrollAmount
            itemTop < viewportStartOffset -> -scrollAmount
            else -> 0f
        }
    }
}
