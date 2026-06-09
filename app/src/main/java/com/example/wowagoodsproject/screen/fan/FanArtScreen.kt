package com.example.wowagoodsproject.screen.fan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    var searchQuery by remember { mutableStateOf("") }
    var showCategoryFilterDialog by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var categorySearch by remember { mutableStateOf("") }

    val categoryList = goodsList
        .map { it.fanGoodsCategory }
        .distinct()
        .filter { it.isNotEmpty() }
        .sorted()

    val filteredCategories = categoryList.filter {
        it.contains(categorySearch, ignoreCase = true)
    }

    val filteredList = filterViewModel.applyFilter(goodsList).let { list ->
        if (selectedCategoryFilter != null) list.filter { it.fanGoodsCategory == selectedCategoryFilter }
        else list
    }

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

    if (showCategoryFilterDialog) {
        Dialog(
            onDismissRequest = {
                showCategoryFilterDialog = false
                categorySearch = ""
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.7f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
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
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredCategories) { cat ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedCategoryFilter = if (selectedCategoryFilter == cat) null else cat
                                        showCategoryFilterDialog = false
                                        categorySearch = ""
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectedCategoryFilter == cat)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Text(
                                    text = cat,
                                    modifier = Modifier.padding(AppStyles.paddingMedium),
                                    color = if (selectedCategoryFilter == cat)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface
                                )
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
                                selectedCategoryFilter = null
                                showCategoryFilterDialog = false
                                categorySearch = ""
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("필터 해제") }
                        Button(
                            onClick = {
                                showCategoryFilterDialog = false
                                categorySearch = ""
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("닫기") }
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
                    if (selectedCategoryFilter != null) {
                        Text(
                            text = selectedCategoryFilter!!,
                            style = AppStyles.textCardSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    IconButton(onClick = { showCategoryFilterDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "카테고리 필터",
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
            }
        )

        FilterBar(
            filterType = filterType,
            onFilterChange = { filterViewModel.setFilter(it) },
            goodsList = goodsList
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
                            memo = goods.fanGoodsMemo,
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