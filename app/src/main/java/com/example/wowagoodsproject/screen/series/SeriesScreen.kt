package com.example.wowagoodsproject.screen.series

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
    var showCharaFilterDialog by remember { mutableStateOf(false) }
    var showGoodsCharaFilterDialog by remember { mutableStateOf(false) }
    var selectedGoodsCharaFilter by remember { mutableStateOf<String?>(null) }

    val sortedCharaList = allCharaList.sortedByDescending { it.charaIsFavorite }

    val gridColumns = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> 2
        WindowWidthSizeClass.Medium -> 3
        else -> 4
    }

    val filteredGoods = filterViewModel.applyFilter(seriesGoods).let { list ->
        if (selectedGoodsCharaFilter != null)
            list.filter { it.chara.contains(selectedGoodsCharaFilter!!) }
        else list
    }

    val goodsCharaList = seriesGoods
        .flatMap { it.chara.split(",").map { c -> c.trim() } }
        .distinct()
        .filter { it.isNotEmpty() }
        .sortedByDescending { charaNm -> allCharaList.find { it.charaNm == charaNm }?.charaIsFavorite == true }

    BackHandler(enabled = selectedSeries != null) {
        viewModel.clearSelectedSeries()
        filterViewModel.setFilter(com.example.wowagoodsproject.component.FilterType.ALL)
        selectedGoodsCharaFilter = null
    }

    selectedGoods?.let { goods ->
        val officialGoods = goods as? GoodsEntity
        GoodsDetailDialog(
            imgPath = goods.imgPath,
            series = goods.series,
            chara = goods.chara,
            category = goods.category,
            price = goods.price,
            isGotten = goods.isGotten,
            memo = (goods as? GoodsEntity)?.goodsMemo ?: "",
            onDismiss = { detailViewModel.dismissDialog() },
            onToggleGotten = {
                officialGoods?.let { viewModel.toggleGotten(it) }
                detailViewModel.dismissDialog()
            },
            onDelete = {},
            showDelete = false
        )
    }

    if (showCharaFilterDialog) {
        Dialog(
            onDismissRequest = { showCharaFilterDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.9f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(AppStyles.paddingLarge)) {
                    Text(text = "캐릭터 필터", style = AppStyles.textCardTitle)
                    Spacer(modifier = Modifier.height(AppStyles.paddingMedium))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.5f)
                    ) {
                        items(sortedCharaList) { chara ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable {
                                        viewModel.setCharaFilter(
                                            if (selectedCharaFilter?.charaNm == chara.charaNm) null else chara
                                        )
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
                                    painter = rememberAsyncImagePainter(
                                        model = if (chara.charaUrl.isNotEmpty()) chara.charaUrl else null
                                    ),
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
                                viewModel.setCharaFilter(null)
                                showCharaFilterDialog = false
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

    if (showGoodsCharaFilterDialog) {
        Dialog(
            onDismissRequest = { showGoodsCharaFilterDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.9f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(AppStyles.paddingLarge)) {
                    Text(text = "캐릭터 필터", style = AppStyles.textCardTitle)
                    Spacer(modifier = Modifier.height(AppStyles.paddingMedium))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.5f)
                    ) {
                        items(goodsCharaList) { charaNm ->
                            val charaEntity = allCharaList.find { it.charaNm == charaNm }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable {
                                        selectedGoodsCharaFilter =
                                            if (selectedGoodsCharaFilter == charaNm) null else charaNm
                                        showGoodsCharaFilterDialog = false
                                    }
                                    .background(
                                        color = when {
                                            selectedGoodsCharaFilter == charaNm -> MaterialTheme.colorScheme.primaryContainer
                                            charaEntity?.charaIsFavorite == true -> MaterialTheme.colorScheme.primary.copy(
                                                alpha = 0.2f
                                            )

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
                    Spacer(modifier = Modifier.height(AppStyles.paddingMedium))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppStyles.paddingMedium)
                    ) {
                        OutlinedButton(
                            onClick = {
                                selectedGoodsCharaFilter = null
                                showGoodsCharaFilterDialog = false
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("필터 해제") }
                        Button(
                            onClick = { showGoodsCharaFilterDialog = false },
                            modifier = Modifier.weight(1f)
                        ) { Text("닫기") }
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = if (selectedSeries != null) "공식 - " + selectedSeries!!.seriesNm else "공식",
            action = {
                if (selectedSeries != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selectedGoodsCharaFilter != null) {
                            Text(
                                text = selectedGoodsCharaFilter!!,
                                style = AppStyles.textCardSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        IconButton(onClick = { showGoodsCharaFilterDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "캐릭터 필터",
                                tint = if (selectedGoodsCharaFilter != null)
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
                        TextButton(onClick = {
                            viewModel.clearSelectedSeries()
                            filterViewModel.setFilter(com.example.wowagoodsproject.component.FilterType.ALL)
                            selectedGoodsCharaFilter = null
                        }) {
                            Text("뒤로")
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selectedCharaFilter != null) {
                            Text(
                                text = selectedCharaFilter!!.charaNm,
                                style = AppStyles.textCardSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        IconButton(onClick = { showCharaFilterDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "캐릭터 필터",
                                tint = if (selectedCharaFilter != null)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        )

        if (selectedSeries != null) {
            FilterBar(
                filterType = filterType,
                onFilterChange = { filterViewModel.setFilter(it) },
                goodsList = seriesGoods
            )
            if (filteredGoods.isEmpty()) {
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
                    val rows = filteredGoods.chunked(gridColumns)
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
                                            memo = goods.goodsMemo,
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
                        itemsIndexed(filteredGoods) { index, goods ->
                            GoodsListItem(
                                imgPath = goods.imgPath,
                                series = goods.series,
                                chara = goods.chara,
                                category = goods.category,
                                price = goods.price,
                                isGotten = goods.isGotten,
                                memo = goods.goodsMemo,
                                onClick = { detailViewModel.selectGoods(goods) }
                            )
                            if (index < filteredGoods.lastIndex) {
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
        } else {
            TabRow(selectedTabIndex = selectedTab) {
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
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(filteredList) { index, series ->
                        val charas = viewModel.getCharasForSeries(series)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { viewModel.selectSeries(series) },
                            shape = RoundedCornerShape(0.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = rememberAsyncImagePainter(
                                            model = if (series.seriesUrl.isNotEmpty()) series.seriesUrl else null
                                        ),
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
                                            text = "${series.seriesDate} / ${series.seriesCountry}",
                                            style = AppStyles.textCardSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = series.seriesNm,
                                            style = AppStyles.textCardTitle
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
                                        charas.chunked(2).forEach { rowCharas ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(
                                                    AppStyles.paddingMedium
                                                )
                                            ) {
                                                rowCharas.forEach { chara ->
                                                    val count =
                                                        seriesCharaCountMap["${series.seriesNm}|${chara.charaNm}"]
                                                            ?: Pair(0, 0)
                                                    Row(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .padding(vertical = AppStyles.paddingSmall)
                                                            .background(
                                                                color = if (chara.charaIsFavorite) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                                shape = RoundedCornerShape(8.dp)
                                                            )
                                                            .padding(AppStyles.paddingSmall),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Image(
                                                            painter = rememberAsyncImagePainter(
                                                                model = if (chara.charaUrl.isNotEmpty()) chara.charaUrl else null
                                                            ),
                                                            contentDescription = null,
                                                            modifier = Modifier
                                                                .size(48.dp)
                                                                .clip(RoundedCornerShape(8.dp)),
                                                            contentScale = ContentScale.Crop
                                                        )
                                                        Spacer(modifier = Modifier.width(AppStyles.paddingSmall))
                                                        Column {
                                                            Text(
                                                                text = chara.charaNm,
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
                                                if (rowCharas.size == 1) {
                                                    Spacer(modifier = Modifier.weight(1f))
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