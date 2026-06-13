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
    onToggleGotten: (GoodsEntity) -> Unit
) {
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
                                    DetailRow(
                                        label = "보유",
                                        value = if (comp.isGotten) "보유" else "미보유",
                                        valueColor = if (comp.isGotten) AppStyles.colorGotten else AppStyles.colorNotGotten
                                    )
                                }
                            }
                            Button(
                                onClick = { onToggleGotten(comp) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (comp.isGotten) "미보유로 변경" else "보유로 변경")
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
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(AppStyles.paddingSmall)
                        ) {
                            items(components) { component ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .alpha(if (component.isGotten) 1f else 0.3f)
                                        .background(
                                            color = if (selectedComponent?.goodsId == component.goodsId)
                                                MaterialTheme.colorScheme.primaryContainer
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
                                            color = if (component.isGotten) AppStyles.colorGotten else AppStyles.colorNotGotten
                                        )
                                        Text(
                                            text = component.price,
                                            style = AppStyles.textCardSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = if (component.isGotten) "보유" else "미보유",
                                        style = if (component.isGotten) AppStyles.textGotten else AppStyles.textNotGotten
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