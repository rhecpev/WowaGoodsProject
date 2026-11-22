package com.example.wowagoodsproject.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * 하단 탭 아이콘. 명조·배신 굿즈 하단 탭이 같이 쓴다.
 * [iconRes] 가 있으면 그 그림(선만 있는 그림이라 선택/미선택 색이 그대로 입혀진다), 없으면 머티리얼 아이콘.
 */
@Composable
fun TabIcon(selected: Boolean, selectedIcon: ImageVector, unselectedIcon: ImageVector, @DrawableRes iconRes: Int? = null) {
    if (iconRes != null) {
        Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(26.dp))
    } else {
        Icon(if (selected) selectedIcon else unselectedIcon, contentDescription = null)
    }
}

/** 명조 캐릭터별 탭 · 캐릭터 입력 칸 아이콘(캐릭터 옆얼굴 선 그림) */
val CHARA_ICON_RES: Int = com.example.wowagoodsproject.R.drawable.ic_chara_role
