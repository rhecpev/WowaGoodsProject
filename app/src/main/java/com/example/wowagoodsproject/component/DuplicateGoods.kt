package com.example.wowagoodsproject.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.wowagoodsproject.db.betray.charaNames
import com.example.wowagoodsproject.ui.theme.AppStyles

/*
 * 같은 굿즈 다시 등록 막기(확인만): 저장하려는 굿즈와 시리즈·캐릭터·카테고리·가격·메모가 모두 같은 굿즈가 있으면
 * 그 굿즈의 상세를 보여 주고 '그래도 추가'로만 저장한다. 명조 비공식 등록과 배신 굿즈 등록이 같이 쓴다.
 */

/**
 * 같은 굿즈인지 견줄 내용. 앞뒤 공백은 무시하고, 캐릭터는 적은 순서가 달라도 같은 사람들이면 같다.
 * 사진·보유 상태·구매 정보는 보지 않는다(같은 굿즈를 다른 상태로 또 넣는 것을 잡으려고).
 */
fun goodsContentKey(series: String, chara: String, category: String, price: String, memo: String): String =
    listOf(
        series.trim(),
        charaNames(chara).sorted().joinToString(","),
        category.trim(),
        price.trim(),
        memo.trim()
    ).joinToString("\u0001")

/** [candidates] 중 [key] 와 같은 굿즈 */
fun <T : GoodsItem> List<T>.sameContentAs(key: String): List<T> =
    filter { goodsContentKey(it.series, it.chara, it.category, it.price, it.memo) == key }

/**
 * 같은 굿즈가 이미 있다고 알리는 창. 이미 있는 굿즈마다 사진과 상세(시리즈·캐릭터·카테고리·가격·메모·보유 상태)를 보여 준다.
 * [onAddAnyway] 를 눌러야 저장한다.
 */
@Composable
fun DuplicateGoodsDialog(
    duplicates: List<GoodsItem>,
    onAddAnyway: () -> Unit,
    onDismiss: () -> Unit,
    /** 고치는 중이면 '그래도 저장' */
    confirmText: String = "그래도 추가"
) {
    AlertDialog(
        shape = RectangleShape,
        onDismissRequest = onDismiss,
        title = { Text("같은 굿즈가 이미 있어요") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "시리즈·캐릭터·카테고리·가격·메모가 모두 같은 굿즈가 ${duplicates.size}개 있습니다. 그래도 하나 더 추가할까요?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                duplicates.forEach { DuplicateGoodsCard(it) }
            }
        },
        confirmButton = { TextButton(onClick = onAddAnyway) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

/** 이미 있는 굿즈 한 개: 사진 + 상세 줄 */
@Composable
private fun DuplicateGoodsCard(g: GoodsItem) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.size(84.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val model = goodsImageModel(g.imgPath)
                if (model != null) {
                    Image(
                        painter = rememberAsyncImagePainter(model),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                DetailLine("시리즈", g.series.ifBlank { "미분류" })
                DetailLine("캐릭터", charaNames(g.chara).joinToString(", "))
                DetailLine("카테고리", g.category)
                DetailLine("가격", g.price)
                DetailLine("메모", g.memo)
                val (label, color) = when (g.status) {
                    GoodsStatus.GOTTEN -> "보유 · ${g.quantity}개" to AppStyles.colorGotten
                    GoodsStatus.PENDING -> "구매예정 · ${g.quantity}개" to AppStyles.colorPending
                    GoodsStatus.NOT_GOTTEN -> "미보유" to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row {
                    Text("상태", style = AppStyles.textCardSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(52.dp))
                    Text(label, style = AppStyles.textCardSmall, fontWeight = FontWeight.Bold, color = color)
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row {
        Text(label, style = AppStyles.textCardSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(52.dp))
        Text(value.ifBlank { "-" }, style = AppStyles.textCardSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
