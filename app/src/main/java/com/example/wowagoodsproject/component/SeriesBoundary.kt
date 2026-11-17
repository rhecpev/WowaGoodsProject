package com.example.wowagoodsproject.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 시리즈 비교용 값. 앞뒤 공백 차이는 같은 시리즈로 본다. */
private fun seriesKey(goods: GoodsItem): String = goods.series.trim()

/** [index] 번째 굿즈가 바로 앞 굿즈와 다른 시리즈로 넘어가는 자리인지. 첫 굿즈는 아니다. */
fun <T : GoodsItem> isSeriesStart(goods: List<T>, index: Int): Boolean =
    index > 0 && seriesKey(goods[index]) != seriesKey(goods[index - 1])

/**
 * 그리드 줄 나누기. 이어지는 같은 시리즈끼리 묶은 뒤 [columns] 개씩 자른다.
 * 한 줄에 두 시리즈가 섞이지 않아서 시리즈가 바뀌는 곳에 줄 단위로 구분선을 넣을 수 있다.
 */
fun <T : GoodsItem> chunkBySeries(goods: List<T>, columns: Int): List<List<T>> {
    if (goods.isEmpty()) return emptyList()
    val runs = mutableListOf<MutableList<T>>()
    goods.forEachIndexed { index, item ->
        if (index == 0 || isSeriesStart(goods, index)) runs.add(mutableListOf())
        runs.last().add(item)
    }
    return runs.flatMap { it.chunked(columns.coerceAtLeast(1)) }
}

/** [rows] 의 [index] 번째 줄이 앞 줄과 다른 시리즈로 시작하는지. */
fun <T : GoodsItem> isSeriesStartRow(rows: List<List<T>>, index: Int): Boolean =
    index > 0 && seriesKey(rows[index].first()) != seriesKey(rows[index - 1].first())

/**
 * 시리즈가 바뀌는 자리에 두는 이중선. 굿즈 사이의 회색 한 줄 구분선(outlineVariant)과 헷갈리지 않게 테마 강조색으로 긋는다.
 * outline 은 다크 계열 테마에서 회색이라 일반 구분선과 거의 같아 보였다.
 * primary 는 모든 테마에서 배경 대비 3:1 이상(라이트 3.1 ~ 다크 15.6)이고 색상도 달라서 구분된다.
 * [verticalPadding] 으로 위아래 여백을 줄 수 있다(카드 목록처럼 원래 간격이 있는 곳).
 */
@Composable
fun SeriesBoundaryDivider(modifier: Modifier = Modifier, verticalPadding: Dp = 0.dp) {
    val color = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding)
    ) {
        HorizontalDivider(thickness = 1.dp, color = color)
        Spacer(modifier = Modifier.height(2.dp))
        HorizontalDivider(thickness = 1.dp, color = color)
    }
}
