package com.example.wowagoodsproject.component

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.wowagoodsproject.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * 구입처 입력 도우미: 지금까지 적은 구입처를 입력 칸 아래 드롭다운으로 보여 주고, 누르면 채운다.
 * 구매 정보 입력 창(PurchaseInfoDialog)과 굿즈 등록 화면의 구입처 칸이 같이 쓴다.
 */

/** 오늘 날짜(폰 날짜) "yyyy-MM-dd". 구매일 기본값 */
fun todayDateString(): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.KOREA).format(java.util.Date())

/** 적힌 구입처들을 많이 쓴 순(같으면 이름순)으로, 겹치지 않게 */
internal fun rankStores(values: List<String>): List<String> =
    values.map { it.trim() }.filter { it.isNotEmpty() }
        .groupingBy { it }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key }

/** [query] 가 들어간 구입처(대소문자 무시). 비어 있으면 전부. 이미 똑같이 적었으면 그것은 뺀다. */
internal fun matchStores(stores: List<String>, query: String, limit: Int = 50): List<String> {
    val q = query.trim()
    return stores.filter { q.isEmpty() || (it.contains(q, ignoreCase = true) && !it.equals(q, ignoreCase = true)) }.take(limit)
}

/** 한 번이라도 본 구입처를 앱에 기억해 두는 곳. 굿즈를 지우거나 구입처를 고쳐도 추천에 남는다. */
private const val STORE_HISTORY_KEY = "purchase_store_history"

/**
 * 추천 순서: 지금 굿즈에 적힌 구입처(많이 쓴 순) → 기억만 남은 구입처(이름순).
 * [current] 는 지금 굿즈들의 구입처 칸 값, [remembered] 는 기억해 둔 구입처.
 */
internal fun mergeStores(current: List<String>, remembered: Set<String>): List<String> {
    val ranked = rankStores(current)
    return ranked + remembered.map { it.trim() }.filter { it.isNotEmpty() && it !in ranked }.distinct().sorted()
}

/**
 * 지금까지 적은 구입처(명조 공식·2차창작·배신 굿즈 전부 + 기억해 둔 것). 화면이 열릴 때 한 번 읽는다.
 * 읽으면서 지금 굿즈에 있는 구입처를 기억에 더해 둔다(나중에 그 굿즈를 지우거나 보유로 바꿔도 남게).
 */
@Composable
fun rememberPurchaseStores(): List<String> {
    val stores by produceState(emptyList<String>()) {
        value = runCatching {
            withContext(Dispatchers.IO) {
                // 굿즈 전체를 읽지 않고 구입처 칸만 읽는다(공식 굿즈는 수천 줄).
                val current = App.database.goodsDao().purchaseStores() +
                    App.fanDatabase.fanGoodsDao().purchaseStores() +
                    App.betrayDatabase.betrayDao().purchaseStores()
                val prefs = App.appContext.getSharedPreferences("wowa_prefs", android.content.Context.MODE_PRIVATE)
                val remembered = prefs.getStringSet(STORE_HISTORY_KEY, emptySet()).orEmpty()
                val merged = mergeStores(current, remembered)
                if (merged.toSet() != remembered) prefs.edit().putStringSet(STORE_HISTORY_KEY, merged.toSet()).apply()
                merged
            }
        }.getOrDefault(emptyList())
    }
    return stores
}

/** [text] 에서 [query] 와 겹치는 구간(대소문자 무시, 여러 번이면 모두) */
internal fun matchRanges(text: String, query: String): List<IntRange> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val ranges = mutableListOf<IntRange>()
    var from = 0
    while (true) {
        val i = text.indexOf(q, from, ignoreCase = true)
        if (i < 0) break
        ranges += i until i + q.length
        from = i + q.length
    }
    return ranges
}

/** 추천 한 줄 높이. 3줄까지 보이고 나머지는 스크롤 */
private val SUGGEST_ROW_HEIGHT = 48.dp
private const val SUGGEST_VISIBLE_ROWS = 3

/**
 * 입력 칸([field])을 감싸, 칸 바로 아래에 칸 폭 그대로 구입처 추천을 붙인다.
 * 각진 모서리 · 얇은 테두리 · 줄마다 구분선(앱 기준). 3줄까지 보이고 더 있으면 스크롤.
 * 적은 글자와 겹치는 부분은 강조색 굵은 글씨. 키보드 입력을 막지 않도록 포커스를 가져가지 않는다.
 */
@Composable
fun StoreSuggestBox(
    expanded: Boolean,
    query: String,
    stores: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    field: @Composable () -> Unit
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val items = remember(stores, query) { matchStores(stores, query) }
    Box(modifier = Modifier.fillMaxWidth().onSizeChanged { size = it }) {
        field()
        if (expanded && items.isNotEmpty() && size.width > 0) {
            val widthDp = with(LocalDensity.current) { size.width.toDp() }
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, size.height),
                onDismissRequest = onDismiss,
                properties = PopupProperties(focusable = false)
            ) {
                Surface(
                    shape = RectangleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .width(widthDp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    LazyColumn(modifier = Modifier.heightIn(max = SUGGEST_ROW_HEIGHT * SUGGEST_VISIBLE_ROWS)) {
                        itemsIndexed(items, key = { _, it -> it }) { i, store ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(SUGGEST_ROW_HEIGHT)
                                    .clickable { onPick(store) }
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    highlighted(store, query),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 겹치는 글자만 강조색 굵게 */
@Composable
private fun highlighted(text: String, query: String): AnnotatedString {
    val color = MaterialTheme.colorScheme.primary
    return remember(text, query, color) {
        val ranges = matchRanges(text, query)
        buildAnnotatedString {
            append(text)
            ranges.forEach { addStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold), it.first, it.last + 1) }
        }
    }
}
