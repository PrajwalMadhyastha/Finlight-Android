// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/components/DragDrop.kt
// REASON: REFACTOR - The drag-and-drop logic has been rewritten to be
// stable and smooth. The dragged item now visually floats over the static list,
// and the actual reordering of items only occurs once, when the drag gesture is
// released. The swap threshold is now correctly set to when an item completely
// passes an adjacent item, providing a predictable and intuitive experience.
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

    // The index of the item as it was at the start of the drag.
    private var initialDraggingItemIndex by mutableStateOf<Int?>(null)

    // The index where the item would be dropped if the gesture ended now.
    private var targetItemIndex by mutableStateOf<Int?>(null)

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
                initialDraggingItemIndex = it.index
                targetItemIndex = it.index // Initialize target to the starting index
            }
    }

    fun onDrag(offset: Offset) {
        draggingItemTranslationY += offset.y

        val draggingItem = currentDraggingItemInfo ?: return
        val initialIndex = initialDraggingItemIndex ?: return

        // Calculate the current visual center of the item being dragged.
        val draggedItemCenterY = draggingItem.offset + draggingItemTranslationY + (draggingItem.size / 2f)

        // Find the target item by checking which item's vertical range the dragged item's center is currently in.
        val targetItem = lazyListState.layoutInfo.visibleItemsInfo.find {
            it.key != draggingItemKey && it.index != 0 &&
                    draggedItemCenterY in it.offset.toFloat()..(it.offset + it.size).toFloat()
        }

        if (targetItem != null) {
            // If we are over a valid target, that's our potential new index.
            targetItemIndex = targetItem.index
        } else {
            // If not directly over another item, it will revert to its original spot if dropped.
            targetItemIndex = initialIndex
        }
    }

    fun onDragEnd() {
        val initialIndex = initialDraggingItemIndex
        val finalTargetIndex = targetItemIndex

        // Perform the single state update now that the drag is complete.
        if (initialIndex != null && finalTargetIndex != null && initialIndex != finalTargetIndex) {
            onMove(initialIndex, finalTargetIndex)
        }

        // Reset all state variables.
        draggingItemKey = null
        initialDraggingItemIndex = null
        targetItemIndex = null
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
