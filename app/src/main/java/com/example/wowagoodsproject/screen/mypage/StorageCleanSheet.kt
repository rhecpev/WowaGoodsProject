package com.example.wowagoodsproject.screen.mypage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.wowagoodsproject.component.FilterDialogFrame
import com.example.wowagoodsproject.ui.theme.AppStyles

/**
 * 용량 정리 시트. 지워도 되는 항목마다 개수와 확보될 크기를 보여 주고, 고른 것만 확인 후 지운다.
 * 지울 거리가 없는 항목은 고를 수 없다. 처음 열 때는 지울 거리가 있는 항목을 모두 골라 둔다.
 */
@Composable
fun StorageCleanSheet(
    items: List<CleanItem>?,
    cleaning: Boolean,
    onClean: (Set<CleanTarget>) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf<Set<CleanTarget>?>(null) }
    // 처음 잰 결과가 오면 지울 거리가 있는 항목을 모두 골라 둔다. 다시 잰 뒤에는 지울 거리가 없어진 항목을 뺀다.
    LaunchedEffect(items) {
        if (items == null) return@LaunchedEffect
        val available = items.filter { it.bytes > 0 || it.count > 0 }.map { it.target }.toSet()
        selected = selected?.intersect(available) ?: available
    }
    val chosen = selected.orEmpty()
    val chosenBytes = items.orEmpty().filter { it.target in chosen }.sumOf { it.bytes }
    val anyEstimated = items.orEmpty().any { it.target in chosen && it.estimated }
    var confirm by remember { mutableStateOf(false) }

    if (confirm) {
        AlertDialog(
            shape = RectangleShape,
            onDismissRequest = { confirm = false },
            title = { Text("선택한 데이터를 지울까요?") },
            text = {
                Text(
                    chosen.sortedBy { it.ordinal }.joinToString("\n") { "· ${it.title}" } +
                            "\n\n지운 기록은 되돌릴 수 없어요. 보유 현황·2차창작 굿즈·선호 캐릭터는 지워지지 않아요."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { confirm = false; onClean(chosen) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("지우기") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("취소") } }
        )
    }

    FilterDialogFrame(title = "용량 정리", onDismiss = onDismiss, onClear = null) {
        Text(
            text = "계속 쌓이거나 다시 받을 수 있는 데이터만 보여 줘요. 굿즈 보유 현황은 지워지지 않아요.",
            style = AppStyles.textCardSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (items == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(items, key = { it.target.name }) { item ->
                    val enabled = item.bytes > 0 || item.count > 0
                    val checked = item.target in chosen
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = enabled && !cleaning) {
                                    selected = if (checked) chosen - item.target else chosen + item.target
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked && enabled, onCheckedChange = null, enabled = enabled)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.target.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = item.target.description,
                                    style = AppStyles.textCardSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (!enabled) "없음"
                                    else (if (item.estimated) "약 " else "") + StorageCleaner.formatBytes(item.bytes),
                                    style = AppStyles.textCardSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (enabled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (item.count >= 0 && enabled) {
                                    Text(
                                        text = "${item.count}개",
                                        style = AppStyles.textCardSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { confirm = true },
                enabled = chosen.isNotEmpty() && !cleaning,
                shape = RectangleShape,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (cleaning) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        if (chosen.isEmpty()) "지울 항목을 고르세요"
                        else "선택한 ${chosen.size}개 지우기 (${if (anyEstimated) "약 " else ""}${StorageCleaner.formatBytes(chosenBytes)})"
                    )
                }
            }
        }
    }
}
