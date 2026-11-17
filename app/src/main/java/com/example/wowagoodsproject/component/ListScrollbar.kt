package com.example.wowagoodsproject.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 긴 목록 오른쪽에 두는 얇은 스크롤바. 목록이 넘칠 때만 보인다(안 넘쳐도 자리는 지켜 줄 폭이 흔들리지 않는다).
 * 막대(강조색 세로 막대)를 끌거나 트랙을 누르면 그 위치로 바로 옮긴다. 앱의 선 스타일에 맞춰 트랙은 2dp 선이다.
 */
@Composable
fun ListScrollbar(state: LazyListState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val canScroll by remember { derivedStateOf { state.canScrollForward || state.canScrollBackward } }
    // 보이는 칸 수와 지금 위치(칸 단위, 소수점으로 칸 안 위치까지)
    val metrics by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            val visible = info.visibleItemsInfo
            val firstSize = visible.firstOrNull()?.size?.takeIf { it > 0 } ?: 1
            val position = state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset.toFloat() / firstSize
            Triple(total, visible.size.coerceAtLeast(1), position)
        }
    }

    BoxWithConstraints(modifier = modifier.width(16.dp).fillMaxHeight()) {
        if (!canScroll) return@BoxWithConstraints
        val (total, visible, position) = metrics
        val trackPx = with(density) { maxHeight.toPx() }
        val thumbPx = (trackPx * visible / total).coerceAtLeast(with(density) { 28.dp.toPx() }).coerceAtMost(trackPx)
        val scrollable = (total - visible).coerceAtLeast(1)
        val offsetPx = (trackPx - thumbPx) * (position / scrollable).coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(16.dp)
                .pointerInput(total, visible, trackPx, thumbPx) {
                    // 누른 곳·끄는 곳에 막대 가운데가 오도록 그 위치의 칸으로 옮긴다.
                    fun jump(y: Float) {
                        val f = ((y - thumbPx / 2f) / (trackPx - thumbPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        scope.launch { state.scrollToItem((f * scrollable).toInt()) }
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        jump(down.position.y)
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.pressed) {
                                jump(change.position.y)
                                change.consume()
                            }
                        } while (change.pressed)
                    }
                }
        ) {
            // 트랙: 가운데 2dp 선
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            // 막대
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = with(density) { offsetPx.toDp() })
                    .width(4.dp)
                    .height(with(density) { thumbPx.toDp() })
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}
