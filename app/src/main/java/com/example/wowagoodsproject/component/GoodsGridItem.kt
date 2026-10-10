package com.example.wowagoodsproject.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.wowagoodsproject.ui.theme.AppStyles

/** 그리드 칸 이미지의 가로:세로 비율 */
private const val GRID_IMAGE_RATIO = 4f / 3f

/** 칸이 이보다 좁으면(한 줄에 많이 놓았을 때) 글자를 빼고 이미지와 상태 점만 보여준다. */
private val GRID_COMPACT_WIDTH = 72.dp

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
    quantity: Int = 1,
    selected: Boolean? = null,
    onClick: () -> Unit = {}
){
    val encodedPath = encodeGoodsImagePath(imgPath)
    val status = gottenStatus ?: if (isGotten) GottenStatus.GOTTEN else GottenStatus.NOT_GOTTEN

    BoxWithConstraints {
        val compact = maxWidth < GRID_COMPACT_WIDTH
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (selected == true) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surface
                )
                .clickable { onClick() }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // 한 줄 개수를 바꿔도 칸 모양이 유지되도록 높이를 너비에 맞춘다.
                    .aspectRatio(GRID_IMAGE_RATIO)
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
                if (compact) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(3.dp)
                            .size(8.dp)
                            .background(status.color(), CircleShape)
                    )
                } else {
                    GottenStatusBadge(
                        status = status,
                        filled = true,
                        quantity = quantity,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                    )
                }
                if (selected != null) {
                    SelectionMark(
                        selected = selected,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(if (compact) 2.dp else 6.dp)
                    )
                }
            }
            if (compact) return@Column
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
}
