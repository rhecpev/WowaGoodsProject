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

/** 보유 상태별 필터. "전체 12 · 보유 3 ..." 처럼 한 줄짜리 칩을 가로 스크롤로 늘어놓는다. */
@Composable
fun FilterBar(
    filterType: FilterType,
    onFilterChange: (FilterType) -> Unit,
    goodsList: List<GoodsItem>
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
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (type, label, count) ->
            val selected = filterType == type
            FilterChip(
                selected = selected,
                onClick = { onFilterChange(type) },
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
    }
}
