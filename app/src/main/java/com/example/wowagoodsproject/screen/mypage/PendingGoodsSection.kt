package com.example.wowagoodsproject.screen.mypage

import com.example.wowagoodsproject.PendingGoodsNotifier
import androidx.compose.material.icons.filled.MoveToInbox
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.TextAutoSize
import coil.compose.rememberAsyncImagePainter
import androidx.compose.ui.text.style.TextAlign
import com.example.wowagoodsproject.component.BulkSelectionSummary
import com.example.wowagoodsproject.component.GoodsItem
import com.example.wowagoodsproject.component.SeriesBoundaryDivider
import com.example.wowagoodsproject.component.chunkBySeries
import com.example.wowagoodsproject.component.isSeriesStart
import com.example.wowagoodsproject.component.isSeriesStartRow
import com.example.wowagoodsproject.component.PurchaseInfo
import com.example.wowagoodsproject.component.PurchaseInfoDialog
import com.example.wowagoodsproject.component.PurchaseInfoEdit
import com.example.wowagoodsproject.component.SelectionMark
import com.example.wowagoodsproject.component.encodeGoodsImagePath
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.ui.theme.AppStyles

/**
 * 구매예정 목록의 다중 선택 일괄 변경에 필요한 값과 동작 묶음. [rememberPendingBulk] 가 만든다.
 *
 * @param notShippedCount 고른 굿즈 중 아직 배송 전인 개수. 0 이면 배송 시작을 누를 수 없다.
 */
class PendingBulk(
    val selectedCount: Int,
    val allSelected: Boolean,
    val notShippedCount: Int,
    val onSelectAll: () -> Unit,
    val onClearSelection: () -> Unit,
    val onStartShipping: () -> Unit,
    val onEditPurchase: () -> Unit
)

/**
 * 화면에 보이는 구매예정 굿즈와 선택 상태로 [PendingBulk] 를 만든다.
 * 구매 정보 입력 다이얼로그도 여기서 띄운다(버튼이 필터 줄에 있든 목록 위에 있든 한 곳에서 관리한다).
 *
 * @param onApply 고른 굿즈에 구입처/구매일/수령예정일을 한 번에 적어 넣는다.
 * @param onStartShipping 고른 굿즈 중 아직 배송 전인 것들을 오늘 날짜로 배송 시작한다.
 */
@Composable
fun rememberPendingBulk(
    officialGoods: List<GoodsEntity>,
    fanGoods: List<FanGoodsEntity>,
    selectedOfficialIds: Set<Int>,
    selectedFanIds: Set<Int>,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onApply: (officialIds: Set<Int>, fanIds: Set<Int>, edit: PurchaseInfoEdit) -> Unit,
    onStartShipping: (officialIds: Set<Int>, fanIds: Set<Int>) -> Unit
): PendingBulk {
    // 필터로 가려진 굿즈가 선택에 남아 있어도 화면에 보이는 것만 다룬다.
    val visibleOfficial = officialGoods.filter { it.goodsId in selectedOfficialIds }
    val visibleFan = fanGoods.filter { it.fanGoodsId in selectedFanIds }
    val officialIds = visibleOfficial.map { it.goodsId }.toSet()
    val fanIds = visibleFan.map { it.fanGoodsId }.toSet()
    return rememberPendingBulkFor(
        totalCount = officialGoods.size + fanGoods.size,
        selected = visibleOfficial + visibleFan,
        onSelectAll = onSelectAll,
        onClearSelection = onClearSelection,
        onApply = { edit -> onApply(officialIds, fanIds, edit) },
        onStartShipping = { onStartShipping(officialIds, fanIds) }
    )
}

/**
 * [rememberPendingBulk] 의 공통 버전. 굿즈 종류(명조 공식·2차창작, 배신 굿즈)와 상관없이 고른 굿즈 목록으로 만든다.
 * 구매 정보 입력 다이얼로그도 여기서 띄운다.
 *
 * @param totalCount 화면에 보이는 구매예정 굿즈 수(전체 선택 표시용)
 * @param selected 화면에 보이는 것 중 고른 굿즈
 */
