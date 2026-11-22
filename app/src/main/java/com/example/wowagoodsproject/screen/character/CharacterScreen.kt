package com.example.wowagoodsproject.screen.character

import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import android.os.SystemClock
import com.example.wowagoodsproject.component.BulkStatusButtons
import com.example.wowagoodsproject.component.BulkSelectionSlot
import com.example.wowagoodsproject.component.BulkSelection
import com.example.wowagoodsproject.component.SubTabStrip
import com.example.wowagoodsproject.component.SidePanelLayout
import com.example.wowagoodsproject.component.BulkStatusArea
import com.example.wowagoodsproject.component.GridColumnsBar
import com.example.wowagoodsproject.component.rememberGridColumns
import com.example.wowagoodsproject.component.bulkStatusMessage
import com.example.wowagoodsproject.component.rememberGoodsSelection
import com.example.wowagoodsproject.component.swipeToChangeTab
import com.example.wowagoodsproject.component.purchaseInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.zIndex
import com.example.wowagoodsproject.component.mergeFavoriteOrder
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.example.wowagoodsproject.component.ActiveFilter
import com.example.wowagoodsproject.component.ActiveFilterChips
import com.example.wowagoodsproject.component.CATEGORY_SET
import com.example.wowagoodsproject.component.EmptyState
import com.example.wowagoodsproject.component.FanGoodsListContent
import com.example.wowagoodsproject.component.FilterBar
import com.example.wowagoodsproject.component.LineTab
import com.example.wowagoodsproject.component.FilterType
import com.example.wowagoodsproject.component.FilterViewModel
import com.example.wowagoodsproject.component.GoodsDetailDialog
import com.example.wowagoodsproject.component.GoodsDetailViewModel
import com.example.wowagoodsproject.component.GoodsListContent
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.ListModeViewModel
import com.example.wowagoodsproject.component.SearchField
import com.example.wowagoodsproject.component.SetGoodsDetailDialog
import com.example.wowagoodsproject.component.filterFanGoodsList
import com.example.wowagoodsproject.component.filterGoodsList
import com.example.wowagoodsproject.component.seriesOptions
import com.example.wowagoodsproject.component.GoodsFilterDialog
import com.example.wowagoodsproject.component.findSetGoods
import com.example.wowagoodsproject.component.getSetComponents
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.navigation.TopBar
import com.example.wowagoodsproject.navigation.TopBarAction
import com.example.wowagoodsproject.ui.theme.AppStyles

