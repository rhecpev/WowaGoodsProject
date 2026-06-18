package com.example.wowagoodsproject.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AppStyles {
    // 색상 - 보유/미보유만 고정색
    val colorGotten = Color(0xFF4CAF50)
    val colorNotGotten = Color(0xFFF44336)
    val colorPartialGotten = Color(0xFFFF9800)
    val colorPending = Color(0xFF2196F3) // 파란색

    // 텍스트 스타일
    val textCardTitle = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold
    )
    val textCardSubtitle = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal
    )
    val textCardSmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal
    )
    val textPrice = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium
    )
    val textGotten = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = colorGotten
    )
    val textNotGotten = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = colorNotGotten
    )
    val textPending = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = colorPending
    )

    // 사이즈
    val cardImageHeightList: Dp = 100.dp
    val cardImageHeightGrid: Dp = 150.dp
    val cardImageWidth: Dp = 100.dp
    val paddingSmall: Dp = 4.dp
    val paddingMedium: Dp = 8.dp
    val paddingLarge: Dp = 16.dp
}