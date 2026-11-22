package com.example.wowagoodsproject.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/** 이보다 낮은 가로 화면이면 고르기 창을 좌우로 나눈다 */
private const val SPLIT_MAX_HEIGHT_DP = 600

/**
 * 고르기 창(캐릭터 선택 등) 본문 배치. [FilterDialogFrame] 안에 둔다.
 * - 세로 화면: 위 [top](검색·고른 것·추가) → 가운데 [list](남은 높이 전부) → 아래 [bottom](완료)
 * - 낮은 가로 화면: 위·아래 조작을 왼쪽 칸에(넘치면 스크롤), 목록은 오른쪽 칸에 세로 전체 높이로.
 *   한 줄로 쌓으면 조작들이 높이를 다 먹어 목록이 0 높이가 되어 아예 안 보이기 때문이다.
 */
@Composable
fun ColumnScope.PickerSplitBody(
    top: @Composable ColumnScope.() -> Unit,
    list: @Composable (Modifier) -> Unit,
    bottom: @Composable ColumnScope.() -> Unit
) {
    val config = LocalConfiguration.current
    // 2번 모드의 오른쪽 칸 안이면 이미 화면 절반 폭이라 다시 나누지 않는다(위아래로 쌓는다).
    val split = config.screenWidthDp > config.screenHeightDp && config.screenHeightDp < SPLIT_MAX_HEIGHT_DP &&
        !LocalInSidePane.current
    if (split) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(modifier = Modifier.weight(0.42f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                top()
                Spacer(modifier = Modifier.height(12.dp))
                bottom()
            }
            list(Modifier.weight(0.58f).fillMaxHeight())
        }
    } else {
        top()
        list(Modifier.weight(1f).fillMaxWidth())
        Spacer(modifier = Modifier.height(12.dp))
        bottom()
    }
}
