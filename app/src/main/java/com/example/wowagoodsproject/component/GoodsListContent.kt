package com.example.wowagoodsproject.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.wowagoodsproject.db.official.GoodsEntity

@Composable
fun GoodsListContent(
    goods: List<GoodsEntity>,
    isGridMode: Boolean,
    gridColumns: Int,
    onGoodsClick: (GoodsEntity) -> Unit
) {
    if (goods.isEmpty()) {
        EmptyState(icon = Icons.Outlined.Inventory2, title = "굿즈가 없습니다")
        return
    }

    if (isGridMode) {
        val rows = goods.chunked(gridColumns)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
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
        LazyColumn(modifier = Modifier.fillMaxSize()) {
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
