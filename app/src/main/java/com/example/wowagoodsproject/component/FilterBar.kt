package com.example.wowagoodsproject.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FilterBar(
    filterType: FilterType,
    onFilterChange: (FilterType) -> Unit,
    goodsList: List<GoodsItem>
) {
    val allCount = goodsList.size
    val gottenCount = goodsList.count { it.isGotten }
    val notGottenCount = goodsList.count { !it.isGotten }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = filterType == FilterType.ALL,
            onClick = { onFilterChange(FilterType.ALL) },
            label = { Text("전체 ($allCount)") }
        )
        FilterChip(
            selected = filterType == FilterType.GOTTEN,
            onClick = { onFilterChange(FilterType.GOTTEN) },
            label = { Text("보유 ($gottenCount)") }
        )
        FilterChip(
            selected = filterType == FilterType.NOT_GOTTEN,
            onClick = { onFilterChange(FilterType.NOT_GOTTEN) },
            label = { Text("미보유 ($notGottenCount)") }
        )
    }
}