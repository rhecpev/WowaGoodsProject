package com.example.wowagoodsproject.screen.mypage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.wowagoodsproject.component.CharaAvatar
import com.example.wowagoodsproject.component.CharaShareGauge
import com.example.wowagoodsproject.component.FavoriteCollectionList
import com.example.wowagoodsproject.component.GoodsItem
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.charaShares
import com.example.wowagoodsproject.component.rememberCharaColors
import com.example.wowagoodsproject.db.betray.charaNames
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.ui.theme.AppStyles

/*
 * 불러오기 확인의 Before / After 표와, 파일 기준 보유 현황 그래프.
 */

/** 캐릭터 한 줄의 지금(before) → 파일(after) 개수 */
data class DiffRow(val name: String, val avatar: String, val before: Int, val after: Int) {
    val diff get() = after - before
}

/** 선호 캐릭터 한 줄: 지금 선호인지 → 불러오면 선호인지 */
data class FavRow(val name: String, val avatar: String, val before: Boolean, val after: Boolean)

/** 배신 굿즈 한 장르의 Before / After */
data class GenreDiff(val name: String, val note: String, val rows: List<DiffRow>, val favRows: List<FavRow>)

/** 파일 기준 보유 현황(마이페이지 '보유 굿즈' 카드와 같은 모양) */
data class FileGraph(
    val title: String,
    /** 보유 굿즈(게이지·총 개수) */
    val gotten: List<GoodsItem>,
    /** 사진·색에 쓰는 캐릭터 목록 */
    val charas: List<CharaEntity>,
    /** 선호 우선순위 순 이름 */
    val favorites: List<String>,
    /** 이름 → (보유, 전체) */
    val collection: Map<String, Pair<Int, Int>>,
    val collectionTitle: String,
    /** 이름 → (공식, 2차창작). 배신 굿즈만 */
    val breakdown: Map<String, Pair<Int, Int>>? = null,
    /** 배신 굿즈 장르 그래프면 true(명조와 색을 달리한다) */
    val betray: Boolean = false,
    /** 배신 굿즈 장르 이름. 제목 앞에 다른 색으로 붙는다 */
    val genreName: String = ""
)

/** 단계 맨 위 제목: [group](명조 / 배신 굿즈) 색 띠 + 단계 이름 */
@Composable
internal fun StepTitle(group: String, title: String, betray: Boolean) {
    // 가로 화면은 왼쪽 단계 칸에 같은 이름이 있으니 띠를 그리지 않는다(내용이 높이를 쓰게)
    if (isWideLayout()) return
    val color = groupColor(betray)
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(color.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(width = 4.dp, height = 18.dp).background(color))
            Spacer(modifier = Modifier.width(8.dp))
            Text(group, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
        HorizontalDivider(thickness = 2.dp, color = color)
    }
}

/** 명조 / 배신 굿즈 색. 제목·구분선에 쓴다. */
@Composable
internal fun groupColor(betray: Boolean): androidx.compose.ui.graphics.Color {
    val scheme = MaterialTheme.colorScheme
    if (!betray) return scheme.primary
    // 밝은 테마들은 tertiary 를 연한 배경용 색으로 쓰므로 그대로 글자에 쓰면 안 보인다.
    // 같은 색 계열을 두고, 바탕과 충분히 구분될 때까지 글자색 쪽으로 진하게 섞는다(어느 테마든 읽힌다).
    return androidx.compose.runtime.remember(scheme.tertiary, scheme.surface, scheme.onSurface) {
        readableOn(scheme.tertiary, scheme.surface, scheme.onSurface)
    }
}

