package com.example.wowagoodsproject.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    /** 머티리얼 아이콘 대신 쓸 그림(있으면 이것을 쓴다) */
    val iconRes: Int? = null
) {
    object Series : BottomNavItem("series", "공식", Icons.Filled.CollectionsBookmark, Icons.Outlined.CollectionsBookmark)
    object Character : BottomNavItem("character", "캐릭터별", Icons.Filled.Groups, Icons.Outlined.Groups, iconRes = CHARA_ICON_RES)
    object FanArt : BottomNavItem("fanart", "비공식", Icons.Filled.Palette, Icons.Outlined.Palette)
    object Search : BottomNavItem("search", "굿즈 검색", Icons.Filled.Search, Icons.Outlined.Search)
    object MyPage : BottomNavItem("mypage", "마이페이지", Icons.Filled.AccountCircle, Icons.Outlined.AccountCircle)
}
