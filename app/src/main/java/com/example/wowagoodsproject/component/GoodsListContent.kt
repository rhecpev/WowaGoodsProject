package com.example.wowagoodsproject.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.wowagoodsproject.db.official.GoodsEntity

@Composable
fun GoodsListContent(
    goods: List<GoodsEntity>,
    isGridMode: Boolean,
    gridColumns: Int,
    onGoodsClick: (GoodsEntity) -> Unit,
    /** null 이면 선택 모드가 아니다. */
    selectedIds: Set<Int>? = null,
    /** 스크롤해서 반짝이며 보여 줄 굿즈 id */
    focusId: Int? = null,
    /** [focusId] 굿즈의 반짝임이 끝났을 때 */
    onFocusShown: () -> Unit = {}
) {
    if (goods.isEmpty()) {
        EmptyState(icon = Icons.Outlined.Inventory2, title = "굿즈가 없습니다")
        return
    }

    // 리스트는 굿즈 하나, 격자는 한 줄이 한 항목이라 위치가 서로 맞지 않는다. 보기 방식을 바꾸면 맨 위부터 본다.
    val listState = rememberSaveable(isGridMode, saver = LazyListState.Saver) { LazyListState() }
    val focusIndex = focusId?.let { id -> goods.indexOfFirst { it.goodsId == id } }?.takeIf { it >= 0 }
    // 격자 모드는 한 줄이 한 항목이다.
    val focusRow = focusIndex?.let { if (isGridMode) it / gridColumns else it }
    LaunchedEffect(focusId, focusRow) {
        if (focusRow == null) return@LaunchedEffect
        listState.scrollToItem(focusRow)
        // 화면 가운데쯤 오도록 맞춘다. 목록 끝 쪽 항목은 더 내려갈 수 없어 그 자리에 둔다.
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == focusRow }
        if (item != null) {
            val centered = (info.viewportSize.height - item.size) / 2
            listState.animateScrollBy((item.offset - centered).toFloat())
        }
    }

    if (isGridMode) {
        val rows = goods.chunked(gridColumns)
        LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
            itemsIndexed(rows) { _, rowItems ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    rowItems.forEach { item ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .highlightFlash(active = item.goodsId == focusId, onFinished = onFocusShown)
                        ) {
                            GoodsGridItem(
                                imgPath = item.imgPath,
                                series = item.series,
                                chara = item.chara,
                                category = item.category,
                                price = item.price,
                                isGotten = item.isGotten,
                                gottenStatus = item.status.asGottenStatus(),
                                memo = item.memo,
                                quantity = item.quantity,
                                selected = selectedIds?.let { item.goodsId in it },
                                onClick = { onGoodsClick(item) }
                            )
                        }
                    }
                    repeat(gridColumns - rowItems.size) {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
            itemsIndexed(goods) { index, item ->
                Box(modifier = Modifier.highlightFlash(active = item.goodsId == focusId, onFinished = onFocusShown)) {
                    GoodsListItem(
                        imgPath = item.imgPath,
                        series = item.series,
                        chara = item.chara,
                        category = item.category,
                        price = item.price,
                        isGotten = item.isGotten,
                        gottenStatus = item.status.asGottenStatus(),
                        memo = item.memo,
                        quantity = item.quantity,
                        selected = selectedIds?.let { item.goodsId in it },
                        onClick = { onGoodsClick(item) }
                    )
                }

                if (index < goods.lastIndex) {
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
