package com.example.wowagoodsproject.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/*
 * 보기 방식 전환 애니메이션(초안). 그리드/리스트 전환과 배신 굿즈 장르 목록의 배경/아이콘 전환이 같이 쓴다.
 *
 * 움직임: 확대/축소(줌) 느낌.
 * - 더 작게, 더 많이 보는 쪽으로 바뀔 때(리스트 → 그리드, 배경 → 아이콘): 지금 화면이 살짝 줄어들며 사라지고,
 *   새 화면이 살짝 큰 데서 제자리로 줄어들며 나타난다(멀리서 보는 느낌).
 * - 반대(그리드 → 리스트, 아이콘 → 배경): 지금 화면이 살짝 커지며 사라지고, 새 화면이 살짝 작은 데서 커지며 나타난다(다가가는 느낌).
 * 나가는 화면은 빨리 사라지고(겹쳐 보이는 시간을 줄인다), 들어오는 화면은 조금 늦게 시작해 부드럽게 멈춘다.
 *
 * 값은 아래 상수만 고치면 모든 화면에 같이 반영된다.
 */

/** 들어오는 화면 시간(ms) */
const val VIEW_SWITCH_IN_MS = 260
/** 나가는 화면 시간(ms) */
const val VIEW_SWITCH_OUT_MS = 140
/** 들어오는 화면이 기다렸다 시작하는 시간(ms). 나가는 화면이 반쯤 사라진 뒤 나타나게 */
const val VIEW_SWITCH_IN_DELAY_MS = 60
/** 확대/축소 크기. 1 에서 이만큼 커지거나 작아진 데서 시작하고 끝난다 */
const val VIEW_SWITCH_ZOOM = 0.06f

/**
 * [zoomOut] 이 true 면 '더 작게, 더 많이 보는' 쪽으로 바뀌는 전환(리스트 → 그리드 등).
 */
fun viewSwitchTransform(zoomOut: Boolean): ContentTransform {
    val bigger = 1f + VIEW_SWITCH_ZOOM
    val smaller = 1f - VIEW_SWITCH_ZOOM
    val enter = fadeIn(tween(VIEW_SWITCH_IN_MS, VIEW_SWITCH_IN_DELAY_MS, LinearOutSlowInEasing)) +
        scaleIn(tween(VIEW_SWITCH_IN_MS, VIEW_SWITCH_IN_DELAY_MS, FastOutSlowInEasing), initialScale = if (zoomOut) bigger else smaller)
    val exit = fadeOut(tween(VIEW_SWITCH_OUT_MS)) +
        scaleOut(tween(VIEW_SWITCH_OUT_MS), targetScale = if (zoomOut) smaller else bigger)
    // 크기 변화는 애니메이션하지 않는다(목록은 늘 화면을 채운다).
    return ContentTransform(
        targetContentEnter = enter,
        initialContentExit = exit,
        sizeTransform = SizeTransform(clip = false) { _, _ -> tween(0) }
    )
}

/**
 * 그리드/리스트 목록을 바꿀 때 애니메이션으로 넘긴다. [content] 는 그릴 보기(true 면 그리드)를 받는다.
 * 나가는 화면과 들어오는 화면이 잠깐 함께 그려지므로, 스크롤 상태처럼 한 목록에만 붙어야 하는 것은
 * [content] 안에서 보기마다 따로 만든다.
 */
@Composable
fun GridListSwitch(
    isGridMode: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (grid: Boolean) -> Unit
) {
    AnimatedContent(
        targetState = isGridMode,
        modifier = modifier,
        transitionSpec = { viewSwitchTransform(zoomOut = targetState) },
        label = "gridListSwitch"
    ) { grid -> content(grid) }
}


