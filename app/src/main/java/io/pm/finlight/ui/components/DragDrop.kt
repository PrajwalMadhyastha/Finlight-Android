// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/components/DragDrop.kt
// REASON: REFACTOR - Implemented a live 'float and swap' drag-and-drop behavior.
// The logic now triggers a swap when the center of the dragged item passes the
// center of a target item. A translationY correction is applied post-swap to
// ensure the dragged item remains perfectly aligned with the user's finger,
// eliminating any visual 'jerk' or jumping and providing a smooth, animated
// reordering experience.
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

        // The current visual center of the item being dragged.
        val draggedItemCenterY = draggingItem.offset + draggingItemTranslationY + (draggingItem.size / 2f)

        // Find the target item by checking which item's vertical range the dragged item's center is currently in.
        val targetItem = lazyListState.layoutInfo.visibleItemsInfo.find {
            it.key != draggingItemKey && it.index != 0 &&
                    draggedItemCenterY in it.offset.toFloat()..(it.offset + it.size).toFloat()
        }

        if (targetItem != null && targetItem.index != draggingItemIndex) {
            // A swap is needed.
            // Calculate the offset difference between the two items' current positions.
            val offsetDiff = targetItem.offset - draggingItem.offset

            // Trigger the state update to reorder the list.
            onMove(draggingItemIndex, targetItem.index)

            // Immediately apply a correction to the translation.
            // This counteracts the visual jump that would otherwise occur when the
            // LazyColumn recomposes and moves the item's base position.
            draggingItemTranslationY -= offsetDiff
        }
    }

    fun onDragEnd() {
        // Reset all state variables.
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
