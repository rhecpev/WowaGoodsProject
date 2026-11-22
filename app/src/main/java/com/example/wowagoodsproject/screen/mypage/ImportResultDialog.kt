package com.example.wowagoodsproject.screen.mypage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.component.CATEGORY_SET
import com.example.wowagoodsproject.component.CharaAvatar
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.ui.theme.AppStyles
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/*
 * 데이터 입력(불러오기·복구) 결과 보고서.
 * 불러오기 직전/직후 기기 상태를 견줘, 요약 숫자 → 항목별 Before/After 막대 → 캐릭터별 보유 변화 → 장르별 막대 순으로 보인다.
 */

/** 결과 보고서용 기기 상태 한 장 */
data class ImportSnapshot(
    val officialGotten: Int = 0,
    val officialPending: Int = 0,
    val fanTotal: Int = 0,
    val fanGotten: Int = 0,
    val fanPending: Int = 0,
    /** 명조 비공식 시리즈 수 */
    val fanSeries: Int = 0,
    val favorites: Int = 0,
    val betrayGoods: Int = 0,
    val betrayGotten: Int = 0,
    val betrayPending: Int = 0,
    /** 배신 굿즈 장르 이름 → 굿즈 수 */
    val betrayByGenre: Map<String, Int> = emptyMap(),
    /** 명조 캐릭터 → 보유 굿즈 수(공식 낱개 + 비공식) */
    val gottenByChara: Map<String, Int> = emptyMap(),
    /** 명조 캐릭터 사진 */
    val avatars: Map<String, String> = emptyMap()
) {
    val gottenTotal get() = officialGotten + fanGotten + betrayGotten
    val pendingTotal get() = officialPending + fanPending + betrayPending

    companion object {
        suspend fun take(): ImportSnapshot {
            val official = App.database.goodsDao().getAll().filter { it.goodsCategory != CATEGORY_SET }
            val fan = App.fanDatabase.fanGoodsDao().getAll()
            val charas = App.charaDatabase.charaDao().getAll()
            val dao = App.betrayDatabase.betrayDao()
            val betray = dao.allGoods()
            val genreNames = dao.genres().associate { it.genreId to it.genreNm.trim() }
            val gotten = official.filter { it.status == GoodsStatus.GOTTEN }
            val fanGotten = fan.filter { it.status == GoodsStatus.GOTTEN }
            val byChara = countByChara(gotten) { it.goodsChara }.toMutableMap()
            countByChara(fanGotten) { it.fanGoodsChara }.forEach { (k, v) -> byChara[k] = (byChara[k] ?: 0) + v }
            return ImportSnapshot(
                officialGotten = gotten.size,
                officialPending = official.count { it.status == GoodsStatus.PENDING },
                fanTotal = fan.size,
                fanGotten = fanGotten.size,
                fanPending = fan.count { it.status == GoodsStatus.PENDING },
                fanSeries = App.fanDatabase.fanGoodsDao().allSeries().size,
                favorites = charas.count { it.charaIsFavorite },
                betrayGoods = betray.size,
                betrayGotten = betray.count { it.status == GoodsStatus.GOTTEN },
                betrayPending = betray.count { it.status == GoodsStatus.PENDING },
                betrayByGenre = genreNames.values.associateWith { 0 } +
                    betray.groupingBy { genreNames[it.genreId].orEmpty() }.eachCount().filterKeys { it.isNotEmpty() },
                gottenByChara = byChara,
                avatars = charas.associate { it.charaNm to it.charaUrl }
            )
        }
    }
}

/** 보고서 한 줄: Before → After */
data class ReportStat(val label: String, val before: Int, val after: Int, val unit: String, val betray: Boolean = false) {
    val diff get() = after - before
}

/** 캐릭터 한 줄: 보유 Before → After */
data class CharaChange(val name: String, val avatar: String, val before: Int, val after: Int) {
    val diff get() = after - before
}

