package com.example.wowagoodsproject.navigation

import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.wowagoodsproject.component.isTypingInLandscape

/**
 * 화면 상단 바. [뒤로] 제목(+부제) ........ 액션 아이콘들
 * 뒤로가기는 텍스트 버튼 대신 왼쪽 화살표로, 액션은 오른쪽 아이콘 버튼으로 둔다.
 * 가로 화면에서 키보드가 올라와 있는 동안은 검색창과 결과가 쓸 높이를 위해 숨긴다([hideWhileTyping]).
 * 헤더에 저장 같은 주요 버튼이 있는 화면은 false 로 넘겨 그대로 둔다.
 */
@Composable
fun TopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    hideWhileTyping: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {}
) {
    if (hideWhileTyping && isTypingInLandscape()) return
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(start = if (onBack != null) 4.dp else 20.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "뒤로",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onBackground
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

/** 상단 바 아이콘 버튼. active 면 강조색 + 작은 점 배지로 "켜져 있음"을 알린다. */
@Composable
fun TopBarAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false
) {
    IconButton(onClick = onClick) {
        BadgedBox(
            badge = {
                if (active) Badge(containerColor = MaterialTheme.colorScheme.primary)
            }
        ) {
            // 아이콘이 바뀌면(보기 방식 전환 등) 반 바퀴 돌며 바뀐다
            androidx.compose.animation.AnimatedContent(
                targetState = icon,
                transitionSpec = {
                    (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(160)) +
                        androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(160), initialScale = 0.6f))
                        .togetherWith(
                            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)) +
                                androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(120), targetScale = 0.6f)
                        )
                },
                label = "topBarIcon"
            ) { shown ->
                // 들어올 때 -90도에서, 나갈 때 90도로 돌아간다
                val turn by transition.animateFloat(label = "turn") { state ->
                    when (state) {
                        androidx.compose.animation.EnterExitState.PreEnter -> -90f
                        androidx.compose.animation.EnterExitState.Visible -> 0f
                        androidx.compose.animation.EnterExitState.PostExit -> 90f
                    }
                }
                Icon(
                    imageVector = shown,
                    contentDescription = contentDescription,
                    tint = if (active) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    modifier = Modifier.graphicsLayer { rotationZ = turn }
                )
            }
        }
    }
}
