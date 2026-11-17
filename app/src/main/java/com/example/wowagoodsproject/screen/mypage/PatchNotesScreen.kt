package com.example.wowagoodsproject.screen.mypage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.wowagoodsproject.component.EmptyState
import com.example.wowagoodsproject.navigation.TopBar
import com.example.wowagoodsproject.ui.theme.AppStyles
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PatchNotesScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: PatchNotesViewModel = viewModel()
) {
    val patchNotes by viewModel.patchNotes.collectAsState()
    val expandedIds = remember { mutableStateMapOf<Int, Boolean>() }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            // 앱 전체 기준: 팝업도 둥근 모서리 없이 각지게.
            shape = RectangleShape,
            onDismissRequest = { showClearDialog = false },
            title = { Text("업데이트 이력 삭제") },
            text = { Text("업데이트 이력 ${patchNotes.size}개를 모두 지울까요?\n지운 이력은 되돌릴 수 없어요. 굿즈 데이터는 그대로예요.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.clearAll(); showClearDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("취소") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = "업데이트 이력",
            onBack = onNavigateBack,
            actions = {
                // 이력이 쌓이면 용량을 차지하므로 한 번에 비울 수 있게 한다.
                if (patchNotes.isNotEmpty()) {
                    TextButton(
                        onClick = { showClearDialog = true },
                        shape = RectangleShape,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("전체 삭제", fontWeight = FontWeight.Bold) }
                }
            }
        )

        if (patchNotes.isEmpty()) {
            EmptyState(
                icon = Icons.Default.History,
                title = "업데이트 이력이 없습니다",
                message = "데이터가 바뀌면 여기에 기록됩니다"
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(patchNotes, key = { it.patchId }) { note ->
                    val isExpanded = expandedIds[note.patchId] ?: false
                    val lines = remember(note.patchContent) { note.patchContent.split("\n") }
                    val entryCount = lines.count { it.startsWith("[") }
                    val arrowRotation by animateFloatAsState(
                        targetValue = if (isExpanded) 180f else 0f,
                        label = "arrow"
                    )

                    // 박스 없이 줄과 아래 얇은 선으로 나눈다.
                    run {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedIds[note.patchId] = !isExpanded }
                                    .padding(horizontal = 4.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = dateFormat.format(Date(note.patchTime)),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "변경 ${entryCount}건",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isExpanded) "접기" else "펼치기",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.rotate(arrowRotation)
                                )
                            }
                            AnimatedVisibility(visible = isExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 4.dp, end = 4.dp, bottom = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    lines.forEach { line -> PatchLine(line) }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

/** "[굿즈 추가] 내용" 이면 앞의 태그를 색 배지로, 들여 쓴 줄(변경 전 → 후)은 작은 글씨로 보여준다. */
@Composable
private fun PatchLine(line: String) {
    val tagEnd = line.indexOf(']')
    if (line.startsWith("[") && tagEnd > 0) {
        val tag = line.substring(1, tagEnd)
        val body = line.substring(tagEnd + 1).trim()
        val color = when {
            tag.endsWith("추가") -> AppStyles.colorGotten
            tag.endsWith("삭제") -> AppStyles.colorNotGotten
            else -> MaterialTheme.colorScheme.primary
        }
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = tag,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                modifier = Modifier.padding(vertical = 1.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    } else if (line.isNotBlank()) {
        Text(
            text = line.trim(),
            style = AppStyles.textCardSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
