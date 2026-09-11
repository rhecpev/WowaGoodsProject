package com.example.wowagoodsproject.screen.news

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import android.widget.Toast
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.example.wowagoodsproject.component.encodeGoodsImagePath
import com.example.wowagoodsproject.db.news.NewsEntity
import com.example.wowagoodsproject.db.news.NewsType
import com.example.wowagoodsproject.navigation.TopBar
import com.example.wowagoodsproject.ui.theme.AppStyles
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NewsScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToSeries: (String) -> Unit = {},
    viewModel: NewsViewModel = viewModel()
) {
    val news by viewModel.news.collectAsState()
    val restorableIds by viewModel.restorableIds.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    // 화면에 들어오면 읽음 처리한다.
    LaunchedEffect(Unit) { viewModel.markAllRead() }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("소식 전체 삭제") },
            text = { Text("소식 ${news.size}개를 모두 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAll()
                    showClearDialog = false
                }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("취소") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = "소식",
            onBack = onNavigateBack,
            actions = {
                if (news.isNotEmpty()) {
                    TextButton(onClick = { showClearDialog = true }) { Text("전체 삭제") }
                }
            }
        )

        if (news.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "새로운 소식이 없습니다",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(AppStyles.paddingSmall))
                    Text(
                        text = "선호 캐릭터의 신규 굿즈와 보유·구매예정 굿즈의 변경 사항이 여기에 쌓입니다",
                        style = AppStyles.textCardSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = AppStyles.paddingSmall),
                verticalArrangement = Arrangement.spacedBy(AppStyles.paddingSmall)
            ) {
                items(news, key = { it.newsId }) { item ->
                    NewsCard(
                        news = item,
                        timeText = dateFormat.format(Date(item.newsTime)),
                        canRestore = item.newsChangedId in restorableIds,
                        onGoClick = { onNavigateToSeries(item.newsSeries) },
                        onRestore = { viewModel.restoreStatus(item) },
                        onDelete = { viewModel.delete(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NewsCard(
    news: NewsEntity,
    timeText: String,
    canRestore: Boolean,
    onGoClick: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val isChanged = news.type == NewsType.CHANGED_GOODS
    val accent = if (isChanged) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val encodedPath = encodeGoodsImagePath(news.newsImgUrl)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppStyles.paddingMedium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(AppStyles.paddingMedium)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isChanged) "변경 확인 필요" else "신규 굿즈",
                    style = AppStyles.textCardSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Spacer(modifier = Modifier.width(AppStyles.paddingSmall))
                Text(
                    text = timeText,
                    style = AppStyles.textCardSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "소식 삭제",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppStyles.paddingSmall))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 64.dp, height = 72.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(
                            model = if (encodedPath.isNotEmpty()) encodedPath else null
                        ),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.width(AppStyles.paddingMedium))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = news.newsChara,
                        style = AppStyles.textCardTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = news.newsCategory,
                        style = AppStyles.textCardSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = news.newsSeries,
                        style = AppStyles.textCardSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (news.newsPrice.isNotBlank()) {
                        Text(
                            text = news.newsPrice,
                            style = AppStyles.textCardSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                TextButton(onClick = onGoClick) { Text("바로가기") }
            }

            // 변경 소식은 같은 카드 아랫줄에 무엇이 바뀌었는지 적는다.
            if (news.detailLines.isNotEmpty()) {
                Spacer(modifier = Modifier.height(AppStyles.paddingSmall))
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(AppStyles.paddingSmall))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    news.detailLines.forEach { line ->
                        Text(
                            text = "· $line",
                            style = AppStyles.textCardSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (canRestore) {
                    Spacer(modifier = Modifier.height(AppStyles.paddingSmall))
                    OutlinedButton(
                        onClick = onRestore,
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("이전 상태로 되돌리기") }
                }
            }
        }
    }
}
