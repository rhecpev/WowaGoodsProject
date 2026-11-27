package com.example.wowagoodsproject.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.wowagoodsproject.ui.theme.AppStyles

@Composable
fun GoodsListItem(
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
    /**
     * 불러오기 확인: 지금 → 불러온 뒤 상태. null 이 아니면 상태 배지 자리에 "보유 → 미보유"처럼 보인다.
     * 한쪽이 null 이면 그쪽에 굿즈가 없다(새로 생김 / 없어짐).
     */
    change: Pair<GottenStatus?, GottenStatus?>? = null,
    /** [change] 의 지금 수량(상태가 같고 수량만 바뀌는 굿즈도 "보유 ×3 → 보유"처럼 보이게) */
    changeBeforeQuantity: Int = 1,
    /** 사진 칸에 붙일 것(보기 전환 애니메이션의 공유 요소 등) */
    imageModifier: Modifier = Modifier,
    /** 글자 칸에 붙일 것(보기 전환 때 글자를 늦게 보이게 하는 등) */
    textModifier: Modifier = Modifier,
    onClick: () -> Unit = {}
){
    val encodedPath = encodeGoodsImagePath(imgPath)
    val status = gottenStatus ?: if (isGotten) GottenStatus.GOTTEN else GottenStatus.NOT_GOTTEN

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected == true) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .clickable { onClick() }
            .padding(AppStyles.paddingMedium)
            .height(AppStyles.cardImageHeightList),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected != null) {
            SelectionMark(selected = selected)
            Spacer(modifier = Modifier.width(12.dp))
        }
        Box(
            modifier = Modifier
                .width(AppStyles.cardImageWidth)
                .fillMaxHeight()
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
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .then(textModifier)
                .fillMaxHeight()
                .padding(start = 12.dp),
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
            Text(
                text = if (memo.isNotEmpty()) "${category} / ${memo}" else category,
                style = AppStyles.textCardSubtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = price,
                    style = AppStyles.textPrice,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (change == null) GottenStatusBadge(status = status, quantity = quantity)
                else StatusChangeBadges(change.first, change.second, quantity, changeBeforeQuantity)
            }
        }
    }
}

/** 지금 → 불러온 뒤 상태 배지(수량 포함). null 이면 "없음" */
@Composable
fun StatusChangeBadges(before: GottenStatus?, after: GottenStatus?, quantity: Int = 1, beforeQuantity: Int = 1) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (before != null) GottenStatusBadge(status = before, quantity = beforeQuantity)
        else Text("없음", style = AppStyles.textCardSmall, color = MaterialTheme.colorScheme.outline)
        Text(" → ", style = AppStyles.textCardSmall, color = MaterialTheme.colorScheme.primary)
        if (after != null) GottenStatusBadge(status = after, quantity = quantity)
        else Text("없음", style = AppStyles.textCardSmall, color = MaterialTheme.colorScheme.outline)
    }
}
