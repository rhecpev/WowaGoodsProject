package com.example.wowagoodsproject.screen.character

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
    val fanGoods by viewModel.fanGoods.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val selectedGoods by detailViewModel.selectedGoods.collectAsState()
    val filterType by filterViewModel.filterType.collectAsState()
    val isGridMode by listModeViewModel.isGridMode.collectAsState()
    val showFavoriteOnly by viewModel.showFavoriteOnly.collectAsState()

    val filteredCharaList = if (showFavoriteOnly)
        charaList.filter { it.charaIsFavorite }
    else
        charaList.sortedByDescending { it.charaIsFavorite }

    val filteredOfficialGoods = filterViewModel.applyFilter(officialGoods)
    val filteredFanGoods = filterViewModel.applyFilter(fanGoods)

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
    }

    selectedGoods?.let { goods ->
        val fanGoodsItem = goods as? FanGoodsEntity
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
                fanGoodsItem?.let { viewModel.toggleFanGotten(it) }
                detailViewModel.dismissDialog()
            },
            onDelete = {
                fanGoodsItem?.let { viewModel.deleteFanGoods(it) }
                detailViewModel.dismissDialog()
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = if (selectedChara != null) selectedChara!!.charaNm else "캐릭터별",
            action = {
                if (selectedChara != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                        }) {
                            Text("뒤로")
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
            if (filteredCharaList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = if (showFavoriteOnly) "선호 캐릭터가 없습니다" else "캐릭터가 없습니다")
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
                                .background(
                                    if (chara.charaIsFavorite) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surface
                                )
                                .clickable { viewModel.selectChara(chara) }
                                .padding(AppStyles.paddingSmall)
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(
                                    model = if (chara.charaUrl.isNotEmpty()) chara.charaUrl else null
                                ),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(80.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.outline),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = chara.charaNm,
                                style = AppStyles.textCardSmall,
                                color = if (chara.charaIsFavorite) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { viewModel.setSelectedTab(0) },
                        text = { Text("공식 (${officialGoods.size})") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { viewModel.setSelectedTab(1) },
                        text = { Text("2차창작 (${fanGoods.size})") }
                    )
                }

                FilterBar(
                    filterType = filterType,
                    onFilterChange = { filterViewModel.setFilter(it) },
                    goodsList = if (selectedTab == 0) officialGoods else fanGoods
                )

                when (selectedTab) {
                    0 -> {
                        if (filteredOfficialGoods.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) { Text("공식 굿즈가 없습니다") }
                        } else {
                            if (isGridMode) {
                                val rows = filteredOfficialGoods.chunked(gridColumns)
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    itemsIndexed(rows) { index, rowItems ->
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            rowItems.forEach { goods ->
                                                Box(modifier = Modifier.weight(1f)) {
                                                    GoodsGridItem(
                                                        imgPath = goods.imgPath,
                                                        series = goods.series,
                                                        chara = goods.chara,
                                                        category = goods.category,
                                                        price = goods.price,
                                                        isGotten = goods.isGotten,
                                                        memo = goods.goodsMemo
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
                                        GoodsListItem(
                                            imgPath = goods.imgPath,
                                            series = goods.series,
                                            chara = goods.chara,
                                            category = goods.category,
                                            price = goods.price,
                                            isGotten = goods.isGotten,
                                            memo = goods.goodsMemo
                                        )
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
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) { Text("2차창작 굿즈가 없습니다") }
                        } else {
                            if (isGridMode) {
                                val rows = filteredFanGoods.chunked(gridColumns)
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    itemsIndexed(rows) { index, rowItems ->
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            rowItems.forEach { goods ->
                                                Box(modifier = Modifier.weight(1f)) {
                                                    GoodsGridItem(
                                                        imgPath = goods.imgPath,
                                                        series = goods.series,
                                                        chara = goods.chara,
                                                        category = goods.category,
                                                        price = goods.price,
                                                        isGotten = goods.isGotten,
                                                        memo = goods.fanGoodsMemo,
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
                                            memo = goods.fanGoodsMemo,
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