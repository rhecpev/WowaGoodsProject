package com.example.wowagoodsproject.navigation

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import com.example.wowagoodsproject.screen.character.CharacterViewModel
import com.example.wowagoodsproject.screen.fan.FanArtViewModel
import com.example.wowagoodsproject.screen.mypage.MyPageViewModel
import com.example.wowagoodsproject.screen.series.SeriesViewModel
import com.example.wowagoodsproject.screen.character.CharacterScreen
import com.example.wowagoodsproject.screen.fan.FanArtScreen
import com.example.wowagoodsproject.screen.fan.FanAddScreen
import com.example.wowagoodsproject.screen.mypage.MyPageScreen
import com.example.wowagoodsproject.screen.series.SeriesScreen

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun MainScreen(
    onThemeChange: (Int) -> Unit = {}
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

    val seriesSelectedSeries by seriesViewModel.selectedSeries.collectAsState()
    val characterSelectedChara by characterViewModel.selectedChara.collectAsState()
    val myPageCurrentSection by myPageViewModel.currentSection.collectAsState()
    val isInSubScreen = seriesSelectedSeries != null ||
            characterSelectedChara != null ||
            myPageCurrentSection != null ||
            currentRoute == "fan_add"
    val navBackground = MaterialTheme.colorScheme.surface
    val navSelected = MaterialTheme.colorScheme.primary
    val navUnselected = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    val navIndicator = MaterialTheme.colorScheme.surfaceVariant

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

    if (isLandscape) {
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
                                Icon(
                                    imageVector = if (currentRoute == item.route) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = null
                                )
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
                    SeriesScreen(widthSizeClass = widthSizeClass, viewModel = seriesViewModel)
                }
                composable(BottomNavItem.Character.route) {
                    CharacterScreen(widthSizeClass = widthSizeClass, viewModel = characterViewModel)
                }
                composable(BottomNavItem.FanArt.route) {
                    FanArtScreen(
                        onNavigateToAdd = { navController.navigate("fan_add") },
                        widthSizeClass = widthSizeClass,
                        viewModel = fanArtViewModel
                    )
                }
                composable(BottomNavItem.MyPage.route) {
                    MyPageScreen(
                        widthSizeClass = widthSizeClass,
                        viewModel = myPageViewModel,
                        onThemeChange = onThemeChange
                    )
                }
                composable("fan_add") {
                    FanAddScreen(onNavigateBack = { navController.popBackStack() })
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
                                    Icon(
                                        imageVector = if (currentRoute == item.route) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = null
                                    )
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
                    SeriesScreen(widthSizeClass = widthSizeClass, viewModel = seriesViewModel)
                }
                composable(BottomNavItem.Character.route) {
                    CharacterScreen(widthSizeClass = widthSizeClass, viewModel = characterViewModel)
                }
                composable(BottomNavItem.FanArt.route) {
                    FanArtScreen(
                        onNavigateToAdd = { navController.navigate("fan_add") },
                        widthSizeClass = widthSizeClass,
                        viewModel = fanArtViewModel
                    )
                }
                composable(BottomNavItem.MyPage.route) {
                    MyPageScreen(
                        widthSizeClass = widthSizeClass,
                        viewModel = myPageViewModel,
                        onThemeChange = onThemeChange
                    )
                }
                composable("fan_add") {
                    FanAddScreen(onNavigateBack = { navController.popBackStack() })
                }
            }
        }
    }
}