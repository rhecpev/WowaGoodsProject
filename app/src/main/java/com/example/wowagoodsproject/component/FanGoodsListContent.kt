package com.example.wowagoodsproject.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.wowagoodsproject.db.fan.FanGoodsEntity

@Composable
fun FanGoodsListContent(
    goods: List<FanGoodsEntity>,
    isGridMode: Boolean,
    gridColumns: Int,
    onGoodsClick: (FanGoodsEntity) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    if (goods.isEmpty()) {
        EmptyState(icon = Icons.Outlined.Palette, title = "2차창작 굿즈가 없습니다")
        return
    }

    if (isGridMode) {
        val rows = goods.chunked(gridColumns)
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
            itemsIndexed(rows) { _, rowItems ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    rowItems.forEach { item ->
                        Box(modifier = Modifier.weight(1f)) {
                            GoodsGridItem(
                                imgPath = item.imgPath,
                                series = item.series,
                                chara = item.chara,
                                category = item.category,
                                price = item.price,
                                isGotten = item.isGotten,
                                gottenStatus = item.status.asGottenStatus(),
                                memo = item.memo,
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
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
            itemsIndexed(goods) { index, item ->
                GoodsListItem(
                    imgPath = item.imgPath,
                    series = item.series,
                    chara = item.chara,
                    category = item.category,
                    price = item.price,
                    isGotten = item.isGotten,
                    gottenStatus = item.status.asGottenStatus(),
                    memo = item.memo,
                    onClick = { onGoodsClick(item) }
                )
                if (index < goods.lastIndex) {
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
