package com.example.wowagoodsproject.screen.mypage

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import coil.compose.rememberAsyncImagePainter
import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.BuildConfig
import com.example.wowagoodsproject.UpdateWorker
import com.example.wowagoodsproject.component.CATEGORY_SET
import com.example.wowagoodsproject.component.FanGoodsListContent
import com.example.wowagoodsproject.component.GoodsDetailDialog
import com.example.wowagoodsproject.component.GoodsDetailViewModel
import com.example.wowagoodsproject.component.GoodsFilterDialog
import com.example.wowagoodsproject.component.GoodsListContent
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.ListModeViewModel
import com.example.wowagoodsproject.component.SetGoodsDetailDialog
import com.example.wowagoodsproject.component.filterFanGoodsList
import com.example.wowagoodsproject.component.filterGoodsList
import com.example.wowagoodsproject.component.filterGoodsListForBar
import com.example.wowagoodsproject.db.character.CharaEntity
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
    onThemeChange: (Int) -> Unit = {},
    onNavigateToPatchNotes: () -> Unit = {}
) {
    val context = LocalContext.current
    val charaList by viewModel.charaList.collectAsState()
    val officialGottenGoods by viewModel.officialGottenGoods.collectAsState()
    val allSeriesGoods by viewModel.allSeriesGoods.collectAsState()
    val fanGottenGoods by viewModel.fanGottenGoods.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val isGridMode by listModeViewModel.isGridMode.collectAsState()
    val currentSection by viewModel.currentSection.collectAsState()
    val selectedCharaFilter by viewModel.selectedCharaFilter.collectAsState()
    val selectedCategoryFilter by viewModel.selectedCategoryFilter.collectAsState()
    val showFilterDialog by viewModel.showFilterDialog.collectAsState()
    val selectedGoods by detailViewModel.selectedGoods.collectAsState()
    val latestVersion by viewModel.latestVersion.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()

    var charaSearchQuery by remember { mutableStateOf("") }
    var isUserDataExpanded by remember { mutableStateOf(false) }
    var selectedThemeMode by remember { mutableIntStateOf(App.getThemeMode()) }
    var selectedSetGoods by remember { mutableStateOf<GoodsEntity?>(null) }

    val prefs = context.getSharedPreferences("wowa_prefs", android.content.Context.MODE_PRIVATE)
    val lastUpdateTime = prefs.getString("last_update_time", null)
    val lastUpdateTotal = prefs.getInt("last_update_total", -1)

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
        .filter { it.isNotEmpty() && it != CATEGORY_SET }
        .sorted()

    val filteredOfficialGoods = filterGoodsList(
        list = officialGottenGoods,
        allGoods = allSeriesGoods,
        charaFilter = selectedCharaFilter,
        categoryFilter = selectedCategoryFilter
    )
    val officialGoodsCount = filterGoodsListForBar(
        list = officialGottenGoods,
        charaFilter = selectedCharaFilter,
        categoryFilter = selectedCategoryFilter
    )
    val filteredFanGoods = filterFanGoodsList(
        list = fanGottenGoods,
        charaFilter = selectedCharaFilter,
        categoryFilter = selectedCategoryFilter
    )

    val searchedCharaList = charaList
        .sortedWith(compareByDescending<CharaEntity> { it.charaIsFavorite }.thenBy { it.charaNm })
        .filter { it.charaNm.contains(charaSearchQuery, ignoreCase = true) }

    selectedSetGoods?.let { setGoods ->
        val components = allSeriesGoods.filter {
            it.category != CATEGORY_SET && it.memo == setGoods.memo && it.series == setGoods.series
        }
        SetGoodsDetailDialog(
            setGoods = setGoods,
            components = components,
            onDismiss = { selectedSetGoods = null },
            onToggleGotten = { component -> viewModel.toggleOfficialGotten(component) },
            onSetPending = { component -> viewModel.setOfficialPending(component) },
            onBulkToggleGotten = { isGotten ->
                viewModel.bulkToggleOfficialGotten(
                    setGoods,
                    isGotten
                ); selectedSetGoods = null
            },
            highlightChara = selectedCharaFilter,
            highlightCategory = selectedCategoryFilter
        )
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
            isPending = goods.status == GoodsStatus.PENDING,
            memo = (goods as? GoodsEntity)?.goodsMemo ?: (goods as? FanGoodsEntity)?.fanGoodsMemo
            ?: "",
            onDismiss = { detailViewModel.dismissDialog() },
            onToggleGotten = {
                officialGoods?.let { viewModel.toggleOfficialGotten(it) }
                fanGoods?.let { viewModel.toggleFanGotten(it) }
                detailViewModel.dismissDialog()
            },
            onSetPending = {
                officialGoods?.let { viewModel.setOfficialPending(it) }
                fanGoods?.let { viewModel.setFanPending(it) }
                detailViewModel.dismissDialog()
            },
            onDelete = {},
            showDelete = false
        )
    }

    if (showFilterDialog) {
        GoodsFilterDialog(
            widthSizeClass = widthSizeClass,
            charaList = charaList.filter { chara -> goodsCharaList.contains(chara.charaNm) },
            categoryList = combinedCategoryList,
            selectedCharaFilter = selectedCharaFilter,
            selectedCategoryFilter = selectedCategoryFilter,
            onCharaSelect = { viewModel.setCharaFilter(it) },
            onCategorySelect = { viewModel.setCategoryFilter(it) },
            onClearFilter = { viewModel.setCharaFilter(null); viewModel.setCategoryFilter(null) },
            onDismiss = { viewModel.setShowFilterDialog(false) }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                title = "마이페이지",
                action = {
                    if (currentSection == "goods") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row {
                                    IconButton(onClick = { viewModel.setShowFilterDialog(true) }) {
                                        Icon(
                                            imageVector = Icons.Default.FilterList,
                                            contentDescription = "필터",
                                            tint = if (selectedCharaFilter != null || selectedCategoryFilter != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
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
                                Row {
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
                            viewModel.setSection(null); charaSearchQuery = ""
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
                            onClick = { viewModel.setSection("goods"); viewModel.loadGottenGoods() },
                            modifier = Modifier
                                .weight(1f)
                                .height(80.dp),
                            shape = RoundedCornerShape(4.dp)
                        ) { Text("보유\n굿즈 목록", textAlign = TextAlign.Center) }
                    }
                    Column {
                        Button(
                            onClick = { isUserDataExpanded = !isUserDataExpanded },
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
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                                        .clickable { selectedThemeMode = 1; onThemeChange(1) },
                                    colors = CardDefaults.outlinedCardColors(
                                        containerColor = Color(
                                            0xFFFFFAF0
                                        )
                                    ),
                                    border = if (selectedThemeMode == 1) androidx.compose.foundation.BorderStroke(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary
                                    )
                                    else androidx.compose.foundation.BorderStroke(
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
                                        .clickable { selectedThemeMode = 2; onThemeChange(2) },
                                    colors = CardDefaults.outlinedCardColors(
                                        containerColor = Color(
                                            0xFF0A0A0A
                                        )
                                    ),
                                    border = if (selectedThemeMode == 2) androidx.compose.foundation.BorderStroke(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary
                                    )
                                    else androidx.compose.foundation.BorderStroke(
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
                    OutlinedButton(
                        onClick = {
                            val today = java.text.SimpleDateFormat(
                                "yyyyMMdd",
                                java.util.Locale.getDefault()
                            ).format(java.util.Date())
                            val lastDate = prefs.getString("manual_update_date", "")
                            val count =
                                if (lastDate == today) prefs.getInt("manual_update_count", 0) else 0
                            val limit = 3

                            if (count < limit) {
                                prefs.edit()
                                    .putString("manual_update_date", today)
                                    .putInt("manual_update_count", count + 1)
                                    .apply()
                                WorkManager.getInstance(context).enqueue(
                                    OneTimeWorkRequestBuilder<UpdateWorker>().build()
                                )
                            } else {
                                Toast.makeText(
                                    context,
                                    "오늘 수동 업데이트 횟수를 초과했습니다 (${limit}회)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("수동 업데이트") }
                    Button(
                        onClick = { onNavigateToPatchNotes() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("업데이트 이력") }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AppStyles.paddingMedium),
                            verticalArrangement = Arrangement.spacedBy(AppStyles.paddingSmall)
                        ) {
                            Text(text = "데이터 업데이트", style = AppStyles.textCardTitle)
                            HorizontalDivider()
                            if (lastUpdateTime == null) {
                                Text(
                                    text = "업데이트 이력 없음",
                                    style = AppStyles.textCardSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = "마지막 업데이트: $lastUpdateTime",
                                    style = AppStyles.textCardSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (lastUpdateTotal > 0) "변경사항 있음 (${lastUpdateTotal}개)" else "최신 상태",
                                    style = AppStyles.textCardSmall,
                                    color = if (lastUpdateTotal > 0) MaterialTheme.colorScheme.primary else AppStyles.colorGotten
                                )
                            }
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://github.com/rhecpev/WowaGoodsProject/releases/latest")
                                )
                                context.startActivity(intent)
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AppStyles.paddingMedium),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "현재 버전: v${BuildConfig.VERSION_NAME}",
                                style = AppStyles.textCardSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "최신 버전: ${latestVersion?.let { "v$it" } ?: "확인 중..."}",
                                style = AppStyles.textCardSmall,
                                color = if (latestVersion != null && latestVersion != BuildConfig.VERSION_NAME)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "릴리즈 페이지 바로가기 →",
                                style = AppStyles.textCardSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
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
                                    .background(if (chara.charaIsFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                    .clickable { viewModel.toggleFavorite(chara) }
                                    .padding(AppStyles.paddingSmall)
                            ) {
                                Image(
                                    painter = rememberAsyncImagePainter(model = chara.charaUrl.ifEmpty { null }),
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
                                    color = if (chara.charaIsFavorite) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
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
                                onClick = { viewModel.setSelectedTab(0) },
                                text = { Text("공식 (${officialGoodsCount.size})") })
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { viewModel.setSelectedTab(1) },
                                text = { Text("2차창작 (${filteredFanGoods.size})") })
                        }

                        when (selectedTab) {
                            0 -> {
                                GoodsListContent(
                                    goods = filteredOfficialGoods,
                                    allGoods = allSeriesGoods,
                                    isGridMode = isGridMode,
                                    gridColumns = gridColumns,
                                    filterType = com.example.wowagoodsproject.component.FilterType.ALL,
                                    highlightCategory = selectedCategoryFilter,
                                    highlightChara = selectedCharaFilter,
                                    onGoodsClick = { detailViewModel.selectGoods(it) },
                                    onSetGoodsClick = { selectedSetGoods = it },
                                    onComponentClick = { detailViewModel.selectGoods(it) },
                                    onBulkToggleGotten = { setGoods, isGotten ->
                                        viewModel.bulkToggleOfficialGotten(
                                            setGoods,
                                            isGotten
                                        )
                                    }

                                )
                            }

                            1 -> {
                                FanGoodsListContent(
                                    goods = filteredFanGoods,
                                    isGridMode = isGridMode,
                                    gridColumns = gridColumns,
                                    onGoodsClick = { detailViewModel.selectGoods(it) }
                                )
                            }
                        }
                    }
                }
            }
        }
        if (isImporting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(AppStyles.paddingMedium))
                    Text(text = "데이터 입력 중...")
                }
            }
        }
    }
}