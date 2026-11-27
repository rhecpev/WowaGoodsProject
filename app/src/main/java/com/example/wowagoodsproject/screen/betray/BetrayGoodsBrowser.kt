package com.example.wowagoodsproject.screen.betray

import com.example.wowagoodsproject.component.GridListSwitch
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.wowagoodsproject.component.ActiveFilter
import com.example.wowagoodsproject.component.ActiveFilterChips
import com.example.wowagoodsproject.component.BulkSelection
import com.example.wowagoodsproject.component.BulkSelectionSlot
import com.example.wowagoodsproject.component.BulkStatusArea
import com.example.wowagoodsproject.component.swipeToChangeTab
import com.example.wowagoodsproject.component.BulkStatusButtons
import com.example.wowagoodsproject.component.EmptyState
import com.example.wowagoodsproject.component.FilterBar
import com.example.wowagoodsproject.component.FilterType
import com.example.wowagoodsproject.component.GoodsDetailDialog
import com.example.wowagoodsproject.component.GoodsFilterDialog
import com.example.wowagoodsproject.component.GoodsGridItem
import com.example.wowagoodsproject.component.GoodsListItem
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.GridColumnsBar
import com.example.wowagoodsproject.component.ListModeViewModel
import com.example.wowagoodsproject.component.SeriesBoundaryDivider
import com.example.wowagoodsproject.component.SidePanelLayout
import com.example.wowagoodsproject.component.asGottenStatus
import com.example.wowagoodsproject.component.bulkStatusMessage
import com.example.wowagoodsproject.component.chunkBySeries
import com.example.wowagoodsproject.component.hasChara
import com.example.wowagoodsproject.component.isSeriesStart
import com.example.wowagoodsproject.component.isSeriesStartRow
import com.example.wowagoodsproject.component.purchaseInfo
import com.example.wowagoodsproject.component.filterPendingInfo
import com.example.wowagoodsproject.component.rememberGoodsSelection
import com.example.wowagoodsproject.component.rememberGridColumns
import com.example.wowagoodsproject.db.betray.BetrayGoodsEntity
import com.example.wowagoodsproject.navigation.TopBar
import com.example.wowagoodsproject.navigation.TopBarAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 배신 굿즈 목록 한 화면. 명조 2차창작 탭과 같은 구성이다:
 * 위 헤더(필터·보기 방식·[actions]) → 걸어 둔 필터 칩 → 보유 상태 필터(다중 선택) → 목록(그리드/리스트).
 * 굿즈를 누르면 같은 상세 팝업이 뜬다.
 *
 * @param goods 이 화면이 보여 줄 굿즈(시리즈·캐릭터 등으로 이미 거른 것)
 * @param tabs 공식/2차창작 같은 세부 탭. 있으면 필터 줄 위에 둔다.
 */
