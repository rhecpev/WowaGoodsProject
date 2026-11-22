package com.example.wowagoodsproject.component

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.background
import coil.compose.rememberAsyncImagePainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.ui.theme.AppStyles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 캐릭터마다 사진에서 뽑은 대표색. [order] 앞의 캐릭터부터 서로 헷갈리지 않게 정한다([assignDistinctColors]).
 * 사진은 작게 읽어 Palette 로 뽑은 색들을 사진에서 많이 쓰인 순서로 후보로 삼는다.
 * 다만 흰·검·회색(배경인 경우가 많다)은 색이 있는 후보들 뒤로 미룬다.
 * 사진을 못 읽은 캐릭터는 이름으로 색을 정한다. 다 정하기 전까지는 빈 지도다.
 */
@Composable
fun rememberCharaColors(order: List<String>, charas: List<CharaEntity>): State<Map<String, Color>> {
    val context = LocalContext.current
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val urls = charas.associate { it.charaNm to it.charaUrl }
    return produceState(initialValue = emptyMap(), order, urls, darkTheme) {
        value = withContext(Dispatchers.IO) {
            val candidates = order.associateWith { name ->
                val url = urls[name].orEmpty()
                if (url.isEmpty()) return@associateWith emptyList()
                runCatching {
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        .size(96)
                        .allowHardware(false) // Palette 가 픽셀을 읽으려면 소프트웨어 비트맵이어야 한다
                        .build()
                    val bitmap = ((context.imageLoader.execute(request) as? SuccessResult)
                        ?.drawable as? BitmapDrawable)?.bitmap ?: return@runCatching emptyList()
                    val p = Palette.from(bitmap).generate()
                    // 많이 쓰인 색이 먼저. 회색 계열은 색이 있는 후보 뒤로.
                    p.swatches
                        .sortedWith(
                            compareBy<Palette.Swatch> { argbToHsl(it.rgb).s < GREY_SATURATION }
                                .thenByDescending { it.population }
                        )
                        .map { it.rgb }
                        .distinct()
                }.getOrDefault(emptyList())
            }
            assignDistinctColors(order, candidates, darkTheme).mapValues { Color(it.value) }
        }
    }
}

/**
 * 보유 굿즈의 캐릭터 비율 게이지. 가로 막대 하나에 캐릭터 색 구간을 쌓고, 아래에 색 네모 + 이름 + 비율을 늘어놓는다.
 * [CharaShares.top] 의 캐릭터마다 색 구간을 그리고, 남은 수가 있으면 회색 '기타'로 합친다.
 * 색을 다 정하기 전까지는 회색으로 보인다.
 */
