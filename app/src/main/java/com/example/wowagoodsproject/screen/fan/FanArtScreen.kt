package com.example.wowagoodsproject.screen.fan

import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.example.wowagoodsproject.component.BulkStatusButtons
import com.example.wowagoodsproject.component.BulkSelectionSlot
import com.example.wowagoodsproject.component.BulkSelection
import com.example.wowagoodsproject.component.SidePanelLayout
import com.example.wowagoodsproject.component.BulkStatusArea
import com.example.wowagoodsproject.component.GridColumnsBar
import com.example.wowagoodsproject.component.rememberGridColumns
import com.example.wowagoodsproject.component.bulkStatusMessage
import com.example.wowagoodsproject.component.rememberGoodsSelection
import com.example.wowagoodsproject.component.purchaseInfo
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.component.EmptyState
import com.example.wowagoodsproject.component.SeriesCard
import com.example.wowagoodsproject.component.SeriesListPage
import com.example.wowagoodsproject.component.seriesCards
import com.example.wowagoodsproject.db.fan.saveSeries
import com.example.wowagoodsproject.screen.betray.SeriesEditScreen
import com.example.wowagoodsproject.screen.betray.SeriesEditViewModel
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.wowagoodsproject.component.ActiveFilter
import com.example.wowagoodsproject.component.ActiveFilterChips
import com.example.wowagoodsproject.component.FanGoodsListContent
import com.example.wowagoodsproject.component.FilterBar
import com.example.wowagoodsproject.component.FilterViewModel
import com.example.wowagoodsproject.component.GoodsDetailDialog
import com.example.wowagoodsproject.component.GoodsDetailViewModel
import com.example.wowagoodsproject.component.GoodsFilterDialog
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.ListModeViewModel
import com.example.wowagoodsproject.component.filterFanGoodsList
import com.example.wowagoodsproject.component.seriesOptions
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.navigation.TopBar
import com.example.wowagoodsproject.navigation.TopBarAction

