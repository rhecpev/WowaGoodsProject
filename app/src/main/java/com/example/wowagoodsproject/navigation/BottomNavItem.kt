package com.example.wowagoodsproject.navigation

sealed class BottomNavItem(
    val route: String,
    val label: String
) {
    object Series : BottomNavItem("series", "공식")
    object Character : BottomNavItem("character", "캐릭터별")
    object FanArt : BottomNavItem("fanart", "2차창작")
    object MyPage : BottomNavItem("mypage", "마이페이지")
}