/** [color] 가 [background] 위에서 대비 [minContrast] 이상이 될 때까지 [toward] 쪽으로 섞는다 */
internal fun readableOn(
    color: androidx.compose.ui.graphics.Color,
    background: androidx.compose.ui.graphics.Color,
    toward: androidx.compose.ui.graphics.Color,
    minContrast: Float = 4.5f
): androidx.compose.ui.graphics.Color {
    fun contrast(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Float {
        val la = a.luminance(); val lb = b.luminance()
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }
    var c = color
    var t = 0f
    while (contrast(c, background) < minContrast && t < 1f) {
        t += 0.1f
        c = androidx.compose.ui.graphics.lerp(color, toward, t.coerceAtMost(1f))
    }
    return c
}

/** 명조 / 배신 굿즈 묶음 머리: 굵은 색 띠 + 이름 */
@Composable
internal fun GroupHeader(title: String, betray: Boolean, modifier: Modifier = Modifier) {
    val color = groupColor(betray)
    Column(modifier = modifier.fillMaxWidth().padding(top = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(color.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(width = 4.dp, height = 18.dp).background(color))
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        }
        HorizontalDivider(thickness = 2.dp, color = color)
    }
}

/** 이름별로 센 두 묶음을 견줘, 달라진 캐릭터만 변화가 큰 순서로 */
internal fun diffRows(before: Map<String, Int>, after: Map<String, Int>, avatar: (String) -> String): List<DiffRow> =
    (before.keys + after.keys).distinct()
        .map { DiffRow(it, avatar(it), before[it] ?: 0, after[it] ?: 0) }
        .filter { it.diff != 0 }
        .sortedWith(compareByDescending<DiffRow> { kotlin.math.abs(it.diff) }.thenBy { it.name })

/** 굿즈 캐릭터 칸으로 센다(여러 명이면 한 명씩 한 번) */
internal fun <T> countByChara(items: List<T>, chara: (T) -> String): Map<String, Int> {
    val m = mutableMapOf<String, Int>()
    items.forEach { g -> charaNames(chara(g)).forEach { m[it] = (m[it] ?: 0) + 1 } }
    return m
}

/** 굿즈 한 개의 Before / After 상태. null 이면 그쪽에 없음(새로 생김 / 없어짐) */
data class GoodsChangeRow(val name: String, val sub: String, val avatar: String, val before: GoodsStatus?, val after: GoodsStatus?)

/**
 * 굿즈를 내용([key]: 상태를 뺀 시리즈·캐릭터·종류 등)으로 짝지어, 상태가 바뀌거나 생기거나 없어지는 굿즈만 고른다.
 * 같은 내용 굿즈가 여러 개면 상태가 같은 것끼리 먼저 짝짓는다. 순서: 상태 바뀜 → 새로 생김 → 없어짐.
 */
internal fun <T> goodsChanges(
    before: List<T>,
    after: List<T>,
    key: (T) -> String,
    status: (T) -> GoodsStatus,
    row: (item: T, before: GoodsStatus?, after: GoodsStatus?) -> GoodsChangeRow
): List<GoodsChangeRow> {
    val changed = mutableListOf<GoodsChangeRow>()
    val added = mutableListOf<GoodsChangeRow>()
    val removed = mutableListOf<GoodsChangeRow>()
    val b = before.groupBy(key)
    val a = after.groupBy(key)
    (b.keys + a.keys).distinct().forEach { k ->
        val restB = b[k].orEmpty().toMutableList()
        val restA = mutableListOf<T>()
        a[k].orEmpty().forEach { item ->
            val i = restB.indexOfFirst { status(it) == status(item) }
            if (i >= 0) restB.removeAt(i) else restA += item
        }
        val pairs = minOf(restB.size, restA.size)
        for (i in 0 until pairs) changed += row(restA[i], status(restB[i]), status(restA[i]))
        restA.drop(pairs).forEach { added += row(it, null, status(it)) }
        restB.drop(pairs).forEach { removed += row(it, status(it), null) }
    }
    return changed + added + removed
}

/** 선호 캐릭터 Before / After. 파일 순서대로, 그다음 파일에서 빠지는 캐릭터 */
internal fun favRows(before: List<String>, after: List<String>, avatar: (String) -> String): List<FavRow> =
    favRows(before, after, emptyList(), avatar)

/** [others] 는 그 밖의 캐릭터. 지금도 파일도 선호가 아니면 뒤에 붙는다(화면에서는 '기타'로 접는다). */
internal fun favRows(before: List<String>, after: List<String>, others: List<String>, avatar: (String) -> String): List<FavRow> {
    val b = before.toSet(); val a = after.toSet()
    return (after + before.filter { it !in a } + others.filter { it !in a && it !in b }).distinct().map { FavRow(it, avatar(it), it in b, it in a) }
}

private val NUM_WIDTH = 52.dp
private val DIFF_WIDTH = 48.dp

/** 표 머리: 왼쪽 Before, 오른쪽 After */
@Composable
internal fun DiffHeader(label: String) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = AppStyles.textCardSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(vertical = 6.dp))
        ZoneCell(before = true) { Text("Before", style = AppStyles.textCardSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(modifier = Modifier.width(DIFF_WIDTH))
        ZoneCell(before = false) { Text("After", style = AppStyles.textCardSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
    }
}

/** Before / After 칸. 위아래 줄이 이어져 구역처럼 보이도록 칸 높이를 꽉 채워 칠한다. */
@Composable
private fun ZoneCell(before: Boolean, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .width(NUM_WIDTH)
            .fillMaxHeight()
            .background(
                if (before) MaterialTheme.colorScheme.surfaceContainerHighest
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            ),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun NameCell(name: String, avatar: String, modifier: Modifier) {
    Row(modifier = modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        CharaAvatar(avatar, 28)
        Spacer(modifier = Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 캐릭터 | Before | ±수치 | After */
@Composable
internal fun DiffTable(label: String, rows: List<DiffRow>) {
    if (rows.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        DiffHeader(label)
        rows.forEach { DiffRowItem(it) }
    }
}

/** 캐릭터 | Before | ±수치 | After 한 줄 */
@Composable
internal fun DiffRowItem(r: DiffRow) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        NameCell(r.name, r.avatar, Modifier.weight(1f))
        ZoneCell(before = true) { Text("${r.before}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(
            if (r.diff > 0) "+${r.diff}" else "${r.diff}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (r.diff > 0) AppStyles.colorGotten else MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(DIFF_WIDTH)
        )
        ZoneCell(before = false) { Text("${r.after}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold) }
    }
}

/**
 * 항목 요약의 Before / After. 한 줄로 이으면 글자가 잘려서 두 칸으로 나누고, 칸 안에서는 줄을 바꿔 다 보인다.
 */
@Composable
internal fun SummaryBeforeAfter(now: String, file: String) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        SummaryCell("Before · 지금", now, before = true, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp).size(18.dp)
        )
        SummaryCell("After · 파일", file, before = false, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SummaryCell(label: String, value: String, before: Boolean, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(
                if (before) MaterialTheme.colorScheme.surfaceContainerHighest
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = AppStyles.textCardSmall,
            fontWeight = FontWeight.Bold,
            color = if (before) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = if (before) FontWeight.Normal else FontWeight.Bold)
    }
}

/** 굿즈 | Before 상태 | → | After 상태. 많으면 앞의 [limit]개만 보이고 나머지는 개수로 */
@Composable
internal fun GoodsChangeTable(label: String, rows: List<GoodsChangeRow>, limit: Int = 50) {
    if (rows.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        DiffHeader(label)
        rows.take(limit).forEach { GoodsChangeRowItem(it) }
        if (rows.size > limit) {
            Text("외 ${rows.size - limit}개", style = AppStyles.textCardSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** 굿즈 | Before 상태 | → | After 상태 한 줄 */
@Composable
internal fun GoodsChangeRowItem(r: GoodsChangeRow) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = Modifier.weight(1f).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            CharaAvatar(r.avatar, 28)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(r.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (r.sub.isNotBlank()) {
                    Text(r.sub, style = AppStyles.textCardSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        ZoneCell(before = true) { StatusText(r.before, strong = false) }
        Box(modifier = Modifier.width(DIFF_WIDTH), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        ZoneCell(before = false) { StatusText(r.after, strong = true) }
    }
}

@Composable
private fun StatusText(status: GoodsStatus?, strong: Boolean) {
    val (text, color) = when (status) {
        null -> "없음" to MaterialTheme.colorScheme.outline
        GoodsStatus.GOTTEN -> "보유" to AppStyles.colorGotten
        GoodsStatus.PENDING -> "구매예정" to AppStyles.colorPending
        GoodsStatus.NOT_GOTTEN -> "미보유" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text,
        style = AppStyles.textCardSmall,
        fontWeight = if (strong && status != null) FontWeight.Bold else FontWeight.Normal,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1
    )
}

/** 캐릭터 | Before 하트 | → | After 하트. 선호면 꽉 찬 하트, 아니면 빈 하트 */
@Composable
internal fun FavTable(label: String, rows: List<FavRow>) {
    if (rows.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        DiffHeader(label)
        rows.forEach { FavRowItem(it) }
    }
}

/** 캐릭터 | Before 하트 | → | After 하트 한 줄 */
@Composable
internal fun FavRowItem(r: FavRow) {
    val changed = r.before != r.after
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        NameCell(r.name, r.avatar, Modifier.weight(1f))
        ZoneCell(before = true) { Heart(r.before) }
        Box(modifier = Modifier.width(DIFF_WIDTH), contentAlignment = Alignment.Center) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = if (changed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        ZoneCell(before = false) { Heart(r.after) }
    }
}

@Composable
private fun Heart(on: Boolean) {
    Icon(
        if (on) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
        contentDescription = if (on) "선호" else "비선호",
        tint = if (on) AppStyles.colorFavorite else MaterialTheme.colorScheme.outline,
        modifier = Modifier.size(20.dp)
    )
}

/** 배신 굿즈 장르별 Before / After */
@Composable
internal fun GenreDiffBlock(g: GenreDiff) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(g.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(g.note, style = AppStyles.textCardSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DiffTable("굿즈", g.rows)
        FavTable("선호 캐릭터", g.favRows)
    }
}

/** 파일 기준 '보유 굿즈' 카드: 총 개수 · 캐릭터 비율 게이지 · 선호 캐릭터 수집 현황 */
@Composable
internal fun FileGraphCard(graph: FileGraph) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                // 장르 이름(배신 굿즈 색) · '보유 굿즈'(기본 글자색) · 개수(강조색)를 서로 다른 색으로
                if (graph.genreName.isNotEmpty()) {
                    Text(graph.genreName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = groupColor(true))
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(graph.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.width(8.dp))
                Text("${graph.gotten.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(12.dp))
            val shares = remember(graph) { charaShares(graph.gotten, graph.favorites, onlyFavorites = true) }
            val colorOrder = remember(shares, graph) {
                val topNames = shares.top.map { it.name }
                graph.favorites.filter { it in topNames } + topNames.filterNot { it in graph.favorites }
            }
            val colors by rememberCharaColors(colorOrder, graph.charas)
            val avatars = remember(graph) { graph.charas.associate { it.charaNm to it.charaUrl } }
            val favs = graph.favorites.map { n -> graph.charas.find { it.charaNm == n } ?: CharaEntity(charaId = 0, charaNm = n) }
            val gauge: @Composable () -> Unit = {
                if (shares.total == 0) {
                    Text("보유 굿즈가 없습니다", style = AppStyles.textCardSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                CharaShareGauge(shares = shares, colors = colors, avatars = avatars, breakdown = graph.breakdown)
            }
            val collection: @Composable () -> Unit = {
                FavoriteCollectionList(favorites = favs, collection = graph.collection, colors = colors, title = graph.collectionTitle)
            }
            // 넓으면(가로 화면) 게이지는 왼쪽, 선호 캐릭터 수집은 오른쪽에 나란히. 좁으면 위아래로.
            androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (maxWidth >= 640.dp && favs.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        Column(modifier = Modifier.weight(1f)) { gauge() }
                        Column(modifier = Modifier.weight(1f)) { collection() }
                    }
                } else {
                    Column {
                        gauge()
                        if (favs.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(20.dp))
                            collection()
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
