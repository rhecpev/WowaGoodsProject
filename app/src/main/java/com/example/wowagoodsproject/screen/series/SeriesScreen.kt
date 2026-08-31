package com.example.wowagoodsproject.screen.series

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.example.wowagoodsproject.component.CATEGORY_SET
import com.example.wowagoodsproject.component.FilterBar
import com.example.wowagoodsproject.component.FilterType
import com.example.wowagoodsproject.component.FilterViewModel
import com.example.wowagoodsproject.component.GoodsDetailDialog
import com.example.wowagoodsproject.component.GoodsDetailViewModel
import com.example.wowagoodsproject.component.GoodsFilterDialog
import com.example.wowagoodsproject.component.GoodsListContent
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.ListModeViewModel
import com.example.wowagoodsproject.component.SetGoodsDetailDialog
import com.example.wowagoodsproject.component.filterGoodsList
import com.example.wowagoodsproject.component.findSetGoods
import com.example.wowagoodsproject.component.getSetComponents
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.navigation.TopBar
import com.example.wowagoodsproject.ui.theme.AppStyles

@Composable
fun SeriesScreen(
    widthSizeClass: WindowWidthSizeClass,
    viewModel: SeriesViewModel = viewModel(),
    filterViewModel: FilterViewModel = viewModel(),
    detailViewModel: GoodsDetailViewModel = viewModel(),
    listModeViewModel: ListModeViewModel = viewModel()
) {
    val filteredList by viewModel.filteredSeriesList.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val allCharaList by viewModel.allCharaList.collectAsState()
    val selectedCharaFilter by viewModel.selectedCharaFilter.collectAsState()
    val selectedSeries by viewModel.selectedSeries.collectAsState()
    val seriesGoods by viewModel.seriesGoods.collectAsState()
    val filterType by filterViewModel.filterType.collectAsState()
    val selectedGoods by detailViewModel.selectedGoods.collectAsState()
    val isGridMode by listModeViewModel.isGridMode.collectAsState()
    val seriesCharaCountMap by viewModel.seriesCharaCountMap.collectAsState()
    val seriesList by viewModel.seriesList.collectAsState()

    // ViewModel에서 collectAsState로 변경
    val selectedGoodsCharaFilter by filterViewModel.selectedCharaFilter.collectAsState()
    val selectedGoodsCategoryFilter by filterViewModel.selectedCategoryFilter.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    val isLandscape = widthSizeClass != WindowWidthSizeClass.Compact
    var showCharaFilterDialog by remember { mutableStateOf(false) }
    var selectedSetGoods by remember { mutableStateOf<GoodsEntity?>(null) }
    var setDialogComponent by remember { mutableStateOf<GoodsEntity?>(null) }
    var showGoodsFilterDialog by remember { mutableStateOf(false) }

    val sortedCharaList = allCharaList
        .sortedWith(compareByDescending<CharaEntity> { it.charaIsFavorite }.thenBy { it.charaNm })
        .filter { it.charaNm.contains(searchQuery, ignoreCase = true) }

    val gridColumns = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> 2
        WindowWidthSizeClass.Medium -> 3
        else -> 4
    }

    val goodsCharaList = seriesGoods
        .flatMap { it.chara.split(",").map { c -> c.trim() } }
        .distinct()
        .filter { it.isNotEmpty() }
        .sortedWith(
            compareByDescending<String> { charaNm -> allCharaList.find { it.charaNm == charaNm }?.charaIsFavorite == true }
                .thenBy { it }
        )
    val goodsCategoryList = seriesGoods
        .map { it.category }
        .distinct()
        .filter { it.isNotEmpty() && it != CATEGORY_SET }  // CATEGORY_SET 추가
        .sorted()


    val filteredGoods = filterGoodsList(
        list = filterViewModel.applyFilter(seriesGoods).second,
        charaFilter = selectedGoodsCharaFilter,
        categoryFilter = selectedGoodsCategoryFilter
    )
    val AllSeriesGoods = filterGoodsList(
        list = filterViewModel.applyFilter(seriesGoods).first,
        charaFilter = selectedGoodsCharaFilter,
        categoryFilter = selectedGoodsCategoryFilter
    )
    BackHandler(enabled = selectedSeries != null) {
        viewModel.clearSelectedSeries()
        filterViewModel.setFilter(FilterType.ALL)
        filterViewModel.clearGoodsFilter()
    }

    // 세트 다이얼로그에서 상태를 바꿔도 아래에 깔린 굿즈 다이얼로그가 최신 값을 보여주도록 다시 조회한다.
    val displayedGoods = selectedGoods?.let { selected ->
        (selected as? GoodsEntity)?.let { sel -> seriesGoods.find { it.goodsId == sel.goodsId } } ?: selected
    }

    displayedGoods?.let { goods ->
        val officialGoods = goods as? GoodsEntity
        val parentSet = officialGoods?.let { findSetGoods(it, seriesGoods) }
        val setComponents = parentSet?.let { getSetComponents(it, seriesGoods) } ?: emptyList()
        GoodsDetailDialog(
            imgPath = goods.imgPath,
            series = goods.series,
            chara = goods.chara,
            category = goods.category,
            price = goods.price,
            isGotten = goods.isGotten,
            memo = (goods as? GoodsEntity)?.goodsMemo ?: "",
            setGoods = parentSet,
            setComponentTotal = setComponents.size,
            setComponentGotten = setComponents.count { it.isGotten },
            onSetClick = {
                setDialogComponent = officialGoods
                selectedSetGoods = parentSet
            },
            onDismiss = { detailViewModel.dismissDialog() },
            onToggleGotten = {
                officialGoods?.let { viewModel.toggleGotten(it) }
                detailViewModel.dismissDialog()
            },
            onSetPending = {
                officialGoods?.let { viewModel.setPending(it) }
                detailViewModel.dismissDialog()
            },
            isPending = (goods as? GoodsEntity)?.status == GoodsStatus.PENDING,
            onDelete = {},
            showDelete = false,
            onSeriesClick = { seriesName ->
                detailViewModel.dismissDialog()
                val series = seriesList.find { it.seriesNm == seriesName }
                series?.let { viewModel.selectSeries(it) }
            }
        )
    }

    selectedSetGoods?.let { setGoods ->
        SetGoodsDetailDialog(
            setGoods = setGoods,
            components = getSetComponents(setGoods, seriesGoods),
            initialComponent = setDialogComponent,
            onDismiss = { selectedSetGoods = null; setDialogComponent = null },
            onToggleGotten = { component -> viewModel.toggleGotten(component) },
            onSetPending = { component -> viewModel.setPending(component) },
            onBulkToggleGotten = { isGotten ->
                viewModel.bulkToggleGotten(setGoods, isGotten)
                selectedSetGoods = null
                setDialogComponent = null
            },
            highlightChara = selectedGoodsCharaFilter,
            highlightCategory = selectedGoodsCategoryFilter
        )
    }

    // 시리즈 목록용 캐릭터 필터 다이얼로그
    if (showCharaFilterDialog) {
        Dialog(
            onDismissRequest = { showCharaFilterDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(if (isLandscape) 0.85f else 0.9f)
                    .fillMaxHeight(if (isLandscape) 0.85f else 0.7f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                if (isLandscape) {
                    Row(modifier = Modifier.padding(AppStyles.paddingLarge)) {
                        Column(
                            modifier = Modifier
                                .width(200.dp)
                                .fillMaxHeight()
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = { Text("검색") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "초기화"
                                            )
                                        }
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Column(verticalArrangement = Arrangement.spacedBy(AppStyles.paddingMedium)) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.setCharaFilter(null); showCharaFilterDialog =
                                        false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("필터 해제") }
                                Button(
                                    onClick = { showCharaFilterDialog = false },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("닫기") }
                            }
                        }
                        Spacer(modifier = Modifier.width(AppStyles.paddingMedium))
                        VerticalDivider(
                            modifier = Modifier.fillMaxHeight(),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        Spacer(modifier = Modifier.width(AppStyles.paddingMedium))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(sortedCharaList) { chara ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clickable {
                                                viewModel.setCharaFilter(if (selectedCharaFilter?.charaNm == chara.charaNm) null else chara)
                                                showCharaFilterDialog = false
                                            }
                                            .background(
                                                color = when {
                                                    selectedCharaFilter?.charaNm == chara.charaNm -> MaterialTheme.colorScheme.primaryContainer
                                                    chara.charaIsFavorite -> MaterialTheme.colorScheme.primary.copy(
                                                        alpha = 0.2f
                                                    )

                                                    else -> Color.Transparent
                                                },
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(AppStyles.paddingSmall)
                                    ) {
                                        Image(
                                            painter = rememberAsyncImagePainter(model = chara.charaUrl.ifEmpty { null }),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(60.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = chara.charaNm, style = AppStyles.textCardSmall)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(modifier = Modifier.padding(AppStyles.paddingLarge)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("검색") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "초기화"
                                        )
                                    }
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(AppStyles.paddingMedium))
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            items(sortedCharaList) { chara ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable {
                                            viewModel.setCharaFilter(if (selectedCharaFilter?.charaNm == chara.charaNm) null else chara)
                                            showCharaFilterDialog = false
                                        }
                                        .background(
                                            color = when {
                                                selectedCharaFilter?.charaNm == chara.charaNm -> MaterialTheme.colorScheme.primaryContainer
                                                chara.charaIsFavorite -> MaterialTheme.colorScheme.primary.copy(
                                                    alpha = 0.2f
                                                )

                                                else -> Color.Transparent
                                            },
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(AppStyles.paddingSmall)
                                ) {
                                    Image(
                                        painter = rememberAsyncImagePainter(model = chara.charaUrl.ifEmpty { null }),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = chara.charaNm, style = AppStyles.textCardSmall)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(AppStyles.paddingMedium))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppStyles.paddingMedium)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.setCharaFilter(null); showCharaFilterDialog = false
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("필터 해제") }
                            Button(
                                onClick = { showCharaFilterDialog = false },
                                modifier = Modifier.weight(1f)
                            ) { Text("닫기") }
                        }
                    }
                }
            }
        }
    }

    // 굿즈용 통합 필터 다이얼로그
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
            onDismiss = { showGoodsFilterDialog = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = if (selectedSeries != null) "공식 - " + selectedSeries!!.seriesNm else "공식",
            action = {
                if (selectedSeries != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row {
                                IconButton(onClick = { showGoodsFilterDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = "필터",
                                        tint = if (selectedGoodsCharaFilter != null || selectedGoodsCategoryFilter != null)
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(onClick = { listModeViewModel.toggleGridMode() }) {
                                    Icon(
                                        imageVector = if (isGridMode) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                        contentDescription = "모드 전환"
                                    )
                                }
                                TextButton(onClick = {
                                    viewModel.clearSelectedSeries()
                                    filterViewModel.setFilter(FilterType.ALL)
                                    filterViewModel.clearGoodsFilter()
                                }) {
                                    Text("뒤로")
                                }
                            }
                            Row {
                                if (selectedGoodsCharaFilter != null) {
                                    Text(
                                        text = selectedGoodsCharaFilter!!,
                                        style = AppStyles.textCardSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                if (selectedGoodsCharaFilter != null && selectedGoodsCategoryFilter != null) {
                                    Text(
                                        text = "/",
                                        style = AppStyles.textCardSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                if (selectedGoodsCategoryFilter != null) {
                                    Text(
                                        text = selectedGoodsCategoryFilter!!,
                                        style = AppStyles.textCardSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { showCharaFilterDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = "캐릭터 필터",
                                    tint = if (selectedCharaFilter != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (selectedCharaFilter != null) {
                                Text(
                                    text = selectedCharaFilter!!.charaNm,
                                    style = AppStyles.textCardSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                        }
                    }
                }
            }
        )
        if (selectedSeries != null) {
            FilterBar(
                filterType = filterType,
                onFilterChange = { filterViewModel.setFilter(it) },
                goodsList = AllSeriesGoods
            )
            GoodsListContent(
                goods = filteredGoods,
                isGridMode = isGridMode,
                gridColumns = gridColumns,
                onGoodsClick = { detailViewModel.selectGoods(it) }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(selectedTab) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            if (dragAmount > 100) {
                                if (selectedTab > 0) viewModel.setSelectedTab(selectedTab - 1)
                            } else if (dragAmount < -100) {
                                if (selectedTab < viewModel.countries.size - 1) viewModel.setSelectedTab(selectedTab + 1)
                            }
                        }
                    }
            ) {
                SecondaryTabRow(selectedTabIndex = selectedTab) {
                    viewModel.countries.forEachIndexed { index, country ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { viewModel.setSelectedTab(index) },
                            text = {
                                Text(
                                    country,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }
                if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "등록된 시리즈가 없습니다", color = MaterialTheme.colorScheme.onBackground)
                }
            } else {
                val tabScrollPositions by viewModel.tabScrollPositions.collectAsState()

                key(selectedTab) {
                    val currentTabPosition = tabScrollPositions[selectedTab] ?: Pair(0, 0)
                    val listState = rememberLazyListState(
                        initialFirstVisibleItemIndex = currentTabPosition.first,
                        initialFirstVisibleItemScrollOffset = currentTabPosition.second
                    )

                    LaunchedEffect(Unit) {
                        snapshotFlow {
                            Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                        }
                            .distinctUntilChanged()
                            .collect { (index, offset) ->
                                viewModel.saveTabScrollPosition(selectedTab, index, offset)
                            }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState
                    ) {
                    itemsIndexed(filteredList) { index, series ->
                        val charas = viewModel.getCharasForSeries(series)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                viewModel.saveScrollPosition(
                                    listState.firstVisibleItemIndex,
                                    listState.firstVisibleItemScrollOffset
                                )
                                viewModel.selectSeries(series)
                            },
                            shape = RoundedCornerShape(0.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            if (isLandscape) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.5f)
                                            .fillMaxHeight()
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            painter = rememberAsyncImagePainter(model = series.seriesUrl.ifEmpty { null }),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(AppStyles.paddingMedium)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = series.seriesNm,
                                                style = AppStyles.textCardTitle
                                            )
                                            Text(
                                                text = "${series.seriesDate} / ${series.seriesCountry}",
                                                style = AppStyles.textCardSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                        }
                                        Spacer(modifier = Modifier.height(AppStyles.paddingSmall))
                                        HorizontalDivider()
                                        Spacer(modifier = Modifier.height(AppStyles.paddingSmall))
                                        if (charas.isEmpty()) {
                                            Text(
                                                text = "등록된 캐릭터가 없습니다",
                                                style = AppStyles.textCardSmall
                                            )
                                        } else {
                                            LazyRow(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(
                                                    AppStyles.paddingMedium
                                                )
                                            ) {
                                                val sortedCharas =
                                                    charas.sortedByDescending { it.charaIsFavorite }
                                                items((sortedCharas.size + 1) / 2) { index ->
                                                    Column(
                                                        verticalArrangement = Arrangement.spacedBy(
                                                            AppStyles.paddingSmall
                                                        )
                                                    ) {
                                                        val charaTop =
                                                            sortedCharas.getOrNull(index * 2)
                                                        if (charaTop != null) {
                                                            val count =
                                                                seriesCharaCountMap["${series.seriesNm}|${charaTop.charaNm}"]
                                                                    ?: Pair(0, 0)
                                                            Row(
                                                                modifier = Modifier
                                                                    .width(IntrinsicSize.Min)
                                                                    .padding(vertical = AppStyles.paddingSmall)
                                                                    .background(
                                                                        color = if (charaTop.charaIsFavorite) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                                        shape = RoundedCornerShape(8.dp)
                                                                    )
                                                                    .padding(AppStyles.paddingSmall),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    Image(
                                                                        painter = rememberAsyncImagePainter(
                                                                            model = charaTop.charaUrl.ifEmpty { null }
                                                                        ),
                                                                        contentDescription = null,
                                                                        modifier = Modifier
                                                                            .size(48.dp)
                                                                            .clip(
                                                                                RoundedCornerShape(8.dp)
                                                                            ),
                                                                        contentScale = ContentScale.Crop
                                                                    )
                                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                        Text(
                                                                            text = charaTop.charaNm,
                                                                            maxLines = 1,
                                                                            overflow = TextOverflow.Ellipsis,
                                                                            style = AppStyles.textCardSubtitle
                                                                        )
                                                                        Text(
                                                                            text = "(${count.first}/${count.second})",
                                                                            style = AppStyles.textCardSmall
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        val charaBottom =
                                                            sortedCharas.getOrNull(index * 2 + 1)
                                                        if (charaBottom != null) {
                                                            val count =
                                                                seriesCharaCountMap["${series.seriesNm}|${charaBottom.charaNm}"]
                                                                    ?: Pair(0, 0)
                                                            Row(
                                                                modifier = Modifier
                                                                    .width(IntrinsicSize.Min)
                                                                    .padding(vertical = AppStyles.paddingSmall)
                                                                    .background(
                                                                        color = if (charaBottom.charaIsFavorite) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                                        shape = RoundedCornerShape(8.dp)
                                                                    )
                                                                    .padding(AppStyles.paddingSmall),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                                        Image(
                                                                            painter = rememberAsyncImagePainter(
                                                                                model = charaBottom.charaUrl.ifEmpty { null }
                                                                            ),
                                                                            contentDescription = null,
                                                                            modifier = Modifier
                                                                                .size(
                                                                                    48.dp
                                                                                )
                                                                                .clip(
                                                                                    RoundedCornerShape(
                                                                                        8.dp
                                                                                    )
                                                                                ),
                                                                            contentScale = ContentScale.Crop
                                                                        )
                                                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                            Text(
                                                                                text = charaBottom.charaNm,
                                                                                maxLines = 1,
                                                                                overflow = TextOverflow.Ellipsis,
                                                                                style = AppStyles.textCardSubtitle
                                                                            )
                                                                            Text(
                                                                                text = "(${count.first}/${count.second})",
                                                                                style = AppStyles.textCardSmall
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Column {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            painter = rememberAsyncImagePainter(model = series.seriesUrl.ifEmpty { null }),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                    Column(modifier = Modifier.padding(AppStyles.paddingMedium)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = series.seriesNm,
                                                style = AppStyles.textCardTitle
                                            )
                                            Text(
                                                text = "${series.seriesDate} / ${series.seriesCountry}",
                                                style = AppStyles.textCardSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                        }
                                        Spacer(modifier = Modifier.height(AppStyles.paddingSmall))
                                        HorizontalDivider()
                                        Spacer(modifier = Modifier.height(AppStyles.paddingSmall))
                                        if (charas.isEmpty()) {
                                            Text(
                                                text = "등록된 캐릭터가 없습니다",
                                                style = AppStyles.textCardSmall
                                            )
                                        } else {
                                            LazyRow(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(
                                                    AppStyles.paddingMedium
                                                )
                                            ) {
                                                items(charas.sortedByDescending { it.charaIsFavorite }) { chara ->
                                                    val count =
                                                        seriesCharaCountMap["${series.seriesNm}|${chara.charaNm}"]
                                                            ?: Pair(0, 0)
                                                    Row(
                                                        modifier = Modifier
                                                            .width(IntrinsicSize.Min)
                                                            .padding(vertical = AppStyles.paddingSmall)
                                                            .background(
                                                                color = if (chara.charaIsFavorite) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                                shape = RoundedCornerShape(8.dp)
                                                            )
                                                            .padding(AppStyles.paddingSmall),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                            Image(
                                                                painter = rememberAsyncImagePainter(
                                                                    model = chara.charaUrl.ifEmpty { null }
                                                                ),
                                                                contentDescription = null,
                                                                modifier = Modifier
                                                                    .size(48.dp)
                                                                    .clip(RoundedCornerShape(8.dp)),
                                                                contentScale = ContentScale.Crop
                                                            )
                                                            Text(
                                                                text = chara.charaNm,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                style = AppStyles.textCardSubtitle,
                                                                color = if (chara.charaIsFavorite) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                                            )
                                                            Text(
                                                                text = "(${count.first}/${count.second})",
                                                                style = AppStyles.textCardSmall,
                                                                color = if (chara.charaIsFavorite) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                    }
                                                }
                                                if (charas.size == 1) {
                                                    item { Spacer(modifier = Modifier.weight(1f)) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (index < filteredList.lastIndex) {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(AppStyles.paddingMedium))
                        }
                    }
                }
                }
            }
            }
        }
    }
}