@Composable
fun CharaShareGauge(
    shares: CharaShares,
    colors: Map<String, Color>,
    modifier: Modifier = Modifier,
    /** 캐릭터 이름 → 사진 주소. 범례 칸에 사진을 둔다. */
    avatars: Map<String, String> = emptyMap(),
    /**
     * 캐릭터 이름 → (공식, 2차창작) 개수. 주면 범례가 "이름 · 공식 N · 2차창작 N · %" 로 나뉜다(배신 굿즈 마이페이지).
     * null 이면 지금처럼 합계 하나만 보인다(명조 마이페이지).
     */
    breakdown: Map<String, Pair<Int, Int>>? = null
) {
    if (shares.total == 0) return
    // '기타'는 배경과 헷갈리지 않게 중간 회색. (구분선 색 outlineVariant 는 너무 옅어 빈 칸처럼 보였다)
    val othersColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val segments = shares.top.map { Triple(it.name, it.count, colors[it.name] ?: othersColor) } +
            listOfNotNull(
                if (shares.othersCount > 0) Triple(shares.othersLabel, shares.othersCount, othersColor) else null
            )
    fun percent(count: Int) = (count * 100f / shares.total).let { if (it < 1f && count > 0) "<1" else "${it.toInt()}" }

    Column(modifier = modifier.fillMaxWidth()) {
        // 구간 사이 1dp 틈으로 이웃한 비슷한 색도 갈라 보이게 한다.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            segments.forEach { (_, count, color) ->
                Box(
                    modifier = Modifier
                        .weight(count.toFloat())
                        .fillMaxHeight()
                        .background(color)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // 범례는 같은 너비의 칸으로 나눈 표: 칸마다 [색 네모][사진] 이름 ……… 개수 · %.
        // 숫자가 칸 오른쪽 끝에 붙어 위아래 줄의 숫자 열이 맞는다. 칸 수는 폭에 맞춘다(세로 화면 2칸).
        // BoxWithConstraints 는 미리 재는 측정(IntrinsicSize)을 지원하지 않아, 가로 화면 마이페이지의
        // 높이 맞춤 줄(IntrinsicSize.Min) 안에서 앱이 꺼졌다. 그래서 직접 칸을 놓는 레이아웃을 쓴다.
        // '기타' 칸의 공식/2차창작은 묶인 캐릭터들의 합
        val othersBreakdown = breakdown?.let { b ->
            shares.othersNames.fold(0 to 0) { acc, n -> b[n]?.let { (o, f) -> acc.first + o to acc.second + f } ?: acc }
        }
        EqualWidthGrid(
            // 공식/2차창작 두 열이 더 들어가므로 칸을 넓힌다(휴대폰 세로 1칸).
            minCellWidth = if (breakdown != null) LEGEND_CELL_MIN_WIDTH_BREAKDOWN else LEGEND_CELL_MIN_WIDTH,
            maxColumns = 4,
            horizontalGap = 16.dp,
            verticalGap = 10.dp
        ) {
            segments.forEach { (name, count, color) ->
                LegendCell(
                    name = name,
                    countText = "$count",
                    percentText = "${percent(count)}%",
                    color = color,
                    avatar = avatars[name]?.takeIf { it.isNotEmpty() },
                    breakdown = breakdown?.let { b -> b[name] ?: if (name == shares.othersLabel) othersBreakdown else (0 to 0) }
                )
            }
        }
    }
}

/**
 * 자식들을 같은 너비의 칸으로 줄 지어 놓는다. 칸 수 = 폭 / [minCellWidth] (1~[maxColumns]).
 * 폭을 모르는 측정(무한 폭)에서는 2칸으로 본다. 기본 측정 정책이라 IntrinsicSize 측정에도 쓸 수 있다.
 */
@Composable
private fun EqualWidthGrid(
    minCellWidth: Dp,
    maxColumns: Int,
    horizontalGap: Dp,
    verticalGap: Dp,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val hGap = horizontalGap.roundToPx()
        val vGap = verticalGap.roundToPx()
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else minCellWidth.roundToPx() * 2
        val columns = (width / minCellWidth.roundToPx()).coerceIn(1, maxColumns)
        val cellWidth = ((width - hGap * (columns - 1)) / columns).coerceAtLeast(0)
        val placeables = measurables.map { it.measure(Constraints.fixedWidth(cellWidth)) }
        val rows = placeables.chunked(columns)
        val rowHeights = rows.map { row -> row.maxOf { it.height } }
        val height = rowHeights.sum() + vGap * (rows.size - 1).coerceAtLeast(0)
        layout(width, height) {
            var y = 0
            rows.forEachIndexed { r, row ->
                row.forEachIndexed { c, p ->
                    p.placeRelative(c * (cellWidth + hGap), y + (rowHeights[r] - p.height) / 2)
                }
                y += rowHeights[r] + vGap
            }
        }
    }
}

/**
 * 범례 칸 하나의 최소 너비. 이보다 좁아지면 한 줄 칸 수를 줄인다.
 * 칸에는 색 선·사진·개수·퍼센트만으로 약 117dp 가 들어가므로, 이름 자리를 남기려면 180dp 는 있어야 한다
 * (150dp 였을 때 태블릿 세로에서 4칸이 되어 이름이 "카…" 처럼 잘렸다). 휴대폰 세로 2칸, 태블릿 세로 3칸.
 */
private val LEGEND_CELL_MIN_WIDTH = 180.dp

/** 공식/2차창작 열이 있는 범례 칸의 최소 너비 */
private val LEGEND_CELL_MIN_WIDTH_BREAKDOWN = 280.dp