@Composable
fun CharacterScreen(
    widthSizeClass: WindowWidthSizeClass,
    viewModel: CharacterViewModel = viewModel(),
    detailViewModel: GoodsDetailViewModel = viewModel(),
    filterViewModel: FilterViewModel = viewModel(),
    listModeViewModel: ListModeViewModel = viewModel(),
    onNavigateToSeries: (String) -> Unit = {},
    /** 비공식 굿즈의 시리즈 이름을 누르면 비공식 탭의 그 시리즈로 간다 */
    onNavigateToFanSeries: (String) -> Unit = {}
) {
    val charaList by viewModel.charaList.collectAsState()
    val selectedChara by viewModel.selectedChara.collectAsState()
    val officialGoods by viewModel.officialGoods.collectAsState()
    val allSeriesGoods by viewModel.allSeriesGoods.collectAsState()
    val fanGoods by viewModel.fanGoods.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val selectedGoods by detailViewModel.selectedGoods.collectAsState()
    val filterType by filterViewModel.filterType.collectAsState()
    val isGridMode by listModeViewModel.isGridMode.collectAsState()
    val showFavoriteOnly by viewModel.showFavoriteOnly.collectAsState()
    val charaGoodsCountMap by viewModel.charaGoodsCountMap.collectAsState()
    val selectedCategoryFilter by filterViewModel.selectedCategoryFilter.collectAsState()
    val selectedSeriesFilter by filterViewModel.selectedSeriesFilter.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showCategoryFilterDialog by remember { mutableStateOf(false) }
    var selectedSetGoods by remember { mutableStateOf<GoodsEntity?>(null) }
    var setDialogComponent by remember { mutableStateOf<GoodsEntity?>(null) }

    // 선호만 보기에서도 검색어가 먹도록 두 조건을 함께 건다.
    val filteredCharaList = charaList
        .filter { !showFavoriteOnly || it.charaIsFavorite }
        .filter { it.charaNm.contains(searchQuery, ignoreCase = true) }
        .sortedBy { it.charaNm }
    // 선호 캐릭터는 사용자가 정한 우선순위대로, 나머지는 이름순.
    val favoriteCharas = filteredCharaList.filter { it.charaIsFavorite }
        .sortedWith(compareBy<CharaEntity> { it.charaFavoriteOrder }.thenBy { it.charaNm })
    val otherCharas = filteredCharaList.filterNot { it.charaIsFavorite }
    val favoriteTotal = charaList.count { it.charaIsFavorite }
    // 전체 선호 순서(이름). 번호는 검색으로 일부만 보여도 이 전체 순서 기준이다.
    val allFavoriteNames = charaList.filter { it.charaIsFavorite }
        .sortedWith(compareBy<CharaEntity> { it.charaFavoriteOrder }.thenBy { it.charaNm })
        .map { it.charaNm }

    // ── 선호 순서 끌어서 바꾸기 ──
    // ≡ 손잡이는 바로, 줄의 나머지는 꾹 누른 뒤 끈다. 끄는 동안은 [dragPreview] 순서로 그리고, 놓으면 저장한다.
    // 위치는 목록 화면(뷰포트) 좌표로 다룬다: [dragPointer] 손가락 위치, [dragGrab] 잡은 곳(줄 안 좌표).
    val charaGridState = rememberLazyGridState()
    val dragScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val edgePx = with(LocalDensity.current) { 56.dp.toPx() }
    var dragPreview by remember { mutableStateOf<List<CharaEntity>?>(null) }
    var dragName by remember { mutableStateOf<String?>(null) }
    var dragPointer by remember { mutableStateOf(Offset.Zero) }
    var dragGrab by remember { mutableStateOf(Offset.Zero) }
    // 꾹 눌렀다 움직이지 않고 떼면 줄 누름(캐릭터 열기)까지 같이 들어오므로, 끌기가 끝난 직후의 누름은 무시한다.
    var dragEndedAt by remember { mutableLongStateOf(0L) }
    val latestFavorites by rememberUpdatedState(favoriteCharas)
    // 저장한 순서가 DB 에서 돌아오면 미리보기를 걷는다(놓자마자 걷으면 잠깐 옛 순서로 튄다).
    LaunchedEffect(favoriteCharas) { if (dragName == null) dragPreview = null }
    val shownFavorites = dragPreview ?: favoriteCharas
    val shownFullOrder = dragPreview?.let { mergeFavoriteOrder(allFavoriteNames, it.map { c -> c.charaNm }) } ?: allFavoriteNames

    val startDrag: (CharaEntity, Offset) -> Unit = start@{ chara, local ->
        val info = charaGridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == chara.charaNm } ?: return@start
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        dragName = chara.charaNm
        dragPreview = latestFavorites
        dragGrab = local
        dragPointer = Offset(info.offset.x + local.x, info.offset.y + local.y)
    }
    val moveDrag: (Offset) -> Unit = move@{ delta ->
        val name = dragName ?: return@move
        val order = dragPreview ?: return@move
        dragPointer += delta
        val target = charaGridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            val key = info.key as? String
            key != null && key != name && order.any { it.charaNm == key } &&
                dragPointer.y >= info.offset.y && dragPointer.y < info.offset.y + info.size.height
        }?.key as? String
        if (target != null) {
            val from = order.indexOfFirst { it.charaNm == name }
            val to = order.indexOfFirst { it.charaNm == target }
            if (from >= 0 && to >= 0) dragPreview = order.toMutableList().apply { add(to, removeAt(from)) }
        }
        // 목록 위·아래 끝 근처로 끌면 그쪽으로 스크롤한다.
        val viewport = charaGridState.layoutInfo.viewportSize.height
        if (dragPointer.y < edgePx) dragScope.launch { charaGridState.scrollBy(-24f) }
        else if (dragPointer.y > viewport - edgePx) dragScope.launch { charaGridState.scrollBy(24f) }
    }
    val endDrag: () -> Unit = {
        val order = dragPreview
        val changed = order != null && order.map { it.charaNm } != latestFavorites.map { it.charaNm }
        if (changed) viewModel.reorderFavorites(order!!.map { it.charaNm }) else dragPreview = null
        dragName = null
        dragEndedAt = SystemClock.uptimeMillis()
    }

    val categoryList =
        (filterViewModel.applyFilter(officialGoods, filterType).second + filterViewModel.applyFilter(fanGoods, filterType).second)
            .map { it.category }
            .distinct()
            .filter { it.isNotEmpty() && it != CATEGORY_SET }
            .sorted()

    // 시리즈 탭에 보여 줄 값. 보유 상태 필터를 건 목록에서 뽑는다.
    val seriesList = seriesOptions(
        filterViewModel.applyFilter(officialGoods, filterType).second +
                filterViewModel.applyFilter(fanGoods, filterType).second
    )
    val filteredOfficialGoods = filterGoodsList(
        list = filterViewModel.applyFilter(officialGoods, filterType).second,
        categoryFilter = selectedCategoryFilter,
        seriesFilter = selectedSeriesFilter
    )

    val AllFilteredOfficialGoods = filterGoodsList(
        list = filterViewModel.applyFilter(officialGoods, filterType).first,
        categoryFilter = selectedCategoryFilter,
        seriesFilter = selectedSeriesFilter
    )

    val filteredFanGoods = filterFanGoodsList(
        list = filterViewModel.applyFilter(fanGoods, filterType).second,
        categoryFilter = selectedCategoryFilter,
        seriesFilter = selectedSeriesFilter
    )

    val AllFilteredFanGoods = filterFanGoodsList(
        list = filterViewModel.applyFilter(fanGoods, filterType).first,
        categoryFilter = selectedCategoryFilter,
        seriesFilter = selectedSeriesFilter
    )
    val gridColumns = rememberGridColumns(listModeViewModel, widthSizeClass)
    val isLandscape = widthSizeClass != WindowWidthSizeClass.Compact

    val context = LocalContext.current
    val selection = rememberGoodsSelection()
    // 공식/2차창작은 id 가 겹칠 수 있으니 탭을 옮기면 선택을 비운다.
    LaunchedEffect(selectedTab) { selection.clear() }

    val closeChara: () -> Unit = {
        selection.exit()
        viewModel.clearSelectedChara()
        viewModel.setSelectedTab(0)
        filterViewModel.setFilter(FilterType.ALL)
        filterViewModel.clearGoodsFilter()
    }

    // 하단 메뉴를 다시 눌러 캐릭터 목록으로 돌아온 경우에도 다중 선택을 끝낸다.
    LaunchedEffect(selectedChara == null) {
        if (selectedChara == null) {
            selection.exit()
        }
    }

    BackHandler(enabled = selectedChara != null) { closeChara() }
    // 나중에 등록한 BackHandler 가 먼저 받으므로, 선택 중이면 뒤로가기는 선택만 끝낸다.
    BackHandler(enabled = selection.mode) { selection.exit() }

    // 시리즈/카테고리 필터 팝업. 이미 한 캐릭터만 보는 화면이라 캐릭터 탭은 뺀다.
    if (showCategoryFilterDialog) {
        GoodsFilterDialog(
            widthSizeClass = widthSizeClass,
            charaList = emptyList(),
            categoryList = categoryList,
            selectedCharaFilter = null,
            selectedCategoryFilter = selectedCategoryFilter,
            onCharaSelect = {},
            onCategorySelect = { filterViewModel.setCategoryFilter(it) },
            onClearFilter = { filterViewModel.clearGoodsFilter() },
            onDismiss = { showCategoryFilterDialog = false },
            seriesList = seriesList,
            selectedSeriesFilter = selectedSeriesFilter,
            onSeriesSelect = { filterViewModel.setSeriesFilter(it) },
            showChara = false,
            // 이 캐릭터의 굿즈(보유 상태 필터 적용)에서만 대표 사진을 고른다.
            scopeGoods = filterViewModel.applyFilter(officialGoods, filterType).second +
                    filterViewModel.applyFilter(fanGoods, filterType).second
        )
    }

    // 세트 다이얼로그에서 상태를 바꿔도 아래에 깔린 굿즈 상세가 최신 값을 보여주도록 다시 조회한다.
    val displayedGoods = selectedGoods?.let { selected ->
        when (selected) {
            is GoodsEntity -> allSeriesGoods.find { it.goodsId == selected.goodsId } ?: selected
            is FanGoodsEntity -> fanGoods.find { it.fanGoodsId == selected.fanGoodsId } ?: selected
            else -> selected
        }
    }

    displayedGoods?.let { goods ->
        val officialGoods = goods as? GoodsEntity
        val fanGoods = goods as? FanGoodsEntity
        val parentSet = officialGoods?.let { findSetGoods(it, allSeriesGoods) }
        val setComponents = parentSet?.let { getSetComponents(it, allSeriesGoods) } ?: emptyList()
        GoodsDetailDialog(
            itemKey = com.example.wowagoodsproject.component.goodsIdentity(goods),
            imgPath = goods.imgPath,
            series = goods.series,
            chara = goods.chara,
            category = goods.category,
            price = goods.price,
            isGotten = goods.isGotten,
            memo = (goods as? GoodsEntity)?.goodsMemo ?: (goods as? FanGoodsEntity)?.fanGoodsMemo
            ?: "",
            setGoods = parentSet,
            setComponentTotal = setComponents.size,
            setComponentGotten = setComponents.count { it.isGotten },
            onSetClick = {
                setDialogComponent = officialGoods
                selectedSetGoods = parentSet
            },
            onDismiss = { detailViewModel.dismissDialog() },
            // 상태를 바꿔도 시트를 닫지 않고 바뀐 값을 바로 보여준다.
            onToggleGotten = {
                officialGoods?.let { viewModel.toggleOfficialGotten(it) }
                fanGoods?.let { viewModel.toggleFanGotten(it) }
            },
            isPending = goods.status == GoodsStatus.PENDING,
            purchaseInfo = goods.purchaseInfo,
            quantity = goods.quantity,
            onQuantityChange = { q ->
                officialGoods?.let { viewModel.setOfficialQuantity(it, q) }
                fanGoods?.let { viewModel.setFanQuantity(it, q) }
            },
            onSavePendingInfo = { info ->
                officialGoods?.let { viewModel.saveOfficialPendingInfo(it, info) }
                fanGoods?.let { viewModel.saveFanPendingInfo(it, info) }
            },
            onSetPending = {
                officialGoods?.let { viewModel.setOfficialPending(it) }
                fanGoods?.let { viewModel.setFanPending(it) }
            },
            onEdit = fanGoods?.let { g -> { com.example.wowagoodsproject.screen.fan.FanEditRequest.open(g.fanGoodsId) } },
            onDelete = {},
            showDelete = false,
            onSeriesClick = { seriesName ->
                detailViewModel.dismissDialog()
                if (fanGoods != null) onNavigateToFanSeries(seriesName) else onNavigateToSeries(seriesName)
            }
        )
    }

    // 세트 굿즈 다이얼로그
    selectedSetGoods?.let { setGoods ->
        SetGoodsDetailDialog(
            setGoods = setGoods,
            components = getSetComponents(setGoods, allSeriesGoods),
            initialComponent = setDialogComponent,
            onDismiss = { selectedSetGoods = null; setDialogComponent = null },
            onToggleGotten = { component -> viewModel.toggleOfficialGotten(component) },
            onSetPending = { component -> viewModel.setOfficialPending(component) },
            onBulkSetStatus = { status ->
                viewModel.bulkSetOfficialStatus(setGoods, status)
                selectedSetGoods = null
                setDialogComponent = null
            },
            highlightChara = selectedChara?.charaNm,
            highlightCategory = selectedCategoryFilter,
            onSavePendingInfo = { component, info -> viewModel.saveOfficialPendingInfo(component, info) },
            onBulkSavePending = { info ->
                viewModel.bulkSetOfficialStatus(setGoods, GoodsStatus.PENDING, info)
                selectedSetGoods = null
                setDialogComponent = null
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val chara = selectedChara
        if (chara == null) {
            TopBar(title = "캐릭터별")

            // 가로 화면에서는 이름 검색과 전체/선호 버튼을 왼쪽 세로 줄로 옮겨 목록이 쓸 높이를 넓힌다.
            SidePanelLayout(
                sidePanel = isLandscape,
                panelWidth = CHARA_SEARCH_PANEL_WIDTH,
                panel = {
                    // 보유 상태 필터와 같은 박스 없는 선 탭
                    val allChip: @Composable (Modifier) -> Unit = { modifier ->
                        LineTab(
                            label = "전체",
                            count = charaList.size,
                            selected = !showFavoriteOnly,
                            onClick = { if (showFavoriteOnly) viewModel.toggleFavoriteOnly() },
                            vertical = isLandscape,
                            modifier = modifier
                        )
                    }
                    val favoriteChip: @Composable (Modifier) -> Unit = { modifier ->
                        LineTab(
                            label = "선호",
                            count = favoriteTotal,
                            selected = showFavoriteOnly,
                            onClick = { if (!showFavoriteOnly) viewModel.toggleFavoriteOnly() },
                            vertical = isLandscape,
                            leadingIcon = {
                                Icon(
                                    imageVector = if (showFavoriteOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = null,
                                    tint = AppStyles.colorFavorite,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = modifier
                        )
                    }
                    Column(
                        modifier = Modifier.padding(horizontal = if (isLandscape) 8.dp else 16.dp, vertical = 8.dp)
                    ) {
                        SearchField(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            placeholder = if (isLandscape) "이름 검색" else "캐릭터 이름 검색"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (isLandscape) {
                            allChip(Modifier.fillMaxWidth())
                            favoriteChip(Modifier.fillMaxWidth())
                        } else {
                            Row {
                                allChip(Modifier)
                                favoriteChip(Modifier)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            ) {
                if (filteredCharaList.isEmpty()) {
                    when {
                        searchQuery.isNotEmpty() -> EmptyState(
                            icon = Icons.Default.SearchOff,
                            title = "'$searchQuery' 검색 결과가 없습니다"
                        )
                        showFavoriteOnly -> EmptyState(
                            icon = Icons.Default.FavoriteBorder,
                            title = "선호 캐릭터가 없습니다",
                            message = "캐릭터 카드의 하트를 눌러 선호 캐릭터로 등록하세요"
                        )
                        else -> EmptyState(icon = Icons.Default.SearchOff, title = "캐릭터가 없습니다")
                    }
                } else {
                    // 선호/그 외가 섞여 있을 때만 구역 제목을 단다.
                    val showSections = favoriteCharas.isNotEmpty() && otherCharas.isNotEmpty()
                    LazyVerticalGrid(
                        // 리스트형 한 줄 카드. 가로 화면도 한 줄에 하나만 둬 선호 순서를 위에서 아래로 읽게 한다.
                        columns = GridCells.Fixed(1),
                        state = charaGridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (showSections) sectionHeader("선호 캐릭터", favoriteCharas.size)
                        items(shownFavorites, key = { it.charaNm }) { item ->
                            val count = charaGoodsCountMap[item.charaNm] ?: Pair(0, 0)
                            val dragging = dragName == item.charaNm
                            val canReorder = allFavoriteNames.size >= 2
                            // 끄는 줄은 손가락을 따라 떠 있고(맨 위에 그림), 나머지 줄은 자리를 비켜 주며 움직인다.
                            val itemModifier = if (dragging) {
                                val info = charaGridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == item.charaNm }
                                Modifier
                                    .zIndex(1f)
                                    .graphicsLayer {
                                        if (info != null) {
                                            translationY = dragPointer.y - dragGrab.y - info.offset.y
                                        }
                                        shadowElevation = 8.dp.toPx()
                                    }
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            } else Modifier.animateItem()
                            Box(
                                modifier = itemModifier.then(
                                    if (canReorder) Modifier.pointerInput(item.charaNm) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { startDrag(item, it) },
                                            onDrag = { change, delta -> change.consume(); moveDrag(delta) },
                                            onDragEnd = endDrag,
                                            onDragCancel = endDrag
                                        )
                                    } else Modifier
                                )
                            ) {
                                CharaCard(
                                    chara = item,
                                    gotten = count.first,
                                    total = count.second,
                                    onClick = {
                                        if (SystemClock.uptimeMillis() - dragEndedAt > 400) viewModel.selectChara(item)
                                    },
                                    onToggleFavorite = { viewModel.toggleFavorite(item) },
                                    // 선호가 둘 이상일 때만 ≡ 손잡이와 순서 번호를 둔다.
                                    orderNumber = if (canReorder) shownFullOrder.indexOf(item.charaNm) + 1 else null,
                                    handleModifier = Modifier.pointerInput(item.charaNm) {
                                        detectDragGestures(
                                            onDragStart = { local -> startDrag(item, Offset(local.x + 4.dp.toPx(), local.y + 10.dp.toPx())) },
                                            onDrag = { change, delta -> change.consume(); moveDrag(delta) },
                                            onDragEnd = endDrag,
                                            onDragCancel = endDrag
                                        )
                                    }
                                )
                            }
                        }
                        if (showSections) sectionHeader("다른 캐릭터", otherCharas.size)
                        items(otherCharas, key = { it.charaNm }) { item ->
                            val count = charaGoodsCountMap[item.charaNm] ?: Pair(0, 0)
                            CharaCard(
                                chara = item,
                                gotten = count.first,
                                total = count.second,
                                onClick = { viewModel.selectChara(item) },
                                onToggleFavorite = { viewModel.toggleFavorite(item) }
                            )
                        }
                    }
                }
            }
        } else {
            val count = charaGoodsCountMap[chara.charaNm] ?: Pair(0, 0)

            TopBar(
                title = chara.charaNm,
                subtitle = "공식 굿즈 ${count.first} / ${count.second} 보유",
                onBack = closeChara,
                actions = {
                    TopBarAction(
                        icon = Icons.Default.FilterList,
                        contentDescription = "필터",
                        onClick = { showCategoryFilterDialog = true },
                        active = selectedCategoryFilter != null || selectedSeriesFilter != null
                    )
                    TopBarAction(
                        icon = if (isGridMode) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                        contentDescription = "보기 방식 전환",
                        onClick = { listModeViewModel.toggleGridMode() }
                    )
                }
            )

            ActiveFilterChips(
                filters = listOfNotNull(
                    selectedSeriesFilter?.let { ActiveFilter(it) { filterViewModel.setSeriesFilter(null) } },
                    selectedCategoryFilter?.let { ActiveFilter(it) { filterViewModel.setCategoryFilter(null) } }
                )
            )

            // 다중 선택으로 고른 굿즈와 일괄 변경 동작. 보고 있는 탭(공식/2차창작)의 것만 다룬다.
            val pickedOfficial = selection.visible(filteredOfficialGoods) { it.goodsId }
            val pickedFan = selection.visible(filteredFanGoods) { it.fanGoodsId }
            val bulk = if (selectedTab == 0) {
                BulkSelection(
                    selectedItems = pickedOfficial,
                    allSelected = filteredOfficialGoods.isNotEmpty() && pickedOfficial.size == filteredOfficialGoods.size,
                    onSelectAll = { selection.selectAll(filteredOfficialGoods.map { it.goodsId }) },
                    onClearSelection = { selection.clear() },
                    onApply = { status, edit ->
                        viewModel.applyOfficialStatus(pickedOfficial, status, edit)
                        selection.clear()
                        Toast.makeText(context, bulkStatusMessage(pickedOfficial.size, status), Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                BulkSelection(
                    selectedItems = pickedFan,
                    allSelected = filteredFanGoods.isNotEmpty() && pickedFan.size == filteredFanGoods.size,
                    onSelectAll = { selection.selectAll(filteredFanGoods.map { it.fanGoodsId }) },
                    onClearSelection = { selection.clear() },
                    onApply = { status, edit ->
                        viewModel.applyFanStatus(pickedFan, status, edit)
                        selection.clear()
                        Toast.makeText(context, bulkStatusMessage(pickedFan.size, status), Toast.LENGTH_SHORT).show()
                    }
                )
            }
            // 가로 화면에서는 탭·필터를 왼쪽 세로 줄로 옮겨 목록이 쓸 높이를 넓힌다(필터 줄이 맨 왼쪽, 탭 줄이 그 오른쪽).
            SidePanelLayout(
                sidePanel = isLandscape,
                tabs = {
                    // 다중 선택 중에는 탭을 못 바꾸게 막는다.
                    SubTabStrip(
                        tabs = listOf("공식 (${AllFilteredOfficialGoods.size})", "2차창작 (${AllFilteredFanGoods.size})"),
                        selectedIndex = selectedTab,
                        onSelect = { viewModel.setSelectedTab(it) },
                        vertical = isLandscape,
                        enabled = !selection.mode
                    )
                },
                panel = {
                    FilterBar(
                        filterType = filterType,
                        onFilterChange = { filterViewModel.setFilter(it) },
                        goodsList = if (selectedTab == 0) AllFilteredOfficialGoods else AllFilteredFanGoods,
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

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // 다중 선택 중에는 탭을 못 바꾸게 막는다.
                            .swipeToChangeTab(selectedTab, 2) { if (!selection.mode) viewModel.setSelectedTab(it) }
                    ) {
                        when (selectedTab) {
                            0 -> {
                                GoodsListContent(
                                    goods = filteredOfficialGoods,
                                    isGridMode = isGridMode,
                                    gridColumns = gridColumns,
                                    onGoodsClick = {
                                        if (selection.mode) selection.toggle(it.goodsId) else detailViewModel.toggleGoods(it)
                                    },
                                    selectedIds = if (selection.mode) selection.ids else null
                                )
                            }

                            1 -> {
                                FanGoodsListContent(
                                    goods = filteredFanGoods,
                                    isGridMode = isGridMode,
                                    gridColumns = gridColumns,
                                    onGoodsClick = {
                                        if (selection.mode) selection.toggle(it.fanGoodsId) else detailViewModel.toggleGoods(it)
                                    },
                                    selectedIds = if (selection.mode) selection.ids else null
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 가로 화면에서 이름 검색창과 전체/선호 버튼을 세우는 줄의 폭 */
private val CHARA_SEARCH_PANEL_WIDTH = 176.dp

private fun LazyGridScope.sectionHeader(title: String, count: Int) {
    item(key = "__header_$title", span = { GridItemSpan(maxLineSpan) }) {
        Row(
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** 선호 캐릭터 줄 맨 왼쪽의 끌기 손잡이(≡)와 순서 번호. 손잡이를 잡고 끌면 바로 옮겨진다. */
@Composable
private fun ReorderHandle(orderNumber: Int, charaNm: String, modifier: Modifier) {
    Row(
        modifier = modifier
            .height(48.dp)
            .semantics { contentDescription = "$charaNm 순서 ${orderNumber}번, 끌어서 바꾸기" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.DragHandle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = "$orderNumber",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 22.dp)
        )
    }
}

/**
 * 캐릭터 목록의 한 줄.
 * 왼쪽: (선호면 ≡ 손잡이 + 순서 번호) + 캐릭터 사진 + 이름. 오른쪽: 보유율 게이지 + 보유/전체 개수와 퍼센트, 맨 끝에 선호 하트.
 */
@Composable
private fun CharaCard(
    chara: CharaEntity,
    gotten: Int,
    total: Int,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    /** 선호 캐릭터 순서 번호(1부터). 있으면 사진 왼쪽에 ≡ 손잡이와 번호를 둔다. */
    orderNumber: Int? = null,
    /** ≡ 손잡이에 붙일 끌기 동작 */
    handleModifier: Modifier = Modifier
) {
    val progress = if (total > 0) gotten.toFloat() / total else 0f
    val isComplete = total > 0 && gotten == total

    // 박스 없이 줄만 두고, 줄 사이는 아래쪽 얇은 구분선으로 나눈다.
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 왼쪽 섹션: (순서 화살표) + 사진 + 이름
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (orderNumber != null) {
                    ReorderHandle(orderNumber = orderNumber, charaNm = chara.charaNm, modifier = handleModifier)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Image(
                    painter = rememberAsyncImagePainter(model = chara.charaUrl.ifEmpty { null }),
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp) // 폭 384dp 휴대폰에서도 이름이 잘리지 않게 사진·게이지·하트를 조금씩 줄였다
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = chara.charaNm,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            // 오른쪽 섹션: 게이지 + 개수/퍼센트. 퍼센트는 버림이라 다 모았을 때만 100%가 된다.
            Column(modifier = Modifier.width(112.dp)) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = if (isComplete) AppStyles.colorGotten else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$gotten / $total",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isComplete) AppStyles.colorGotten else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isComplete) AppStyles.colorGotten else MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }
            // 선호 하트는 줄 맨 오른쪽 끝, 퍼센트 옆에 둔다.
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (chara.charaIsFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (chara.charaIsFavorite) "선호 해제" else "선호 캐릭터로 등록",
                    tint = if (chara.charaIsFavorite) AppStyles.colorFavorite
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
