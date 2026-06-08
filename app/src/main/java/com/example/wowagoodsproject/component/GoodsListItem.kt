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
import coil.compose.rememberAsyncImagePainter
import com.example.wowagoodsproject.ui.theme.AppStyles
import java.io.File
import java.net.URI

@Composable
fun GoodsListItem(
    imgPath: String,
    series: String,
    chara: String,
    category: String,
    price: Int,
    isGotten: Boolean,
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(AppStyles.cardImageHeightList),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .padding(start = AppStyles.paddingMedium)
                    .width(AppStyles.cardImageWidth)
                    .height(AppStyles.cardImageHeightList)
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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(AppStyles.paddingMedium),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Text(
                    text = series,
                    style = AppStyles.textCardTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = chara,
                    style = AppStyles.textCardSubtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = category,
                    style = AppStyles.textCardSubtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${price}원",
                    style = AppStyles.textPrice,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = if (isGotten) "보유" else "미보유",
                style = if (isGotten) AppStyles.textGotten else AppStyles.textNotGotten,
                modifier = Modifier.padding(AppStyles.paddingMedium)
            )
        }
    }
}