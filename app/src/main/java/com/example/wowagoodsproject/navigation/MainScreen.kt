package com.example.wowagoodsproject.navigation

import com.example.wowagoodsproject.OpenTarget
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import com.example.wowagoodsproject.App
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.example.wowagoodsproject.component.FilterType
import com.example.wowagoodsproject.component.FilterViewModel
import com.example.wowagoodsproject.component.GoodsDetailViewModel
import com.example.wowagoodsproject.component.ListModeViewModel
import com.example.wowagoodsproject.screen.character.CharacterViewModel
import com.example.wowagoodsproject.screen.fan.FanArtViewModel
import com.example.wowagoodsproject.screen.mypage.MyPageViewModel
import com.example.wowagoodsproject.screen.series.SeriesViewModel
import com.example.wowagoodsproject.screen.character.CharacterScreen
import com.example.wowagoodsproject.screen.fan.FanArtScreen
import com.example.wowagoodsproject.screen.fan.FanAddScreen
import com.example.wowagoodsproject.screen.mypage.MyPageScreen
import com.example.wowagoodsproject.screen.mypage.PatchNotesScreen
import com.example.wowagoodsproject.screen.mypage.ChangedGoodsScreen
import com.example.wowagoodsproject.screen.mypage.ChangedGoodsViewModel
import com.example.wowagoodsproject.component.ProcessFilter
import com.example.wowagoodsproject.screen.news.NewsScreen
import com.example.wowagoodsproject.screen.news.NewsViewModel
import androidx.compose.foundation.lazy.LazyListState
import com.example.wowagoodsproject.screen.search.SearchScreen
import com.example.wowagoodsproject.screen.search.SearchViewModel
import com.example.wowagoodsproject.screen.series.SeriesScreen
import com.example.wowagoodsproject.screen.series.GoodsFocus
import com.example.wowagoodsproject.screen.betray.BetrayRoot

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun MainScreen(
    onThemeChange: (Int) -> Unit = {},
    openTarget: OpenTarget? = null,
    onOpenTargetHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    // 데이터 입력·복구가 끝나면 어느 화면에서든 결과 창을 띄운다.
    com.example.wowagoodsproject.screen.mypage.ImportResultHost()
    // 비공식 굿즈 상세의 '이 굿즈 수정' → 수정 화면
    val fanEditId by com.example.wowagoodsproject.screen.fan.FanEditRequest.goodsId.collectAsState()
    LaunchedEffect(fanEditId) {
        val id = fanEditId ?: return@LaunchedEffect
        com.example.wowagoodsproject.screen.fan.FanEditRequest.goodsId.value = null
        navController.navigate("fan_edit/$id")
    }
    val items = listOf(
        BottomNavItem.Series,
        BottomNavItem.FanArt,
        BottomNavItem.Search,
        BottomNavItem.Character,
        BottomNavItem.MyPage
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val context = LocalContext.current
    val windowSizeClass = calculateWindowSizeClass(context as androidx.activity.ComponentActivity)
    val widthSizeClass = windowSizeClass.widthSizeClass

    val seriesViewModel: SeriesViewModel = viewModel()
    val characterViewModel: CharacterViewModel = viewModel()
    val fanArtViewModel: FanArtViewModel = viewModel()
    val myPageViewModel: MyPageViewModel = viewModel()
    val filterViewModel: FilterViewModel = viewModel(key = "seriesFilter")
    val detailViewModel: GoodsDetailViewModel = viewModel(key = "seriesDetail")
    val listModeViewModel: ListModeViewModel = viewModel(key = "seriesListMode") { ListModeViewModel("series") }
    val seriesSelectedSeries by seriesViewModel.selectedSeries.collectAsState()
    val myPageCurrentSection by myPageViewModel.currentSection.collectAsState()
    val unreadNewsCount by myPageViewModel.unreadNewsCount.collectAsState()
    // 하단(가로 화면은 왼쪽) 메뉴를 숨기는 화면들. 캐릭터의 굿즈 화면에서는 공식 시리즈 화면처럼 메뉴를 남겨 둔다.
    val isInSubScreen = myPageCurrentSection != null ||
            currentRoute?.startsWith("fan_add") == true ||
            currentRoute?.startsWith("fan_edit") == true ||
            currentRoute == "patch_notes" ||
            currentRoute == "release_notes" ||
            currentRoute == "news" ||
            currentRoute == "betray"
    val navBackground = MaterialTheme.colorScheme.surface
    val navSelected = MaterialTheme.colorScheme.primary
    val navUnselected = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    val navIndicator = MaterialTheme.colorScheme.surfaceVariant
    val characterFilterViewModel: FilterViewModel = viewModel(key = "characterFilter")
    val characterListModeViewModel: ListModeViewModel = viewModel(key = "characterListMode") { ListModeViewModel("character") }
    val characterDetailViewModel: GoodsDetailViewModel = viewModel(key = "characterDetail")
    val fanFilterViewModel: FilterViewModel = viewModel(key = "fanFilter")
    val fanListModeViewModel: ListModeViewModel = viewModel(key = "fanListMode") { ListModeViewModel("fan") }
    val fanDetailViewModel: GoodsDetailViewModel = viewModel(key = "fanDetail")
    val searchViewModel: SearchViewModel = viewModel()
    // 소식에서 바로가기로 나갔다가 돌아올 때 필터와 스크롤 위치를 이어 가도록 화면 밖에 둔다.
    val newsViewModel: NewsViewModel = viewModel(key = "news")
    var newsListState by remember { mutableStateOf(LazyListState()) }
    val changedGoodsViewModel: ChangedGoodsViewModel = viewModel(key = "changedGoods")
    var changedGoodsListState by remember { mutableStateOf(LazyListState()) }
    val searchDetailViewModel: GoodsDetailViewModel = viewModel(key = "searchDetail")
    val myPageListModeViewModel: ListModeViewModel = viewModel(key = "myPageListMode") { ListModeViewModel("mypage") }
    val pendingListModeViewModel: ListModeViewModel = viewModel(key = "pendingListMode") { ListModeViewModel("pending") }
    val searchListModeViewModel: ListModeViewModel = viewModel(key = "searchListMode") { ListModeViewModel("search") }
    fun resetTab(route: String) {
        when (route) {
            // 굿즈 화면에서 걸어 둔 필터가 다음에 여는 시리즈·캐릭터에 남지 않게 함께 푼다.
            BottomNavItem.Series.route -> {
                seriesViewModel.clearSelectedSeries()
                seriesViewModel.setCharaFilter(null)
                filterViewModel.setFilter(FilterType.ALL)
                filterViewModel.clearGoodsFilter()
            }
            BottomNavItem.Character.route -> {
                characterViewModel.clearSelectedChara()
                characterViewModel.setSelectedTab(0)
                characterFilterViewModel.setFilter(FilterType.ALL)
                characterFilterViewModel.clearGoodsFilter()
            }
            BottomNavItem.FanArt.route -> {
                fanArtViewModel.setOpenSeries(null)
                fanFilterViewModel.clearGoodsFilter()
            }
            BottomNavItem.Search.route -> searchViewModel.clearFilters()
            BottomNavItem.MyPage.route -> {
                myPageViewModel.setSection(null)
                myPageViewModel.setCharaFilter(null)
            }
        }
    }

    fun navigateToSeries(seriesName: String) {
        characterViewModel.clearSelectedChara()
        myPageViewModel.setSection(null)
        // 공식 탭에서 예전에 걸어 둔 굿즈 필터가 새로 여는 시리즈에 남지 않게 푼다.
        filterViewModel.setFilter(FilterType.ALL)
        filterViewModel.clearGoodsFilter()
        navController.navigate(BottomNavItem.Series.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
        val series = seriesViewModel.seriesList.value.find { it.seriesNm == seriesName }
        series?.let { seriesViewModel.selectSeries(it) }
    }

    fun navigateToTab(route: String) {
        characterViewModel.clearSelectedChara()
        myPageViewModel.setSection(null)
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    /** 비공식 탭의 [seriesName] 시리즈를 연다(다른 탭에서 비공식 굿즈의 시리즈 이름을 눌렀을 때). */
    fun navigateToFanSeries(seriesName: String) {
        fanArtViewModel.setOpenSeries(seriesName.trim().ifEmpty { com.example.wowagoodsproject.screen.fan.NO_FAN_SERIES })
        fanFilterViewModel.clearGoodsFilter()
        navigateToTab(BottomNavItem.FanArt.route)
    }

    // 소식·변경된 굿즈에서 시리즈로 이동했으면, 그 시리즈를 뒤로가기로 닫을 때 그 화면으로 돌아간다.
    // 탭 이동은 시작 화면까지 비우므로 그 화면이 뒤 스택에 남지 않아 따로 기억해 둔다.
    var returnRoute by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentRoute, seriesSelectedSeries) {
        // 다른 탭으로 가거나 시리즈를 다른 방법으로 닫으면 더는 돌아가지 않는다.
        if (currentRoute != BottomNavItem.Series.route || seriesSelectedSeries == null) returnRoute = null
    }

    /** 소식 화면을 새로 연다. 지난번 필터·스크롤 위치는 지운다. */
    fun openNews() {
        newsViewModel.clearFilter()
        newsListState = LazyListState()
        navController.navigate("news") { launchSingleTop = true }
    }

    fun backToReturnRoute() {
        val route = returnRoute ?: return
        returnRoute = null
        // 소식·변경된 굿즈는 이동할 때 닫았으므로 마이페이지 탭으로 돌아간 뒤 다시 연다.
        navigateToTab(BottomNavItem.MyPage.route)
        if (navController.currentDestination?.route != route) {
            navController.navigate(route) { launchSingleTop = true }
        }
    }

    /** [route] 화면(소식·변경된 굿즈)에서 굿즈가 있는 시리즈로 옮겨 그 굿즈를 보여 준다. */
    fun openGoodsFrom(route: String, focus: GoodsFocus) {
        if (seriesViewModel.seriesList.value.none { it.seriesNm == focus.series }) {
            // 원본 데이터에서 시리즈가 없어진 경우. 기록은 그대로 남는다.
            Toast.makeText(context, "시리즈를 찾을 수 없습니다", Toast.LENGTH_SHORT).show()
            return
        }
        // 화면을 띄운 채로 탭을 옮기면 마이페이지 탭 상태에 그 화면까지 저장돼,
        // 나중에 마이페이지 탭을 누를 때 그 화면이 다시 열린다. 먼저 닫고 옮긴다.
        navController.popBackStack(route, inclusive = true)
        navigateToSeries(focus.series)
        seriesViewModel.focusGoods(focus)
        returnRoute = route
    }

    // 알림을 눌러 들어오면 해당 위치로 이동한다.
    // 공식 굿즈는 공식 탭의 그 시리즈를 열고 상세를 띄우고, 2차창작은 2차창작 탭에서 상세를 띄운다.
    // 신규 굿즈 알림은 소식 화면을 연다.
    LaunchedEffect(openTarget) {
        val target = openTarget ?: return@LaunchedEffect
        if (target is OpenTarget.News) {
            if (navController.currentDestination?.route != "news") openNews()
        } else if (target is OpenTarget.BetrayGoods) {
            // 배신 굿즈: 배신 굿즈 화면을 열고, 그 장르 → 시리즈 → 상세 순으로 화면이 받아 연다.
            val goods = App.betrayDatabase.betrayDao().goodsById(target.id)
            if (goods != null) {
                com.example.wowagoodsproject.screen.betray.BetrayOpenRequest.goods.value = goods
                if (navController.currentDestination?.route != "betray") navController.navigate("betray") { launchSingleTop = true }
            }
        } else if (target is OpenTarget.Goods && target.isFan) {
            val goods = App.fanDatabase.fanGoodsDao().getById(target.id)
            // 상세를 닫아도 그 굿즈가 든 시리즈 목록이 보이게 시리즈를 열어 둔다.
            goods?.let {
                fanArtViewModel.setOpenSeries(it.fanGoodsSeries.trim().ifEmpty { com.example.wowagoodsproject.screen.fan.NO_FAN_SERIES })
                // 예전에 걸어 둔 필터 때문에 그 굿즈가 목록에서 안 보이지 않게 푼다.
                fanFilterViewModel.clearGoodsFilter()
            }
            navigateToTab(BottomNavItem.FanArt.route)
            goods?.let { fanDetailViewModel.selectGoods(it) }
        } else if (target is OpenTarget.Goods) {
            val goods = App.database.goodsDao().getById(target.id)
            if (goods != null) {
                // 앱을 막 켠 경우 시리즈 목록이 아직 비어 있을 수 있어 잠깐 기다린다.
                withTimeoutOrNull(5_000) { seriesViewModel.seriesList.first { it.isNotEmpty() } }
                navigateToSeries(goods.goodsSeries)
                detailViewModel.selectGoods(goods)
            } else {
                navigateToTab(BottomNavItem.Series.route)
            }
        }
        onOpenTargetHandled()
    }

    // 화면들은 한 번만 만들어 두고 세로(하단 메뉴)·가로(왼쪽 메뉴) 배치 사이를 옮겨 다닌다.
    // 그래서 화면을 돌려도 보던 화면의 스크롤 위치, 열어 둔 창, 다중 선택 같은 상태가 그대로 남는다.
    val currentOnThemeChange by rememberUpdatedState(onThemeChange)
    val navHost = remember(navController) {
        movableContentOf { modifier: Modifier, widthSizeClass: WindowWidthSizeClass ->
            NavHost(
                navController = navController,
                startDestination = BottomNavItem.Series.route,
                modifier = modifier
            ) {
                composable(BottomNavItem.Series.route) {
                    SeriesScreen(
                        widthSizeClass = widthSizeClass,
                        viewModel = seriesViewModel,
                        filterViewModel = filterViewModel,
                        detailViewModel = detailViewModel,
                        listModeViewModel = listModeViewModel,
                        onSeriesClosed = { backToReturnRoute() }
                    )
                }
                composable(BottomNavItem.Character.route) {
                    CharacterScreen(
                        widthSizeClass = widthSizeClass,
                        viewModel = characterViewModel,
                        filterViewModel = characterFilterViewModel,
                        listModeViewModel = characterListModeViewModel,
                        detailViewModel = characterDetailViewModel,
                        onNavigateToSeries = { navigateToSeries(it) },
                        onNavigateToFanSeries = { navigateToFanSeries(it) }
                    )
                }
                composable(BottomNavItem.FanArt.route) {
                    FanArtScreen(
                        onNavigateToAdd = { series -> navController.navigate("fan_add?series=${android.net.Uri.encode(series)}") },
                        widthSizeClass = widthSizeClass,
                        viewModel = fanArtViewModel,
                        filterViewModel = fanFilterViewModel,
                        listModeViewModel = fanListModeViewModel,
                        detailViewModel = fanDetailViewModel,
                        onNavigateToSeries = { navigateToSeries(it) }
                    )
                }
                composable(BottomNavItem.Search.route) {
                    SearchScreen(
                        widthSizeClass = widthSizeClass,
                        viewModel = searchViewModel,
                        detailViewModel = searchDetailViewModel,
                        listModeViewModel = searchListModeViewModel,
                        onNavigateToSeries = { navigateToSeries(it) },
                        onNavigateToFanSeries = { navigateToFanSeries(it) }
                    )
                }
                composable(BottomNavItem.MyPage.route) {
                    MyPageScreen(
                        widthSizeClass = widthSizeClass,
                        viewModel = myPageViewModel,
                        listModeViewModel = myPageListModeViewModel,
                        pendingListModeViewModel = pendingListModeViewModel,
                        onThemeChange = { currentOnThemeChange(it) },
                        onNavigateToPatchNotes = { navController.navigate("patch_notes") },
                        onNavigateToReleaseNotes = { navController.navigate("release_notes") },
                        onNavigateToChangedGoods = {
                            // 새로 열 때마다 미처리만 보이는 처음 상태로 연다.
                            changedGoodsViewModel.setProcessFilter(ProcessFilter.TODO)
                            changedGoodsListState = LazyListState()
                            navController.navigate("changed_goods") { launchSingleTop = true }
                        },
                        onNavigateToNews = { openNews() },
                        onNavigateToSeries = { navigateToSeries(it) },
                        onNavigateToFanSeries = { navigateToFanSeries(it) },
                        onNavigateToBetray = { navController.navigate("betray") { launchSingleTop = true } }
                    )
                }
                // 배신 굿즈: 자체 하단 메뉴를 가진 앱 속 앱. 명조 쪽 하단 메뉴는 숨긴다.
                composable("betray") {
                    BetrayRoot(widthSizeClass = widthSizeClass, onExit = { navController.popBackStack() })
                }
                composable(
                    "fan_edit/{id}",
                    arguments = listOf(androidx.navigation.navArgument("id") { type = androidx.navigation.NavType.IntType })
                ) { entry ->
                    FanAddScreen(
                        onNavigateBack = { navController.popBackStack() },
                        editGoodsId = entry.arguments?.getInt("id")
                    )
                }
                composable(
                    "fan_add?series={series}",
                    arguments = listOf(androidx.navigation.navArgument("series") { defaultValue = "" })
                ) { entry ->
                    FanAddScreen(
                        onNavigateBack = { navController.popBackStack() },
                        initialSeries = entry.arguments?.getString("series").orEmpty()
                    )
                }
                composable("release_notes") {
                    com.example.wowagoodsproject.screen.mypage.ReleaseNotesScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable("patch_notes") {
                    PatchNotesScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable("news") {
                    NewsScreen(
                        widthSizeClass = widthSizeClass,
                        viewModel = newsViewModel,
                        listState = newsListState,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToGoods = { news ->
                            openGoodsFrom(
                                "news",
                                GoodsFocus(news.newsSeries, news.newsChara, news.newsCategory, news.newsPrice)
                            )
                        }
                    )
                }
                composable("changed_goods") {
                    ChangedGoodsScreen(
                        viewModel = changedGoodsViewModel,
                        listState = changedGoodsListState,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToGoods = { goods ->
                            openGoodsFrom(
                                "changed_goods",
                                GoodsFocus(goods.goodsSeries, goods.goodsChara, goods.goodsCategory, goods.goodsPrice)
                            )
                        }
                    )
                }
            }
        }
    }

    // 가로 2번 모드(8인치 이상 태블릿): 굿즈 상세·필터·고르기 창을 열면 오른쪽 절반 칸이 밀고 들어와 거기에 연다.
    // 8인치 미만은 1번 모드: 창이 오른쪽에서 덮어서 나온다(AdaptiveSheet 가 알아서 한다).
    val fixedPane = isLandscape && com.example.wowagoodsproject.component.useFixedSidePane()
    val sidePane = com.example.wowagoodsproject.component.rememberSidePaneState()
    if (isLandscape) CompositionLocalProvider(
        // 세로 모드의 Scaffold 는 기본 글자/아이콘 색을 onBackground 로 깔아 준다.
        // 가로 모드는 Row 만 써서 이 값이 빠지므로, 색을 따로 안 준 글자가 세로와 달라지지 않게 같은 값을 준다.
        LocalContentColor provides MaterialTheme.colorScheme.onBackground,
        com.example.wowagoodsproject.component.LocalSidePane provides if (fixedPane) sidePane else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding()
        ) {
            if (!isInSubScreen) {
                NavigationRail(
                    containerColor = navBackground,
                    contentColor = navSelected,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    items.forEach { item ->
                        NavigationRailItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                // 2번 모드에서 상세 칸이 열려 있으면 탭을 옮기는 것과 동시에 칸을 바로 없앤다.
                                sidePane.closeAllNow()
                                if (currentRoute == item.route) {
                                    resetTab(item.route)
                                } else {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            label = { Text(item.label) },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        // 마이페이지 탭에만 안 읽은 소식 개수를 띄운다.
                                        if (item.route == BottomNavItem.MyPage.route && unreadNewsCount > 0) {
                                            Badge {
                                                Text(if (unreadNewsCount > 99) "99+" else "$unreadNewsCount")
                                            }
                                        }
                                    }
                                ) {
                                    TabIcon(selected = currentRoute == item.route, selectedIcon = item.selectedIcon, unselectedIcon = item.unselectedIcon, iconRes = item.iconRes)
                                }
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = navSelected,
                                selectedTextColor = navSelected,
                                unselectedIconColor = navUnselected,
                                unselectedTextColor = navUnselected,
                                indicatorColor = navIndicator
                            )
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            if (fixedPane) {
                // 아무것도 안 열었으면 왼쪽 화면이 전체 폭을 쓴다.
                // 굿즈 등을 누르면 오른쪽 칸이 옆에서 밀고 들어와 [왼쪽 화면 | 오른쪽 칸] 반반이 되고, 닫으면 밀려 나가며 접힌다(탭 이동은 바로 사라짐).
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    val leftClass = widthSizeClassOf(if (sidePane.isOpen) maxWidth / 2 else maxWidth)
                    Row(modifier = Modifier.fillMaxSize()) {
                        navHost(Modifier.weight(1f).fillMaxHeight(), leftClass)
                        val paneAnim = com.example.wowagoodsproject.component.SIDE_PANE_ANIM_MS
                        androidx.compose.animation.AnimatedVisibility(
                            visible = sidePane.isOpen,
                            enter = androidx.compose.animation.expandHorizontally(
                                androidx.compose.animation.core.tween(paneAnim), expandFrom = androidx.compose.ui.Alignment.Start
                            ) + androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(paneAnim)) { it / 2 },
                            // 닫을 때(같은 굿즈 다시 누르기·닫기·뒤로)는 나올 때 거꾸로 밀려 나가며 접히고, 탭을 옮길 때는 바로 사라진다
                            exit = if (sidePane.closeInstantly) androidx.compose.animation.ExitTransition.None else {
                                androidx.compose.animation.shrinkHorizontally(
                                    androidx.compose.animation.core.tween(paneAnim), shrinkTowards = androidx.compose.ui.Alignment.Start
                                ) + androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(paneAnim)) { it / 2 }
                            },
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            // 폭은 안쪽에서 정해야 펼쳐지는 애니메이션이 0 → 절반으로 자란다
                            Row(modifier = Modifier.fillMaxWidth(0.5f).fillMaxHeight()) {
                                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                com.example.wowagoodsproject.component.SidePaneHost(sidePane, Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    }
                }
            } else {
                navHost(Modifier, widthSizeClass)
            }
        }
    } else {
        Scaffold(
            bottomBar = {
                if (!isInSubScreen) {
                    NavigationBar(
                        containerColor = navBackground,
                        contentColor = navSelected
                    ) {
                        items.forEach { item ->
                            NavigationBarItem(
                                selected = currentRoute == item.route,
                                onClick = {
                                    if (currentRoute == item.route) {
                                        resetTab(item.route)
                                    } else {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                label = { Text(item.label) },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            // 마이페이지 탭에만 안 읽은 소식 개수를 띄운다.
                                            if (item.route == BottomNavItem.MyPage.route && unreadNewsCount > 0) {
                                                Badge {
                                                    Text(if (unreadNewsCount > 99) "99+" else "$unreadNewsCount")
                                                }
                                            }
                                        }
                                    ) {
                                        TabIcon(selected = currentRoute == item.route, selectedIcon = item.selectedIcon, unselectedIcon = item.unselectedIcon, iconRes = item.iconRes)
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = navSelected,
                                    selectedTextColor = navSelected,
                                    unselectedIconColor = navUnselected,
                                    unselectedTextColor = navUnselected,
                                    indicatorColor = navIndicator
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            navHost(Modifier.padding(innerPadding), widthSizeClass)
        }
    }
}

/** 폭(dp)으로 화면 크기 구분을 정한다(창 크기 구분과 같은 기준: 600 · 840) */
private fun widthSizeClassOf(width: androidx.compose.ui.unit.Dp): WindowWidthSizeClass = when {
    width < 600.dp -> WindowWidthSizeClass.Compact
    width < 840.dp -> WindowWidthSizeClass.Medium
    else -> WindowWidthSizeClass.Expanded
}
