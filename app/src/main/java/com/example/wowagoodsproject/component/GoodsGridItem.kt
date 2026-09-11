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

@Composable
fun GoodsGridItem(
    imgPath: String,
    series: String,
    chara: String,
    category: String,
    price: String,
    isGotten: Boolean,
    gottenStatus: GottenStatus? = null,
    memo: String,
    onClick: () -> Unit = {}
){
    val encodedPath = encodeGoodsImagePath(imgPath)
    val status = gottenStatus ?: if (isGotten) GottenStatus.GOTTEN else GottenStatus.NOT_GOTTEN

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
            GottenStatusBadge(
                status = status,
                filled = true,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            )
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
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
                text = if (memo.isNotEmpty()) "${category} / ${memo}" else category,
                style = AppStyles.textCardSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = price,
                style = AppStyles.textCardSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
