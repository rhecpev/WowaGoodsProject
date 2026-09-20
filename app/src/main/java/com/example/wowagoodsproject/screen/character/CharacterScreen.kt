package com.example.wowagoodsproject.screen.character

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
import com.example.wowagoodsproject.component.CategoryChoiceList
import com.example.wowagoodsproject.component.EmptyState
import com.example.wowagoodsproject.component.FanGoodsListContent
import com.example.wowagoodsproject.component.FilterBar
import com.example.wowagoodsproject.component.FilterDialogFrame
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
    onNavigateToSeries: (String) -> Unit = {}
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

    var searchQuery by remember { mutableStateOf("") }
    var showCategoryFilterDialog by remember { mutableStateOf(false) }
    var categorySearch by remember { mutableStateOf("") }
    var selectedSetGoods by remember { mutableStateOf<GoodsEntity?>(null) }
    var setDialogComponent by remember { mutableStateOf<GoodsEntity?>(null) }

    // 선호만 보기에서도 검색어가 먹도록 두 조건을 함께 건다.
    val filteredCharaList = charaList
        .filter { !showFavoriteOnly || it.charaIsFavorite }
        .filter { it.charaNm.contains(searchQuery, ignoreCase = true) }
        .sortedBy { it.charaNm }
    val favoriteCharas = filteredCharaList.filter { it.charaIsFavorite }
    val otherCharas = filteredCharaList.filterNot { it.charaIsFavorite }
    val favoriteTotal = charaList.count { it.charaIsFavorite }

    val categoryList =
        (filterViewModel.applyFilter(officialGoods).second + filterViewModel.applyFilter(fanGoods).second)
            .map { it.category }
            .distinct()
            .filter { it.isNotEmpty() && it != CATEGORY_SET }
            .sorted()

    val filteredCategories = categoryList.filter {
        it.contains(categorySearch, ignoreCase = true)
    }
    val filteredOfficialGoods = filterGoodsList(
        list = filterViewModel.applyFilter(officialGoods).second,
        categoryFilter = selectedCategoryFilter
    )

    val AllFilteredOfficialGoods = filterGoodsList(
        list = filterViewModel.applyFilter(officialGoods).first,
        categoryFilter = selectedCategoryFilter
    )

    val filteredFanGoods = filterFanGoodsList(
        list = filterViewModel.applyFilter(fanGoods).second,
        categoryFilter = selectedCategoryFilter
    )

    val AllFilteredFanGoods = filterFanGoodsList(
        list = filterViewModel.applyFilter(fanGoods).first,
        categoryFilter = selectedCategoryFilter
    )
    val gridColumns = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> 2
        WindowWidthSizeClass.Medium -> 3
        else -> 4
    }

    val closeChara: () -> Unit = {
        viewModel.clearSelectedChara()
        viewModel.setSelectedTab(0)
        filterViewModel.setFilter(FilterType.ALL)
        filterViewModel.clearGoodsFilter()
        categorySearch = ""
    }

    BackHandler(enabled = selectedChara != null) { closeChara() }

    // 카테고리 필터 팝업
    if (showCategoryFilterDialog) {
        val closeDialog: () -> Unit = {
            showCategoryFilterDialog = false
            categorySearch = ""
        }
        FilterDialogFrame(
            title = "카테고리 필터",
            onDismiss = closeDialog,
            onClear = if (selectedCategoryFilter != null) {
                { filterViewModel.clearGoodsFilter(); closeDialog() }
            } else null
        ) {
            SearchField(
                query = categorySearch,
                onQueryChange = { categorySearch = it },
                placeholder = "카테고리 검색",
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
            Spacer(modifier = Modifier.height(12.dp))
            CategoryChoiceList(
                categories = filteredCategories,
                selected = selectedCategoryFilter,
                onSelect = { cat ->
                    filterViewModel.setCategoryFilter(if (selectedCategoryFilter == cat) null else cat)
                    closeDialog()
                },
                modifier = Modifier.weight(1f)
            )
        }
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
            onSetPending = {
                officialGoods?.let { viewModel.setOfficialPending(it) }
                fanGoods?.let { viewModel.setFanPending(it) }
            },
            onDelete = {},
            showDelete = false,
            onSeriesClick = { seriesName ->
                detailViewModel.dismissDialog()
                onNavigateToSeries(seriesName)
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
            highlightCategory = selectedCategoryFilter
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val chara = selectedChara
        if (chara == null) {
            TopBar(title = "캐릭터별")

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "캐릭터 이름 검색"
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !showFavoriteOnly,
                        onClick = { if (showFavoriteOnly) viewModel.toggleFavoriteOnly() },
                        label = { Text("전체 ${charaList.size}") }
                    )
                    FilterChip(
                        selected = showFavoriteOnly,
                        onClick = { if (!showFavoriteOnly) viewModel.toggleFavoriteOnly() },
                        label = { Text("선호 $favoriteTotal") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (showFavoriteOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = AppStyles.colorFavorite,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    )
                }
            }

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
                    columns = GridCells.Adaptive(minSize = 96.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (showSections) sectionHeader("선호 캐릭터", favoriteCharas.size)
                    items(favoriteCharas, key = { it.charaNm }) { item ->
                        val count = charaGoodsCountMap[item.charaNm] ?: Pair(0, 0)
                        CharaCard(
                            chara = item,
                            gotten = count.first,
                            total = count.second,
                            onClick = { viewModel.selectChara(item) },
                            onToggleFavorite = { viewModel.toggleFavorite(item) }
                        )
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
        } else {
            // 선택 당시 값이 아니라 DB 최신 값으로 하트 상태를 보여준다.
            val liveChara = charaList.find { it.charaNm == chara.charaNm } ?: chara
            val count = charaGoodsCountMap[chara.charaNm] ?: Pair(0, 0)

            TopBar(
                title = chara.charaNm,
                subtitle = "공식 굿즈 ${count.first} / ${count.second} 보유",
                onBack = closeChara,
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite(liveChara) }) {
                        Icon(
                            imageVector = if (liveChara.charaIsFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (liveChara.charaIsFavorite) "선호 해제" else "선호 캐릭터로 등록",
                            tint = if (liveChara.charaIsFavorite) AppStyles.colorFavorite else LocalContentColor.current
                        )
                    }
                    TopBarAction(
                        icon = Icons.Default.FilterList,
                        contentDescription = "카테고리 필터",
                        onClick = { showCategoryFilterDialog = true },
                        active = selectedCategoryFilter != null
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
                    selectedCategoryFilter?.let { ActiveFilter(it) { filterViewModel.setCategoryFilter(null) } }
                )
            )

            SecondaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { viewModel.setSelectedTab(0) },
                    text = { Text("공식 (${AllFilteredOfficialGoods.size})") })
                Tab(
                    selected = selectedTab == 1,
                    onClick = { viewModel.setSelectedTab(1) },
                    text = { Text("2차창작 (${AllFilteredFanGoods.size})") })
            }

            FilterBar(
                filterType = filterType,
                onFilterChange = { filterViewModel.setFilter(it) },
                goodsList = if (selectedTab == 0) AllFilteredOfficialGoods else AllFilteredFanGoods
            )

            when (selectedTab) {
                0 -> {
                    GoodsListContent(
                        goods = filteredOfficialGoods,
                        isGridMode = isGridMode,
                        gridColumns = gridColumns,
                        onGoodsClick = { detailViewModel.selectGoods(it) }
                    )
                }

                1 -> {
                    FanGoodsListContent(
                        goods = filteredFanGoods,
                        isGridMode = isGridMode,
                        gridColumns = gridColumns,
                        onGoodsClick = { detailViewModel.selectGoods(it) }
                    )
                }
            }
        }
    }
}

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

/** 원형 아바타 + 이름 + 공식 굿즈 수집 진행률. 오른쪽 위 하트로 선호를 바로 토글한다. */
@Composable
private fun CharaCard(
    chara: CharaEntity,
    gotten: Int,
    total: Int,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val progress = if (total > 0) gotten.toFloat() / total else 0f
    val isComplete = total > 0 && gotten == total

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(
            width = 1.dp,
            color = if (chara.charaIsFavorite) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = rememberAsyncImagePainter(model = chara.charaUrl.ifEmpty { null }),
                    contentDescription = null,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = chara.charaNm,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape),
                    color = if (isComplete) AppStyles.colorGotten else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$gotten / $total",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isComplete) AppStyles.colorGotten else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = if (chara.charaIsFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (chara.charaIsFavorite) "선호 해제" else "선호 캐릭터로 등록",
                    tint = if (chara.charaIsFavorite) AppStyles.colorFavorite
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
