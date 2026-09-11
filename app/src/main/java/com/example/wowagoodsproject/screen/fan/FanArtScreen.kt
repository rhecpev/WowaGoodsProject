package com.example.wowagoodsproject.screen.fan

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.navigation.TopBar
import com.example.wowagoodsproject.navigation.TopBarAction

@Composable
fun FanArtScreen(
    onNavigateToAdd: () -> Unit,
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

    val selectedGoodsCharaFilter by filterViewModel.selectedCharaFilter.collectAsState()
    val selectedGoodsCategoryFilter by filterViewModel.selectedCategoryFilter.collectAsState()

    val filteredList = filterFanGoodsList(
        list = filterViewModel.applyFilter(goodsList).second,
        charaFilter = selectedGoodsCharaFilter,
        categoryFilter = selectedGoodsCategoryFilter
    )

    val AllFilteredList = filterFanGoodsList(
        list = filterViewModel.applyFilter(goodsList).first,
        charaFilter = selectedGoodsCharaFilter,
        categoryFilter = selectedGoodsCategoryFilter
    )

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
        .sortedWith(
            compareByDescending<String> { charaNm -> allCharaList.find { it.charaNm == charaNm }?.charaIsFavorite == true }
                .thenBy { it }
        )
    val goodsCategoryList = goodsList
        .map { it.category }
        .distinct()
        .filter { it.isNotEmpty() }
        .sorted()

    LaunchedEffect(Unit) {
        viewModel.loadGoods()
    }

    // 상태를 바꾼 뒤에도 상세 시트가 최신 값을 보여주도록 목록에서 다시 찾는다.
    val displayedGoods = (selectedGoods as? FanGoodsEntity)?.let { selected ->
        goodsList.find { it.fanGoodsId == selected.fanGoodsId } ?: selected
    }

    displayedGoods?.let { fanGoods ->
        GoodsDetailDialog(
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
            onDelete = {
                viewModel.delete(fanGoods)
                detailViewModel.dismissDialog()
            },
            onSeriesClick = { seriesName ->
                detailViewModel.dismissDialog()
                onNavigateToSeries(seriesName)
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
            onDismiss = { showGoodsFilterDialog = false }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                title = "2차창작",
                actions = {
                    TopBarAction(
                        icon = Icons.Default.FilterList,
                        contentDescription = "필터",
                        onClick = { showGoodsFilterDialog = true },
                        active = selectedGoodsCharaFilter != null || selectedGoodsCategoryFilter != null
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
                    selectedGoodsCharaFilter?.let { ActiveFilter(it) { filterViewModel.setCharaFilter(null) } },
                    selectedGoodsCategoryFilter?.let { ActiveFilter(it) { filterViewModel.setCategoryFilter(null) } }
                ),
                onClearAll = { filterViewModel.clearGoodsFilter() }
            )

            FilterBar(
                filterType = filterType,
                onFilterChange = { filterViewModel.setFilter(it) },
                goodsList = AllFilteredList
            )

            FanGoodsListContent(
                goods = filteredList,
                isGridMode = isGridMode,
                gridColumns = gridColumns,
                onGoodsClick = { detailViewModel.selectGoods(it) },
                // 목록 끝이 등록 버튼에 가려지지 않게 아래 여백을 둔다.
                contentPadding = PaddingValues(bottom = 88.dp)
            )
        }

        ExtendedFloatingActionButton(
            onClick = onNavigateToAdd,
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("굿즈 등록") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}
