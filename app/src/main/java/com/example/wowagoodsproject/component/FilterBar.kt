package com.example.wowagoodsproject.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun FilterBar(
    filterType: FilterType,
    onFilterChange: (FilterType) -> Unit,
    goodsList: List<GoodsItem>
) {
    val allCount = goodsList.size
    val gottenCount = goodsList.count { it.isGotten }
    val notGottenCount = goodsList.count { it.status == GoodsStatus.NOT_GOTTEN }
    val pendingCount = goodsList.count { it.status == GoodsStatus.PENDING }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = filterType == FilterType.ALL,
            onClick = { onFilterChange(FilterType.ALL) },
            modifier = Modifier.weight(1f),
            label = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("전체", maxLines = 1, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                    Text("($allCount)", maxLines = 1, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                }
            }
        )
        FilterChip(
            selected = filterType == FilterType.GOTTEN,
            onClick = { onFilterChange(FilterType.GOTTEN) },
            modifier = Modifier.weight(1f),
            label = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("보유", maxLines = 1, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                    Text("($gottenCount)", maxLines = 1, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                }
            }
        )
        FilterChip(
            selected = filterType == FilterType.NOT_GOTTEN,
            onClick = { onFilterChange(FilterType.NOT_GOTTEN) },
            modifier = Modifier.weight(1f),
            label = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("미보유", maxLines = 1, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                    Text("($notGottenCount)", maxLines = 1, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                }
            }
        )
        FilterChip(
            selected = filterType == FilterType.PENDING,
            onClick = { onFilterChange(FilterType.PENDING) },
            modifier = Modifier.weight(1f),
            label = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("구매예정", maxLines = 1, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                    Text("($pendingCount)", maxLines = 1, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                }
            }
        )
    }
}