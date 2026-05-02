// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/components/PagerUtils.kt
// REASON: NEW FILE - Centralized the pagerTabIndicatorOffset helper function
// to resolve conflicting overload errors and avoid code duplication.
// =================================================================================
package io.pm.finlight.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.TabPosition
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.Dp
import kotlin.math.absoluteValue

private fun lerp(
    start: Dp,
    stop: Dp,
    fraction: Float,
): Dp {
    return Dp(start.value + (stop.value - start.value) * fraction)
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pagerTabIndicatorOffset(
    pagerState: PagerState,
    tabPositions: List<TabPosition>,
): Modifier =
    composed {
        if (tabPositions.isEmpty()) {
            this
        } else {
            val currentPage = pagerState.currentPage
            val fraction = pagerState.currentPageOffsetFraction.absoluteValue

            val currentTab = tabPositions[currentPage]
            val nextTab = tabPositions.getOrNull(currentPage + 1)

            val targetIndicatorOffset =
                if (nextTab != null) {
                    lerp(currentTab.left, nextTab.left, fraction)
                } else {
                    currentTab.left
                }

            val indicatorWidth =
                if (nextTab != null) {
                    lerp(currentTab.width, nextTab.width, fraction)
                } else {
                    currentTab.width
                }

            this.fillMaxWidth()
                .wrapContentSize(Alignment.BottomStart)
                .offset(x = targetIndicatorOffset)
                .width(indicatorWidth)
        }
    }
