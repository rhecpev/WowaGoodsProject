package com.example.wowagoodsproject.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

const val GRID_COLUMNS_MIN = 1
const val GRID_COLUMNS_MAX = 10

/**
 * 이 화면의 그리드 한 줄 개수.
 * 사용자가 정한 값이 있으면 화면 방향과 상관없이 그 개수 그대로 놓고, 없으면 화면 너비에 맞춘다.
 */
@Composable
fun rememberGridColumns(listModeViewModel: ListModeViewModel, widthSizeClass: WindowWidthSizeClass): Int {
    val saved by listModeViewModel.gridColumns.collectAsState()
    if (saved in GRID_COLUMNS_MIN..GRID_COLUMNS_MAX) return saved
    return when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> 2
        WindowWidthSizeClass.Medium -> 3
        else -> 4
    }
}

/** 그리드 보기일 때만 나오는 한 줄 개수 조절 줄. 필터들 아래, 목록 바로 위에 둔다. */
@Composable
fun GridColumnsBar(listModeViewModel: ListModeViewModel, widthSizeClass: WindowWidthSizeClass) {
    val isGridMode by listModeViewModel.isGridMode.collectAsState()
    if (!isGridMode) return
    val columns = rememberGridColumns(listModeViewModel, widthSizeClass)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.GridView,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        // 숫자가 두 자리가 되어도 슬라이더가 밀리지 않게 폭을 잡아 둔다.
        Text(
            text = "한 줄 ${columns}개",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp)
        )
        Slider(
            value = columns.toFloat(),
            onValueChange = { listModeViewModel.setGridColumns(it.roundToInt()) },
            valueRange = GRID_COLUMNS_MIN.toFloat()..GRID_COLUMNS_MAX.toFloat(),
            steps = GRID_COLUMNS_MAX - GRID_COLUMNS_MIN - 1,
            modifier = Modifier.weight(1f)
        )
    }
}