@Composable
private fun LegendCell(
    name: String,
    countText: String,
    percentText: String,
    color: Color,
    avatar: String?,
    modifier: Modifier = Modifier,
    /** (공식, 2차창작). 있으면 합계 열 대신 두 열로 보인다. */
    breakdown: Pair<Int, Int>? = null
) {
    // 소식·세트 목록처럼 칸 왼쪽에 그 캐릭터 색의 세로선. 색 표시와 칸 경계를 함께 맡는다.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        // 사진은 캐릭터 칸에만 둔다. '누구 외 몇 명' 칸은 사진 자리를 비우지 않아야
        // 좁은 세로 화면(칸 약 180dp)에서도 "구원 외 19명" 이 잘리지 않고 다 들어간다.
        if (avatar != null) {
            LegendAvatar(url = avatar)
            Spacer(modifier = Modifier.width(6.dp))
        }
        // "구원 외 12명" 처럼 묶인 칸은 칸이 좁아도 '외 N명' 이 잘리지 않게 이름만 말줄임한다.
        val others = Regex("^(.*) (외 \\d+명)$").find(name)
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = others?.groupValues?.get(1) ?: name,
                style = AppStyles.textCardSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (others != null) {
                Text(
                    text = " " + others.groupValues[2],
                    style = AppStyles.textCardSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        // 개수·퍼센트를 각자 고정 너비의 오른쪽 정렬 열로 두고 숫자 폭을 같게(tabular) 해,
        // 위아래 칸의 자릿수가 달라도(1092 / 9, 50% / 1%) 끝자리가 한 줄로 맞는다.
        if (breakdown != null) {
            BreakdownNumber(label = "공식", value = breakdown.first, width = OFFICIAL_COLUMN_WIDTH)
            BreakdownNumber(label = "2차창작", value = breakdown.second, width = FAN_COLUMN_WIDTH)
        } else {
            Text(
                text = countText,
                style = NumberStyle,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .width(COUNT_COLUMN_WIDTH)
                    .padding(vertical = 4.dp)
            )
        }
        Text(
            text = percentText,
            style = NumberStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.width(PERCENT_COLUMN_WIDTH)
        )
    }
}

/** 범례의 "공식 12" / "2차창작 3" 한 열. 이름표는 옅게, 숫자는 굵게, 오른쪽 정렬 */
@Composable
private fun BreakdownNumber(label: String, value: Int, width: androidx.compose.ui.unit.Dp) {
    Row(
        modifier = Modifier.width(width).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = AppStyles.textCardSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            "$value",
            style = NumberStyle,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** 공식/2차창작 열 너비: 이름표 + 네 자리 숫자 */
private val OFFICIAL_COLUMN_WIDTH = 64.dp
private val FAN_COLUMN_WIDTH = 82.dp

/** 숫자 열 너비: 개수는 네 자리(9999), 퍼센트는 '100%' 가 들어가는 폭 */
private val COUNT_COLUMN_WIDTH = 36.dp
private val PERCENT_COLUMN_WIDTH = 38.dp

/** 모든 숫자가 같은 폭(tabular figures)인 작은 글씨 */
private val NumberStyle = AppStyles.textCardSmall.copy(fontFeatureSettings = "tnum")

/** 범례의 20dp 동그란 캐릭터 사진 */
@Composable
private fun LegendAvatar(url: String) {
    Image(
        painter = rememberAsyncImagePainter(model = url),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

/**
 * B안: 선호 캐릭터 수집 현황 목록. 캐릭터별 탭처럼 사진 + 이름 + 진행 막대 + 보유/전체 + 퍼센트.
 * 순서는 선호 우선순위. 퍼센트는 버림이라 다 모았을 때만 100%(초록).
 */
@Composable
fun FavoriteCollectionList(
    favorites: List<CharaEntity>,
    collection: Map<String, Pair<Int, Int>>,
    colors: Map<String, Color>,
    modifier: Modifier = Modifier,
    /** 목록 제목. 명조는 공식 굿즈로만 세므로 '공식'을 붙인다. */
    title: String = "선호 캐릭터 공식 수집현황"
) {
    if (favorites.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        favorites.forEach { chara ->
            val (gotten, total) = collection[chara.charaNm] ?: (0 to 0)
            val fraction = if (total > 0) gotten.toFloat() / total else 0f
            val complete = total > 0 && gotten == total
            val barColor = if (complete) AppStyles.colorGotten
            else colors[chara.charaNm] ?: MaterialTheme.colorScheme.primary
            Row(verticalAlignment = Alignment.CenterVertically) {
                LegendAvatar(url = chara.charaUrl)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = chara.charaNm,
                    style = AppStyles.textCardSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(72.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                // 2dp 바탕선 위에 캐릭터 색 4dp 막대(앱의 선 스타일)
                Box(modifier = Modifier.weight(1f).height(6.dp), contentAlignment = Alignment.CenterStart) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(4.dp)
                            .background(barColor)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$gotten/$total",
                    style = NumberStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(56.dp)
                )
                Text(
                    text = "${(fraction * 100).toInt()}%",
                    style = NumberStyle,
                    fontWeight = FontWeight.Bold,
                    color = if (complete) AppStyles.colorGotten else MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(PERCENT_COLUMN_WIDTH)
                )
            }
        }
    }
}
