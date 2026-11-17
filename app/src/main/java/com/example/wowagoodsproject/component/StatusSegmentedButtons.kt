package com.example.wowagoodsproject.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.wowagoodsproject.ui.theme.AppStyles

fun statusColor(status: GoodsStatus): Color = when (status) {
    GoodsStatus.GOTTEN -> AppStyles.colorGotten
    GoodsStatus.PENDING -> AppStyles.colorPending
    GoodsStatus.NOT_GOTTEN -> AppStyles.colorNotGotten
}

/**
 * 미보유 / 구매예정 / 보유 중 하나를 고르는 선 탭 한 줄.
 * "보유로 변경", "구매예정 취소" 같은 토글 버튼 여러 개 대신 현재 상태를 한눈에 보고 바로 바꿀 수 있다.
 * 박스 없이 칸을 똑같이 나누고, 고른 칸만 그 상태 색으로 글자와 밑줄을 칠한다. 아래에 얇은 기준선을 깐다.
 *
 * @param current 현재 상태. null 이면 아무 칸도 선택되지 않는다(여러 굿즈를 한 번에 다룰 때 상태가 섞인 경우).
 */
@Composable
fun StatusSegmentedButtons(
    current: GoodsStatus?,
    onSelect: (GoodsStatus) -> Unit,
    modifier: Modifier = Modifier,
    /** false 면 미보유 / 보유 두 칸만 보여 준다(구매 정보를 받지 않는 곳). */
    showPending: Boolean = true
) {
    val options = listOfNotNull(
        GoodsStatus.NOT_GOTTEN to "미보유",
        if (showPending) GoodsStatus.PENDING to "구매예정" else null,
        GoodsStatus.GOTTEN to "보유"
    )
    Column(modifier = modifier.fillMaxWidth().selectableGroup()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            options.forEach { (status, label) ->
                val selected = current == status
                val color = statusColor(status)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { if (!selected) onSelect(status) }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (selected) color else Color.Transparent)
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