@Composable
fun rememberPendingBulkFor(
    totalCount: Int,
    selected: List<GoodsItem>,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onApply: (edit: PurchaseInfoEdit) -> Unit,
    onStartShipping: () -> Unit
): PendingBulk {
    var showPurchaseDialog by remember { mutableStateOf(false) }

    if (showPurchaseDialog) {
        // 고른 굿즈들의 현재 값. 하나로 같을 때만 다이얼로그에 미리 채워 준다.
        PurchaseInfoDialog(
            initial = PurchaseInfo(
                purchaseStore = selected.map { it.purchaseStore }.distinct().singleOrNull() ?: "",
                receiveDate = selected.map { it.receiveDate }.distinct().singleOrNull() ?: "",
                purchaseDate = selected.map { it.purchaseDate }.distinct().singleOrNull() ?: ""
            ),
            targetCount = selected.size,
            // 이미 구매예정인 굿즈의 정보 수정이라 '구매일 미정'을 오늘로 바꾸지 않는다.
            prefillToday = false,
            onDismiss = { showPurchaseDialog = false },
            onConfirm = { edit ->
                showPurchaseDialog = false
                onApply(edit)
            }
        )
    }

    return PendingBulk(
        selectedCount = selected.size,
        allSelected = totalCount > 0 && selected.size == totalCount,
        notShippedCount = selected.count { it.shippingDate.isEmpty() },
        onSelectAll = onSelectAll,
        onClearSelection = onClearSelection,
        // 이미 배송 중인 굿즈는 시작일을 그대로 두고, 배송 전인 것만 오늘로 시작한다(부르는 쪽이 처리).
        onStartShipping = onStartShipping,
        onEditPurchase = { showPurchaseDialog = true }
    )
}

/**
 * 다중 선택 중에 잠긴 필터 칩 대신 그 자리에 넣는 내용([PendingInfoFilterBar] 의 selectionContent).
 * 세로 화면은 안내 한 줄(버튼은 목록 위의 [PendingBulkButtons]),
 * [vertical] 이면(가로 화면의 왼쪽 줄) 안내와 버튼을 모두 위아래로 쌓는다.
 */
@Composable
fun PendingBulkSlot(bulk: PendingBulk, vertical: Boolean) {
    if (!vertical) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BulkSelectionSummary(bulk.selectedCount, bulk.allSelected, bulk.onSelectAll, bulk.onClearSelection)
        }
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (bulk.selectedCount > 0) "${bulk.selectedCount}개 선택됨" else "굿즈를 눌러\n선택하세요",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = if (bulk.selectedCount > 0) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = if (bulk.allSelected) bulk.onClearSelection else bulk.onSelectAll) {
            Text(if (bulk.allSelected) "선택 해제" else "전체 선택")
        }
        PendingBulkActionButtons(bulk, buttonModifier = Modifier.fillMaxWidth())
    }
}

/** 세로 화면에서 목록 바로 위에 두는 배송 시작 / 구매 정보 버튼 줄. 안내 줄은 [PendingBulkSlot] 이 맡는다. */
@Composable
private fun PendingBulkButtons(bulk: PendingBulk) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PendingBulkActionButtons(bulk, buttonModifier = Modifier.weight(1f))
        }
    }
}

