package com.example.wowagoodsproject.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 보유 상태별 필터. "전체 12 · 보유 3 ..." 처럼 한 줄짜리 칩을 가로 스크롤로 늘어놓는다.
 * [onToggleSelection] 을 넘기면 맨 왼쪽에 다중 선택 버튼을 둔다.
 */
@Composable
fun FilterBar(
    filterType: FilterType,
    onFilterChange: (FilterType) -> Unit,
    goodsList: List<GoodsItem>,
    selectionMode: Boolean = false,
    onToggleSelection: (() -> Unit)? = null
) {
    val options = listOf(
        Triple(FilterType.ALL, "전체", goodsList.size),
        Triple(FilterType.GOTTEN, "보유", goodsList.count { it.isGotten }),
        Triple(FilterType.NOT_GOTTEN, "미보유", goodsList.count { it.status == GoodsStatus.NOT_GOTTEN }),
        Triple(FilterType.PENDING, "구매예정", goodsList.count { it.status == GoodsStatus.PENDING })
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onToggleSelection != null) {
            MultiSelectToggle(selectionMode = selectionMode, onToggle = onToggleSelection)
        }
        options.forEach { (type, label, count) ->
            CountFilterChip(
                label = label,
                count = count,
                selected = filterType == type,
                onClick = { onFilterChange(type) }
            )
        }
    }
}

/**
 * 구매예정 목록의 구매 정보 필터. [FilterBar] 와 같은 한 줄짜리 칩이지만 여러 개를 함께 켤 수 있고,
 * 켠 조건을 모두 만족하는 굿즈만 남는다. "전체" 를 누르면 모두 끈다.
 * 숫자는 다른 구매 정보 필터를 걸기 전 [goodsList] 에서 센 값이다.
 */
@Composable
fun PendingInfoFilterBar(
    selected: Set<PendingInfoFilter>,
    onToggle: (PendingInfoFilter) -> Unit,
    onClear: () -> Unit,
    goodsList: List<GoodsItem>,
    selectionMode: Boolean = false,
    onToggleSelection: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onToggleSelection != null) {
            MultiSelectToggle(selectionMode = selectionMode, onToggle = onToggleSelection)
        }
        CountFilterChip(
            label = "전체",
            count = goodsList.size,
            selected = selected.isEmpty(),
            onClick = onClear
        )
        PendingInfoFilter.entries.forEach { filter ->
            CountFilterChip(
                label = filter.label,
                count = goodsList.count(filter.matches),
                selected = filter in selected,
                onClick = { onToggle(filter) }
            )
        }
    }
}

@Composable
private fun CountFilterChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, maxLines = 1)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$count",
                    maxLines = 1,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.primary
                )
            }
        }
    )
}