@Composable
fun BetrayGoodsBrowser(
    title: String,
    goods: List<BetrayGoodsEntity>,
    viewModel: BetrayViewModel,
    listModeViewModel: ListModeViewModel,
    widthSizeClass: WindowWidthSizeClass,
    emptyTitle: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    tabs: (@Composable () -> Unit)? = null,
    /** [tabs] 를 좌우로 밀어 바꾸기(명조와 같다): (지금 탭, 탭 수, 바꾸기). 다중 선택 중에는 막는다 */
    tabSwipe: Triple<Int, Int, (Int) -> Unit>? = null,
    /** 시리즈 칸을 고를 수 있게 할지(이미 한 시리즈만 보는 화면은 false) */
    showSeriesFilter: Boolean = true,
    /** 목록 위에 둘 머리말(시리즈 사진·이름 등) */
    header: (@Composable () -> Unit)? = null,
    /** 상세 팝업에서 시리즈 이름을 누르면 */
    onSeriesClick: (BetrayGoodsEntity) -> Unit = {},
    /**
     * 구매예정 목록(명조 마이페이지 구매예정과 같은 화면): 보유 상태 줄 대신 구매 정보 필터
     * (구입처 미정·구매일 미정·수령일 미정·배송 중·배송 전), 줄마다 구매 정보와 배송 버튼, 다중 선택으로 배송 시작·구매 정보 한꺼번에.
     */
    pendingMode: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val allGoods by viewModel.goods.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val isGridMode by listModeViewModel.isGridMode.collectAsState()
    val gridColumns = rememberGridColumns(listModeViewModel, widthSizeClass)
    val isLandscape = widthSizeClass != WindowWidthSizeClass.Compact

    var filterType by remember { mutableStateOf(FilterType.ALL) }
    var pendingInfoFilter by remember { mutableStateOf<com.example.wowagoodsproject.component.PendingInfoFilter?>(null) }
    var charaFilter by remember { mutableStateOf<String?>(null) }
    var categoryFilter by remember { mutableStateOf<String?>(null) }
    var seriesFilter by remember { mutableStateOf<String?>(null) }
    var showFilter by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<Int?>(null) }
    val selection = rememberGoodsSelection()
    BackHandler(enabled = selection.mode) { selection.exit() }

    // 구매예정 목록은 명조 마이페이지처럼 공식 다음에 2차창작, 각각 시리즈 순
    val sorted = remember(goods, pendingMode) {
        if (pendingMode) com.example.wowagoodsproject.db.betray.BetrayKind.entries.flatMap { k -> sortForList(goods.filter { it.kind == k }) }
        else sortForList(goods)
    }
    // 캐릭터·카테고리·시리즈를 먼저 거르고(필터 칩 숫자용), 그다음 보유 상태로 거른다.
    val scoped = remember(sorted, charaFilter, categoryFilter, seriesFilter) {
        sorted.filter { g ->
            (charaFilter == null || hasChara(g.goodsChara, charaFilter!!)) &&
                (categoryFilter == null || g.goodsCategory == categoryFilter) &&
                (seriesFilter == null || seriesKey(g) == seriesFilter)
        }
    }
    // 구매예정 목록은 구매예정 굿즈만, 구매 정보 필터로 거른다.
    val scopedPending = remember(scoped, pendingMode) { if (pendingMode) scoped.filter { it.status == GoodsStatus.PENDING } else emptyList() }
    val shown = remember(scoped, filterType, scopedPending, pendingInfoFilter) {
        if (pendingMode) scopedPending.filterPendingInfo(pendingInfoFilter) else when (filterType) {
            FilterType.ALL -> scoped
            FilterType.GOTTEN -> scoped.filter { it.status == GoodsStatus.GOTTEN }
            FilterType.NOT_GOTTEN -> scoped.filter { it.status == GoodsStatus.NOT_GOTTEN }
            FilterType.PENDING -> scoped.filter { it.status == GoodsStatus.PENDING }
        }
    }

    // 알림으로 연 굿즈가 이 목록에 있으면 상세를 띄우고 요청을 비운다.
    val openRequest by BetrayOpenRequest.goods.collectAsState()
    LaunchedEffect(openRequest, goods) {
        val req = openRequest ?: return@LaunchedEffect
        if (goods.any { it.goodsId == req.goodsId }) {
            selectedId = req.goodsId
            BetrayOpenRequest.goods.value = null
        }
    }

    // 상세 팝업은 상태를 바꾼 뒤에도 최신 값을 보이도록 id 로 다시 찾는다.
    allGoods.find { it.goodsId == selectedId }?.let { g ->
        GoodsDetailDialog(
            itemKey = com.example.wowagoodsproject.component.goodsIdentity(g),
            imgPath = g.imgPath,
            series = g.series,
            chara = g.chara,
            category = g.category,
            price = g.price,
            isGotten = g.isGotten,
            isPending = g.status == GoodsStatus.PENDING,
            memo = g.memo,
            onDismiss = { selectedId = null },
            onToggleGotten = { viewModel.toggleGotten(g) },
            onSetPending = { viewModel.togglePending(g) },
            onEdit = { BetrayEditRequest.goods.value = g },
            onDelete = { viewModel.delete(g); selectedId = null },
            onSeriesClick = { selectedId = null; onSeriesClick(g) },
            purchaseInfo = g.purchaseInfo,
            onSavePendingInfo = { info -> viewModel.savePending(g, info) },
            shippingDate = g.shippingDate,
            onToggleShipping = {
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date())
                viewModel.setShipping(g, if (g.shippingDate.isEmpty()) today else "")
            },
            quantity = g.quantity,
            onQuantityChange = { q -> viewModel.setQuantity(g, q) }
        )
    }

    if (showFilter) {
        val charas = remember(goods, favorites) { betrayCharas(goods, favorites) }
        GoodsFilterDialog(
            widthSizeClass = widthSizeClass,
            charaList = charas.mapIndexed { i, c -> c.asCharaEntity(i + 1) },
            categoryList = goods.map { it.goodsCategory }.filter { it.isNotBlank() }.distinct().sorted(),
            selectedCharaFilter = charaFilter,
            selectedCategoryFilter = categoryFilter,
            onCharaSelect = { charaFilter = it },
            onCategorySelect = { categoryFilter = it },
            onClearFilter = { charaFilter = null; categoryFilter = null; seriesFilter = null },
            onDismiss = { showFilter = false },
            seriesList = if (showSeriesFilter) goods.map { seriesKey(it) }.distinct().sorted() else emptyList(),
            selectedSeriesFilter = seriesFilter,
            onSeriesSelect = if (showSeriesFilter) ({ seriesFilter = it }) else null,
            scopeGoods = goods,
            useSeriesDb = false
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = title,
            subtitle = subtitle,
            onBack = onBack,
            actions = {
                TopBarAction(
                    icon = Icons.Default.FilterList,
                    contentDescription = "필터",
                    onClick = { showFilter = true },
                    active = charaFilter != null || categoryFilter != null || seriesFilter != null
                )
                TopBarAction(
                    icon = if (isGridMode) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                    contentDescription = "보기 방식 전환",
                    onClick = { listModeViewModel.toggleGridMode() }
                )
                actions()
            }
        )
        header?.invoke()
        ActiveFilterChips(
            filters = listOfNotNull(
                charaFilter?.let { ActiveFilter(it) { charaFilter = null } },
                seriesFilter?.let { ActiveFilter(it) { seriesFilter = null } },
                categoryFilter?.let { ActiveFilter(it) { categoryFilter = null } }
            ),
            onClearAll = { charaFilter = null; categoryFilter = null; seriesFilter = null }
        )

        val picked = selection.visible(shown) { it.goodsId }
        val bulk = BulkSelection(
            selectedItems = picked,
            allSelected = shown.isNotEmpty() && picked.size == shown.size,
            onSelectAll = { selection.selectAll(shown.map { it.goodsId }) },
            onClearSelection = { selection.clear() },
            onApply = { status, edit ->
                viewModel.applyStatus(picked, status, edit)
                selection.clear()
                Toast.makeText(context, bulkStatusMessage(picked.size, status), Toast.LENGTH_SHORT).show()
            }
        )
        val today = { SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date()) }
        // 구매예정 목록의 일괄 변경(명조 마이페이지와 같은 부품): 배송 시작 · 구매 정보
        val pendingBulk = com.example.wowagoodsproject.screen.mypage.rememberPendingBulkFor(
            totalCount = shown.size,
            selected = picked,
            onSelectAll = { selection.selectAll(shown.map { it.goodsId }) },
            onClearSelection = { selection.clear() },
            onApply = { edit ->
                viewModel.applyStatus(picked, GoodsStatus.PENDING, edit)
                selection.clear()
                Toast.makeText(context, "구매 정보를 적용했습니다", Toast.LENGTH_SHORT).show()
            },
            onStartShipping = {
                // 이미 배송 중인 굿즈는 시작일을 그대로 두고, 배송 전인 것만 오늘로 시작한다.
                val d = today()
                picked.filter { it.shippingDate.isEmpty() }.forEach { viewModel.setShipping(it, d) }
                selection.clear()
                Toast.makeText(context, "배송 시작으로 표시했습니다", Toast.LENGTH_SHORT).show()
            }
        )
        SidePanelLayout(
            sidePanel = isLandscape,
            tabs = tabs,
            panel = {
                if (pendingMode) com.example.wowagoodsproject.component.PendingInfoFilterBar(
                    selected = pendingInfoFilter,
                    onSelect = { pendingInfoFilter = it },
                    goodsList = scopedPending,
                    selectionMode = selection.mode,
                    onToggleSelection = { selection.toggleMode() },
                    vertical = isLandscape,
                    selectionContent = { com.example.wowagoodsproject.screen.mypage.PendingBulkSlot(pendingBulk, vertical = isLandscape) }
                ) else FilterBar(
                    filterType = filterType,
                    onFilterChange = { filterType = it },
                    goodsList = scoped,
                    selectionMode = selection.mode,
                    onToggleSelection = { selection.toggleMode() },
                    vertical = isLandscape,
                    selectionContent = { BulkSelectionSlot(bulk, vertical = isLandscape) }
                )
            }
        ) {
            GridColumnsBar(listModeViewModel, widthSizeClass)
            if (pendingMode) {
                com.example.wowagoodsproject.screen.mypage.PendingGoodsList(
                    items = shown,
                    selectionMode = selection.mode,
                    isSelected = { (it as? BetrayGoodsEntity)?.goodsId in selection.ids },
                    onToggle = { g -> (g as? BetrayGoodsEntity)?.let { selection.toggle(it.goodsId) } },
                    bulk = pendingBulk,
                    showBulkButtons = !isLandscape,
                    isGridMode = isGridMode,
                    gridColumns = gridColumns,
                    isFiltered = pendingInfoFilter != null || charaFilter != null || categoryFilter != null || seriesFilter != null,
                    onToggleShipping = { g ->
                        (g as? BetrayGoodsEntity)?.let { viewModel.setShipping(it, if (it.shippingDate.isEmpty()) today() else "") }
                    },
                    onOpenGoods = { g -> (g as? BetrayGoodsEntity)?.let { selectedId = if (selectedId == it.goodsId) null else it.goodsId } }
                )
                return@SidePanelLayout
            }
            Box(
                modifier = Modifier.fillMaxSize().then(
                    tabSwipe?.let { (index, count, onSelect) ->
                        Modifier.swipeToChangeTab(index, count) { if (!selection.mode) onSelect(it) }
                    } ?: Modifier
                )
            ) {
                BulkStatusArea {
                    if (selection.mode && !isLandscape) BulkStatusButtons(bulk)
                    BetrayGoodsList(
                        goods = shown,
                        isGridMode = isGridMode,
                        gridColumns = gridColumns,
                        emptyTitle = if (goods.isEmpty()) emptyTitle else "조건에 맞는 굿즈가 없습니다",
                        selectedIds = if (selection.mode) selection.ids else null,
                        onClick = { if (selection.mode) selection.toggle(it.goodsId) else selectedId = if (selectedId == it.goodsId) null else it.goodsId }
                    )
                }
            }
        }
    }
}