/** 배송 시작 / 구매 정보 버튼 두 개. 가로로 놓을지 세로로 쌓을지는 감싸는 쪽이 정한다. */
@Composable
private fun PendingBulkActionButtons(bulk: PendingBulk, buttonModifier: Modifier) {
    TextButton(
        onClick = bulk.onStartShipping,
        enabled = bulk.notShippedCount > 0,
        shape = RectangleShape,
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = buttonModifier
    ) {
        Icon(
            imageVector = Icons.Default.LocalShipping,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text("배송 시작", maxLines = 1)
    }
    TextButton(
        onClick = bulk.onEditPurchase,
        enabled = bulk.selectedCount > 0,
        shape = RectangleShape,
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = buttonModifier
    ) {
        Icon(
            imageVector = Icons.Default.CalendarMonth,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text("구매 정보", maxLines = 1)
    }
}

/**
 * 구매예정 굿즈 목록(명조 공식·2차창작).
 * 줄을 누르면 굿즈 상세 팝업이 열려 그 굿즈의 구매 정보와 배송을 바로 고칠 수 있다.
 * 다중 선택 중([selectionMode])에는 줄을 눌러 여러 개를 고른 뒤
 * 구입처/구매일/수령예정일을 한 번에 적어 넣거나 배송 시작으로 표시한다([bulk]).
 * 줄마다 있는 배송 시작/취소 버튼으로 하나씩도 바꿀 수 있다.
 */
@Composable
fun PendingGoodsSection(
    officialGoods: List<GoodsEntity>,
    fanGoods: List<FanGoodsEntity>,
    selectionMode: Boolean,
    selectedOfficialIds: Set<Int>,
    selectedFanIds: Set<Int>,
    onToggleOfficial: (Int) -> Unit,
    onToggleFan: (Int) -> Unit,
    bulk: PendingBulk,
    /** 다중 선택 중에 목록 위에 일괄 변경 버튼 줄을 둘지. 가로 화면에서는 버튼이 왼쪽 필터 줄 안에 있어 끈다. */
    showBulkButtons: Boolean = true,
    /** true 면 줄 대신 카드를 한 줄에 [gridColumns] 개씩 놓는다. */
    isGridMode: Boolean = false,
    gridColumns: Int = 2,
    isFiltered: Boolean = false,
    /** 줄마다 있는 배송 버튼. 배송 전이면 시작, 배송 중이면 취소한다. */
    onToggleShipping: (GoodsItem) -> Unit = {},
    /** 줄을 눌렀을 때. 굿즈 상세 팝업을 연다. */
    onOpenGoods: (GoodsItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    PendingGoodsList(
        // 공식 다음에 2차창작
        items = officialGoods + fanGoods,
        selectionMode = selectionMode,
        isSelected = { g ->
            when (g) {
                is GoodsEntity -> g.goodsId in selectedOfficialIds
                is FanGoodsEntity -> g.fanGoodsId in selectedFanIds
                else -> false
            }
        },
        onToggle = { g ->
            when (g) {
                is GoodsEntity -> onToggleOfficial(g.goodsId)
                is FanGoodsEntity -> onToggleFan(g.fanGoodsId)
            }
        },
        bulk = bulk,
        showBulkButtons = showBulkButtons,
        isGridMode = isGridMode,
        gridColumns = gridColumns,
        isFiltered = isFiltered,
        onToggleShipping = onToggleShipping,
        onOpenGoods = onOpenGoods,
        modifier = modifier
    )
}

/**
 * 구매예정 굿즈 목록의 공통 버전. 명조 마이페이지와 배신 굿즈 장르 마이페이지가 같이 쓴다.
 * 굿즈 종류와 상관없이 [items] 순서대로 놓고, 고르기·열기는 부르는 쪽 함수로 한다.
 */
@Composable
fun PendingGoodsList(
    items: List<GoodsItem>,
    selectionMode: Boolean,
    isSelected: (GoodsItem) -> Boolean,
    onToggle: (GoodsItem) -> Unit,
    bulk: PendingBulk,
    showBulkButtons: Boolean = true,
    isGridMode: Boolean = false,
    gridColumns: Int = 2,
    isFiltered: Boolean = false,
    onToggleShipping: (GoodsItem) -> Unit = {},
    onOpenGoods: (GoodsItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (isFiltered) "조건에 맞는 굿즈가 없습니다"
                    else "구매예정으로 표시한 굿즈가 없습니다",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isFiltered) "위에서 필터를 바꾸거나 지워 보세요"
                    else "굿즈 상세에서 '구매예정'을 고르면 여기에 모입니다",
                    style = AppStyles.textCardSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (selectionMode && showBulkButtons) PendingBulkButtons(bulk)

        // 보기 방식을 바꾸면 줌 애니메이션으로 넘긴다(ViewSwitchAnimation.kt)
        com.example.wowagoodsproject.component.GridListSwitch(isGridMode, Modifier.fillMaxWidth().weight(1f)) { grid ->
            if (grid) {
            // 시리즈가 바뀌면 새 줄에서 시작하고, 그 줄 위에 이중선을 둔다.
            val rows = remember(items, gridColumns) { chunkBySeries(items, gridColumns) }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = AppStyles.paddingMedium,
                    end = AppStyles.paddingMedium,
                    top = 4.dp,
                    bottom = AppStyles.paddingMedium
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(rows, key = { _, row -> pendingKey(row.first()) }) { rowIndex, rowItems ->
                    if (isSeriesStartRow(rows, rowIndex)) SeriesBoundaryDivider(verticalPadding = 4.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowItems.forEach { goods ->
                            Box(modifier = Modifier.weight(1f)) {
                                PendingGoodsCard(
                                    goods = goods,
                                    isFanGoods = isFanItem(goods),
                                    selected = if (!selectionMode) null else isSelected(goods),
                                    onClick = { if (selectionMode) onToggle(goods) else onOpenGoods(goods) }
                                )
                            }
                        }
                        repeat(gridColumns - rowItems.size) {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = AppStyles.paddingLarge,
                    end = AppStyles.paddingLarge,
                    top = 4.dp,
                    bottom = AppStyles.paddingMedium
                )
            ) {
                // 줄마다 아래 선이 있어 줄 사이 간격은 두지 않는다. 앞 굿즈와 시리즈가 다르면 그 줄 위에 이중선을 둔다.
                itemsIndexed(items, key = { _, goods -> pendingKey(goods) }) { index, goods ->
                    if (isSeriesStart(items, index)) SeriesBoundaryDivider(verticalPadding = 4.dp)
                    PendingGoodsRow(
                        goods = goods,
                        isFanGoods = isFanItem(goods),
                        selected = if (!selectionMode) null else isSelected(goods),
                        onClick = { if (selectionMode) onToggle(goods) else onOpenGoods(goods) },
                        onToggleShipping = { onToggleShipping(goods) }
                    )
                }
            }
            }
        }
    }
}

/** 2차창작(비공식) 굿즈인지. 배신 굿즈는 종류 칸으로 본다. */
private fun isFanItem(goods: GoodsItem): Boolean = when (goods) {
    is GoodsEntity -> false
    is com.example.wowagoodsproject.db.betray.BetrayGoodsEntity -> goods.kind == com.example.wowagoodsproject.db.betray.BetrayKind.FAN
    else -> true
}

private fun pendingKey(goods: GoodsItem): String = when (goods) {
    is GoodsEntity -> "official-${goods.goodsId}"
    is FanGoodsEntity -> "fan-${goods.fanGoodsId}"
    is com.example.wowagoodsproject.db.betray.BetrayGoodsEntity -> "betray-${goods.goodsId}"
    else -> goods.hashCode().toString()
}

/** 카드가 이보다 좁으면(한 줄에 많이 놓았을 때) 글자를 빼고 이미지와 표시만 보여준다. */
private val PENDING_CARD_COMPACT_WIDTH = 96.dp

/**
 * 그리드 보기의 구매예정 굿즈 카드: 이미지 + 굿즈 정보 + 구입처 / 수령예정일.
 * 배송 중이면 이미지 오른쪽 위에 표시한다. 배송 시작·취소는 카드를 눌러 여는 상세 팝업에서 한다.
 * [selected] 가 null 이 아니면(다중 선택 중) 이미지 왼쪽 위에 선택 표시를 둔다.
 */
@Composable
private fun PendingGoodsCard(
    goods: GoodsItem,
    isFanGoods: Boolean,
    selected: Boolean?,
    onClick: () -> Unit
) {
    val encodedPath = encodeGoodsImagePath(goods.imgPath)
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date()) }
    val overdue = goods.receiveDate.isNotEmpty() && goods.receiveDate < today
    val shipping = goods.shippingDate.isNotEmpty()
    val shippingOverdue = PendingGoodsNotifier.isShippingOverdue(goods.shippingDate)

    BoxWithConstraints {
        val compact = maxWidth < PENDING_CARD_COMPACT_WIDTH
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = RectangleShape,
            color = if (selected == true) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(
                            model = if (encodedPath.isNotEmpty()) encodedPath else null
                        ),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    if (selected != null) {
                        SelectionMark(
                            selected = selected,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(if (compact) 2.dp else 6.dp)
                        )
                    }
                    if (shipping) {
                        Icon(
                            imageVector = Icons.Default.LocalShipping,
                            contentDescription = if (shippingOverdue) "배송 시작 후 일주일 지남" else "배송 중",
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(if (compact) 2.dp else 6.dp)
                                .background(
                                    if (shippingOverdue) MaterialTheme.colorScheme.error else AppStyles.colorPending,
                                    RoundedCornerShape(50)
                                )
                                .padding(3.dp)
                                .size(if (compact) 10.dp else 14.dp)
                        )
                    }
                }
                if (compact) return@Column
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = goods.series.ifEmpty { if (isFanGoods) "2차창작" else goods.category } +
                                if (goods.quantity > 1) " ×${goods.quantity}" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOf(goods.chara, goods.category, goods.memo)
                            .filter { it.isNotEmpty() }.joinToString(" - "),
                        style = AppStyles.textCardSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    PurchaseChip(
                        icon = Icons.Default.Storefront,
                        text = goods.purchaseStore.ifEmpty { "구입처 미정" },
                        filled = goods.purchaseStore.isNotEmpty()
                    )
                    PurchaseChip(
                        icon = Icons.Default.MoveToInbox,
                        text = when {
                            goods.receiveDate.isEmpty() -> "수령일 미정"
                            overdue -> "${goods.receiveDate} 지남"
                            else -> goods.receiveDate
                        },
                        filled = goods.receiveDate.isNotEmpty(),
                        warning = overdue
                    )
                }
            }
        }
    }
}

