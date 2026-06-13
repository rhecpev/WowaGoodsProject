package com.example.wowagoodsproject.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.wowagoodsproject.db.fan.FanGoodsEntity

@Composable
fun FanGoodsListContent(
    goods: List<FanGoodsEntity>,
    isGridMode: Boolean,
    gridColumns: Int,
    onGoodsClick: (FanGoodsEntity) -> Unit
) {
    if (goods.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "등록된 굿즈가 없습니다", color = MaterialTheme.colorScheme.onBackground)
        }
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
                                memo = item.memo,
                                onClick = { onGoodsClick(item) }
                            )
                        }
                    }
                    repeat(gridColumns - rowItems.size) {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
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
                    memo = item.memo,
                    onClick = { onGoodsClick(item) }
                )
                if (index < goods.lastIndex) {
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(com.example.wowagoodsproject.ui.theme.AppStyles.paddingMedium))
                }
            }
        }
    }
}