/** 보고서 내용. 불러온 항목만 넣는다. */
data class ImportReportData(
    val summary: List<ReportStat>,
    val parts: List<ReportStat>,
    val charas: List<CharaChange>,
    val genres: List<ReportStat>
)

/** [report] 를 보고서 표로. 불러오기 앞뒤 상태가 없으면 null */
internal fun reportDataOf(report: RestoreReport, charaLimit: Int = 5): ImportReportData? {
    val b = report.before ?: return null
    val a = report.after ?: return null
    val wuwaGoods = report.ownedInBackup != null || report.fanGoods != null
    val summary = listOfNotNull(
        ReportStat("보유 굿즈", b.gottenTotal, a.gottenTotal, "개"),
        ReportStat("구매예정", b.pendingTotal, a.pendingTotal, "개"),
        if (report.favorites != null) ReportStat("선호 캐릭터", b.favorites, a.favorites, "명") else null
    )
    val parts = buildList {
        if (report.ownedInBackup != null) {
            add(ReportStat("명조 공식 보유", b.officialGotten, a.officialGotten, "개"))
            add(ReportStat("명조 공식 구매예정", b.officialPending, a.officialPending, "개"))
        }
        if (report.fanGoods != null) {
            add(ReportStat("명조 비공식 굿즈", b.fanTotal, a.fanTotal, "개"))
            add(ReportStat("명조 비공식 보유", b.fanGotten, a.fanGotten, "개"))
        }
        if (report.fanSeries != null) add(ReportStat("명조 비공식 시리즈", b.fanSeries, a.fanSeries, "개"))
        if (report.favorites != null) add(ReportStat("명조 선호 캐릭터", b.favorites, a.favorites, "명"))
        if (report.betrayGoods != null) {
            add(ReportStat("배신 굿즈", b.betrayGoods, a.betrayGoods, "개", betray = true))
            add(ReportStat("배신 굿즈 보유", b.betrayGotten, a.betrayGotten, "개", betray = true))
        }
    }
    val charas = if (!wuwaGoods) emptyList() else (b.gottenByChara.keys + a.gottenByChara.keys).distinct()
        .map { CharaChange(it, a.avatars[it] ?: b.avatars[it].orEmpty(), b.gottenByChara[it] ?: 0, a.gottenByChara[it] ?: 0) }
        .filter { it.diff != 0 }
        .sortedWith(compareByDescending<CharaChange> { kotlin.math.abs(it.diff) }.thenByDescending { it.after }.thenBy { it.name })
        .take(charaLimit)
    val genres = report.betrayGenres.orEmpty().distinct().map { n ->
        ReportStat(n, b.betrayByGenre[n] ?: 0, a.betrayByGenre[n] ?: 0, "개", betray = true)
    }
    return ImportReportData(summary, parts, charas, genres)
}

/** 데이터 입력이 끝났을 때 띄울 결과 보고서. 어느 화면에서 불러와도 앱 맨 위에서 한 번 띄운다. */
object ImportResultBus {
    data class Result(val title: String, val report: RestoreReport)

    val result = MutableStateFlow<Result?>(null)

    fun show(report: RestoreReport, title: String = "불러오기 보고서") {
        result.value = Result(title, report)
    }
}

/** 앱 맨 위(MainScreen)에 둔다. 결과가 있으면 보고서를 띄운다. */
@Composable
fun ImportResultHost() {
    val result by ImportResultBus.result.collectAsState()
    result?.let { ImportResultDialog(it.title, it.report, onDismiss = { ImportResultBus.result.value = null }) }
}

