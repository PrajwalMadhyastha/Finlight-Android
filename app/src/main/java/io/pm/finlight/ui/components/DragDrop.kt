// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/components/DragDrop.kt
// REASON: REFACTOR - The drag-and-drop logic has been completely rewritten to
// implement a "float and swap" behavior. The dragged item now visually floats
// over the static list, and the actual reordering of items only occurs once,
// when the drag gesture is released. This provides a smoother, more stable,
// and predictable user experience, preventing items from jumping during the drag.
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
    // The key of the item being dragged.
    var draggingItemKey by mutableStateOf<Any?>(null)
        private set

    // The vertical displacement of the dragged item from its original position.
    var draggingItemTranslationY by mutableFloatStateOf(0f)
        private set

    // The index of the item as it was at the start of the drag.
    private var initialDraggingItemIndex by mutableStateOf<Int?>(null)

    // The index where the item would be dropped if the gesture ended now.
    private var displacedItemIndex by mutableStateOf<Int?>(null)

    // A direct reference to the LazyListItemInfo of the item being dragged.
    private val currentDraggingItem: LazyListItemInfo?
        get() = draggingItemKey?.let { key ->
            lazyListState.layoutInfo.visibleItemsInfo.find { it.key == key }
        }

    fun onDragStart(offset: Offset) {
        lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { item -> offset.y.toInt() in item.offset..(item.offset + item.size) }
            ?.also {
                // Prevent dragging the first item (the Hero card)
                if (it.index == 0) return
                draggingItemKey = it.key
                initialDraggingItemIndex = it.index
                displacedItemIndex = it.index
            }
    }

    fun onDrag(offset: Offset) {
        draggingItemTranslationY += offset.y

        val draggingItem = currentDraggingItem ?: return
        val initialIndex = initialDraggingItemIndex ?: return
        val currentDisplacedIndex = displacedItemIndex ?: return

        val draggedItemTop = draggingItem.offset + draggingItemTranslationY
        val draggedItemBottom = draggedItemTop + draggingItem.size

        // Find the item we are currently hovering over.
        val targetItem = lazyListState.layoutInfo.visibleItemsInfo.find {
            it.key != draggingItemKey && // Not dragging over itself
                    it.index != 0 // And not over the hero card
        }

        if (targetItem == null) {
            // If not over any item, the target is the original position.
            displacedItemIndex = initialIndex
            return
        }

        // Determine if a swap should occur based on crossing the target's threshold.
        if (currentDisplacedIndex != targetItem.index) {
            val isMovingDown = targetItem.index > initialIndex
            val isMovingUp = targetItem.index < initialIndex

            if (isMovingDown) {
                // When moving down, swap when the top of the dragged item passes the top of the target.
                if (draggedItemTop > targetItem.offset) {
                    displacedItemIndex = targetItem.index
                }
            } else if (isMovingUp) {
                // When moving up, swap when the bottom of the dragged item passes the bottom of the target.
                if (draggedItemBottom < targetItem.offset + targetItem.size) {
                    displacedItemIndex = targetItem.index
                }
            }
        }
    }

    fun onDragEnd() {
        val initialIndex = initialDraggingItemIndex
        val displacedIndex = displacedItemIndex
        // Only call onMove if the item was actually moved to a new position.
        if (initialIndex != null && displacedIndex != null && initialIndex != displacedIndex) {
            onMove(initialIndex, displacedIndex)
        }

        // Reset all state variables.
        draggingItemKey = null
        initialDraggingItemIndex = null
        displacedItemIndex = null
        draggingItemTranslationY = 0f
    }

    fun checkForOverScroll(): Float {
        val draggingItem = currentDraggingItem ?: return 0f
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
