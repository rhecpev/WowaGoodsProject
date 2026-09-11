package com.example.wowagoodsproject.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 칩 하나 = 걸려 있는 필터 하나. 누르면 그 필터만 해제된다. */
data class ActiveFilter(val label: String, val onRemove: () -> Unit)

/**
 * 지금 걸려 있는 필터를 상단 바 아래에 칩으로 보여준다 (쇼핑/메일 앱의 필터 칩 패턴).
 * 필터가 없으면 아무것도 그리지 않는다.
 */
@Composable
fun ActiveFilterChips(
    filters: List<ActiveFilter>,
    onClearAll: (() -> Unit)? = null
) {
    if (filters.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        filters.forEach { filter ->
            InputChip(
                selected = true,
                onClick = filter.onRemove,
                label = { Text(filter.label, maxLines = 1) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "${filter.label} 필터 해제",
                        modifier = Modifier.size(InputChipDefaults.IconSize)
                    )
                }
            )
        }
        if (onClearAll != null && filters.size > 1) {
            TextButton(onClick = onClearAll) { Text("모두 해제") }
        }
    }
}
