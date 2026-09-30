package com.example.wowagoodsproject.navigation

import com.example.wowagoodsproject.OpenTarget
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import com.example.wowagoodsproject.App
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
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
import com.example.wowagoodsproject.screen.news.NewsScreen
import com.example.wowagoodsproject.screen.series.SeriesScreen

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun MainScreen(
    onThemeChange: (Int) -> Unit = {},
    openTarget: OpenTarget? = null,
    onOpenTargetHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    val items = listOf(
        BottomNavItem.Series,
        BottomNavItem.Character,
        BottomNavItem.FanArt,
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
    val listModeViewModel: ListModeViewModel = viewModel(key = "seriesListMode")
    val seriesSelectedSeries by seriesViewModel.selectedSeries.collectAsState()
    val characterSelectedChara by characterViewModel.selectedChara.collectAsState()
    val myPageCurrentSection by myPageViewModel.currentSection.collectAsState()
    val unreadNewsCount by myPageViewModel.unreadNewsCount.collectAsState()
    val isInSubScreen = characterSelectedChara != null ||
            myPageCurrentSection != null ||
            currentRoute == "fan_add" ||
            currentRoute == "patch_notes" ||
            currentRoute == "news"
    val navBackground = MaterialTheme.colorScheme.surface
    val navSelected = MaterialTheme.colorScheme.primary
    val navUnselected = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    val navIndicator = MaterialTheme.colorScheme.surfaceVariant
    val characterFilterViewModel: FilterViewModel = viewModel(key = "characterFilter")
    val characterListModeViewModel: ListModeViewModel = viewModel(key = "characterListMode")
    val characterDetailViewModel: GoodsDetailViewModel = viewModel(key = "characterDetail")
    val fanFilterViewModel: FilterViewModel = viewModel(key = "fanFilter")
    val fanListModeViewModel: ListModeViewModel = viewModel(key = "fanListMode")
    val fanDetailViewModel: GoodsDetailViewModel = viewModel(key = "fanDetail")
    fun resetTab(route: String) {
        when (route) {
            BottomNavItem.Series.route -> {
                seriesViewModel.clearSelectedSeries()
                seriesViewModel.setCharaFilter(null)
            }
            BottomNavItem.Character.route -> {
                characterViewModel.clearSelectedChara()
                characterViewModel.setSelectedTab(0)
            }
            BottomNavItem.FanArt.route -> {}
            BottomNavItem.MyPage.route -> {
                myPageViewModel.setSection(null)
                myPageViewModel.setCharaFilter(null)
            }
        }
    }

    fun navigateToSeries(seriesName: String) {
        characterViewModel.clearSelectedChara()
        myPageViewModel.setSection(null)
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

    // 알림을 눌러 들어오면 해당 위치로 이동한다.
    // 공식 굿즈는 공식 탭의 그 시리즈를 열고 상세를 띄우고, 2차창작은 2차창작 탭에서 상세를 띄운다.
    // 신규 굿즈 알림은 소식 화면을 연다.
    LaunchedEffect(openTarget) {
        val target = openTarget ?: return@LaunchedEffect
        if (target is OpenTarget.News) {
            navController.navigate("news") { launchSingleTop = true }
        } else if (target is OpenTarget.Goods && target.isFan) {
            val goods = App.fanDatabase.fanGoodsDao().getById(target.id)
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

    if (isLandscape) CompositionLocalProvider(
        // 세로 모드의 Scaffold 는 기본 글자/아이콘 색을 onBackground 로 깔아 준다.
        // 가로 모드는 Row 만 써서 이 값이 빠지므로, 색을 따로 안 준 글자가 세로와 달라지지 않게 같은 값을 준다.
        LocalContentColor provides MaterialTheme.colorScheme.onBackground
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
                                    Icon(
                                        imageVector = if (currentRoute == item.route) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = null
                                    )
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
            NavHost(navController = navController, startDestination = BottomNavItem.Series.route) {
                composable(BottomNavItem.Series.route) {
                    SeriesScreen(
                        widthSizeClass = widthSizeClass,
                        viewModel = seriesViewModel,
                        filterViewModel = filterViewModel,
                        detailViewModel = detailViewModel,
                        listModeViewModel = listModeViewModel
                    )
                }
                composable(BottomNavItem.Character.route) {
                    CharacterScreen(
                        widthSizeClass = widthSizeClass,
                        viewModel = characterViewModel,
                        filterViewModel = characterFilterViewModel,
                        listModeViewModel = characterListModeViewModel,
                        detailViewModel = characterDetailViewModel,
                        onNavigateToSeries = { navigateToSeries(it) }
                    )
                }
                composable(BottomNavItem.FanArt.route) {
                    FanArtScreen(
                        onNavigateToAdd = { navController.navigate("fan_add") },
                        widthSizeClass = widthSizeClass,
                        viewModel = fanArtViewModel,
                        filterViewModel = fanFilterViewModel,
                        listModeViewModel = fanListModeViewModel,
                        detailViewModel = fanDetailViewModel,
                        onNavigateToSeries = { navigateToSeries(it) }
                    )
                }
                composable(BottomNavItem.MyPage.route) {
                    MyPageScreen(
                        widthSizeClass = widthSizeClass,
                        viewModel = myPageViewModel,
                        onThemeChange = onThemeChange,
                        onNavigateToPatchNotes = { navController.navigate("patch_notes") },
                        onNavigateToNews = { navController.navigate("news") },
                        onNavigateToSeries = { navigateToSeries(it) }
                    )
                }
                composable("fan_add") {
                    FanAddScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable("patch_notes") {
                    PatchNotesScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable("news") {
                    NewsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToSeries = { navigateToSeries(it) }
                    )
                }
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
                                        Icon(
                                            imageVector = if (currentRoute == item.route) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = null
                                        )
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
            NavHost(
                navController = navController,
                startDestination = BottomNavItem.Series.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(BottomNavItem.Series.route) {
                    SeriesScreen(
                        widthSizeClass = widthSizeClass,
                        viewModel = seriesViewModel,
                        filterViewModel = filterViewModel,
                        detailViewModel = detailViewModel,
                        listModeViewModel = listModeViewModel
                    )
                }
                composable(BottomNavItem.Character.route) {
                    CharacterScreen(
                        widthSizeClass = widthSizeClass,
                        viewModel = characterViewModel,
                        filterViewModel = characterFilterViewModel,
                        listModeViewModel = characterListModeViewModel,
                        detailViewModel = characterDetailViewModel,
                        onNavigateToSeries = { navigateToSeries(it) }
                    )
                }
                composable(BottomNavItem.FanArt.route) {
                    FanArtScreen(
                        onNavigateToAdd = { navController.navigate("fan_add") },
                        widthSizeClass = widthSizeClass,
                        viewModel = fanArtViewModel,
                        filterViewModel = fanFilterViewModel,
                        listModeViewModel = fanListModeViewModel,
                        detailViewModel = fanDetailViewModel,
                        onNavigateToSeries = { navigateToSeries(it) }
                    )
                }
                composable(BottomNavItem.MyPage.route) {
                    MyPageScreen(
                        widthSizeClass = widthSizeClass,
                        viewModel = myPageViewModel,
                        onThemeChange = onThemeChange,
                        onNavigateToPatchNotes = { navController.navigate("patch_notes") },
                        onNavigateToNews = { navController.navigate("news") },
                        onNavigateToSeries = { navigateToSeries(it) }
                    )
                }
                composable("fan_add") {
                    FanAddScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable("patch_notes") {
                    PatchNotesScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable("news") {
                    NewsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToSeries = { navigateToSeries(it) }
                    )
                }
            }
        }
    }
}