@Composable
fun ImportResultDialog(title: String, report: RestoreReport, onDismiss: () -> Unit) {
    val data = remember(report) { reportDataOf(report) }
    val time = remember { java.text.SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.KOREA).format(java.util.Date()) }
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    // 가로 화면: 창을 넓게, 머리 한 줄에 제목·시각·확인, 내용은 두 칸(높이가 낮아 한 줄로 쌓으면 거의 안 보인다)
    val wide = config.screenWidthDp > config.screenHeightDp
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = if (wide) Modifier.fillMaxWidth(0.94f).widthIn(max = 1100.dp).fillMaxHeight(0.94f)
            else Modifier.fillMaxWidth(0.94f).widthIn(max = 560.dp).fillMaxHeight(0.9f)
        ) {
            // 막대가 차례로 차오르도록 순번을 매긴다(두 칸으로 나눠도 위에서부터 차례로).
            var order = 0
            val summary: @Composable () -> Unit = {
                if (data != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    data.summary.forEach { SummaryTile(it, Modifier.weight(1f)) }
                }
            }
            val parts: @Composable () -> Unit = {
                if (data != null && data.parts.isNotEmpty()) {
                    ReportSection("항목별 변화")
                    val max = data.parts.maxOf { maxOf(it.before, it.after) }.coerceAtLeast(1)
                    data.parts.forEach { StatBars(it, max, order++) }
                }
            }
            val charas: @Composable () -> Unit = {
                if (data != null && data.charas.isNotEmpty()) {
                    ReportSection("캐릭터별 보유 변화 TOP ${data.charas.size}")
                    val max = data.charas.maxOf { maxOf(it.before, it.after) }.coerceAtLeast(1)
                    data.charas.forEachIndexed { i, c -> CharaBar(i + 1, c, max, order++) }
                }
            }
            val genres: @Composable () -> Unit = {
                if (data != null && data.genres.isNotEmpty()) {
                    ReportSection("배신 굿즈 장르별 굿즈 수", betray = true)
                    val max = data.genres.maxOf { maxOf(it.before, it.after) }.coerceAtLeast(1)
                    data.genres.forEach { StatBars(it, max, order++) }
                }
            }
            val missing: @Composable () -> Unit = {
                if (report.missing > 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "공식 굿즈 ${report.missing}개는 이 기기 굿즈 목록에 없어 건너뛰었어요",
                            style = AppStyles.textCardSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            if (wide) {
                Column {
                    // 머리 한 줄: 제목 · 시각 · 확인
                    Row(
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "$time · 불러오기 전과 후",
                            style = AppStyles.textCardSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = onDismiss,
                            shape = RectangleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onPrimary,
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) { Text("확인", fontWeight = FontWeight.Bold) }
                    }
                    if (data == null) {
                        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
                            FallbackLines(report)
                            missing()
                        }
                    } else {
                        val hasRight = data.charas.isNotEmpty() || data.genres.isNotEmpty()
                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            // 왼쪽: 요약 숫자 · 항목별 변화
                            Column(
                                modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                                    .padding(start = 20.dp, end = if (hasRight) 12.dp else 20.dp, top = 12.dp, bottom = 12.dp)
                            ) {
                                summary(); parts()
                                if (!hasRight) missing()
                            }
                            // 오른쪽: 캐릭터 TOP · 장르별(없으면 왼쪽 칸만)
                            if (hasRight) {
                                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Column(
                                    modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                                        .padding(start = 12.dp, end = 20.dp, top = 0.dp, bottom = 12.dp)
                                ) {
                                    charas(); genres(); missing()
                                }
                            }
                        }
                    }
                }
                return@Surface
            }
            Column {
                // 머리: 제목 · 시각
                Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    Text(
                        "$time · 불러오기 전과 후",
                        style = AppStyles.textCardSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    if (data == null) FallbackLines(report) else { summary(); parts(); charas(); genres() }
                    missing()
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Button(
                    onClick = onDismiss,
                    shape = RectangleShape,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(48.dp)
                ) { Text("확인", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun ReportSection(title: String, betray: Boolean = false) {
    val color = groupColor(betray)
    Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(thickness = 2.dp, color = color)
    }
}

/** 요약 칸: 큰 After 숫자 + 증감 */
@Composable
private fun SummaryTile(s: ReportStat, modifier: Modifier) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 10.dp, vertical = 10.dp)) {
        Text(s.label, style = AppStyles.textCardSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${s.after}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(s.unit, style = AppStyles.textCardSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp, bottom = 3.dp))
        }
        DiffText(s.diff)
    }
}

