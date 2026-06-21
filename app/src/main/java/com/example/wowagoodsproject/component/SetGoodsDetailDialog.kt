package com.example.wowagoodsproject.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.ui.theme.AppStyles
import java.io.File
import java.net.URI

@Composable
fun SetGoodsDetailDialog(
    setGoods: GoodsEntity,
    components: List<GoodsEntity>,
    onDismiss: () -> Unit,
    onToggleGotten: (GoodsEntity) -> Unit,
    onSetPending: (GoodsEntity) -> Unit = {},
    onBulkToggleGotten: (Boolean) -> Unit = {},
    highlightChara: String? = null,
    highlightCategory: String? = null
){
    var selectedComponent by remember { mutableStateOf<GoodsEntity?>(null) }
    val currentComponent = selectedComponent?.let { selected ->
        components.find { it.goodsId == selected.goodsId }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AppStyles.paddingLarge)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(AppStyles.paddingLarge)
                ) {
                    // ── 왼쪽: 선택된 구성품 상세 or 빈 화면 ──
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (currentComponent != null) {
                            val comp = currentComponent
                            val compEncodedPath = if (comp.imgPath.startsWith("http")) {
                                try {
                                    URI(null, comp.imgPath.removePrefix("https://"), null).toASCIIString()
                                        .let { "https://" + it.removePrefix("https:/") }
                                } catch (e: Exception) { comp.imgPath }
                            } else comp.imgPath

                            LazyColumn(
                                modifier = Modifier.weight(1f)
                            ) {
                                item {
                                    Image(
                                        painter = rememberAsyncImagePainter(
                                            model = if (compEncodedPath.isNotEmpty()) compEncodedPath else null
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(150.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                    Spacer(modifier = Modifier.height(AppStyles.paddingMedium))
                                    DetailRow(label = "시리즈", value = comp.series)
                                    DetailRow(label = "캐릭터", value = comp.chara)
                                    DetailRow(label = "카테고리", value = comp.category)
                                    DetailRow(label = "가격", value = comp.price)
                                    DetailRow(label = "메모", value = comp.memo)
                                    DetailRow(
                                        label = "보유 여부",
                                        value = when (comp.status) {
                                            GoodsStatus.GOTTEN -> "보유"
                                            GoodsStatus.PENDING -> "구매예정"
                                            else -> "미보유"
                                        },
                                        valueColor = when (comp.status) {
                                            GoodsStatus.GOTTEN -> AppStyles.colorGotten
                                            GoodsStatus.PENDING -> AppStyles.colorPending
                                            else -> AppStyles.colorNotGotten
                                        }
                                    )
                                }
                            }
                            Button(
                                onClick = { onToggleGotten(comp) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (comp.isGotten) "미보유로 변경" else "보유로 변경")
                            }
                            if (!comp.isGotten) {
                                Spacer(modifier = Modifier.height(AppStyles.paddingSmall))
                                Button(
                                    onClick = { onSetPending(comp) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (comp.status == GoodsStatus.PENDING) "구매예정 취소" else "구매예정")
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "구성품을 선택하세요",
                                    style = AppStyles.textCardSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight(),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // ── 오른쪽: 구성품 목록 ──
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Text(text = "구성품", style = AppStyles.textCardTitle)
                        Spacer(modifier = Modifier.height(AppStyles.paddingSmall))
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
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(AppStyles.paddingSmall)
                        ) {

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
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .alpha(if (component.isGotten || component.status == GoodsStatus.PENDING) 1f else 0.3f)                                        .background(
                                            color = if (selectedComponent?.goodsId == component.goodsId)
                                                MaterialTheme.colorScheme.primaryContainer
                                            else if (isHighlighted)
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                            else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedComponent = component },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(AppStyles.paddingSmall)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val compEncodedPath = if (component.imgPath.startsWith("http")) {
                                            try {
                                                URI(null, component.imgPath.removePrefix("https://"), null).toASCIIString()
                                                    .let { "https://" + it.removePrefix("https:/") }
                                            } catch (e: Exception) { component.imgPath }
                                        } else component.imgPath
                                        Image(
                                            painter = rememberAsyncImagePainter(
                                                model = if (compEncodedPath.isNotEmpty()) compEncodedPath else null
                                            ),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
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
                                        Text(
                                            text = component.price,
                                            style = AppStyles.textCardSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = when (component.status) {
                                            GoodsStatus.GOTTEN -> "보유"
                                            GoodsStatus.PENDING -> "구매예정"
                                            else -> "미보유"
                                        },
                                        style = when (component.status) {
                                            GoodsStatus.GOTTEN -> AppStyles.textGotten
                                            GoodsStatus.PENDING -> AppStyles.textPending
                                            else -> AppStyles.textNotGotten
                                        }
                                    )
                                }
                                HorizontalDivider(
                                    thickness = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(AppStyles.paddingMedium))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppStyles.paddingMedium)
                ) {
                    Button(
                        onClick = { onBulkToggleGotten(true) },
                        modifier = Modifier.weight(1f)
                    ) { Text("일괄 보유") }
                    OutlinedButton(
                        onClick = { onBulkToggleGotten(false) },
                        modifier = Modifier.weight(1f)
                    ) { Text("일괄 미보유") }
                }
                Spacer(modifier = Modifier.height(AppStyles.paddingSmall))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("닫기")
                }
            }
        }
    }
}