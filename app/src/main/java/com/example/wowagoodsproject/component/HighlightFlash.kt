package com.example.wowagoodsproject.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val FLASH_COUNT = 3

/**
 * [active] 가 켜지면 항목 위에 강조색을 몇 번 깜빡여 눈에 띄게 한다.
 * 다 깜빡이면 [onFinished] 를 부른다.
 */
@Composable
fun Modifier.highlightFlash(active: Boolean, onFinished: () -> Unit = {}): Modifier {
    val alpha = remember { Animatable(0f) }
    val currentOnFinished by rememberUpdatedState(onFinished)
    LaunchedEffect(active) {
        if (!active) {
            alpha.snapTo(0f)
            return@LaunchedEffect
        }
        // 스크롤이 거의 멈춘 뒤에 깜빡이기 시작한다.
        delay(300)
        repeat(FLASH_COUNT) {
            alpha.animateTo(1f, tween(350))
            alpha.animateTo(0f, tween(450))
        }
        currentOnFinished()
    }
    val color = MaterialTheme.colorScheme.primary
    return drawWithContent {
        drawContent()
        val a = alpha.value
        if (a > 0f) {
            drawRect(color.copy(alpha = 0.25f * a))
            drawRect(color.copy(alpha = a), style = Stroke(width = 3.dp.toPx()))
        }
    }
}
