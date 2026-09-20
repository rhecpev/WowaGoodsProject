package com.example.wowagoodsproject.screen.mypage

import android.content.SharedPreferences
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.delay
import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.BuildConfig
import com.example.wowagoodsproject.UpdateManager
import com.example.wowagoodsproject.UpdateWorker
import com.example.wowagoodsproject.component.CATEGORY_SET
import com.example.wowagoodsproject.component.FanGoodsListContent
import com.example.wowagoodsproject.component.GoodsDetailDialog
import com.example.wowagoodsproject.component.GoodsDetailViewModel
import com.example.wowagoodsproject.component.GoodsFilterDialog
import com.example.wowagoodsproject.component.GoodsListContent
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.ListModeViewModel
import com.example.wowagoodsproject.component.ProgressDialog
import com.example.wowagoodsproject.component.SetGoodsDetailDialog
import com.example.wowagoodsproject.component.filterFanGoodsList
import com.example.wowagoodsproject.component.filterGoodsList
import com.example.wowagoodsproject.component.findSetGoods
import com.example.wowagoodsproject.component.getSetComponents
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.navigation.TopBar
import com.example.wowagoodsproject.navigation.TopBarAction
import com.example.wowagoodsproject.component.ActiveFilter
import com.example.wowagoodsproject.component.ActiveFilterChips
import com.example.wowagoodsproject.ui.theme.AppStyles
import com.example.wowagoodsproject.ui.theme.AppTheme

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
    val pendingOfficialGoods by viewModel.pendingOfficialGoods.collectAsState()
    val pendingFanGoods by viewModel.pendingFanGoods.collectAsState()
    val isUpdating by UpdateManager.isRunning.collectAsState()

    var selectedThemeMode by remember { mutableIntStateOf(App.getThemeMode()) }
    // 구매예정 목록에서 일괄 적용할 굿즈들
    var selectedPendingOfficialIds by remember { mutableStateOf(emptySet<Int>()) }
    var selectedPendingFanIds by remember { mutableStateOf(emptySet<Int>()) }
    var selectedSetGoods by remember { mutableStateOf<GoodsEntity?>(null) }
    var setDialogComponent by remember { mutableStateOf<GoodsEntity?>(null) }

    val prefs = remember { context.getSharedPreferences("wowa_prefs", android.content.Context.MODE_PRIVATE) }
    // 업데이트가 끝나면(isUpdating 이 false 로 바뀌면) 마지막 업데이트 정보를 다시 읽는다.
    val lastUpdateTime = remember(isUpdating) { prefs.getString("last_update_time", null) }
    val lastUpdateTotal = remember(isUpdating) { prefs.getInt("last_update_total", -1) }
    var manualUsedToday by remember { mutableIntStateOf(readManualUpdateCount(prefs)) }

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
        val count = readManualUpdateCount(prefs)
        val bypassed = bypassUntil > System.currentTimeMillis()

        when {
            // 이미 돌고 있으면 횟수를 차감하지 않는다.
            UpdateManager.isRunning.value -> {
                Toast.makeText(context, "이미 업데이트 중입니다", Toast.LENGTH_SHORT).show()
            }

            bypassed || count < MANUAL_UPDATE_LIMIT -> {
                // 제한이 해제된 동안 쓴 횟수는 오늘치에서 차감하지 않는다.
                if (!bypassed) {
                    prefs.edit()
                        .putString("manual_update_date", todayKey())
                        .putInt("manual_update_count", count + 1)
                        .apply()
                    manualUsedToday = count + 1
                }
                // 재시도 대기 중인 이전 요청이 있으면 새 요청으로 바꿔 바로 실행되게 한다.
                WorkManager.getInstance(context).enqueueUniqueWork(
                    MANUAL_UPDATE_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<UpdateWorker>().build()
                )
            }

            else -> {
                Toast.makeText(
                    context,
                    "오늘 수동 업데이트 횟수를 초과했습니다 (${MANUAL_UPDATE_LIMIT}회)",
                    Toast.LENGTH_SHORT
                ).show()
            }
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
        selectedPendingOfficialIds = emptySet()
        selectedPendingFanIds = emptySet()
    }

    val gridColumns = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> 2
        WindowWidthSizeClass.Medium -> 3
        else -> 4
    }

    // 아래 목록들은 굿즈 수만큼 도는 계산이라 화면이 다시 그려질 때마다 하면 안 된다.
    // (테마 변경처럼 화면 전체가 recomposition 될 때 특히 비싸다)
    val favoriteCharaNames = remember(charaList) {
        charaList.filter { it.charaIsFavorite }.map { it.charaNm }.toSet()
    }

    val goodsCharaList = remember(officialGottenGoods, fanGottenGoods, favoriteCharaNames) {
        (officialGottenGoods + fanGottenGoods)
            .flatMap { it.chara.split(",").map { c -> c.trim() } }
            .distinct()
            .filter { it.isNotEmpty() }
            .sortedByDescending { charaNm -> charaNm in favoriteCharaNames }
    }

    val combinedCategoryList = remember(officialGottenGoods, fanGottenGoods) {
        (officialGottenGoods + fanGottenGoods)
            .map { it.category }
            .distinct()
            .filter { it.isNotEmpty() && it != CATEGORY_SET }
            .sorted()
    }

    val filteredOfficialGoods = remember(officialGottenGoods, selectedCharaFilter, selectedCategoryFilter) {
        filterGoodsList(
            list = officialGottenGoods,
            charaFilter = selectedCharaFilter,
            categoryFilter = selectedCategoryFilter
        )
    }
    val filteredFanGoods = remember(fanGottenGoods, selectedCharaFilter, selectedCategoryFilter) {
        filterFanGoodsList(
            list = fanGottenGoods,
            charaFilter = selectedCharaFilter,
            categoryFilter = selectedCategoryFilter
        )
    }

    // 세트 다이얼로그에서 상태를 바꿔도 아래에 깔린 굿즈 다이얼로그가 최신 값을 보여주도록 다시 조회한다.
    val displayedGoods = remember(selectedGoods, allSeriesGoods, fanGottenGoods) {
        selectedGoods?.let { selected ->
            when (selected) {
                is GoodsEntity -> allSeriesGoods.find { it.goodsId == selected.goodsId } ?: selected
                is FanGoodsEntity -> fanGottenGoods.find { it.fanGoodsId == selected.fanGoodsId } ?: selected
                else -> selected
            }
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
            onBulkSetStatus = { status ->
                viewModel.bulkSetOfficialStatus(setGoods, status)
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

    if (isImporting) {
        ProgressDialog(
            title = "데이터 입력 중",
            fraction = importProgress,
            icon = Icons.Default.FileUpload
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val closeSection: () -> Unit = {
            viewModel.setSection(null)
            viewModel.setCharaFilter(null)
            viewModel.setCategoryFilter(null)
            selectedPendingOfficialIds = emptySet()
            selectedPendingFanIds = emptySet()
        }
        TopBar(
            title = when (currentSection) {
                "goods" -> "보유 굿즈"
                "pending" -> "구매예정 굿즈"
                else -> "마이페이지"
            },
            onBack = if (currentSection != null) closeSection else null,
            actions = {
                if (currentSection == "goods") {
                    TopBarAction(
                        icon = Icons.Default.FilterList,
                        contentDescription = "필터",
                        onClick = { viewModel.setShowFilterDialog(true) },
                        active = selectedCharaFilter != null || selectedCategoryFilter != null
                    )
                    TopBarAction(
                        icon = if (isGridMode) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                        contentDescription = "보기 방식 전환",
                        onClick = { listModeViewModel.toggleGridMode() }
                    )
                } else if (currentSection == null) {
                    IconButton(onClick = onNavigateToNews) {
                        BadgedBox(
                            badge = {
                                if (unreadNewsCount > 0) {
                                    Badge {
                                        Text(if (unreadNewsCount > 99) "99+" else "$unreadNewsCount")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "소식",
                                tint = if (unreadNewsCount > 0) MaterialTheme.colorScheme.primary
                                else LocalContentColor.current
                            )
                        }
                    }
                }
            }
        )
        if (currentSection == "goods") {
            ActiveFilterChips(
                filters = listOfNotNull(
                    selectedCharaFilter?.let { ActiveFilter(it) { viewModel.setCharaFilter(null) } },
                    selectedCategoryFilter?.let { ActiveFilter(it) { viewModel.setCategoryFilter(null) } }
                ),
                onClearAll = { viewModel.setCharaFilter(null); viewModel.setCategoryFilter(null) }
            )
        }

        if (currentSection == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(AppStyles.paddingLarge),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                CollectionSummaryCard(
                    officialCount = officialGottenGoods.size,
                    fanCount = fanGottenGoods.size,
                    onClick = { viewModel.setSection("goods"); viewModel.loadGottenGoods() }
                )

                SettingsGroup(title = "굿즈") {
                    val pendingTotal = pendingOfficialGoods.size + pendingFanGoods.size
                    SettingsRow(
                        icon = Icons.Default.ShoppingCart,
                        title = "구매예정 굿즈",
                        subtitle = if (pendingTotal > 0) "구매일·구입처를 정리해 두세요"
                        else "구매예정으로 표시한 굿즈가 없습니다",
                        trailing = if (pendingTotal > 0) {
                            { StatusPill(text = "${pendingTotal}개", color = AppStyles.colorPending) }
                        } else null,
                        onClick = {
                            viewModel.setSection("pending")
                            viewModel.loadGottenGoods()
                        }
                    )
                }

                SettingsGroup(title = "유저 데이터") {
                    SettingsRow(
                        icon = Icons.Default.FileDownload,
                        title = "데이터 추출",
                        subtitle = "보유 현황·2차창작·선호 캐릭터를 백업 파일로 저장",
                        showChevron = false,
                        onClick = { viewModel.exportData(context) }
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.FileUpload,
                        title = "데이터 입력",
                        subtitle = "백업 파일(zip)에서 불러오기",
                        showChevron = false,
                        onClick = { fileLauncher.launch(arrayOf("application/zip")) }
                    )
                }

                SettingsGroup(title = "업데이트") {
                    SettingsRow(
                        icon = Icons.Default.Refresh,
                        title = "수동 업데이트",
                        subtitle = if (isLimitBypassed) {
                            "횟수 제한 해제 중 (${formatRemainTime(bypassRemainMillis)})"
                        } else {
                            "오늘 ${(MANUAL_UPDATE_LIMIT - manualUsedToday).coerceAtLeast(0)}/${MANUAL_UPDATE_LIMIT}회 남음"
                        },
                        subtitleColor = if (isLimitBypassed) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        showChevron = false,
                        onClick = onManualUpdate
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.History,
                        title = "업데이트 이력",
                        onClick = onNavigateToPatchNotes
                    )
                    SettingsDivider()
                    // 이 줄을 연속으로 누르면 횟수 제한이 풀리므로 눌림 효과를 보여주지 않는다.
                    SettingsRow(
                        icon = Icons.Default.Schedule,
                        title = "마지막 업데이트",
                        subtitle = lastUpdateTime ?: "업데이트 이력 없음",
                        showIndication = false,
                        onClick = onBypassTap,
                        trailing = if (lastUpdateTime != null) {
                            {
                                StatusPill(
                                    text = if (lastUpdateTotal > 0) "${lastUpdateTotal}개 변경" else "최신 상태",
                                    color = if (lastUpdateTotal > 0) MaterialTheme.colorScheme.primary
                                    else AppStyles.colorGotten
                                )
                            }
                        } else null,
                        showChevron = false
                    )
                }

                SettingsGroup(title = "앱 정보") {
                    val latest = latestVersion
                    val hasNewVersion = latest != null && latest != BuildConfig.VERSION_NAME
                    SettingsRow(
                        icon = Icons.Default.Info,
                        title = "현재 버전 v${BuildConfig.VERSION_NAME}",
                        subtitle = when {
                            latest == null -> "최신 버전 확인 중..."
                            hasNewVersion -> "새 버전 v$latest 이 나왔습니다"
                            else -> "최신 버전입니다"
                        },
                        subtitleColor = if (hasNewVersion) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        trailing = if (hasNewVersion) {
                            { StatusPill(text = "업데이트", color = MaterialTheme.colorScheme.error) }
                        } else null,
                        onClick = {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://github.com/rhecpev/WowaGoodsProject/releases/latest")
                            )
                            context.startActivity(intent)
                        }
                    )
                }

                Column {
                    // 제목 줄에 지금 쓰는 테마 이름을 적어 두고, 아래는 색 동그라미만 둔다.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "화면 테마",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = AppTheme.fromId(selectedThemeMode).label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        // 테마 수가 늘어도 한 줄에 들어가도록 폭을 똑같이 나눠 갖는다.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectableGroup()
                                .padding(horizontal = AppStyles.paddingMedium, vertical = 10.dp)
                        ) {
                            AppTheme.entries.forEach { theme ->
                                ThemeSwatchButton(
                                    theme = theme,
                                    selected = selectedThemeMode == theme.id,
                                    onSelect = {
                                        selectedThemeMode = theme.id
                                        onThemeChange(theme.id)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        } else when (currentSection) {
            "pending" -> {
                PendingGoodsSection(
                    officialGoods = pendingOfficialGoods,
                    fanGoods = pendingFanGoods,
                    selectedOfficialIds = selectedPendingOfficialIds,
                    selectedFanIds = selectedPendingFanIds,
                    onToggleOfficial = { id ->
                        selectedPendingOfficialIds =
                            if (id in selectedPendingOfficialIds) selectedPendingOfficialIds - id
                            else selectedPendingOfficialIds + id
                    },
                    onToggleFan = { id ->
                        selectedPendingFanIds =
                            if (id in selectedPendingFanIds) selectedPendingFanIds - id
                            else selectedPendingFanIds + id
                    },
                    onSelectAll = {
                        selectedPendingOfficialIds = pendingOfficialGoods.map { it.goodsId }.toSet()
                        selectedPendingFanIds = pendingFanGoods.map { it.fanGoodsId }.toSet()
                    },
                    onClearSelection = {
                        selectedPendingOfficialIds = emptySet()
                        selectedPendingFanIds = emptySet()
                    },
                    onApply = { purchaseDate, purchaseStore ->
                        viewModel.applyPurchaseInfo(
                            officialIds = selectedPendingOfficialIds,
                            fanIds = selectedPendingFanIds,
                            purchaseDate = purchaseDate,
                            purchaseStore = purchaseStore
                        )
                        selectedPendingOfficialIds = emptySet()
                        selectedPendingFanIds = emptySet()
                        Toast.makeText(context, "구매 정보를 적용했습니다", Toast.LENGTH_SHORT).show()
                    }
                )
            }

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
}


/** 맨 위의 보유 굿즈 요약 카드. 누르면 보유 굿즈 목록으로 들어간다. */
@Composable
private fun CollectionSummaryCard(
    officialCount: Int,
    fanCount: Int,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Box {
            // 배경 장식용 큰 아이콘
            Icon(
                imageVector = Icons.Default.Inventory2,
                contentDescription = null,
                tint = LocalContentColor.current.copy(alpha = 0.12f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 24.dp)
                    .size(128.dp)
            )
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "보유 굿즈",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(text = "목록 보기", style = MaterialTheme.typography.labelLarge)
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SummaryStat(count = officialCount, label = "공식")
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .width(1.dp)
                            .height(36.dp)
                            .background(LocalContentColor.current.copy(alpha = 0.3f))
                    )
                    SummaryStat(count = fanCount, label = "2차창작")
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(count: Int, label: String) {
    Column {
        Text(
            text = "$count",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = AppStyles.textCardSmall,
            color = LocalContentColor.current.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

/** 제목 + 둥근 카드 안에 설정 줄들을 묶는다. */
@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        SectionLabel(title)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(content = content)
        }
    }
}

/** 아이콘 + 제목/부제 + 오른쪽 표시(배지나 화살표)로 이루어진 설정 한 줄 */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    showIndication: Boolean = true,
    showChevron: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    val clickModifier = if (showIndication) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickModifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = AppStyles.textCardSmall,
                    color = subtitleColor,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(AppStyles.paddingMedium))
        when {
            trailing != null -> trailing()
            showChevron -> Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 아이콘 칸 너비만큼 들여 쓴 줄 구분선 */
@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 68.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Text(
        text = text,
        style = AppStyles.textCardSmall.copy(fontWeight = FontWeight.Bold),
        color = color,
        maxLines = 1,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/**
 * 눌러서 테마를 바꾸는 색 동그라미.
 * 바깥은 테마 배경색, 안쪽은 메인 색상이고, 고른 것만 테두리가 굵어지고 체크가 들어간다.
 * 시스템 테마는 배경을 밝게/어둡게 반반으로 칠해 자동이라는 걸 보여준다.
 */
@Composable
private fun ThemeSwatchButton(
    theme: AppTheme,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ringColor =
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val ringWidth = if (selected) 2.5.dp else 1.dp
    Box(
        modifier = modifier
            // 동그라미가 작아 손가락이 닿기 어려우므로 나눠 가진 칸 전체를 누를 수 있게 한다.
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect
            )
            .semantics { contentDescription = theme.label },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(26.dp)) {
            val alt = theme.swatchBackgroundAlt
            if (alt == null) {
                drawCircle(color = theme.swatchBackground)
            } else {
                drawArc(color = theme.swatchBackground, startAngle = 90f, sweepAngle = 180f, useCenter = true)
                drawArc(color = alt, startAngle = 270f, sweepAngle = 180f, useCenter = true)
            }
            drawCircle(color = theme.swatch, radius = size.minDimension / 2f * 0.62f)
            val stroke = ringWidth.toPx()
            drawCircle(
                color = ringColor,
                radius = size.minDimension / 2f - stroke / 2f,
                style = Stroke(width = stroke)
            )
        }
    }
}

/** 마지막 업데이트 항목을 연속으로 눌러 수동 업데이트 횟수 제한을 푸는 조건 */
private const val BYPASS_TAP_COUNT = 10
private const val BYPASS_TAP_INTERVAL_MS = 2000L
private const val BYPASS_DURATION_MS = 10 * 60 * 1000L
private const val MANUAL_LIMIT_BYPASS_KEY = "manual_limit_bypass_until"

private const val MANUAL_UPDATE_LIMIT = 3
private const val MANUAL_UPDATE_WORK_NAME = "manual_data_update"

private fun todayKey(): String =
    java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date())

/** 오늘 쓴 수동 업데이트 횟수. 날짜가 바뀌었으면 0 */
private fun readManualUpdateCount(prefs: SharedPreferences): Int =
    if (prefs.getString("manual_update_date", "") == todayKey()) prefs.getInt("manual_update_count", 0) else 0

private fun formatRemainTime(millis: Long): String {
    val totalSeconds = millis / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
