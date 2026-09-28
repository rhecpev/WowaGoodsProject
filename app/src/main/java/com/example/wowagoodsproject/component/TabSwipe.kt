package com.example.wowagoodsproject.component

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** 손가락을 이만큼 이상 옆으로 밀어야 탭이 넘어간다. */
private val TAB_SWIPE_THRESHOLD = 64.dp

/**
 * 좌우로 밀어서 세부 탭을 옮기는 제스처.
 * 오른쪽으로 밀면 이전 탭, 왼쪽으로 밀면 다음 탭. 손을 뗄 때 민 거리를 보고 한 칸만 옮긴다.
 * 가로 스크롤 목록(LazyRow 등)이 먼저 가져간 드래그에는 반응하지 않는다.
 */
fun Modifier.swipeToChangeTab(
    selectedIndex: Int,
    tabCount: Int,
    onSelect: (Int) -> Unit
): Modifier = composed {
    val thresholdPx = with(LocalDensity.current) { TAB_SWIPE_THRESHOLD.toPx() }
    val currentIndex by rememberUpdatedState(selectedIndex)
    val currentCount by rememberUpdatedState(tabCount)
    val currentOnSelect by rememberUpdatedState(onSelect)

    pointerInput(Unit) {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                when {
                    total > thresholdPx && currentIndex > 0 -> currentOnSelect(currentIndex - 1)
                    total < -thresholdPx && currentIndex < currentCount - 1 -> currentOnSelect(currentIndex + 1)
                }
            },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                total += dragAmount
            }
        )
    }
}