/** 그리드/리스트 목록. 명조 2차창작 목록(FanGoodsListContent)과 같은 모양이다. */
@Composable
fun BetrayGoodsList(
    goods: List<BetrayGoodsEntity>,
    isGridMode: Boolean,
    gridColumns: Int,
    emptyTitle: String,
    selectedIds: Set<Int>?,
    onClick: (BetrayGoodsEntity) -> Unit
) {
    if (goods.isEmpty()) {
        EmptyState(icon = Icons.Outlined.Inventory2, title = emptyTitle, message = "오른쪽 위 '+'로 추가하세요")
        return
    }
    val padding = PaddingValues(bottom = 16.dp)
    // 보기 방식을 바꾸면 줌 애니메이션으로 넘긴다(ViewSwitchAnimation.kt)
    com.example.wowagoodsproject.component.ProvideGoodsCardImageSize(gridColumns) {
    GridListSwitch(isGridMode) { grid ->
        if (grid) {
            val rows = remember(goods, gridColumns) { chunkBySeries(goods, gridColumns) }
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
                itemsIndexed(rows) { rowIndex, rowItems ->
                    if (isSeriesStartRow(rows, rowIndex)) SeriesBoundaryDivider()
                    Row(modifier = Modifier.fillMaxWidth()) {
                        rowItems.forEach { item ->
                            Box(modifier = Modifier.weight(1f)) {
                                GoodsGridItem(
                                    imgPath = item.imgPath, series = item.series, chara = item.chara,
                                    category = item.category, price = item.price, isGotten = item.isGotten,
                                    gottenStatus = item.status.asGottenStatus(), memo = item.memo, quantity = item.quantity,
                                    selected = selectedIds?.let { item.goodsId in it },
                                    onClick = { onClick(item) }
                                )
                            }
                        }
                        repeat(gridColumns - rowItems.size) { Box(modifier = Modifier.weight(1f)) }
                    }
                    if (rowIndex < rows.lastIndex && !isSeriesStartRow(rows, rowIndex + 1)) {
                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
                itemsIndexed(goods, key = { _, g -> g.goodsId }) { index, item ->
                    GoodsListItem(
                        imgPath = item.imgPath, series = item.series, chara = item.chara,
                        category = item.category, price = item.price, isGotten = item.isGotten,
                        gottenStatus = item.status.asGottenStatus(), memo = item.memo, quantity = item.quantity,
                        selected = selectedIds?.let { item.goodsId in it },
                        onClick = { onClick(item) }
                    )
                    if (index < goods.lastIndex) {
                        if (isSeriesStart(goods, index + 1)) SeriesBoundaryDivider()
                        else HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
    }

}