@Composable
fun FanArtScreen(
    /** 굿즈 등록 화면을 연다. 넘기는 값은 미리 채울 시리즈(없으면 "") */
    onNavigateToAdd: (String) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    viewModel: FanArtViewModel = viewModel(),
    listModeViewModel: ListModeViewModel = viewModel(),
    detailViewModel: GoodsDetailViewModel = viewModel(),
    filterViewModel: FilterViewModel = viewModel(),
    onNavigateToSeries: (String) -> Unit = {}
) {
    val goodsList by viewModel.goodsList.collectAsState()
    val isGridMode by listModeViewModel.isGridMode.collectAsState()
    val selectedGoods by detailViewModel.selectedGoods.collectAsState()
    val filterType by filterViewModel.filterType.collectAsState()
    var showGoodsFilterDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val selection = rememberGoodsSelection()
    BackHandler(enabled = selection.mode) { selection.exit() }

    // ── 시리즈: 공식 굿즈처럼 시리즈 카드(사진) → 시리즈를 열면 그 굿즈 목록 ──
    val registeredSeries by viewModel.series.collectAsState()
    /** 연 시리즈. null 이면 시리즈 카드 */
    // 하단 '비공식' 탭을 다시 누르면 시리즈 목록으로 돌아가도록 뷰모델에 둔다(MainScreen 의 resetTab).
    val openSeries by viewModel.openSeries.collectAsState()
    /** 시리즈 추가·편집. null 닫힘, "" 새 시리즈, 그 밖에는 고칠 시리즈 이름 */
    var seriesEdit by rememberSaveable { mutableStateOf<String?>(null) }
    val allCharasForSeries by viewModel.allCharaList.collectAsState()
    val seriesCardList = remember(goodsList, registeredSeries, allCharasForSeries) {
        val byName = allCharasForSeries.associateBy { it.charaNm }
        seriesCards(
            goods = goodsList,
            seriesOf = { it.fanGoodsSeries },
            statusOf = { it.status },
            imageOf = { it.fanGoodsImgPath },
            dateOf = { it.fanGoodsReleaseDate },
            charaOf = { it.fanGoodsChara },
            registered = registeredSeries.associate { it.seriesNm to it.seriesImgPath },
            registeredDates = registeredSeries.associate { it.seriesNm to it.seriesDate },
            charaInfo = { n -> byName[n]?.let { c -> c.charaUrl to (if (c.charaIsFavorite) c.charaFavoriteOrder else null) } ?: ("" to null) },
            noSeriesName = NO_FAN_SERIES,
            ignoreCase = true
        )
    }
    // 연 시리즈의 굿즈만 아래 목록·필터에 쓴다.
    val scopedGoods = remember(goodsList, openSeries) {
        when (val open = openSeries) {
            null -> goodsList
            // 시리즈 카드와 같이 대소문자를 가리지 않는다.
            else -> goodsList.filter { it.fanGoodsSeries.trim().ifEmpty { NO_FAN_SERIES }.equals(open, ignoreCase = true) }
        }
    }
    BackHandler(enabled = openSeries != null && !selection.mode) {
        viewModel.setOpenSeries(null)
        filterViewModel.clearGoodsFilter()
    }

    val selectedGoodsCharaFilter by filterViewModel.selectedCharaFilter.collectAsState()
    val selectedGoodsCategoryFilter by filterViewModel.selectedCategoryFilter.collectAsState()
    val selectedGoodsSeriesFilter by filterViewModel.selectedSeriesFilter.collectAsState()

    val filteredList = filterFanGoodsList(
        list = filterViewModel.applyFilter(scopedGoods, filterType).second,
        charaFilter = selectedGoodsCharaFilter,
        categoryFilter = selectedGoodsCategoryFilter,
        seriesFilter = selectedGoodsSeriesFilter
    )

    val AllFilteredList = filterFanGoodsList(
        list = filterViewModel.applyFilter(scopedGoods, filterType).first,
        charaFilter = selectedGoodsCharaFilter,
        categoryFilter = selectedGoodsCategoryFilter,
        seriesFilter = selectedGoodsSeriesFilter
    )

    val gridColumns = rememberGridColumns(listModeViewModel, widthSizeClass)
    val isLandscape = widthSizeClass != WindowWidthSizeClass.Compact
    val allCharaList by viewModel.allCharaList.collectAsState()

    val goodsCharaList = scopedGoods
        .flatMap { it.chara.split(",").map { c -> c.trim() } }
        .distinct()
        .filter { it.isNotEmpty() }
        .sortedWith(
            compareByDescending<String> { charaNm -> allCharaList.find { it.charaNm == charaNm }?.charaIsFavorite == true }
                .thenBy { it }
        )
    val goodsCategoryList = scopedGoods
        .map { it.category }
        .distinct()
        .filter { it.isNotEmpty() }
        .sorted()
    // 2차창작은 시리즈를 비워 두기도 해서, 적어 둔 굿즈가 하나도 없으면 시리즈 탭이 안 보인다.
    val goodsSeriesList = seriesOptions(scopedGoods)

    LaunchedEffect(Unit) {
        viewModel.loadGoods()
    }

    // 상태를 바꾼 뒤에도 상세 시트가 최신 값을 보여주도록 목록에서 다시 찾는다.
    val displayedGoods = (selectedGoods as? FanGoodsEntity)?.let { selected ->
        goodsList.find { it.fanGoodsId == selected.fanGoodsId } ?: selected
    }

    displayedGoods?.let { fanGoods ->
        GoodsDetailDialog(
            itemKey = com.example.wowagoodsproject.component.goodsIdentity(fanGoods),
            imgPath = fanGoods.imgPath,
            series = fanGoods.series,
            chara = fanGoods.chara,
            category = fanGoods.category,
            price = fanGoods.price,
            isGotten = fanGoods.isGotten,
            memo = fanGoods.fanGoodsMemo,
            onDismiss = { detailViewModel.dismissDialog() },
            isPending = fanGoods.status == GoodsStatus.PENDING,
            onToggleGotten = { viewModel.toggleGotten(fanGoods) },
            onSetPending = { viewModel.setPending(fanGoods) },
            purchaseInfo = fanGoods.purchaseInfo,
            quantity = fanGoods.quantity,
            onQuantityChange = { q -> viewModel.setQuantity(fanGoods, q) },
            onSavePendingInfo = { info -> viewModel.savePendingInfo(fanGoods, info) },
            onEdit = fanGoods?.let { g -> { com.example.wowagoodsproject.screen.fan.FanEditRequest.open(g.fanGoodsId) } },
            onDelete = {
                viewModel.delete(fanGoods)
                detailViewModel.dismissDialog()
            },
            // 2차창작 시리즈 이름을 누르면 이 탭의 그 시리즈로 간다.
            onSeriesClick = { seriesName ->
                detailViewModel.dismissDialog()
                viewModel.setOpenSeries(seriesName.trim().ifEmpty { NO_FAN_SERIES })
            }
        )
    }

    if (showGoodsFilterDialog) {
        GoodsFilterDialog(
            widthSizeClass = widthSizeClass,
            charaList = allCharaList.filter { chara -> goodsCharaList.contains(chara.charaNm) },
            categoryList = goodsCategoryList,
            selectedCharaFilter = selectedGoodsCharaFilter,
            selectedCategoryFilter = selectedGoodsCategoryFilter,
            onCharaSelect = { filterViewModel.setCharaFilter(it) },
            onCategorySelect = { filterViewModel.setCategoryFilter(it) },
            onClearFilter = { filterViewModel.clearGoodsFilter() },
            onDismiss = { showGoodsFilterDialog = false },
            seriesList = goodsSeriesList,
            selectedSeriesFilter = selectedGoodsSeriesFilter,
            onSeriesSelect = { filterViewModel.setSeriesFilter(it) },
            scopeGoods = scopedGoods
        )
    }

    // 시리즈 추가·편집 화면
    val editingSeries = seriesEdit
    if (editingSeries != null) {
        val seriesVm: SeriesEditViewModel = viewModel(key = "fanSeriesEdit")
        val reg = registeredSeries.firstOrNull { it.seriesNm == editingSeries }
        val target = editingSeries.ifEmpty { null }
        SeriesEditScreen(
            key = "fanSeries:$editingSeries",
            target = target,
            imgPath = reg?.seriesImgPath.orEmpty(),
            iconPath = reg?.seriesIconPath.orEmpty(),
            date = reg?.seriesDate.orEmpty(),
            subtitle = "비공식",
            photoPrefix = "fan_series_",
            viewModel = seriesVm,
            persist = { n, b, i, d -> App.fanDatabase.fanGoodsDao().saveSeries(target, n, b, i, d) },
            onSaved = { saved ->
                if (openSeries != null && openSeries.equals(target, ignoreCase = true)) viewModel.setOpenSeries(saved)
                seriesEdit = null
            },
            onClose = { seriesEdit = null },
            onDelete = if (reg != null) ({ viewModel.deleteSeries(editingSeries); seriesEdit = null }) else null
        )
        return
    }

    // 시리즈 목록에서 '+' 를 누르면 [굿즈 · 시리즈] 메뉴.
    // 시리즈 상세에서는 그 시리즈를 보고 있으므로 메뉴 없이 바로 굿즈 등록(그 시리즈를 미리 채움).
    val addButton: @Composable RowScope.() -> Unit = {
        val current = openSeries
        if (current == null) {
            com.example.wowagoodsproject.component.AddMenuButton(onGoods = { onNavigateToAdd("") }, onSeries = { seriesEdit = "" })
        } else {
            val prefill = if (current == NO_FAN_SERIES) "" else current
            com.example.wowagoodsproject.component.AddMenuButton(onGoods = { onNavigateToAdd(prefill) }, onSeries = null)
        }
    }

    // 시리즈 목록(첫 화면): 명조 공식 시리즈 목록과 같은 모양
    if (openSeries == null) {
        val inGoods = remember(goodsList) { goodsList.flatMap { it.chara.split(",").map { c -> c.trim() } }.filter { it.isNotEmpty() }.toSet() }
        SeriesListPage(
            title = "비공식",
            cards = seriesCardList,
            charaChoices = allCharasForSeries.filter { it.charaNm in inGoods }
                .sortedWith(compareByDescending<com.example.wowagoodsproject.db.character.CharaEntity> { it.charaIsFavorite }.thenBy { it.charaNm }),
            isLandscape = isLandscape,
            onOpen = { viewModel.setOpenSeries(it) },
            emptyTitle = "비공식 굿즈가 없습니다",
            emptyMessage = "오른쪽 위 '+'로 굿즈나 시리즈를 추가하세요",
            actions = { addButton() }
        )
        return
    }
    val openCard = seriesCardList.firstOrNull { it.name.equals(openSeries, ignoreCase = true) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                title = openSeries.orEmpty(),
                subtitle = "비공식",
                onBack = { viewModel.setOpenSeries(null); filterViewModel.clearGoodsFilter() },
                actions = {
                    TopBarAction(
                        icon = Icons.Default.FilterList,
                        contentDescription = "필터",
                        onClick = { showGoodsFilterDialog = true },
                        active = selectedGoodsCharaFilter != null || selectedGoodsCategoryFilter != null ||
                                selectedGoodsSeriesFilter != null
                    )
                    TopBarAction(
                        icon = if (isGridMode) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                        contentDescription = "보기 방식 전환",
                        onClick = { listModeViewModel.toggleGridMode() }
                    )
                    if (openCard != null && openCard.name != NO_FAN_SERIES) {
                        TopBarAction(icon = Icons.Default.Edit, contentDescription = "시리즈 편집", onClick = { seriesEdit = openCard.name })
                    }
                    addButton()
                }
            )

            ActiveFilterChips(
                filters = listOfNotNull(
                    selectedGoodsCharaFilter?.let { ActiveFilter(it) { filterViewModel.setCharaFilter(null) } },
                    selectedGoodsSeriesFilter?.let { ActiveFilter(it) { filterViewModel.setSeriesFilter(null) } },
                    selectedGoodsCategoryFilter?.let { ActiveFilter(it) { filterViewModel.setCategoryFilter(null) } }
                ),
                onClearAll = { filterViewModel.clearGoodsFilter() }
            )

            // 다중 선택으로 고른 굿즈와 일괄 변경 동작
            val picked = selection.visible(filteredList) { it.fanGoodsId }
            val bulk = BulkSelection(
                selectedItems = picked,
                allSelected = filteredList.isNotEmpty() && picked.size == filteredList.size,
                onSelectAll = { selection.selectAll(filteredList.map { it.fanGoodsId }) },
                onClearSelection = { selection.clear() },
                onApply = { status, edit ->
                    viewModel.applyStatus(picked, status, edit)
                    selection.clear()
                    Toast.makeText(context, bulkStatusMessage(picked.size, status), Toast.LENGTH_SHORT).show()
                }
            )
            // 가로 화면에서는 탭·필터를 왼쪽 세로 줄로 옮겨 목록이 쓸 높이를 넓힌다.
            SidePanelLayout(
                sidePanel = isLandscape,
                panel = {
                    FilterBar(
                        filterType = filterType,
                        onFilterChange = { filterViewModel.setFilter(it) },
                        goodsList = AllFilteredList,
                        selectionMode = selection.mode,
                        onToggleSelection = { selection.toggleMode() },
                        vertical = isLandscape,
                        // 다중 선택 중에는 잠긴 필터 칩 대신 그 자리에 선택 조작을 보여준다.
                        selectionContent = { BulkSelectionSlot(bulk, vertical = isLandscape) }
                    )
                }
            ) {
                GridColumnsBar(listModeViewModel, widthSizeClass)
                BulkStatusArea {
                    // 안내 줄은 필터 칩 자리에 있다. 가로 화면에서는 버튼도 왼쪽 줄 안에 있어 여기에 둘 것이 없다.
                    if (selection.mode && !isLandscape) BulkStatusButtons(bulk)

                    FanGoodsListContent(
                        goods = filteredList,
                        isGridMode = isGridMode,
                        gridColumns = gridColumns,
                        onGoodsClick = {
                            if (selection.mode) selection.toggle(it.fanGoodsId) else detailViewModel.toggleGoods(it)
                        },
                        selectedIds = if (selection.mode) selection.ids else null,
                        contentPadding = PaddingValues(bottom = 16.dp)
                    )
                }
            }
        }
    }
}

/** 시리즈를 비워 둔 2차창작 굿즈를 묶는 이름 */
const val NO_FAN_SERIES = "미분류"

