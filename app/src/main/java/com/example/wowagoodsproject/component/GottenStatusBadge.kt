package com.example.wowagoodsproject.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
 */
@Composable
fun GottenStatusBadge(
    status: GottenStatus,
    modifier: Modifier = Modifier,
    filled: Boolean = false
) {
    val (text, color) = when (status) {
        GottenStatus.GOTTEN -> "보유" to AppStyles.colorGotten
        GottenStatus.NOT_GOTTEN -> "미보유" to AppStyles.colorNotGotten
        GottenStatus.PARTIAL -> "일부보유" to AppStyles.colorPartialGotten
        GottenStatus.PENDING -> "구매예정" to AppStyles.colorPending
    }
    Text(
        text = text,
        style = AppStyles.textCardSmall.copy(fontWeight = FontWeight.Bold),
        color = if (filled) Color.White else color,
        maxLines = 1,
        modifier = modifier
            .background(
                color = if (filled) color.copy(alpha = 0.9f) else color.copy(alpha = 0.14f),
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

fun GoodsStatus.asGottenStatus(): GottenStatus = when (this) {
    GoodsStatus.GOTTEN -> GottenStatus.GOTTEN
    GoodsStatus.PENDING -> GottenStatus.PENDING
    GoodsStatus.NOT_GOTTEN -> GottenStatus.NOT_GOTTEN
}
