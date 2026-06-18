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
import androidx.compose.ui.text.font.FontWeight
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
    isExpanded: Boolean = false,
    price: String,
    isGotten: Boolean,
    gottenStatus: GottenStatus? = null,
    memo: String,
    components: List<String> = emptyList(),
    highlightCategory: String? = null, // 추가
    onClick: () -> Unit = {}
){
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
            .background(
                if (isExpanded) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surface
            )
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
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = chara,
                    style = AppStyles.textCardSubtitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (components.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$category [",
                            style = AppStyles.textCardSubtitle,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        components.forEachIndexed { index, cat ->
                            Text(
                                text = if (index < components.lastIndex) "$cat, " else "$cat]",
                                style = AppStyles.textCardSubtitle,
                                color = if (cat == highlightCategory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                } else {
                    Text(
                        text = if (memo.isNotEmpty()) "${category} / ${memo}" else category,
                        style = AppStyles.textCardSubtitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = price,
                    style = AppStyles.textPrice,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val status = gottenStatus ?: if (isGotten) GottenStatus.GOTTEN else GottenStatus.NOT_GOTTEN
            Text(
                text = when (status) {
                    GottenStatus.GOTTEN -> "보유"
                    GottenStatus.NOT_GOTTEN -> "미보유"
                    GottenStatus.PARTIAL -> "일부보유"
                    GottenStatus.PENDING -> "구매예정"
                },
                style = AppStyles.textCardSmall.copy(fontWeight = FontWeight.Bold),
                color = when (status) {
                    GottenStatus.GOTTEN -> AppStyles.colorGotten
                    GottenStatus.NOT_GOTTEN -> AppStyles.colorNotGotten
                    GottenStatus.PARTIAL -> AppStyles.colorPartialGotten
                    GottenStatus.PENDING -> AppStyles.colorPending
                },
                modifier = Modifier.padding(AppStyles.paddingMedium)
            )
        }
    }
}