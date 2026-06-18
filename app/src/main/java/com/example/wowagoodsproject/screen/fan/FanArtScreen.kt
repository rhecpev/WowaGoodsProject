package com.example.wowagoodsproject.screen.fan


import androidx.compose.foundation.layout.*

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.unit.dp

import androidx.lifecycle.viewmodel.compose.viewModel
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
        .sortedByDescending { charaNm -> allCharaList.find { it.charaNm == charaNm }?.charaIsFavorite == true }

    val goodsCategoryList = goodsList
        .map { it.category }
        .distinct()
        .filter { it.isNotEmpty() }
        .sorted()


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
            memo = (goods).fanGoodsMemo,
            onDismiss = { detailViewModel.dismissDialog() },
            isPending = (goods as? FanGoodsEntity)?.status == GoodsStatus.PENDING,
            onToggleGotten = {
                viewModel.toggleGotten(fanGoods)
                detailViewModel.dismissDialog()
            },
            onSetPending = {
                viewModel.setPending(fanGoods)
                detailViewModel.dismissDialog()
            },
            onDelete = {
                viewModel.delete(fanGoods)
                detailViewModel.dismissDialog()
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
                                            tint = if (selectedGoodsCharaFilter != null || selectedGoodsCategoryFilter != null)                                                MaterialTheme.colorScheme.primary
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

        FanGoodsListContent(
            goods = filteredList,
            isGridMode = isGridMode,
            gridColumns = gridColumns,
            onGoodsClick = { detailViewModel.selectGoods(it) }
        )
    }
}