/**
 * 썸네일 + 굿즈 정보 + 구입처/구매일/수령예정일(+배송) + 배송 버튼 한 줄.
 * [selected] 가 null 이 아니면(다중 선택 중) 다른 목록처럼 맨 왼쪽에 선택 표시를 둔다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PendingGoodsRow(
    goods: GoodsItem,
    isFanGoods: Boolean,
    selected: Boolean?,
    onClick: () -> Unit,
    onToggleShipping: () -> Unit
) {
    val encodedPath = encodeGoodsImagePath(goods.imgPath)
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date()) }
    // 날짜를 "yyyy-MM-dd" 문자열로 저장하므로 문자열 비교가 곧 날짜 비교다.
    val overdue = goods.receiveDate.isNotEmpty() && goods.receiveDate < today
    val shipping = goods.shippingDate.isNotEmpty()
    val shippingOverdue = PendingGoodsNotifier.isShippingOverdue(goods.shippingDate)
    // 박스 없이 줄만 두고 아래 얇은 선으로 나눈다. 다중 선택으로 고른 줄만 옅게 칠한다.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected == true) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
            )
            // 평소에는 상세 팝업을 열고, 다중 선택 중에는 선택을 바꾼다.
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (selected != null) {
                SelectionMark(selected = selected)
            }
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    // 2차창작은 시리즈를 안 적었을 수 있다.
                    text = goods.series.ifEmpty { if (isFanGoods) "2차창작" else goods.category } +
                            if (goods.quantity > 1) " ×${goods.quantity}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(goods.chara, goods.category, goods.memo)
                        .filter { it.isNotEmpty() }.joinToString(" - "),
                    style = AppStyles.textCardSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                // 칩 세 개가 한 줄에 안 들어가는 좁은 화면에서는 다음 줄로 넘긴다.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PurchaseChip(
                        icon = Icons.Default.Storefront,
                        text = goods.purchaseStore.ifEmpty { "구입처 미정" },
                        filled = goods.purchaseStore.isNotEmpty()
                    )
                    PurchaseChip(
                        icon = Icons.Default.CalendarMonth,
                        text = goods.purchaseDate.ifEmpty { "구매일 미정" },
                        filled = goods.purchaseDate.isNotEmpty()
                    )
                    PurchaseChip(
                        icon = Icons.Default.MoveToInbox,
                        text = when {
                            goods.receiveDate.isEmpty() -> "수령일 미정"
                            overdue -> "${goods.receiveDate} 지남"
                            else -> goods.receiveDate
                        },
                        filled = goods.receiveDate.isNotEmpty(),
                        warning = overdue
                    )
                    if (shipping) {
                        PurchaseChip(
                            icon = Icons.Default.LocalShipping,
                            text = if (shippingOverdue) "배송 ${goods.shippingDate}~ 일주일 지남"
                            else "배송 중 ${goods.shippingDate}~",
                            filled = true,
                            warning = shippingOverdue
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                // 줄을 누르면 선택이 바뀌므로, 배송 버튼은 따로 눌리는 작은 버튼으로 둔다.
                if (shipping) {
                    TextButton(
                        onClick = onToggleShipping,
                        shape = RectangleShape,
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("배송 취소", style = AppStyles.textCardSmall.copy(fontWeight = FontWeight.SemiBold))
                    }
                } else {
                    TextButton(
                        onClick = onToggleShipping,
                        shape = RectangleShape,
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalShipping,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("배송 시작", style = AppStyles.textCardSmall.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun PurchaseChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    filled: Boolean,
    warning: Boolean = false
) {
    val color = when {
        warning -> MaterialTheme.colorScheme.error
        filled -> AppStyles.colorPending
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    // 박스 없이 아이콘 + 색 글자만 둔다.
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp)
        )
        // 가로 화면 그리드처럼 칸이 좁으면 날짜 숫자가 잘려 보이지 않게, 잘리기 전에 글자 크기부터 줄인다.
        // 최소 크기로도 안 들어갈 때(아주 긴 구입처 이름 등)만 말줄임한다.
        val style = AppStyles.textCardSmall
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(
                minFontSize = PURCHASE_CHIP_MIN_FONT,
                maxFontSize = style.fontSize,
                stepSize = 0.5.sp
            )
        )
    }
}

private val PURCHASE_CHIP_MIN_FONT = 8.sp
