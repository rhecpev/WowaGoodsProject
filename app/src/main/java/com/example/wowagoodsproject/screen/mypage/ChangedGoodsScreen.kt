package com.example.wowagoodsproject.screen.mypage

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChangeCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.example.wowagoodsproject.component.EmptyState
import com.example.wowagoodsproject.component.GoodsDetailDialog
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.ProcessFilter
import com.example.wowagoodsproject.component.ProcessFilterBar
import com.example.wowagoodsproject.component.SetGoodsDetailDialog
import com.example.wowagoodsproject.component.encodeGoodsImagePath
import com.example.wowagoodsproject.component.findSetGoods
import com.example.wowagoodsproject.component.getSetComponents
import com.example.wowagoodsproject.component.purchaseInfo
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.navigation.TopBar
import com.example.wowagoodsproject.ui.theme.AppStyles
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 데이터 업데이트로 바뀐 보유·구매예정 굿즈를 모아 본다.
 * 소식은 지우거나 한 번에 200개까지만 쌓이지만, 이 기록은 사용자가 지우기 전까지 남는다.
 * 카드는 기록 당시가 아니라 굿즈의 지금 값으로 그리고, 여기서 다이얼로그로 고친 굿즈에는 확인 표시를 한다.
 */
@Composable
fun ChangedGoodsScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToGoods: (GoodsEntity) -> Unit = {},
    viewModel: ChangedGoodsViewModel = viewModel(),
    listState: LazyListState = rememberLazyListState()
) {
    val items by viewModel.items.collectAsState()
    val message by viewModel.message.collectAsState()
    val processFilter by viewModel.processFilter.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    var showClearCheckedDialog by remember { mutableStateOf(false) }
    val doneCount = items.count { it.record.isChecked }
    val filteredItems = when (processFilter) {
        ProcessFilter.ALL -> items
        ProcessFilter.DONE -> items.filter { it.record.isChecked }
        ProcessFilter.TODO -> items.filter { !it.record.isChecked }
    }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val context = LocalContext.current

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    ChangedGoodsDialogs(viewModel = viewModel, onSeriesClick = onNavigateToGoods)

    if (showClearCheckedDialog) {
        AlertDialog(
            onDismissRequest = { showClearCheckedDialog = false },
            title = { Text("체크완료 삭제") },
            text = { Text("처리한 기록 ${doneCount}개를 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteChecked()
                    showClearCheckedDialog = false
                }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCheckedDialog = false }) { Text("취소") }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("기록 전체 삭제") },
            text = { Text("변경 기록 ${items.size}개를 모두 삭제할까요?\n삭제하면 초기화된 상태를 되돌릴 수 없습니다.") },
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
            title = "변경된 굿즈",
            onBack = onNavigateBack,
            actions = {
                if (doneCount > 0) {
                    TextButton(onClick = { showClearCheckedDialog = true }) { Text("체크완료 삭제") }
                }
                if (items.isNotEmpty()) {
                    TextButton(onClick = { showClearDialog = true }) { Text("전체 삭제") }
                }
            }
        )

        if (items.isNotEmpty()) {
            ProcessFilterBar(
                selected = processFilter,
                onSelect = { viewModel.setProcessFilter(it) },
                totalCount = items.size,
                doneCount = doneCount
            )
        }

        if (items.isEmpty()) {
            EmptyState(
                icon = Icons.Default.ChangeCircle,
                title = "변경된 굿즈가 없습니다",
                message = "보유·구매예정 굿즈의 정보가 데이터 업데이트로 바뀌면 여기에 기록됩니다"
            )
        } else if (filteredItems.isEmpty()) {
            EmptyState(
                icon = Icons.Default.ChangeCircle,
                title = if (processFilter == ProcessFilter.DONE) "처리한 굿즈가 없습니다" else "미처리 굿즈가 없습니다",
                message = "카드를 눌러 다이얼로그에서 보유 상태나 수량 등을 고치면 처리로 넘어갑니다"
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(vertical = AppStyles.paddingSmall),
                verticalArrangement = Arrangement.spacedBy(AppStyles.paddingSmall)
            ) {
                items(filteredItems, key = { it.record.changedId }) { item ->
                    ChangedGoodsCard(
                        item = item,
                        timeText = dateFormat.format(Date(item.record.changedTime)),
                        onGoClick = { item.current?.let(onNavigateToGoods) },
                        onOpen = { item.current?.let { viewModel.openGoods(it) } },
                        onRestore = { viewModel.restore(item.record) },
                        onDelete = { viewModel.delete(item.record) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChangedGoodsCard(
    item: ChangedGoodsItem,
    timeText: String,
    onGoClick: () -> Unit,
    onOpen: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val record = item.record
    val current = item.current
    val (kindText, kindColor) = when {
        record.statusKept -> "정보 변경" to MaterialTheme.colorScheme.primary
        record.isDeleted -> "원본에서 삭제" to MaterialTheme.colorScheme.error
        record.isRestored -> "상태 되돌림" to AppStyles.colorGotten
        else -> "상태 초기화" to MaterialTheme.colorScheme.error
    }
    // 지금 굿즈가 있으면 그 값을, 원본에서 사라졌으면 사라지기 전 정보를 보여 준다.
    val chara = current?.goodsChara ?: record.oldChara
    val category = current?.goodsCategory ?: record.oldCategory
    val series = current?.goodsSeries ?: record.oldSeries
    val price = current?.goodsPrice ?: record.oldPrice
    val encodedPath = encodeGoodsImagePath(current?.goodsUrl ?: record.oldUrl)

    // 열 굿즈가 없으면 누를 수 없다. 버튼(바로가기·되돌리기·삭제)은 각자 눌림을 받는다.
    Card(
        onClick = onOpen,
        enabled = current != null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppStyles.paddingMedium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
            disabledContentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(modifier = Modifier.padding(AppStyles.paddingMedium)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = kindText,
                    style = AppStyles.textCardSmall,
                    fontWeight = FontWeight.Bold,
                    color = kindColor
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
                        contentDescription = "기록 삭제",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppStyles.paddingSmall))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (record.isChecked) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "확인함",
                        tint = AppStyles.colorGotten,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(AppStyles.paddingSmall))
                }
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
                        text = chara,
                        style = AppStyles.textCardTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = category,
                        style = AppStyles.textCardSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = series,
                        style = AppStyles.textCardSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (price.isNotBlank()) {
                        Text(
                            text = price,
                            style = AppStyles.textCardSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    StatusLine(current = current, oldStatus = record.oldStatus)
                }

                if (current != null) {
                    TextButton(onClick = onGoClick) { Text("바로가기") }
                }
            }

            if (record.detailLines.isNotEmpty()) {
                Spacer(modifier = Modifier.height(AppStyles.paddingSmall))
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(AppStyles.paddingSmall))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    record.detailLines.forEach { line ->
                        Text(
                            text = "· $line",
                            style = AppStyles.textCardSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (record.canRestore) {
                Spacer(modifier = Modifier.height(AppStyles.paddingSmall))
                OutlinedButton(
                    onClick = onRestore,
                    modifier = Modifier.align(Alignment.End)
                ) { Text("이전 상태로 되돌리기") }
            }
        }
    }
}

/** 기록에서 연 굿즈의 상세 다이얼로그와, 그 굿즈가 세트 구성품이면 세트 다이얼로그 */
@Composable
private fun ChangedGoodsDialogs(
    viewModel: ChangedGoodsViewModel,
    onSeriesClick: (GoodsEntity) -> Unit
) {
    val openedGoodsId by viewModel.openedGoodsId.collectAsState()
    val allGoods by viewModel.allGoods.collectAsState()
    var selectedSetGoods by remember { mutableStateOf<GoodsEntity?>(null) }
    var setDialogComponent by remember { mutableStateOf<GoodsEntity?>(null) }

    // 상태를 바꾸면 굿즈 목록이 다시 흘러와 다이얼로그도 최신 값을 보여 준다.
    val openedGoods = allGoods.find { it.goodsId == openedGoodsId }
    val seriesGoods = remember(allGoods, openedGoods?.goodsSeries, selectedSetGoods?.goodsSeries) {
        val series = selectedSetGoods?.goodsSeries ?: openedGoods?.goodsSeries
        allGoods.filter { it.goodsSeries == series }
    }

    openedGoods?.let { goods ->
        val parentSet = findSetGoods(goods, seriesGoods)
        val setComponents = parentSet?.let { getSetComponents(it, seriesGoods) } ?: emptyList()
        GoodsDetailDialog(
            imgPath = goods.imgPath,
            series = goods.series,
            chara = goods.chara,
            category = goods.category,
            price = goods.price,
            isGotten = goods.isGotten,
            isPending = goods.status == GoodsStatus.PENDING,
            memo = goods.goodsMemo,
            setGoods = parentSet,
            setComponentTotal = setComponents.size,
            setComponentGotten = setComponents.count { it.isGotten },
            onSetClick = {
                setDialogComponent = goods
                selectedSetGoods = parentSet
            },
            onDismiss = { viewModel.dismissGoods() },
            onToggleGotten = {
                viewModel.toggleGotten(goods)
                viewModel.dismissGoods()
            },
            onSetPending = {
                viewModel.togglePending(goods)
                viewModel.dismissGoods()
            },
            purchaseInfo = goods.purchaseInfo,
            shippingDate = goods.shippingDate,
            onToggleShipping = { viewModel.toggleShipping(goods) },
            quantity = goods.quantity,
            onQuantityChange = { viewModel.setQuantity(goods, it) },
            onSavePendingInfo = { info ->
                viewModel.savePending(goods, info)
                viewModel.dismissGoods()
            },
            onDelete = {},
            showDelete = false,
            onSeriesClick = {
                viewModel.dismissGoods()
                onSeriesClick(goods)
            }
        )
    }

    selectedSetGoods?.let { setGoods ->
        val components = getSetComponents(setGoods, seriesGoods)
        SetGoodsDetailDialog(
            setGoods = setGoods,
            components = components,
            initialComponent = setDialogComponent,
            onDismiss = { selectedSetGoods = null; setDialogComponent = null },
            onToggleGotten = { component -> viewModel.toggleGotten(component) },
            onSetPending = { component -> viewModel.togglePending(component) },
            onBulkSetStatus = { status ->
                viewModel.bulkSetStatus(setGoods, components, status)
                selectedSetGoods = null
                setDialogComponent = null
            },
            onSavePendingInfo = { component, info -> viewModel.savePending(component, info) },
            onBulkSavePending = { info ->
                viewModel.bulkSetStatus(setGoods, components, GoodsStatus.PENDING, info)
                selectedSetGoods = null
                setDialogComponent = null
            }
        )
    }
}

/**
 * 굿즈의 지금 상태와 수량. 상태가 기록 당시와 달라졌으면(초기화 등) 원래 상태도 함께 적는다.
 * 원본에서 사라진 굿즈는 사라지기 전 상태만 적는다.
 */
@Composable
private fun StatusLine(current: GoodsEntity?, oldStatus: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (current != null) {
            val quantity = if (current.status != GoodsStatus.NOT_GOTTEN && current.goodsQuantity > 1) " · ${current.goodsQuantity}개" else ""
            StatusPill(statusLabel(current.goodsStatus) + quantity, statusColor(current.goodsStatus))
            if (current.goodsStatus != oldStatus && oldStatus.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "원래 ${statusLabel(oldStatus)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (oldStatus.isNotEmpty()) {
            StatusPill("${statusLabel(oldStatus)}이던 굿즈", statusColor(oldStatus))
        }
    }
}

private fun statusLabel(status: String) = when (status) {
    GoodsStatus.PENDING.name -> "구매 예정"
    GoodsStatus.GOTTEN.name -> "보유 중"
    else -> "미보유"
}

@Composable
private fun statusColor(status: String): Color = when (status) {
    GoodsStatus.PENDING.name -> AppStyles.colorPending
    GoodsStatus.GOTTEN.name -> AppStyles.colorGotten
    else -> AppStyles.colorNotGotten
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
