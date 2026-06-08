package com.example.wowagoodsproject.screen.fan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.wowagoodsproject.component.FilterBar
import com.example.wowagoodsproject.component.FilterViewModel
import com.example.wowagoodsproject.component.GoodsDetailDialog
import com.example.wowagoodsproject.component.GoodsDetailViewModel
import com.example.wowagoodsproject.component.GoodsGridItem
import com.example.wowagoodsproject.component.GoodsListItem
import com.example.wowagoodsproject.component.ListModeViewModel
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
    val filteredList = filterViewModel.applyFilter(goodsList)

    val gridColumns = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> 2
        WindowWidthSizeClass.Medium -> 3
        else -> 4
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

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = "2차창작",
            action = {
                Row {
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
            }
        )

        FilterBar(
            filterType = filterType,
            onFilterChange = { filterViewModel.setFilter(it) },
            goodsList = goodsList
        )

        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "등록된 굿즈가 없습니다")
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
                    itemsIndexed(filteredList) { index, goods ->
                        GoodsListItem(
                            imgPath = goods.fanGoodsImgPath,
                            series = goods.fanGoodsSeries,
                            chara = goods.fanGoodsChara,
                            category = goods.fanGoodsCategory,
                            price = goods.fanGoodsPrice,
                            isGotten = goods.fanGoodsIsGotten,
                            onClick = { detailViewModel.selectGoods(goods) }
                        )
                        if (index < filteredList.lastIndex) {
                            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(AppStyles.paddingMedium))
                        }
                    }
                }
            }
        }
    }
}