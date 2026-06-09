package com.example.wowagoodsproject.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.wowagoodsproject.ui.theme.AppStyles
import java.io.File
import java.net.URI

@Composable
fun GoodsGridItem(
    imgPath: String,
    series: String,
    chara: String,
    category: String,
    price: Int,
    isGotten: Boolean,
    memo: String,
    onClick: () -> Unit = {}
) {
    val encodedPath = if (imgPath.startsWith("http")) {
        try {
            URI(null, imgPath.removePrefix("https://"), null).toASCIIString()
                .let { "https://" + it.removePrefix("https:/") }
        } catch (e: Exception) {
            imgPath
        }
    } else imgPath

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(AppStyles.cardImageHeightGrid)
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
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline
        )
        Column(
            modifier = Modifier.padding(AppStyles.paddingMedium)
        ) {
            Text(
                text = series,
                style = AppStyles.textCardTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = chara,
                style = AppStyles.textCardSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${category} / ${memo}",
                style = AppStyles.textCardSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${price}원",
                style = AppStyles.textCardSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (isGotten) "보유" else "미보유",
                style = if (isGotten) AppStyles.textGotten else AppStyles.textNotGotten
            )
        }
    }
}