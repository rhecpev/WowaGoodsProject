package com.example.wowagoodsproject.screen.character

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.wowagoodsproject.component.FilterViewModel
import com.example.wowagoodsproject.component.GoodsDetailDialog
import com.example.wowagoodsproject.component.GoodsDetailViewModel
import com.example.wowagoodsproject.component.GoodsGridItem
import com.example.wowagoodsproject.component.GoodsListItem
import com.example.wowagoodsproject.component.GottenStatus
import com.example.wowagoodsproject.component.ListModeViewModel
import com.example.wowagoodsproject.component.SetGoodsDetailDialog
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.navigation.TopBar
import com.example.wowagoodsproject.ui.theme.AppStyles

@Composable
fun CharacterScreen(
    widthSizeClass: WindowWidthSizeClass,
    viewModel: CharacterViewModel = viewModel(),
    detailViewModel: GoodsDetailViewModel = viewModel(),
    filterViewModel: FilterViewModel = viewModel(),
    listModeViewModel: ListModeViewModel = viewModel()
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
    val selectedCategoryFilter by filterViewModel.selectedCategoryFilter.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showCategoryFilterDialog by remember { mutableStateOf(false) }
    var categorySearch by remember { mutableStateOf("") }
    var selectedSetGoods by remember { mutableStateOf<GoodsEntity?>(null) }
    val expandedStates = remember { mutableStateMapOf<Int, Boolean>() }

    val filteredCharaList = if (showFavoriteOnly)
        charaList.filter { it.charaIsFavorite }
    else
        charaList.sortedByDescending { it.charaIsFavorite }
            .filter { it.charaNm.contains(searchQuery, ignoreCase = true) }

    val categoryList =
        (filterViewModel.applyFilter(officialGoods).second + filterViewModel.applyFilter(fanGoods).second)
            .map { it.category }
            .distinct()
            .filter { it.isNotEmpty() && it != CATEGORY_SET }  // CATEGORY_SET 추가
            .sorted()

    val filteredCategories = categoryList.filter {
        it.contains(categorySearch, ignoreCase = true)
    }

    val filteredOfficialGoods = filterViewModel.applyFilter(officialGoods).second.let { list ->
        val setMemos = allSeriesGoods.filter { it.category == CATEGORY_SET }.map { it.memo }.toSet()
        var result = list.filter { it.category == CATEGORY_SET || it.memo !in setMemos || it.memo.isEmpty() }
        if (selectedCategoryFilter != null) {
            result = result.filter { goods ->
                if (goods.category == CATEGORY_SET) {
                    allSeriesGoods.any {
                        it.category != CATEGORY_SET &&
                                it.memo == goods.memo &&
                                it.category == selectedCategoryFilter
                    }
                } else {
                    goods.category == selectedCategoryFilter
                }
            }
        }
        result
    }

    val filteredFanGoods = filterViewModel.applyFilter(fanGoods).second.let { list ->
        if (selectedCategoryFilter != null) list.filter { it.category == selectedCategoryFilter }
        else list
    }

    val AllFilteredOfficialGoods = filterViewModel.applyFilter(officialGoods).first.let { list ->
        if (selectedCategoryFilter != null) list.filter { it.category == selectedCategoryFilter }
        else list
    }
    val AllFilteredFanGoods = filterViewModel.applyFilter(fanGoods).first.let { list ->
        if (selectedCategoryFilter != null) list.filter { it.category == selectedCategoryFilter }
        else list
    }

    val gridColumns = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> 2
        WindowWidthSizeClass.Medium -> 3
        else -> 4
    }

    val charaGridColumns = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> 3
        WindowWidthSizeClass.Medium -> 4
        else -> 5
    }

    BackHandler(enabled = selectedChara != null) {
        viewModel.clearSelectedChara()
        viewModel.setSelectedTab(0)
        filterViewModel.setFilter(com.example.wowagoodsproject.component.FilterType.ALL)
        filterViewModel.clearGoodsFilter()
        categorySearch = ""
    }

    // 세트 굿즈 다이얼로그
    selectedSetGoods?.let { setGoods ->
        val components = allSeriesGoods.filter {
            it.category != CATEGORY_SET && it.memo == setGoods.memo
        }
        SetGoodsDetailDialog(
            setGoods = setGoods,
            components = components,
            onDismiss = { selectedSetGoods = null },
            onToggleGotten = { component ->
                viewModel.toggleOfficialGotten(component)
            }
        )
    }

    // 카테고리 필터 다이얼로그
    if (showCategoryFilterDialog) {
        val isLandscape = widthSizeClass != WindowWidthSizeClass.Compact
        Dialog(
            onDismissRequest = { showCategoryFilterDialog = false; categorySearch = "" },
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
                        Column(modifier = Modifier.width(200.dp).fillMaxHeight()) {
                            Text(text = "카테고리 필터", style = AppStyles.textCardTitle)
                            Spacer(modifier = Modifier.height(AppStyles.paddingMedium))
                            OutlinedTextField(
                                value = categorySearch,
                                onValueChange = { categorySearch = it },
                                label = { Text("검색") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                trailingIcon = {
                                    if (categorySearch.isNotEmpty()) {
                                        IconButton(onClick = { categorySearch = "" }) {
                                            Icon(imageVector = Icons.Default.Clear, contentDescription = "초기화")
                                        }
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Column(verticalArrangement = Arrangement.spacedBy(AppStyles.paddingMedium)) {
                                OutlinedButton(
                                    onClick = { filterViewModel.clearGoodsFilter(); showCategoryFilterDialog = false; categorySearch = "" },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("필터 해제") }
                                Button(
                                    onClick = { showCategoryFilterDialog = false; categorySearch = "" },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("닫기") }
                            }
                        }
                        Spacer(modifier = Modifier.width(AppStyles.paddingMedium))
                        VerticalDivider(modifier = Modifier.fillMaxHeight(), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.width(AppStyles.paddingMedium))
                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(filteredCategories) { cat ->
                                    Column {
                                        Card(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                filterViewModel.setCategoryFilter(if (selectedCategoryFilter == cat) null else cat)
                                                showCategoryFilterDialog = false
                                                categorySearch = ""
                                            },
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (selectedCategoryFilter == cat) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                            )
                                        ) {
                                            Text(
                                                text = cat,
                                                modifier = Modifier.padding(AppStyles.paddingMedium),
                                                color = if (selectedCategoryFilter == cat) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(modifier = Modifier.padding(AppStyles.paddingLarge)) {
                        Text(text = "카테고리 필터", style = AppStyles.textCardTitle)
                        Spacer(modifier = Modifier.height(AppStyles.paddingMedium))
                        OutlinedTextField(
                            value = categorySearch,
                            onValueChange = { categorySearch = it },
                            label = { Text("검색") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                if (categorySearch.isNotEmpty()) {
                                    IconButton(onClick = { categorySearch = "" }) {
                                        Icon(imageVector = Icons.Default.Clear, contentDescription = "초기화")
                                    }
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(AppStyles.paddingMedium))
                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(filteredCategories) { cat ->
                                Column {
                                    Card(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            filterViewModel.setCategoryFilter(if (selectedCategoryFilter == cat) null else cat)
                                            showCategoryFilterDialog = false
                                            categorySearch = ""
                                        },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (selectedCategoryFilter == cat) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                        )
                                    ) {
                                        Text(
                                            text = cat,
                                            modifier = Modifier.padding(AppStyles.paddingMedium),
                                            color = if (selectedCategoryFilter == cat) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(AppStyles.paddingMedium))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppStyles.paddingMedium)) {
                            OutlinedButton(
                                onClick = { filterViewModel.clearGoodsFilter(); showCategoryFilterDialog = false; categorySearch = "" },
                                modifier = Modifier.weight(1f)
                            ) { Text("필터 해제") }
                            Button(
                                onClick = { showCategoryFilterDialog = false; categorySearch = "" },
                                modifier = Modifier.weight(1f)
                            ) { Text("닫기") }
                        }
                    }
                }
            }
        }
    }

    selectedGoods?.let { goods ->
        val officialGoods = goods as? GoodsEntity
        val fanGoods = goods as? FanGoodsEntity
        GoodsDetailDialog(
            imgPath = goods.imgPath,
            series = goods.series,
            chara = goods.chara,
            category = goods.category,
            price = goods.price,
            isGotten = goods.isGotten,
            memo = (goods as? GoodsEntity)?.goodsMemo ?: (goods as? FanGoodsEntity)?.fanGoodsMemo ?: "",
            onDismiss = { detailViewModel.dismissDialog() },
            onToggleGotten = {
                officialGoods?.let { viewModel.toggleOfficialGotten(it) }
                fanGoods?.let { viewModel.toggleFanGotten(it) }
                detailViewModel.dismissDialog()
            },
            onDelete = {},
            showDelete = false
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = if (selectedChara != null) selectedChara!!.charaNm else "캐릭터별",
            action = {
                if (selectedChara != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row {
                                IconButton(onClick = { showCategoryFilterDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = "카테고리 필터",
                                        tint = if (selectedCategoryFilter != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(onClick = { listModeViewModel.toggleGridMode() }) {
                                    Icon(
                                        imageVector = if (isGridMode) Icons.Default.ViewList else Icons.Default.GridView,
                                        contentDescription = "모드 전환"
                                    )
                                }
                                TextButton(onClick = {
                                    viewModel.clearSelectedChara()
                                    viewModel.setSelectedTab(0)
                                    filterViewModel.setFilter(com.example.wowagoodsproject.component.FilterType.ALL)
                                    filterViewModel.clearGoodsFilter()
                                    categorySearch = ""
                                }) {
                                    Text("뒤로")
                                }
                            }
                            Row {
                                if (selectedCategoryFilter != null) {
                                    Text(text = selectedCategoryFilter!!, style = AppStyles.textCardSmall, color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                            }
                        }
                    }
                } else {
                    IconButton(onClick = { viewModel.toggleFavoriteOnly() }) {
                        Icon(
                            imageVector = if (showFavoriteOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "선호 캐릭터만 보기",
                            tint = if (showFavoriteOnly) Color.Red else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        )

        if (selectedChara == null) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("캐릭터 검색") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppStyles.paddingMedium, vertical = AppStyles.paddingSmall),
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "검색 초기화")
                        }
                    }
                }
            )
            if (filteredCharaList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (showFavoriteOnly) "선호 캐릭터가 없습니다" else "캐릭터가 없습니다",
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(charaGridColumns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(0.dp),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    items(filteredCharaList) { chara ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (chara.charaIsFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                .clickable { viewModel.selectChara(chara) }
                                .padding(AppStyles.paddingSmall)
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(model = if (chara.charaUrl.isNotEmpty()) chara.charaUrl else null),
                                contentDescription = null,
                                modifier = Modifier.size(80.dp).border(1.dp, MaterialTheme.colorScheme.outline),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = chara.charaNm,
                                style = AppStyles.textCardSmall,
                                color = if (chara.charaIsFavorite) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { viewModel.setSelectedTab(0) }, text = { Text("공식 (${AllFilteredOfficialGoods.size})") })
                    Tab(selected = selectedTab == 1, onClick = { viewModel.setSelectedTab(1) }, text = { Text("2차창작 (${AllFilteredFanGoods.size})") })
                }

                FilterBar(
                    filterType = filterType,
                    onFilterChange = { filterViewModel.setFilter(it) },
                    goodsList = if (selectedTab == 0) AllFilteredOfficialGoods else AllFilteredFanGoods
                )

                when (selectedTab) {
                    0 -> {
                        if (filteredOfficialGoods.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "공식 굿즈가 없습니다", color = MaterialTheme.colorScheme.onBackground)
                            }
                        } else {
                            if (isGridMode) {
                                val rows = filteredOfficialGoods.chunked(gridColumns)
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    itemsIndexed(rows) { index, rowItems ->
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            rowItems.forEach { goods ->
                                                val gottenStatus = if (goods.category == CATEGORY_SET) {
                                                    val components = allSeriesGoods.filter {
                                                        it.category != CATEGORY_SET && it.memo == goods.memo
                                                    }
                                                    when {
                                                        components.isEmpty() -> if (goods.isGotten) GottenStatus.GOTTEN else GottenStatus.NOT_GOTTEN
                                                        components.all { it.isGotten } -> GottenStatus.GOTTEN
                                                        components.none { it.isGotten } -> GottenStatus.NOT_GOTTEN
                                                        else -> GottenStatus.PARTIAL
                                                    }
                                                } else {
                                                    if (goods.isGotten) GottenStatus.GOTTEN else GottenStatus.NOT_GOTTEN
                                                }
                                                val componentCategories = if (goods.category == CATEGORY_SET) {
                                                    allSeriesGoods.filter {
                                                        it.category != CATEGORY_SET && it.memo == goods.memo
                                                    }.map { it.category }
                                                } else emptyList()
                                                Box(modifier = Modifier.weight(1f)) {
                                                    GoodsGridItem(
                                                        imgPath = goods.imgPath,
                                                        series = goods.series,
                                                        chara = goods.chara,
                                                        category = goods.category,
                                                        gottenStatus = gottenStatus,
                                                        price = goods.price,
                                                        isGotten = goods.isGotten,
                                                        memo = goods.memo,
                                                        components = componentCategories,
                                                        highlightCategory = selectedCategoryFilter,
                                                        onClick = {
                                                            if (goods.category == CATEGORY_SET) {
                                                                selectedSetGoods = goods
                                                            } else {
                                                                detailViewModel.selectGoods(goods)
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                            repeat(gridColumns - rowItems.size) {
                                                Box(modifier = Modifier.weight(1f))
                                            }
                                        }
                                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    itemsIndexed(filteredOfficialGoods) { index, goods ->
                                        val isExpanded = expandedStates[index] ?: false
                                        val componentCategories = if (goods.category == CATEGORY_SET) {
                                            allSeriesGoods.filter {
                                                it.category != CATEGORY_SET && it.memo == goods.memo
                                            }.map { it.category }
                                        } else emptyList()
                                        val gottenStatus = if (goods.category == CATEGORY_SET) {
                                            val components = allSeriesGoods.filter {
                                                it.category != CATEGORY_SET && it.memo == goods.memo
                                            }
                                            when {
                                                components.isEmpty() -> if (goods.isGotten) GottenStatus.GOTTEN else GottenStatus.NOT_GOTTEN
                                                components.all { it.isGotten } -> GottenStatus.GOTTEN
                                                components.none { it.isGotten } -> GottenStatus.NOT_GOTTEN
                                                else -> GottenStatus.PARTIAL
                                            }
                                        } else {
                                            if (goods.isGotten) GottenStatus.GOTTEN else GottenStatus.NOT_GOTTEN
                                        }
                                        GoodsListItem(
                                            imgPath = goods.imgPath,
                                            series = goods.series,
                                            chara = goods.chara,
                                            category = goods.category,
                                            price = goods.price,
                                            isExpanded = isExpanded,
                                            isGotten = goods.isGotten,
                                            gottenStatus = gottenStatus,
                                            memo = goods.memo,
                                            components = componentCategories,
                                            highlightCategory = selectedCategoryFilter,
                                            onClick = {
                                                if (goods.category == CATEGORY_SET) {
                                                    expandedStates[index] = !isExpanded
                                                } else {
                                                    detailViewModel.selectGoods(goods)
                                                }
                                            }
                                        )
                                        if (goods.category == CATEGORY_SET && isExpanded) {
                                            val components = allSeriesGoods.filter {
                                                it.category != CATEGORY_SET && it.memo == goods.memo
                                            }
                                            LazyRow(
                                                modifier = Modifier.fillMaxWidth().padding(AppStyles.paddingMedium),
                                                horizontalArrangement = Arrangement.spacedBy(AppStyles.paddingMedium)
                                            ) {
                                                items(components) { component ->
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        modifier = Modifier.width(80.dp).clickable { detailViewModel.selectGoods(component) }
                                                    ) {
                                                        Box(
                                                            modifier = Modifier.size(80.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Image(
                                                                painter = rememberAsyncImagePainter(model = if (component.imgPath.isNotEmpty()) component.imgPath else null),
                                                                contentDescription = null,
                                                                modifier = Modifier.fillMaxSize(),
                                                                contentScale = ContentScale.Fit
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            text = component.category,
                                                            style = AppStyles.textCardSmall,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            color = if (component.isGotten) AppStyles.colorGotten else AppStyles.colorNotGotten
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        if (index < filteredOfficialGoods.lastIndex) {
                                            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                                            Spacer(modifier = Modifier.height(AppStyles.paddingMedium))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        if (filteredFanGoods.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "2차창작 굿즈가 없습니다", color = MaterialTheme.colorScheme.onBackground)
                            }
                        } else {
                            if (isGridMode) {
                                val rows = filteredFanGoods.chunked(gridColumns)
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    itemsIndexed(rows) { index, rowItems ->
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            rowItems.forEach { goods ->
                                                val gottenStatus = if (goods.category == CATEGORY_SET) {
                                                    val components = allSeriesGoods.filter {
                                                        it.category != CATEGORY_SET && it.memo == goods.memo
                                                    }
                                                    when {
                                                        components.isEmpty() -> if (goods.isGotten) GottenStatus.GOTTEN else GottenStatus.NOT_GOTTEN
                                                        components.all { it.isGotten } -> GottenStatus.GOTTEN
                                                        components.none { it.isGotten } -> GottenStatus.NOT_GOTTEN
                                                        else -> GottenStatus.PARTIAL
                                                    }
                                                } else {
                                                    if (goods.isGotten) GottenStatus.GOTTEN else GottenStatus.NOT_GOTTEN
                                                }
                                                Box(modifier = Modifier.weight(1f)) {
                                                    GoodsGridItem(
                                                        imgPath = goods.imgPath,
                                                        series = goods.series,
                                                        chara = goods.chara,
                                                        category = goods.category,
                                                        gottenStatus = gottenStatus,
                                                        price = goods.price,
                                                        isGotten = goods.isGotten,
                                                        memo = goods.memo,
                                                        onClick = { detailViewModel.selectGoods(goods) }
                                                    )
                                                }
                                            }
                                            repeat(gridColumns - rowItems.size) {
                                                Box(modifier = Modifier.weight(1f))
                                            }
                                        }
                                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    itemsIndexed(filteredFanGoods) { index, goods ->
                                        GoodsListItem(
                                            imgPath = goods.imgPath,
                                            series = goods.series,
                                            chara = goods.chara,
                                            category = goods.category,
                                            price = goods.price,
                                            isGotten = goods.isGotten,
                                            memo = goods.memo,
                                            onClick = { detailViewModel.selectGoods(goods) }
                                        )
                                        if (index < filteredFanGoods.lastIndex) {
                                            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
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
    }
}