@Composable
private fun DiffText(diff: Int) {
    val (text, color) = when {
        diff > 0 -> "▲ $diff" to AppStyles.colorGotten
        diff < 0 -> "▼ ${-diff}" to MaterialTheme.colorScheme.error
        else -> "변화 없음" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, style = AppStyles.textCardSmall, fontWeight = FontWeight.Bold, color = color)
}

/** 순번에 맞춰 0 → [target] 으로 차오르는 값 */
@Composable
private fun rememberGrow(target: Float, order: Int): Float {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target) {
        delay(120L * order)
        anim.animateTo(target, tween(durationMillis = 650, easing = FastOutSlowInEasing))
    }
    return anim.value
}

/** 한 항목: 이름 · Before → After 숫자 · 증감, 그 아래 Before(옅게) / After(진하게) 막대 */
@Composable
private fun StatBars(s: ReportStat, max: Int, order: Int) {
    val color = groupColor(s.betray)
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${s.before} → ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${s.after}${s.unit}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.widthIn(min = 56.dp), contentAlignment = Alignment.CenterEnd) { DiffText(s.diff) }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Bar(fraction = rememberGrow(s.before.toFloat() / max, order), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), height = 6)
        Spacer(modifier = Modifier.height(3.dp))
        Bar(fraction = rememberGrow(s.after.toFloat() / max, order), color = color, height = 12)
    }
}

@Composable
private fun Bar(fraction: Float, color: Color, height: Int) {
    Box(modifier = Modifier.fillMaxWidth().height(height.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Box(modifier = Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(color))
    }
}

/** 캐릭터 한 줄: 순위 · 사진 · 이름 · 막대(Before 까지는 기본색, 늘어난 만큼 초록 / 줄어든 만큼 빨강 테두리) · 증감 */
@Composable
private fun CharaBar(rank: Int, c: CharaChange, max: Int, order: Int) {
    val grow = rememberGrow(1f, order)
    val base = minOf(c.before, c.after).toFloat() / max
    val top = maxOf(c.before, c.after).toFloat() / max
    val changeColor = if (c.diff > 0) AppStyles.colorGotten else MaterialTheme.colorScheme.error
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$rank", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(22.dp))
        CharaAvatar(c.avatar, 32)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${c.before} → ${c.after}", style = AppStyles.textCardSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(3.dp))
            Box(modifier = Modifier.fillMaxWidth().height(10.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                // 늘어나거나 줄어든 구간(위쪽 끝까지)
                Box(modifier = Modifier.fillMaxWidth((top * grow).coerceIn(0f, 1f)).fillMaxHeight().background(changeColor.copy(alpha = if (c.diff > 0) 1f else 0.35f)))
                // 그대로인 구간
                Box(modifier = Modifier.fillMaxWidth((base * grow).coerceIn(0f, 1f)).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.widthIn(min = 48.dp), contentAlignment = Alignment.CenterEnd) { DiffText(c.diff) }
    }
}

/** 앞뒤 상태를 못 읽었을 때: 불러온 개수만 줄로 */
@Composable
private fun FallbackLines(report: RestoreReport) {
    val lines = listOfNotNull(
        report.ownedInBackup?.let { "명조 공식 보유·구매예정" to "${report.restored} / ${it}개" },
        report.fanGoods?.let { "명조 비공식 굿즈" to "${it}개" },
        report.fanSeries?.let { "명조 비공식 시리즈" to "${it}개" },
        report.favorites?.let { "명조 선호 캐릭터" to "${it}명" },
        report.betrayGoods?.let { "배신 굿즈" to "${it}개" }
    )
    lines.forEach { (label, value) ->
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
