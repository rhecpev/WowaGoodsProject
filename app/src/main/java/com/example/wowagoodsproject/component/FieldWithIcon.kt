package com.example.wowagoodsproject.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/*
 * 입력 칸 왼쪽 아이콘 칸. 굿즈 등록·시리즈/장르/캐릭터 편집의 글자 칸·고르기 칸·날짜 칸·캐릭터 줄이 같이 쓴다.
 * 아이콘 아래 제목은 한 줄에 들어가는 짧은 이름으로 쓴다(메모, 이름, 수령일 …).
 */

/** 아이콘 + 제목 칸의 폭. 모든 칸의 값이 같은 자리에서 시작한다 */
private val ICON_COLUMN_WIDTH = 60.dp

/**
 * 입력 칸 왼쪽에 아이콘을 둔다. [leadingIcon] 이 없으면 칸만.
 * [label] 이 있으면 왼쪽 칸에 아이콘, 그 아래 제목을 두고(칸 안에는 값과 밑줄만), 아이콘은 값 글자 줄에 맞춘다.
 * [centerAligned] 면 왼쪽 칸을 오른쪽 내용의 가운데에 맞춘다(캐릭터 줄처럼 밑줄 없는 칸).
 * 굿즈 등록의 모든 칸(글자 칸·고르기 칸·캐릭터)이 같은 자리·크기를 쓴다.
 */
@Composable
fun FieldWithIcon(
    leadingIcon: (@Composable () -> Unit)?,
    label: String? = null,
    centerAligned: Boolean = false,
    content: @Composable () -> Unit
) {
    if (leadingIcon == null) {
        content()
        return
    }
    if (label != null) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = if (centerAligned) androidx.compose.ui.Alignment.CenterVertically else androidx.compose.ui.Alignment.Top
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .width(ICON_COLUMN_WIDTH)
                    .padding(end = 8.dp, top = if (centerAligned) 0.dp else 10.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                    Box(modifier = Modifier.size(24.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { leadingIcon() }
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Box(modifier = Modifier.weight(1f)) { content() }
        }
        return
    }
    androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
        Box(
            modifier = Modifier.padding(end = 14.dp, bottom = 12.dp).size(24.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                leadingIcon()
            }
        }
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

