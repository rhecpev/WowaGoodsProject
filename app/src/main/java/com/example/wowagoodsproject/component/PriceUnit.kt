package com.example.wowagoodsproject.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/*
 * 가격 단위: 원 · 위안 · 엔. 가격은 지금처럼 "3000원", "59위안" 처럼 금액 뒤에 단위를 붙인 글자로 저장한다
 * (공식 굿즈 데이터·백업과 같은 형식). 입력 화면에서만 금액과 단위를 나눠 받는다.
 */

/** 고를 수 있는 단위(이 순서로 놓는다) */
val PRICE_UNITS = listOf("원", "위안", "엔")

/**
 * 저장된 가격을 (금액, 단위)로 나눈다. 끝이 [PRICE_UNITS] 중 하나가 아니면 단위는 null 이고 금액은 적힌 그대로다.
 * "59위안" → ("59", "위안"), "3,000 원" → ("3,000", "원"), "$5" → ("$5", null), "" → ("", null)
 */
fun splitPrice(price: String): Pair<String, String?> {
    val t = price.trim()
    // 긴 단위부터 본다("위안"이 "안"으로 끝나는 다른 단위와 헷갈리지 않게)
    val unit = PRICE_UNITS.sortedByDescending { it.length }.firstOrNull { t.endsWith(it) && t.length > it.length } ?: return t to null
    return t.removeSuffix(unit).trim() to unit
}

/** 금액과 단위를 합친다. 금액이 비어 있으면 빈 값(단위만 저장하지 않는다) */
fun joinPrice(amount: String, unit: String?): String {
    val a = amount.trim()
    if (a.isEmpty()) return ""
    return if (unit == null) a else a + unit
}

/**
 * 단위 고르기: 원 · 위안 · 엔. 고른 것은 강조색 굵은 글씨에 밑줄(앱의 밑줄 탭과 같은 모양).
 */
@Composable
fun PriceUnitPicker(selected: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        PRICE_UNITS.forEach { unit ->
            val on = unit == selected
            Column(
                modifier = Modifier
                    .clickable(role = Role.RadioButton) { onSelect(unit) }
                    .semantics { this.selected = on; contentDescription = "단위 $unit" }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    unit,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(2.dp)
                        .background(if (on) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                )
            }
        }
    }
}
