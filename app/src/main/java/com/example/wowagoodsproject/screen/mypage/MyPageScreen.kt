package com.example.wowagoodsproject.screen.mypage

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.*
import android.widget.Toast
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.delay
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
import com.example.wowagoodsproject.component.findSetGoods
import com.example.wowagoodsproject.component.getSetComponents
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.navigation.TopBar
import com.example.wowagoodsproject.ui.theme.AppStyles
import com.example.wowagoodsproject.screen.series.SeriesViewModel

@Composable
fun MyPageScreen(
    widthSizeClass: WindowWidthSizeClass,
    viewModel: MyPageViewModel = viewModel(),
    listModeViewModel: ListModeViewModel = viewModel(),
    detailViewModel: GoodsDetailViewModel = viewModel(),
    onThemeChange: (Int) -> Unit = {},
    onNavigateToPatchNotes: () -> Unit = {},
    onNavigateToNews: () -> Unit = {},
    onNavigateToSeries: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val seriesViewModel: SeriesViewModel = viewModel()
    val seriesList by seriesViewModel.seriesList.collectAsState()
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
    val importProgress by viewModel.importProgress.collectAsState()
    val unreadNewsCount by viewModel.unreadNewsCount.collectAsState()

    var isUserDataExpanded by remember { mutableStateOf(false) }
    var isUpdateExpanded by remember { mutableStateOf(false) }
    var isVersionExpanded by remember { mutableStateOf(false) }
    var selectedThemeMode by remember { mutableIntStateOf(App.getThemeMode()) }
    var selectedSetGoods by remember { mutableStateOf<GoodsEntity?>(null) }
    var setDialogComponent by remember { mutableStateOf<GoodsEntity?>(null) }

    val prefs = context.getSharedPreferences("wowa_prefs", android.content.Context.MODE_PRIVATE)
    val lastUpdateTime = prefs.getString("last_update_time", null)
    val lastUpdateTotal = prefs.getInt("last_update_total", -1)

    // 마지막 업데이트 항목을 연속 10번 누르면 수동 업데이트 횟수 제한이 10분간 풀린다.
    var bypassUntil by remember { mutableLongStateOf(prefs.getLong(MANUAL_LIMIT_BYPASS_KEY, 0L)) }
    var bypassTapCount by remember { mutableIntStateOf(0) }
    var lastBypassTapTime by remember { mutableLongStateOf(0L) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // 해제 중에는 남은 시간을 초 단위로 갱신한다.
    LaunchedEffect(bypassUntil) {
        while (bypassUntil > System.currentTimeMillis()) {
            nowMillis = System.currentTimeMillis()
            delay(1000)
        }
        nowMillis = System.currentTimeMillis()
    }
    val bypassRemainMillis = (bypassUntil - nowMillis).coerceAtLeast(0L)
    val isLimitBypassed = bypassRemainMillis > 0L

    val onBypassTap: () -> Unit = {
        val now = System.currentTimeMillis()
        // 연속 탭으로 인정하는 간격
        bypassTapCount = if (now - lastBypassTapTime <= BYPASS_TAP_INTERVAL_MS) bypassTapCount + 1 else 1
        lastBypassTapTime = now

        when {
            bypassTapCount >= BYPASS_TAP_COUNT -> {
                bypassTapCount = 0
                bypassUntil = now + BYPASS_DURATION_MS
                prefs.edit().putLong(MANUAL_LIMIT_BYPASS_KEY, bypassUntil).apply()
                Toast.makeText(
                    context,
                    "수동 업데이트 횟수 제한이 10분간 해제되었습니다",
                    Toast.LENGTH_SHORT
                ).show()
            }

            bypassTapCount >= BYPASS_TAP_COUNT - 3 -> {
                Toast.makeText(
                    context,
                    "${BYPASS_TAP_COUNT - bypassTapCount}번 남았습니다",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val onManualUpdate: () -> Unit = {
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
            .format(java.util.Date())
        val lastDate = prefs.getString("manual_update_date", "")
        val count = if (lastDate == today) prefs.getInt("manual_update_count", 0) else 0
        val limit = 3
        val bypassed = bypassUntil > System.currentTimeMillis()

        if (bypassed || count < limit) {
            // 제한이 해제된 동안 쓴 횟수는 오늘치에서 차감하지 않는다.
            if (!bypassed) {
                prefs.edit()
                    .putString("manual_update_date", today)
                    .putInt("manual_update_count", count + 1)
                    .apply()
            }
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
    }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importData(context, it) }
    }

    BackHandler(enabled = currentSection != null) {
        viewModel.setSection(null)
        viewModel.setCharaFilter(null)
        viewModel.setCategoryFilter(null)
    }

    val gridColumns = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> 2
        WindowWidthSizeClass.Medium -> 3
        else -> 4
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
        charaFilter = selectedCharaFilter,
        categoryFilter = selectedCategoryFilter
    )
    val filteredFanGoods = filterFanGoodsList(
        list = fanGottenGoods,
        charaFilter = selectedCharaFilter,
        categoryFilter = selectedCategoryFilter
    )

    // 세트 다이얼로그에서 상태를 바꿔도 아래에 깔린 굿즈 다이얼로그가 최신 값을 보여주도록 다시 조회한다.
    val displayedGoods = selectedGoods?.let { selected ->
        when (selected) {
            is GoodsEntity -> allSeriesGoods.find { it.goodsId == selected.goodsId } ?: selected
            is FanGoodsEntity -> fanGottenGoods.find { it.fanGoodsId == selected.fanGoodsId } ?: selected
            else -> selected
        }
    }

    displayedGoods?.let { goods ->
        val officialGoods = goods as? GoodsEntity
        val fanGoods = goods as? FanGoodsEntity
        val parentSet = officialGoods?.let { findSetGoods(it, allSeriesGoods) }
        val setComponents = parentSet?.let { getSetComponents(it, allSeriesGoods) } ?: emptyList()
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
            setGoods = parentSet,
            setComponentTotal = setComponents.size,
            setComponentGotten = setComponents.count { it.isGotten },
            onSetClick = {
                setDialogComponent = officialGoods
                selectedSetGoods = parentSet
            },
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
            showDelete = false,
            onSeriesClick = { seriesName ->
                detailViewModel.dismissDialog()
                onNavigateToSeries(seriesName)
            }
        )
    }

    selectedSetGoods?.let { setGoods ->
        SetGoodsDetailDialog(
            setGoods = setGoods,
            components = getSetComponents(setGoods, allSeriesGoods),
            initialComponent = setDialogComponent,
            onDismiss = { selectedSetGoods = null; setDialogComponent = null },
            onToggleGotten = { component -> viewModel.toggleOfficialGotten(component) },
            onSetPending = { component -> viewModel.setOfficialPending(component) },
            onBulkToggleGotten = { isGotten ->
                viewModel.bulkToggleOfficialGotten(setGoods, isGotten)
                selectedSetGoods = null
                setDialogComponent = null
            },
            highlightChara = selectedCharaFilter,
            highlightCategory = selectedCategoryFilter
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
                                            imageVector = if (isGridMode) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
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
                            viewModel.setSection(null)
                        }) {
                            Text("뒤로")
                        }
                    } else {
                        BadgedBox(
                            badge = {
                                if (unreadNewsCount > 0) {
                                    Badge {
                                        Text(if (unreadNewsCount > 99) "99+" else "$unreadNewsCount")
                                    }
                                }
                            }
                        ) {
                            IconButton(onClick = onNavigateToNews) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "소식",
                                    tint = if (unreadNewsCount > 0) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
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
                    MenuButton(
                        text = "보유 굿즈 목록",
                        icon = Icons.Default.Inventory2,
                        onClick = { viewModel.setSection("goods"); viewModel.loadGottenGoods() }
                    )

                    ExpandableMenu(
                        text = "유저 데이터",
                        icon = Icons.Default.Storage,
                        expanded = isUserDataExpanded,
                        onToggle = { isUserDataExpanded = !isUserDataExpanded }
                    ) {
                        SubMenuButton(
                            text = "추출",
                            icon = Icons.Default.FileDownload,
                            onClick = { viewModel.exportData(context) }
                        )
                        SubMenuButton(
                            text = "입력",
                            icon = Icons.Default.FileUpload,
                            onClick = { fileLauncher.launch(arrayOf("application/zip")) }
                        )
                    }

                    ExpandableMenu(
                        text = "업데이트 관리",
                        icon = Icons.Default.Sync,
                        expanded = isUpdateExpanded,
                        onToggle = { isUpdateExpanded = !isUpdateExpanded }
                    ) {
                        SubMenuButton(
                            text = "수동 업데이트",
                            icon = Icons.Default.Refresh,
                            onClick = onManualUpdate
                        )
                        SubMenuButton(
                            text = "업데이트 이력",
                            icon = Icons.Default.History,
                            onClick = { onNavigateToPatchNotes() }
                        )
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = onBypassTap
                                ),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(AppStyles.paddingMedium),
                                verticalArrangement = Arrangement.spacedBy(AppStyles.paddingSmall)
                            ) {
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
                                if (isLimitBypassed) {
                                    Text(
                                        text = "횟수 제한 해제 중 (${formatRemainTime(bypassRemainMillis)})",
                                        style = AppStyles.textCardSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    ExpandableMenu(
                        text = "앱 버전 정보",
                        icon = Icons.Default.Info,
                        expanded = isVersionExpanded,
                        onToggle = { isVersionExpanded = !isVersionExpanded }
                    ) {
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

                    Spacer(modifier = Modifier.height(AppStyles.paddingSmall))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppStyles.paddingMedium)
                    ) {
                        ThemeCard(
                            label = "라이트",
                            icon = if (selectedThemeMode == 1) Icons.Default.WbSunny else Icons.Outlined.WbSunny,
                            containerColor = Color(0xFFFFFAF0),
                            iconTint = Color(0xFFB8860B),
                            labelColor = Color(0xFF3D2B00),
                            selected = selectedThemeMode == 1,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedThemeMode = 1; onThemeChange(1) }
                        )
                        ThemeCard(
                            label = "다크",
                            icon = if (selectedThemeMode == 2) Icons.Default.DarkMode else Icons.Outlined.DarkMode,
                            containerColor = Color(0xFF0A0A0A),
                            iconTint = Color(0xFFF3E85A),
                            labelColor = Color(0xFFEEEEEE),
                            selected = selectedThemeMode == 2,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedThemeMode = 2; onThemeChange(2) }
                        )
                    }
                }
            } else when (currentSection) {
                "goods" -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        SecondaryTabRow(selectedTabIndex = selectedTab) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { viewModel.setSelectedTab(0) },
                                text = { Text("공식 (${filteredOfficialGoods.size})") })
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { viewModel.setSelectedTab(1) },
                                text = { Text("2차창작 (${filteredFanGoods.size})") })
                        }

                        when (selectedTab) {
                            0 -> {
                                GoodsListContent(
                                    goods = filteredOfficialGoods,
                                    isGridMode = isGridMode,
                                    gridColumns = gridColumns,
                                    onGoodsClick = { detailViewModel.selectGoods(it) }
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
                    CircularProgressIndicator(progress = { importProgress })
                    Spacer(modifier = Modifier.height(AppStyles.paddingMedium))
                    Text(text = "데이터 입력 중... ${(importProgress * 100).toInt()}%")
                }
            }
        }
    }
}


