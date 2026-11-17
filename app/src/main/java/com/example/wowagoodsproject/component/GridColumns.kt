package com.example.wowagoodsproject.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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

/**
 * 그리드 보기일 때만 나오는 한 줄 개수 조절 줄. 필터들 아래, 목록 바로 위에 둔다.
 * 앱의 선 스타일에 맞춰 두꺼운 둥근 트랙 대신 2dp 선 + 칸마다 작은 눈금, 손잡이는 강조색 세로 막대로 그린다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridColumnsBar(listModeViewModel: ListModeViewModel, widthSizeClass: WindowWidthSizeClass) {
    val isGridMode by listModeViewModel.isGridMode.collectAsState()
    if (!isGridMode) return
    val columns = rememberGridColumns(listModeViewModel, widthSizeClass)
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.outlineVariant
    val interaction = remember { MutableInteractionSource() }

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
            text = buildAnnotatedString {
                append("한 줄 ")
                withStyle(SpanStyle(color = active, fontWeight = FontWeight.Bold)) { append("$columns") }
                append("개")
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp)
        )
        Slider(
            value = columns.toFloat(),
            onValueChange = { listModeViewModel.setGridColumns(it.roundToInt()) },
            valueRange = GRID_COLUMNS_MIN.toFloat()..GRID_COLUMNS_MAX.toFloat(),
            steps = GRID_COLUMNS_MAX - GRID_COLUMNS_MIN - 1,
            interactionSource = interaction,
            thumb = {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 20.dp)
                        .background(active)
                )
            },
            track = { state ->
                val range = state.valueRange
                val fraction = ((state.value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                ) {
                    val y = size.height / 2f
                    val line = 2.dp.toPx()
                    val activeEnd = size.width * fraction
                    drawLine(inactive, Offset(0f, y), Offset(size.width, y), strokeWidth = line)
                    drawLine(active, Offset(0f, y), Offset(activeEnd, y), strokeWidth = line)
                    // 칸마다 작은 세로 눈금. 지나온 눈금은 강조색.
                    val count = GRID_COLUMNS_MAX - GRID_COLUMNS_MIN
                    val tick = 6.dp.toPx()
                    for (i in 0..count) {
                        val x = size.width * i / count
                        drawLine(
                            color = if (x <= activeEnd) active else inactive,
                            start = Offset(x, y - tick / 2f),
                            end = Offset(x, y + tick / 2f),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }
            },
            modifier = Modifier.weight(1f)
        )
    }
}
