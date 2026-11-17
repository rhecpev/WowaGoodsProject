package com.example.wowagoodsproject.component

import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.wowagoodsproject.ui.theme.AppStyles

/**
 * 보유 상태를 알약 모양 배지로 보여준다.
 * filled = true 는 이미지 위에 겹칠 때 쓴다 (진한 배경 + 흰 글자).
 * quantity 가 2 이상이면 보유/구매예정 뒤에 "×2" 처럼 수량을 붙인다.
 */
@Composable
fun GottenStatusBadge(
    status: GottenStatus,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    quantity: Int = 1
) {
    val text = when (status) {
        GottenStatus.GOTTEN -> "보유"
        GottenStatus.NOT_GOTTEN -> "미보유"
        GottenStatus.PARTIAL -> "일부보유"
        GottenStatus.PENDING -> "구매예정"
    }
    val color = status.color()
    val showQuantity = quantity > 1 && (status == GottenStatus.GOTTEN || status == GottenStatus.PENDING)
    val label = if (showQuantity) "$text ×$quantity" else text
    val style = AppStyles.textCardSmall.copy(fontWeight = FontWeight.Bold)
    if (filled) {
        // 사진 위: 글자만 두면 안 읽히므로 각진 상태 색 띠에 흰 글자.
        Text(
            text = label,
            style = style,
            color = Color.White,
            maxLines = 1,
            modifier = modifier
                .background(color.copy(alpha = 0.9f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    } else {
        // 바탕 위: 박스 없이 왼쪽 상태 색 세로 막대 + 상태 색 글자.
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 12.dp)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(text = label, style = style, color = color, maxLines = 1)
        }
    }
}

fun GottenStatus.color(): Color = when (this) {
    GottenStatus.GOTTEN -> AppStyles.colorGotten
    GottenStatus.NOT_GOTTEN -> AppStyles.colorNotGotten
    GottenStatus.PARTIAL -> AppStyles.colorPartialGotten
    GottenStatus.PENDING -> AppStyles.colorPending
}

fun GoodsStatus.asGottenStatus(): GottenStatus = when (this) {
    GoodsStatus.GOTTEN -> GottenStatus.GOTTEN
    GoodsStatus.PENDING -> GottenStatus.PENDING
    GoodsStatus.NOT_GOTTEN -> GottenStatus.NOT_GOTTEN
}
