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
import com.example.wowagoodsproject.ui.theme.AppStyles

/** 그리드 칸 이미지의 가로:세로 비율 */
private const val GRID_IMAGE_RATIO = 4f / 3f

/** 칸이 이보다 좁으면(한 줄에 많이 놓았을 때) 글자를 빼고 이미지와 상태 점만 보여준다. */
internal val GRID_COMPACT_WIDTH = 72.dp

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
    /** 사진 칸에 붙일 것(보기 전환 애니메이션의 공유 요소 등) */
    imageModifier: Modifier = Modifier,
    /** 글자 칸에 붙일 것(보기 전환 때 글자를 늦게 보이게 하는 등) */
    textModifier: Modifier = Modifier,
    /**
     * 좁은 칸(글자 없이 사진·상태 점만)인지. 목록이 한 번 재서 넘기면 칸마다 너비를 재지 않는다(그리는 시간이 줄어든다).
     * null 이면 칸이 직접 잰다.
     */
    compact: Boolean? = null,
    onClick: () -> Unit = {}
){
    val encodedPath = encodeGoodsImagePath(imgPath)
    val status = gottenStatus ?: if (isGotten) GottenStatus.GOTTEN else GottenStatus.NOT_GOTTEN

    if (compact != null) {
        GridItemBody(encodedPath, series, chara, category, price, status, memo, quantity, selected, imageModifier, textModifier, compact, onClick)
        return
    }
    BoxWithConstraints {
        GridItemBody(encodedPath, series, chara, category, price, status, memo, quantity, selected, imageModifier, textModifier, maxWidth < GRID_COMPACT_WIDTH, onClick)
    }
}

@Composable
private fun GridItemBody(
    encodedPath: String,
    series: String,
    chara: String,
    category: String,
    price: String,
    status: GottenStatus,
    memo: String,
    quantity: Int,
    selected: Boolean?,
    imageModifier: Modifier,
    textModifier: Modifier,
    compact: Boolean,
    onClick: () -> Unit
) {
    run {
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
                    .then(imageModifier)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = rememberGoodsCardPainter(encodedPath),
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
                            .background(status.color())
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
                modifier = textModifier.padding(AppStyles.paddingMedium)
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
