package com.example.wowagoodsproject.screen.mypage

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.BuildConfig
import com.example.wowagoodsproject.component.GoodsDetailDialog
import com.example.wowagoodsproject.component.GoodsDetailViewModel
import com.example.wowagoodsproject.component.GoodsGridItem
import com.example.wowagoodsproject.component.GoodsListItem
import com.example.wowagoodsproject.component.ListModeViewModel
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.navigation.TopBar
import com.example.wowagoodsproject.ui.theme.AppStyles

@Composable
fun MyPageScreen(
    widthSizeClass: WindowWidthSizeClass,
    viewModel: MyPageViewModel = viewModel(),
    listModeViewModel: ListModeViewModel = viewModel(),
    detailViewModel: GoodsDetailViewModel = viewModel(),
    onThemeChange: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val charaList by viewModel.charaList.collectAsState()
    val officialGottenGoods by viewModel.officialGottenGoods.collectAsState()
    val fanGottenGoods by viewModel.fanGottenGoods.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val isGridMode by listModeViewModel.isGridMode.collectAsState()
    val currentSection by viewModel.currentSection.collectAsState()
    val selectedCharaFilter by viewModel.selectedCharaFilter.collectAsState()
    val selectedCategoryFilter by viewModel.selectedCategoryFilter.collectAsState()
    val showFilterDialog by viewModel.showFilterDialog.collectAsState()
    val selectedGoods by detailViewModel.selectedGoods.collectAsState()
    val updateStatus by viewModel.updateStatus.collectAsState()

    var filterDialogTab by remember { mutableStateOf(0) }
    var filterSearch by remember { mutableStateOf("") }
    var charaSearchQuery by remember { mutableStateOf("") }
    var isDbExpanded by remember { mutableStateOf(false) }
    var isUserDataExpanded by remember { mutableStateOf(false) }
    var selectedThemeMode by remember { mutableStateOf(App.getThemeMode()) }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importData(context, it) }
    }

    BackHandler(enabled = currentSection != null) {
        viewModel.setSection(null)
        viewModel.setCharaFilter(null)
        viewModel.setCategoryFilter(null)
        charaSearchQuery = ""
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

    val goodsCharaList = (officialGottenGoods + fanGottenGoods)
        .flatMap { it.chara.split(",").map { c -> c.trim() } }
        .distinct()
        .filter { it.isNotEmpty() }
        .sortedByDescending { charaNm -> charaList.find { it.charaNm == charaNm }?.charaIsFavorite == true }

    val combinedCategoryList = (officialGottenGoods + fanGottenGoods)
        .map { it.category }
        .distinct()
        .filter { it.isNotEmpty() }
        .sorted()

    val filteredOfficialGoods = officialGottenGoods.let { list ->
        var result = list
        if (selectedCharaFilter != null) result =
            result.filter { it.chara.contains(selectedCharaFilter!!) }
        if (selectedCategoryFilter != null) result =
            result.filter { it.category == selectedCategoryFilter }
        result
    }
    val filteredFanGoods = fanGottenGoods.let { list ->
        var result = list
        if (selectedCharaFilter != null) result =
            result.filter { it.chara.contains(selectedCharaFilter!!) }
        if (selectedCategoryFilter != null) result =
            result.filter { it.category == selectedCategoryFilter }
        result
    }

    val filteredCharaList = goodsCharaList.filter {
        it.contains(filterSearch, ignoreCase = true)
    }
    val filteredCategoryList = combinedCategoryList.filter {
        it.contains(filterSearch, ignoreCase = true)
    }

    val searchedCharaList = charaList
        .sortedByDescending { it.charaIsFavorite }
        .filter { it.charaNm.contains(charaSearchQuery, ignoreCase = true) }

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
            memo = (goods as? GoodsEntity)?.goodsMemo
                ?: (goods as? FanGoodsEntity)?.fanGoodsMemo
                ?: "",
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

    if (showFilterDialog) {
        Dialog(
            onDismissRequest = {
                viewModel.setShowFilterDialog(false)
                filterSearch = ""
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
                    Text(text = "필터", style = AppStyles.textCardTitle)
                    Spacer(modifier = Modifier.height(AppStyles.paddingMedium))

                    TabRow(selectedTabIndex = filterDialogTab) {
                        Tab(
                            selected = filterDialogTab == 0,
                            onClick = {
                                filterDialogTab = 0
                                filterSearch = ""
                            },
                            text = { Text("캐릭터") }
                        )
                        Tab(
                            selected = filterDialogTab == 1,
                            onClick = {
                                filterDialogTab = 1
                                filterSearch = ""
                            },
                            text = { Text("카테고리") }
                        )
                    }

                    Spacer(modifier = Modifier.height(AppStyles.paddingMedium))

                    OutlinedTextField(
                        value = filterSearch,
                        onValueChange = { filterSearch = it },
                        label = { Text("검색") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            if (filterSearch.isNotEmpty()) {
                                IconButton(onClick = { filterSearch = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "초기화"
                                    )
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(AppStyles.paddingMedium))

                    when (filterDialogTab) {
                        0 -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                items(filteredCharaList) { charaNm ->
                                    val charaEntity = charaList.find { it.charaNm == charaNm }
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clickable {
                                                viewModel.setCharaFilter(
                                                    if (selectedCharaFilter == charaNm) null else charaNm
                                                )
                                                viewModel.setShowFilterDialog(false)
                                                filterSearch = ""
                                            }
                                            .background(
                                                color = when {
                                                    selectedCharaFilter == charaNm -> MaterialTheme.colorScheme.primaryContainer
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
                        }

                        1 -> {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(filteredCategoryList) { cat ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.setCategoryFilter(
                                                    if (selectedCategoryFilter == cat) null else cat
                                                )
                                                viewModel.setShowFilterDialog(false)
                                                filterSearch = ""
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
                                viewModel.setCategoryFilter(null)
                                viewModel.setShowFilterDialog(false)
                                filterSearch = ""
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("필터 해제") }
                        Button(
                            onClick = {
                                viewModel.setShowFilterDialog(false)
                                filterSearch = ""
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
            title = "마이페이지",
            action = {
                if (currentSection == "goods") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row() {
                                IconButton(onClick = { viewModel.setShowFilterDialog(true) }) {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = "필터",
                                        tint = if (selectedCharaFilter != null || selectedCategoryFilter != null)
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
                                    viewModel.setSection(null)
                                    viewModel.setCharaFilter(null)
                                    viewModel.setCategoryFilter(null)
                                }) {
                                    Text("뒤로")
                                }
                            }
                            Row() {
                                if (selectedCharaFilter != null) {
                                    Text(
                                        text = selectedCharaFilter!!,
                                        style = AppStyles.textCardSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                if (selectedCharaFilter != null && selectedCategoryFilter != null) {
                                    Text(
                                        text = "/",
                                        style = AppStyles.textCardSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                if (selectedCategoryFilter != null) {
                                    Text(
                                        text = selectedCategoryFilter!!,
                                        style = AppStyles.textCardSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                            }
                        }

                    }
                } else if (currentSection != null) {
                    TextButton(onClick = {
                        viewModel.setSection(null)
                        charaSearchQuery = ""
                    }) {
                        Text("뒤로")
                    }
                }
            }
        )

        if (currentSection == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(AppStyles.paddingLarge),
                verticalArrangement = Arrangement.spacedBy(AppStyles.paddingMedium)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppStyles.paddingMedium)
                ) {
                    Button(
                        onClick = { viewModel.setSection("favorite") },
                        modifier = Modifier
                            .weight(1f)
                            .height(80.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) { Text("캐릭터\n선호 설정", textAlign = TextAlign.Center) }
                    Button(
                        onClick = {
                            viewModel.setSection("goods")
                            viewModel.loadGottenGoods()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(80.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) { Text("보유\n굿즈 목록", textAlign = TextAlign.Center) }
                }

                Column {
                    Button(
                        onClick = {
                            isDbExpanded = !isDbExpanded
                            isUserDataExpanded = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("DB 업데이트")
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = if (isDbExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null
                        )
                    }
                    AnimatedVisibility(visible = isDbExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = AppStyles.paddingMedium),
                            verticalArrangement = Arrangement.spacedBy(AppStyles.paddingSmall)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.updateCharacters() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = updateStatus == null
                            ) {
                                Text(if (updateStatus != null) "업데이트 중..." else "캐릭터 업데이트")
                            }
                            OutlinedButton(
                                onClick = { viewModel.updateGoods() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = updateStatus == null
                            ) {
                                Text(if (updateStatus != null) "업데이트 중..." else "공식 굿즈 업데이트")
                            }
                        }
                    }
                }

                Column {
                    Button(
                        onClick = {
                            isUserDataExpanded = !isUserDataExpanded
                            isDbExpanded = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("유저 데이터")
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = if (isUserDataExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null
                        )
                    }
                    AnimatedVisibility(visible = isUserDataExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = AppStyles.paddingMedium),
                            verticalArrangement = Arrangement.spacedBy(AppStyles.paddingSmall)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.exportData(context) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("추출") }
                            OutlinedButton(
                                onClick = { fileLauncher.launch(arrayOf("application/zip")) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("입력") }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(AppStyles.paddingMedium)) {
                        Text(text = "테마 설정", style = AppStyles.textCardTitle)
                        Spacer(modifier = Modifier.height(AppStyles.paddingMedium))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppStyles.paddingMedium)
                        ) {
                            OutlinedCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedThemeMode = 1
                                        onThemeChange(1)
                                    },
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = Color(0xFFFFFAF0)
                                ),
                                border = if (selectedThemeMode == 1)
                                    androidx.compose.foundation.BorderStroke(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary
                                    )
                                else
                                    androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline
                                    )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(AppStyles.paddingMedium)
                                        .fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = if (selectedThemeMode == 1) Icons.Default.WbSunny else Icons.Outlined.WbSunny,
                                        contentDescription = "라이트 모드",
                                        tint = Color(0xFFB8860B),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "라이트",
                                        style = AppStyles.textCardSmall,
                                        color = Color(0xFF3D2B00)
                                    )
                                }
                            }
                            OutlinedCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedThemeMode = 2
                                        onThemeChange(2)
                                    },
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = Color(0xFF0A0A0A)
                                ),
                                border = if (selectedThemeMode == 2)
                                    androidx.compose.foundation.BorderStroke(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary
                                    )
                                else
                                    androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline
                                    )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(AppStyles.paddingMedium)
                                        .fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = if (selectedThemeMode == 2) Icons.Default.DarkMode else Icons.Outlined.DarkMode,
                                        contentDescription = "다크 모드",
                                        tint = Color(0xFFF3E85A),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "다크",
                                        style = AppStyles.textCardSmall,
                                        color = Color(0xFFEEEEEE)
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    style = AppStyles.textCardSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        } else when (currentSection) {
            "favorite" -> {
                Text(
                    text = "선호 캐릭터 설정",
                    style = AppStyles.textCardTitle,
                    modifier = Modifier.padding(AppStyles.paddingLarge)
                )
                OutlinedTextField(
                    value = charaSearchQuery,
                    onValueChange = { charaSearchQuery = it },
                    label = { Text("캐릭터 검색") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = AppStyles.paddingMedium,
                            vertical = AppStyles.paddingSmall
                        ),
                    singleLine = true,
                    trailingIcon = {
                        if (charaSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { charaSearchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "검색 초기화"
                                )
                            }
                        }
                    }
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(charaGridColumns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(0.dp),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    items(searchedCharaList) { chara ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (chara.charaIsFavorite) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surface
                                )
                                .clickable { viewModel.toggleFavorite(chara) }
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
                            Spacer(modifier = Modifier.height(AppStyles.paddingSmall))
                            Icon(
                                imageVector = if (chara.charaIsFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (chara.charaIsFavorite) Color.Red else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp)
                            )
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

            "goods" -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = {
                                viewModel.setSelectedTab(0)
                            },
                            text = { Text("공식 (${filteredOfficialGoods.size})") }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = {
                                viewModel.setSelectedTab(1)
                            },
                            text = { Text("2차창작 (${filteredFanGoods.size})") }
                        )
                    }

                    when (selectedTab) {
                        0 -> {
                            if (filteredOfficialGoods.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.background),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "보유한 공식 굿즈가 없습니다",
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
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
                                                            memo = goods.goodsMemo,
                                                            onClick = {
                                                                detailViewModel.selectGoods(
                                                                    goods
                                                                )
                                                            }
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
                                        itemsIndexed(filteredOfficialGoods) { index, goods ->
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
                                            if (index < filteredOfficialGoods.lastIndex) {
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

                        1 -> {
                            if (filteredFanGoods.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.background),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "보유한 2차창작 굿즈가 없습니다",
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
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
                                                            onClick = {
                                                                detailViewModel.selectGoods(
                                                                    goods
                                                                )
                                                            }
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
        }
    }
}