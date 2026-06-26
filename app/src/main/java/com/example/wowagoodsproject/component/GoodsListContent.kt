package com.example.wowagoodsproject.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.ui.theme.AppStyles
import androidx.compose.ui.graphics.Color
@Composable
fun GoodsListContent(
    goods: List<GoodsEntity>,
    allGoods: List<GoodsEntity>,
    isGridMode: Boolean,
    gridColumns: Int,
    filterType: FilterType,
    highlightCategory: String? = null,
    highlightChara: String? = null,
    onGoodsClick: (GoodsEntity) -> Unit,
    onSetGoodsClick: (GoodsEntity) -> Unit,
    onComponentClick: (GoodsEntity) -> Unit,
    onBulkToggleGotten: (GoodsEntity, Boolean) -> Unit  // 추가

) {
    val expandedStates = remember { mutableStateMapOf<Int, Boolean>() }

    fun getComponents(setGoods: GoodsEntity) = allGoods.filter {
        it.category != CATEGORY_SET && it.memo == setGoods.memo && it.series == setGoods.series
    }

    fun getGottenStatus(item: GoodsEntity): GottenStatus {
        if (item.category != CATEGORY_SET) {
            return when (item.status) {
                GoodsStatus.GOTTEN -> GottenStatus.GOTTEN
                GoodsStatus.PENDING -> GottenStatus.PENDING
                else -> GottenStatus.NOT_GOTTEN
            }
        }
        val components = getComponents(item)
        val target = if (highlightChara != null) components.filter { it.chara.contains(highlightChara) } else components
        return when {
            target.isEmpty() -> when (item.status) {
                GoodsStatus.GOTTEN -> GottenStatus.GOTTEN
                GoodsStatus.PENDING -> GottenStatus.PENDING
                else -> GottenStatus.NOT_GOTTEN
            }
            target.all { it.status == GoodsStatus.GOTTEN } -> GottenStatus.GOTTEN
            target.none { it.status == GoodsStatus.GOTTEN || it.status == GoodsStatus.PENDING } -> GottenStatus.NOT_GOTTEN
            else -> GottenStatus.PARTIAL
        }
    }

    fun getComponentCategories(item: GoodsEntity): List<String> {
        if (item.category != CATEGORY_SET) return emptyList()
        return getComponents(item).map { it.category }
    }

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
                        val gottenStatus = getGottenStatus(item)
                        val componentCategories = getComponentCategories(item)
                        Box(modifier = Modifier.weight(1f)) {
                            GoodsGridItem(
                                imgPath = item.imgPath,
                                series = item.series,
                                chara = item.chara,
                                category = item.category,
                                price = item.price,
                                isGotten = item.isGotten,
                                gottenStatus = gottenStatus,
                                memo = item.memo,
                                components = componentCategories,
                                highlightCategory = highlightCategory,
                                onClick = {
                                    if (item.category == CATEGORY_SET) onSetGoodsClick(item)
                                    else onGoodsClick(item)
                                }
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
                val isExpanded = expandedStates[index] ?: false
                val gottenStatus = getGottenStatus(item)
                val componentCategories = getComponentCategories(item)

                GoodsListItem(
                    imgPath = item.imgPath,
                    series = item.series,
                    chara = item.chara,
                    category = item.category,
                    price = item.price,
                    isGotten = item.isGotten,
                    isExpanded = isExpanded,
                    gottenStatus = gottenStatus,
                    memo = item.memo,
                    components = componentCategories,
                    highlightCategory = highlightCategory,
                    onClick = {
                        if (item.category == CATEGORY_SET) {
                            expandedStates[index] = !isExpanded
                        } else {
                            onGoodsClick(item)
                        }
                    }
                )

                if (item.category == CATEGORY_SET && isExpanded) {
                    val components = getComponents(item)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppStyles.paddingMedium),
                        horizontalArrangement = Arrangement.spacedBy(AppStyles.paddingMedium)
                    ) {
                        Button(
                            onClick = { onBulkToggleGotten(item, true) },
                            modifier = Modifier.weight(1f)
                        ) { Text("일괄 보유") }
                        OutlinedButton(
                            onClick = { onBulkToggleGotten(item, false) },
                            modifier = Modifier.weight(1f)
                        ) { Text("일괄 미보유") }
                    }
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppStyles.paddingMedium),
                        horizontalArrangement = Arrangement.spacedBy(AppStyles.paddingMedium)
                    ) {
                        val sortedComponents = components.sortedWith(
                            compareByDescending<GoodsEntity> { component ->
                                when {
                                    (highlightChara != null && component.chara.contains(highlightChara)) &&
                                            (highlightCategory != null && component.category == highlightCategory) -> 2
                                    (highlightChara != null && component.chara.contains(highlightChara)) ||
                                            (highlightCategory != null && component.category == highlightCategory) -> 1
                                    else -> 0
                                }
                            }.thenByDescending { component ->
                                when (component.status) {
                                    GoodsStatus.GOTTEN -> 2
                                    GoodsStatus.PENDING -> 1
                                    else -> 0
                                }
                            }
                        )
                        items(sortedComponents) { component ->
                            val isHighlighted = when {
                                highlightChara != null && highlightCategory != null ->
                                    component.chara.contains(highlightChara) && component.category == highlightCategory
                                highlightChara != null ->
                                    component.chara.contains(highlightChara)
                                highlightCategory != null ->
                                    component.category == highlightCategory
                                else -> false
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(80.dp)
                                    .background(
                                        color = if (isHighlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .alpha(if (component.isGotten || component.status == GoodsStatus.PENDING) 1f else 0.3f)                                    .clickable { onComponentClick(component) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = rememberAsyncImagePainter(
                                            model = if (component.imgPath.isNotEmpty()) component.imgPath else null
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = component.category,
                                    style = AppStyles.textCardSubtitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = when (component.status) {
                                        GoodsStatus.GOTTEN -> AppStyles.colorGotten
                                        GoodsStatus.PENDING -> AppStyles.colorPending
                                        else -> AppStyles.colorNotGotten
                                    }
                                )
                            }
                        }
                    }
                }

                if (index < goods.lastIndex) {
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(AppStyles.paddingMedium))
                }
            }
        }
    }
}