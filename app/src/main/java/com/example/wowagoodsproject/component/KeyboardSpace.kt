package com.example.wowagoodsproject.component

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * 가로 화면에서 키보드가 올라와 있는지.
 * 가로 화면은 키보드가 높이의 절반 이상을 차지해 헤더·시트 제목까지 두면 검색창이나 결과가 밀려나므로,
 * 이때만 그런 머리 부분을 잠깐 숨기는 데 쓴다. 키보드를 내리면 다시 false 가 된다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun isTypingInLandscape(): Boolean {
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    return landscape && WindowInsets.isImeVisible
}
