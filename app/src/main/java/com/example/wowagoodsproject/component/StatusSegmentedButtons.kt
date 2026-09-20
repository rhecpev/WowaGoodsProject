package com.example.wowagoodsproject.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.wowagoodsproject.ui.theme.AppStyles

fun statusColor(status: GoodsStatus): Color = when (status) {
    GoodsStatus.GOTTEN -> AppStyles.colorGotten
    GoodsStatus.PENDING -> AppStyles.colorPending
    GoodsStatus.NOT_GOTTEN -> AppStyles.colorNotGotten
}

/**
 * 미보유 / 구매예정 / 보유 중 하나를 고르는 세그먼트 버튼.
 * "보유로 변경", "구매예정 취소" 같은 토글 버튼 여러 개 대신 현재 상태를 한눈에 보고 바로 바꿀 수 있다.
 *
 * @param current 현재 상태. null 이면 아무 칸도 선택되지 않는다(여러 굿즈를 한 번에 다룰 때 상태가 섞인 경우).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusSegmentedButtons(
    current: GoodsStatus?,
    onSelect: (GoodsStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        GoodsStatus.NOT_GOTTEN to "미보유",
        GoodsStatus.PENDING to "구매예정",
        GoodsStatus.GOTTEN to "보유"
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (status, label) ->
            val color = statusColor(status)
            SegmentedButton(
                selected = current == status,
                onClick = { if (current != status) onSelect(status) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = color.copy(alpha = 0.16f),
                    activeContentColor = color,
                    activeBorderColor = color,
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                label = { Text(label, maxLines = 1) }
            )
        }
    }
}

/**
 * 세그먼트에서 고른 목표 상태를 기존 토글 콜백으로 옮긴다.
 * (toggleGotten: 보유 ↔ 미보유 / 그 외 → 보유, setPending: 구매예정 ↔ 미보유 / 그 외 → 구매예정)
 */
fun applyStatusChange(
    current: GoodsStatus,
    target: GoodsStatus,
    onToggleGotten: () -> Unit,
    onSetPending: () -> Unit
) {
    when (target) {
        current -> Unit
        GoodsStatus.GOTTEN -> onToggleGotten()
        GoodsStatus.PENDING -> onSetPending()
        GoodsStatus.NOT_GOTTEN -> if (current == GoodsStatus.GOTTEN) onToggleGotten() else onSetPending()
    }
}
