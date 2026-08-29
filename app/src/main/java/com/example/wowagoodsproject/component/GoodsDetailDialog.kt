package com.example.wowagoodsproject.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.example.wowagoodsproject.ui.theme.AppStyles
import java.io.File

@Composable
fun GoodsDetailDialog(
    imgPath: String,
    series: String,
    chara: String,
    category: String,
    price: String,
    isGotten: Boolean,
    isPending: Boolean = false,
    memo: String = "",
    setGoods: GoodsItem? = null,
    setComponentTotal: Int = 0,
    setComponentGotten: Int = 0,
    onSetClick: () -> Unit = {},
    onDismiss: () -> Unit,
    onToggleGotten: () -> Unit,
    onSetPending: () -> Unit,
    onDelete: () -> Unit,
    showDelete: Boolean = true,
    onSeriesClick: (String) -> Unit = {}
){
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    val encodedPath = encodeGoodsImagePath(imgPath)

    val imageModel = if (encodedPath.startsWith("http")) {
        encodedPath
    } else if (encodedPath.isNotEmpty()) {
        File(encodedPath)
    } else null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = if (isLandscape)
                Modifier.fillMaxWidth(0.7f).fillMaxHeight(0.9f)
            else
                Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.7f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AppStyles.paddingLarge)
            ) {
                if (isLandscape) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(AppStyles.paddingLarge)
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(model = imageModel),
                            contentDescription = null,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentScale = ContentScale.Fit
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            DetailRow(label = "시리즈", value = series, onValueClick = { onSeriesClick(series) })
                            DetailRow(label = "캐릭터", value = chara)
                            DetailRow(label = "카테고리", value = category)
                            DetailRow(label = "가격", value = price)
                            DetailRow(
                                label = "보유 여부",
                                value = when {
                                    isGotten -> "보유"
                                    isPending -> "구매예정"
                                    else -> "미보유"
                                },
                                valueColor = when {
                                    isGotten -> AppStyles.colorGotten
                                    isPending -> AppStyles.colorPending
                                    else -> AppStyles.colorNotGotten
                                }
                            )
                            if (memo.isNotEmpty()) {
                                DetailRow(label = "메모", value = memo)
                            }
                        }
                    }
                } else {
                    Column(modifier = Modifier.weight(1f)) {
                        Image(
                            painter = rememberAsyncImagePainter(model = imageModel),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.height(AppStyles.paddingLarge))
                        DetailRow(label = "시리즈", value = series, onValueClick = { onSeriesClick(series) })
                        DetailRow(label = "캐릭터", value = chara)
                        DetailRow(label = "카테고리", value = category)
                        DetailRow(label = "가격", value = price)
                        DetailRow(
                            label = "보유 여부",
                            value = when {
                                isGotten -> "보유"
                                isPending -> "구매예정"
                                else -> "미보유"
                            },
                            valueColor = when {
                                isGotten -> AppStyles.colorGotten
                                isPending -> AppStyles.colorPending
                                else -> AppStyles.colorNotGotten
                            }
                        )
                        if (memo.isNotEmpty()) {
                            DetailRow(label = "메모", value = memo)
                        }
                    }
                }

                if (setGoods != null) {
                    Spacer(modifier = Modifier.height(AppStyles.paddingMedium))
                    SetGoodsSummary(
                        setGoods = setGoods,
                        componentTotal = setComponentTotal,
                        componentGotten = setComponentGotten,
                        onClick = onSetClick
                    )
                }

                Spacer(modifier = Modifier.height(AppStyles.paddingLarge))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppStyles.paddingMedium)
                ) {
                    if (showDelete) {
                        OutlinedButton(
                            onClick = onDelete,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("삭제")
                        }
                    }
                    Button(onClick = onToggleGotten, modifier = Modifier.weight(1f)) {
                        Text(if (isGotten) "미보유로 변경" else "보유로 변경")
                    }
                    if (!isGotten) {
                        Button(onClick = onSetPending, modifier = Modifier.weight(1f)) {
                            Text(if (isPending) "구매예정 취소" else "구매예정")
                        }
                    }
                }
            }
        }
    }
}

/** 이 굿즈가 속한 세트 굿즈 요약. 누르면 같은 세트의 굿즈 목록 팝업이 열린다. */
@Composable
private fun SetGoodsSummary(
    setGoods: GoodsItem,
    componentTotal: Int,
    componentGotten: Int,
    onClick: () -> Unit
) {
    val encodedPath = encodeGoodsImagePath(setGoods.imgPath)
    val setName = setGoods.memo.ifEmpty { CATEGORY_SET }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "세트 굿즈",
            style = AppStyles.textCardSubtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(AppStyles.paddingSmall))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppStyles.paddingMedium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppStyles.paddingMedium)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
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
                        text = setName,
                        style = AppStyles.textCardSubtitle.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (setGoods.price.isNotEmpty()) {
                        Text(
                            text = setGoods.price,
                            style = AppStyles.textCardSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "구성품 ${componentTotal}개 · ${componentGotten}/${componentTotal} 보유",
                        style = AppStyles.textCardSmall,
                        color = when {
                            componentTotal > 0 && componentGotten == componentTotal -> AppStyles.colorGotten
                            componentGotten > 0 -> AppStyles.colorPartialGotten
                            else -> AppStyles.colorNotGotten
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "세트 구성품 보기",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    onValueClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppStyles.paddingSmall),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = AppStyles.textCardSubtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = AppStyles.textCardSubtitle.copy(
                textDecoration = if (onValueClick != null) TextDecoration.Underline else TextDecoration.None
            ),
            color = valueColor,
            modifier = Modifier
                .padding(start = AppStyles.paddingLarge)
                .then(
                    if (onValueClick != null) Modifier.clickable { onValueClick() }
                    else Modifier
                )
        )
    }
}
