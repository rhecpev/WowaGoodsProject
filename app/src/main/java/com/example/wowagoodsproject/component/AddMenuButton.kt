package com.example.wowagoodsproject.component

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 상단바 오른쪽 '+' 단추. 누르면 아래로 [굿즈 · 시리즈] 메뉴가 열려 둘 중 하나를 추가한다.
 * [onSeries] 가 null 이면 '+' 가 바로 굿즈 등록이다.
 */
@Composable
fun AddMenuButton(onGoods: () -> Unit, onSeries: (() -> Unit)?) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { if (onSeries == null) onGoods() else open = true }) {
            Icon(Icons.Default.Add, contentDescription = "추가", tint = MaterialTheme.colorScheme.primary)
        }
        if (onSeries != null) {
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    text = { Text("굿즈") },
                    leadingIcon = { Icon(Icons.Outlined.Redeem, contentDescription = null) },
                    onClick = { open = false; onGoods() }
                )
                DropdownMenuItem(
                    text = { Text("시리즈") },
                    leadingIcon = { Icon(Icons.Outlined.CollectionsBookmark, contentDescription = null) },
                    onClick = { open = false; onSeries() }
                )
            }
        }
    }
}
