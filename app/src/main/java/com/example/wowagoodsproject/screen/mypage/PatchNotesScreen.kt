package com.example.wowagoodsproject.screen.mypage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = "업데이트 이력",
            action = {
                TextButton(onClick = onNavigateBack) {
                    Text("뒤로")
                }
            }
        )

        if (patchNotes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "업데이트 이력이 없습니다",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(patchNotes) { note ->
                    val isExpanded = expandedIds[note.patchId] ?: false
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedIds[note.patchId] = !isExpanded }
                                .padding(AppStyles.paddingMedium),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dateFormat.format(Date(note.patchTime)),
                                style = AppStyles.textCardTitle
                            )
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null
                            )
                        }
                        if (isExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = AppStyles.paddingLarge,
                                        end = AppStyles.paddingMedium,
                                        bottom = AppStyles.paddingMedium
                                    ),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                note.patchContent.split("\n").forEach { line ->
                                    Text(
                                        text = line,
                                        style = AppStyles.textCardSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}