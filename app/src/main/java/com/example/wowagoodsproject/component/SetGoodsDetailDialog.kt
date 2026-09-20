package com.example.wowagoodsproject.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.example.wowagoodsproject.db.official.GoodsEntity

/**
 * 세트 굿즈와 구성품 목록.
 * 세로 화면: 위에 고른 구성품 요약 + 상태 세그먼트, 아래에 구성품 목록.
 * 가로 화면: 왼쪽 상세, 오른쪽 목록.
 */
@Composable
fun SetGoodsDetailDialog(
    setGoods: GoodsEntity,
    components: List<GoodsEntity>,
    initialComponent: GoodsEntity? = null,
    onDismiss: () -> Unit,
    onToggleGotten: (GoodsEntity) -> Unit,
    onSetPending: (GoodsEntity) -> Unit = {},
    onBulkSetStatus: (GoodsStatus) -> Unit = {},
    highlightChara: String? = null,
    highlightCategory: String? = null
){
    var selectedComponent by remember(initialComponent) { mutableStateOf(initialComponent) }
    val currentComponent = selectedComponent?.let { selected ->
        components.find { it.goodsId == selected.goodsId }
    }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val gottenCount = components.count { it.isGotten }

    val sortedComponents = remember(components, highlightChara, highlightCategory) {
        components.sortedWith(
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
    }

    val onChangeStatus: (GoodsEntity, GoodsStatus) -> Unit = { comp, target ->
        applyStatusChange(
            current = comp.status,
            target = target,
            onToggleGotten = { onToggleGotten(comp) },
            onSetPending = { onSetPending(comp) }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = setGoods.memo.ifEmpty { CATEGORY_SET },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${setGoods.series} · ${gottenCount}/${components.size} 보유",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "닫기")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (isLandscape) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (currentComponent != null) {
                                SelectedComponentDetail(
                                    comp = currentComponent,
                                    large = true,
                                    onChangeStatus = { onChangeStatus(currentComponent, it) }
                                )
                            } else {
                                SelectHint()
                            }
                        }
                        ComponentList(
                            components = sortedComponents,
                            selectedId = selectedComponent?.goodsId,
                            highlightChara = highlightChara,
                            highlightCategory = highlightCategory,
                            onSelect = { selectedComponent = it },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                } else {
                    if (currentComponent != null) {
                        SelectedComponentDetail(
                            comp = currentComponent,
                            large = false,
                            onChangeStatus = { onChangeStatus(currentComponent, it) }
                        )
                    } else {
                        SelectHint()
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    ComponentList(
                        components = sortedComponents,
                        selectedId = selectedComponent?.goodsId,
                        highlightChara = highlightChara,
                        highlightCategory = highlightCategory,
                        onSelect = { selectedComponent = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "구성품 ${components.size}개 일괄 변경",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                // 구성품 상태가 이미 하나로 맞춰져 있으면 그 칸을 선택된 상태로 보여준다(섞여 있으면 선택 없음).
                StatusSegmentedButtons(
                    current = components.map { it.status }.distinct().singleOrNull(),
                    onSelect = onBulkSetStatus
                )
            }
        }
    }
}

@Composable
private fun SelectHint() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Text(
            text = "아래 구성품을 누르면 여기서 보유 상태를 바꿀 수 있어요",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun SelectedComponentDetail(
    comp: GoodsEntity,
    large: Boolean,
    onChangeStatus: (GoodsStatus) -> Unit
) {
    val encodedPath = encodeGoodsImagePath(comp.imgPath)
    val painter = rememberAsyncImagePainter(model = if (encodedPath.isNotEmpty()) encodedPath else null)

    Column {
        if (large) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = comp.category,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            DetailRow(label = "캐릭터", value = comp.chara)
            DetailRow(label = "가격", value = comp.price)
            if (comp.memo.isNotEmpty()) DetailRow(label = "메모", value = comp.memo)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Image(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = comp.category,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = comp.chara,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = comp.price,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        StatusSegmentedButtons(current = comp.status, onSelect = onChangeStatus)
    }
}

@Composable
private fun ComponentList(
    components: List<GoodsEntity>,
    selectedId: Int?,
    highlightChara: String?,
    highlightCategory: String?,
    onSelect: (GoodsEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "구성품 ${components.size}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(components, key = { it.goodsId }) { component ->
                val isSelected = selectedId == component.goodsId
                val isHighlighted = when {
                    highlightChara != null && highlightCategory != null ->
                        component.chara.contains(highlightChara) && component.category == highlightCategory
                    highlightChara != null -> component.chara.contains(highlightChara)
                    highlightCategory != null -> component.category == highlightCategory
                    else -> false
                }
                val encodedPath = encodeGoodsImagePath(component.imgPath)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                isSelected -> MaterialTheme.colorScheme.secondaryContainer
                                isHighlighted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                else -> Color.Transparent
                            }
                        )
                        .clickable { onSelect(component) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .alpha(if (component.status == GoodsStatus.NOT_GOTTEN) 0.45f else 1f)
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(
                                model = if (encodedPath.isNotEmpty()) encodedPath else null
                            ),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = component.category,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = component.price,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    GottenStatusBadge(status = component.status.asGottenStatus())
                }
            }
        }
    }
}
