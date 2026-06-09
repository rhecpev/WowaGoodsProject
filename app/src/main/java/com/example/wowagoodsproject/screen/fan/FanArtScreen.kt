package com.example.wowagoodsproject.screen.fan

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.example.wowagoodsproject.component.FilterBar
import com.example.wowagoodsproject.component.FilterViewModel
import com.example.wowagoodsproject.component.GoodsDetailDialog
import com.example.wowagoodsproject.component.GoodsDetailViewModel
import com.example.wowagoodsproject.component.GoodsGridItem
import com.example.wowagoodsproject.component.GoodsListItem
import com.example.wowagoodsproject.component.ListModeViewModel
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.navigation.TopBar
import com.example.wowagoodsproject.screen.series.SeriesViewModel
import com.example.wowagoodsproject.ui.theme.AppStyles

@Composable
fun FanArtScreen(
    onNavigateToAdd: () -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    viewModel: FanArtViewModel = viewModel(),
    listModeViewModel: ListModeViewModel = viewModel(),
    detailViewModel: GoodsDetailViewModel = viewModel(),
    filterViewModel: FilterViewModel = viewModel()
) {
    val goodsList by viewModel.goodsList.collectAsState()
    val isGridMode by listModeViewModel.isGridMode.collectAsState()
    val selectedGoods by detailViewModel.selectedGoods.collectAsState()
    val filterType by filterViewModel.filterType.collectAsState()
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var showGoodsFilterDialog by remember { mutableStateOf(false) }
    var goodsFilterDialogTab by remember { mutableStateOf(0) }
    var goodsFilterSearch by remember { mutableStateOf("") }
    var selectedGoodsCharaFilter by remember { mutableStateOf<String?>(null) }
    var selectedGoodsCategoryFilter by remember { mutableStateOf<String?>(null) }
    val categoryList = goodsList
        .map { it.fanGoodsCategory }
        .distinct()
        .filter { it.isNotEmpty() }
        .sorted()

    val filteredList = filterViewModel.applyFilter(goodsList).second.let { list ->
        var result = list
        if (selectedGoodsCharaFilter != null)
            result = result.filter { it.chara.contains(selectedGoodsCharaFilter!!) }
        if (selectedGoodsCategoryFilter != null)
            result = result.filter { it.category == selectedGoodsCategoryFilter }
        result
    }
    val AllFilteredList = filterViewModel.applyFilter(goodsList).first.let { list ->
        var result = list
        if (selectedGoodsCharaFilter != null)
            result = result.filter { it.chara.contains(selectedGoodsCharaFilter!!) }
        if (selectedGoodsCategoryFilter != null)
            result = result.filter { it.category == selectedGoodsCategoryFilter }
        result
    }



    val gridColumns = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> 2
        WindowWidthSizeClass.Medium -> 3
        else -> 4
    }
    val allCharaList by viewModel.allCharaList.collectAsState()

    val goodsCharaList = goodsList
        .flatMap { it.chara.split(",").map { c -> c.trim() } }
        .distinct()
        .filter { it.isNotEmpty() }
        .sortedByDescending { charaNm -> allCharaList.find { it.charaNm == charaNm }?.charaIsFavorite == true }

    val goodsCategoryList = goodsList
        .map { it.category }
        .distinct()
        .filter { it.isNotEmpty() }
        .sorted()

    val filteredGoodsCharaList = goodsCharaList.filter {
        it.contains(goodsFilterSearch, ignoreCase = true)
    }
    val filteredGoodsCategoryList = goodsCategoryList.filter {
        it.contains(goodsFilterSearch, ignoreCase = true)
    }
    LaunchedEffect(Unit) {
        viewModel.loadGoods()
    }

    selectedGoods?.let { goods ->
        val fanGoods = goods as FanGoodsEntity
        GoodsDetailDialog(
            imgPath = goods.imgPath,
            series = goods.series,
            chara = goods.chara,
            category = goods.category,
            price = goods.price,
            isGotten = goods.isGotten,
            memo = (goods as? FanGoodsEntity)?.fanGoodsMemo ?: "",
            onDismiss = { detailViewModel.dismissDialog() },
            onToggleGotten = {
                viewModel.toggleGotten(fanGoods)
                detailViewModel.dismissDialog()
            },
            onDelete = {
                viewModel.delete(fanGoods)
                detailViewModel.dismissDialog()
            }
        )
    }


    if (showGoodsFilterDialog) {
        val isLandscape = widthSizeClass != WindowWidthSizeClass.Compact

        Dialog(
            onDismissRequest = {
                showGoodsFilterDialog = false
                goodsFilterSearch = ""
            },
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

                        // ── 왼쪽 패널 ──
                        Column(
                            modifier = Modifier
                                .width(200.dp)
                                .fillMaxHeight()
                        ) {
                            Text(text = "필터", style = AppStyles.textCardTitle)
                            Spacer(modifier = Modifier.height(AppStyles.paddingMedium))

                            TabRow(selectedTabIndex = goodsFilterDialogTab) {
                                Tab(
                                    selected = goodsFilterDialogTab == 0,
                                    onClick = { goodsFilterDialogTab = 0; goodsFilterSearch = "" },
                                    text = { Text("캐릭터") }
                                )
                                Tab(
                                    selected = goodsFilterDialogTab == 1,
                                    onClick = { goodsFilterDialogTab = 1; goodsFilterSearch = "" },
                                    text = { Text("카테고리") }
                                )
                            }

                            Spacer(modifier = Modifier.height(AppStyles.paddingMedium))

                            OutlinedTextField(
                                value = goodsFilterSearch,
                                onValueChange = { goodsFilterSearch = it },
                                label = { Text("검색") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                trailingIcon = {
                                    if (goodsFilterSearch.isNotEmpty()) {
                                        IconButton(onClick = { goodsFilterSearch = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "초기화")
                                        }
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            Column(verticalArrangement = Arrangement.spacedBy(AppStyles.paddingMedium)) {
                                OutlinedButton(
                                    onClick = {
                                        selectedGoodsCharaFilter = null
                                        selectedGoodsCategoryFilter = null
                                        showGoodsFilterDialog = false
                                        goodsFilterSearch = ""
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("필터 해제") }
                                Button(
                                    onClick = {
                                        showGoodsFilterDialog = false
                                        goodsFilterSearch = ""
                                    },
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

                        // ── 오른쪽 패널 ──
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            when (goodsFilterDialogTab) {
                                0 -> {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(3),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(filteredGoodsCharaList) { charaNm ->
                                            val charaEntity = allCharaList.find { it.charaNm == charaNm }
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier
                                                    .clickable {
                                                        selectedGoodsCharaFilter =
                                                            if (selectedGoodsCharaFilter == charaNm) null else charaNm
                                                        showGoodsFilterDialog = false
                                                        goodsFilterSearch = ""
                                                    }
                                                    .background(
                                                        color = when {
                                                            selectedGoodsCharaFilter == charaNm -> MaterialTheme.colorScheme.primaryContainer
                                                            charaEntity?.charaIsFavorite == true -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                                            else -> Color.Transparent
                                                        },
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .padding(AppStyles.paddingSmall)
                                            ) {
                                                Image(
                                                    painter = rememberAsyncImagePainter(
                                                        model = if (charaEntity?.charaUrl?.isNotEmpty() == true) charaEntity.charaUrl else null
                                                    ),
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(60.dp)
                                                        .clip(RoundedCornerShape(8.dp)),
                                                    contentScale = ContentScale.Crop
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(text = charaNm, style = AppStyles.textCardSmall)
                                            }
                                        }
                                    }
                                }
                                1 -> {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(filteredGoodsCategoryList) { cat ->
                                            Column {
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            selectedGoodsCategoryFilter =
                                                                if (selectedGoodsCategoryFilter == cat) null else cat
                                                            showGoodsFilterDialog = false
                                                            goodsFilterSearch = ""
                                                        },
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = if (selectedGoodsCategoryFilter == cat)
                                                            MaterialTheme.colorScheme.primaryContainer
                                                        else MaterialTheme.colorScheme.surface
                                                    )
                                                ) {
                                                    Text(
                                                        text = cat,
                                                        modifier = Modifier.padding(AppStyles.paddingMedium),
                                                        color = if (selectedGoodsCategoryFilter == cat)
                                                            MaterialTheme.colorScheme.onPrimaryContainer
                                                        else MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                                HorizontalDivider(
                                                    thickness = 1.dp,
                                                    color = MaterialTheme.colorScheme.outlineVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                } else {
                    // ── 세로 레이아웃 ──
                    Column(modifier = Modifier.padding(AppStyles.paddingLarge)) {
                        Text(text = "필터", style = AppStyles.textCardTitle)
                        Spacer(modifier = Modifier.height(AppStyles.paddingMedium))

                        TabRow(selectedTabIndex = goodsFilterDialogTab) {
                            Tab(
                                selected = goodsFilterDialogTab == 0,
                                onClick = { goodsFilterDialogTab = 0; goodsFilterSearch = "" },
                                text = { Text("캐릭터") }
                            )
                            Tab(
                                selected = goodsFilterDialogTab == 1,
                                onClick = { goodsFilterDialogTab = 1; goodsFilterSearch = "" },
                                text = { Text("카테고리") }
                            )
                        }

                        Spacer(modifier = Modifier.height(AppStyles.paddingMedium))

                        OutlinedTextField(
                            value = goodsFilterSearch,
                            onValueChange = { goodsFilterSearch = it },
                            label = { Text("검색") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                if (goodsFilterSearch.isNotEmpty()) {
                                    IconButton(onClick = { goodsFilterSearch = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "초기화"
                                        )
                                    }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(AppStyles.paddingMedium))

                        when (goodsFilterDialogTab) {
                            0 -> {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                ) {
                                    items(filteredGoodsCharaList) { charaNm ->
                                        val charaEntity = allCharaList.find { it.charaNm == charaNm }
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .clickable {
                                                    selectedGoodsCharaFilter =
                                                        if (selectedGoodsCharaFilter == charaNm) null else charaNm
                                                    showGoodsFilterDialog = false
                                                    goodsFilterSearch = ""
                                                }
                                                .background(
                                                    color = when {
                                                        selectedGoodsCharaFilter == charaNm -> MaterialTheme.colorScheme.primaryContainer
                                                        charaEntity?.charaIsFavorite == true -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                                        else -> Color.Transparent
                                                    },
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .padding(AppStyles.paddingSmall)
                                        ) {
                                            Image(
                                                painter = rememberAsyncImagePainter(
                                                    model = if (charaEntity?.charaUrl?.isNotEmpty() == true) charaEntity.charaUrl else null
                                                ),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(60.dp)
                                                    .clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(text = charaNm, style = AppStyles.textCardSmall)
                                        }
                                    }
                                }
                            }
                            1 -> {
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(filteredGoodsCategoryList) { cat ->
                                        Column {
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selectedGoodsCategoryFilter =
                                                            if (selectedGoodsCategoryFilter == cat) null else cat
                                                        showGoodsFilterDialog = false
                                                        goodsFilterSearch = ""
                                                    },
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (selectedGoodsCategoryFilter == cat)
                                                        MaterialTheme.colorScheme.primaryContainer
                                                    else MaterialTheme.colorScheme.surface
                                                )
                                            ) {
                                                Text(
                                                    text = cat,
                                                    modifier = Modifier.padding(AppStyles.paddingMedium),
                                                    color = if (selectedGoodsCategoryFilter == cat)
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            HorizontalDivider(
                                                thickness = 1.dp,
                                                color = MaterialTheme.colorScheme.outlineVariant
                                            )
                                        }
                                    }
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
                                    selectedGoodsCharaFilter = null
                                    selectedGoodsCategoryFilter = null
                                    showGoodsFilterDialog = false
                                    goodsFilterSearch = ""
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("필터 해제") }
                            Button(
                                onClick = {
                                    showGoodsFilterDialog = false
                                    goodsFilterSearch = ""
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("닫기") }
                        }
                    }
                }
            }
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = "2차창작",
            action = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row() {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row() {
                                    IconButton(onClick = { showGoodsFilterDialog = true }) {
                                        Icon(
                                            imageVector = Icons.Default.FilterList,
                                            contentDescription = "필터",
                                            tint = if (selectedCategoryFilter != null)
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    IconButton(onClick = { listModeViewModel.toggleGridMode() }) {
                                        Icon(
                                            imageVector = if (isGridMode) Icons.Default.ViewList else Icons.Default.GridView,
                                            contentDescription = "모드 전환"
                                        )
                                    }
                                    TextButton(onClick = onNavigateToAdd) {
                                        Text("+ 추가")
                                    }
                                }
                                Row() {
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

                    }

                }
            }
        )

        FilterBar(
            filterType = filterType,
            onFilterChange = { filterViewModel.setFilter(it) },
            goodsList = AllFilteredList
        )

        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "등록된 굿즈가 없습니다",
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        } else {
            if (isGridMode) {
                val rows = filteredList.chunked(gridColumns)
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(rows) { index, rowItems ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            rowItems.forEach { goods ->
                                Box(modifier = Modifier.weight(1f)) {
                                    GoodsGridItem(
                                        imgPath = goods.fanGoodsImgPath,
                                        series = goods.fanGoodsSeries,
                                        chara = goods.fanGoodsChara,
                                        category = goods.fanGoodsCategory,
                                        price = goods.fanGoodsPrice,
                                        isGotten = goods.fanGoodsIsGotten,
                                        memo = goods.fanGoodsMemo,
                                        onClick = { detailViewModel.selectGoods(goods) }
                                    )
                                }
                            }
                            repeat(gridColumns - rowItems.size) {
                                Box(modifier = Modifier.weight(1f))
                            }
                        }
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(filteredList) { index, goods ->
                        GoodsListItem(
                            imgPath = goods.fanGoodsImgPath,
                            series = goods.fanGoodsSeries,
                            chara = goods.fanGoodsChara,
                            category = goods.fanGoodsCategory,
                            price = goods.fanGoodsPrice,
                            isGotten = goods.fanGoodsIsGotten,
                            memo = goods.fanGoodsMemo,
                            onClick = { detailViewModel.selectGoods(goods) }
                        )
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