/** 하위 메뉴가 없는 최상위 메뉴 한 줄 */
@Composable
private fun MenuButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(AppStyles.paddingSmall))
        Text(text)
        Spacer(modifier = Modifier.weight(1f))
    }
}

/** 누르면 아래로 하위 메뉴가 펼쳐지는 최상위 메뉴 */
@Composable
private fun ExpandableMenu(
    text: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Button(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(AppStyles.paddingSmall))
            Text(text)
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = AppStyles.paddingLarge, top = AppStyles.paddingSmall),
                verticalArrangement = Arrangement.spacedBy(AppStyles.paddingSmall),
                content = content
            )
        }
    }
}

/** 펼쳐진 하위 메뉴 한 줄 */
@Composable
private fun SubMenuButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(AppStyles.paddingSmall))
        Text(text)
        Spacer(modifier = Modifier.weight(1f))
    }
}

/** 라이트/다크 테마 선택 카드 */
@Composable
private fun ThemeCard(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    iconTint: Color,
    labelColor: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(
            modifier = Modifier
                .padding(AppStyles.paddingMedium)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, style = AppStyles.textCardSmall, color = labelColor)
        }
    }
}

/** 마지막 업데이트 항목을 연속으로 눌러 수동 업데이트 횟수 제한을 푸는 조건 */
private const val BYPASS_TAP_COUNT = 10
private const val BYPASS_TAP_INTERVAL_MS = 2000L
private const val BYPASS_DURATION_MS = 10 * 60 * 1000L
private const val MANUAL_LIMIT_BYPASS_KEY = "manual_limit_bypass_until"

private fun formatRemainTime(millis: Long): String {
    val totalSeconds = millis / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
