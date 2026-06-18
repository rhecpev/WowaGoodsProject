package com.example.wowagoodsproject.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.example.wowagoodsproject.ui.theme.AppStyles
import java.io.File
import java.net.URI

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
    onDismiss: () -> Unit,
    onToggleGotten: () -> Unit,
    onSetPending: () -> Unit,
    onDelete: () -> Unit,
    showDelete: Boolean = true
){
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    val encodedPath = if (imgPath.startsWith("http")) {
        try {
            URI(null, imgPath.removePrefix("https://"), null).toASCIIString()
                .let { "https://" + it.removePrefix("https:/") }
        } catch (e: Exception) {
            imgPath
        }
    } else imgPath

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
                            DetailRow(label = "시리즈", value = series)
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
                        DetailRow(label = "시리즈", value = series)
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

@Composable
fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
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
            style = AppStyles.textCardSubtitle,
            color = valueColor,
            modifier = Modifier.padding(start = AppStyles.paddingLarge)
        )
    }
}