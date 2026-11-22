package com.example.wowagoodsproject.screen.mypage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.wowagoodsproject.component.CharaAvatar
import com.example.wowagoodsproject.component.GoodsListItem
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.SearchField
import com.example.wowagoodsproject.component.asGottenStatus
import com.example.wowagoodsproject.ui.theme.AppStyles

/*
 * 불러오기·추출 전 '한 단계씩' 확인하는 화면 조각.
 * 파일 내용 → 선호 캐릭터(맞으면 체크) → 굿즈(캐릭터마다 목록을 보고 체크) → 마지막 확인.
 */

/** 확인 화면에 보여 줄 굿즈 한 줄 */
data class ReviewGoods(
    val official: Boolean,
    val series: String,
    val chara: String,
    val category: String,
    val price: String,
    val memo: String,
    val status: GoodsStatus,
    val quantity: Int,
    val img: String = ""
) {
    val charaNames: List<String>
        get() = chara.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct().ifEmpty { listOf(NO_CHARA) }

    companion object {
        /** 캐릭터 칸이 빈 굿즈를 모으는 이름 */
        const val NO_CHARA = "캐릭터 없음"
    }
}

/** 초록 체크(켜짐) / 빈 원(꺼짐) */
@Composable
internal fun GreenCheck(checked: Boolean, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Icon(
        imageVector = if (checked) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
        contentDescription = contentDescription,
        tint = if (checked) AppStyles.colorGotten else MaterialTheme.colorScheme.outline,
        modifier = modifier
    )
}

/** 누르면 켜고 끄는 넓은 확인 줄 */
@Composable
internal fun CheckConfirmRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (checked) AppStyles.colorGotten.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHigh)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GreenCheck(checked, Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

/** 가로 화면이면 단계 틀이 [왼쪽 단계 칸 | 오른쪽 내용] 으로 나뉜다 */
@Composable
internal fun isWideLayout(): Boolean {
    val c = androidx.compose.ui.platform.LocalConfiguration.current
    return c.screenWidthDp > c.screenHeightDp
}

/** 가로 화면에서 창 제목·닫기를 왼쪽 단계 칸이 그리도록 넘겨 준다(세로는 null: 창이 맨 위에 그린다) */
private class WizardChrome(val title: String, val onDismiss: () -> Unit)
private val LocalWizardChrome = staticCompositionLocalOf<WizardChrome?> { null }

/** 단계식 확인을 띄우는 전체 화면 창 */
@Composable
internal fun WizardScreen(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val wide = isWideLayout()
    // 바텀시트는 끌어내리거나 바깥을 누르면 닫혀 확인하던 내용이 날아간다.
    // 단계식 확인은 전체 화면으로 띄우고, X 단추(또는 첫 단계에서 뒤로)로만 닫는다.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, dismissOnBackPress = true)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 16.dp, top = if (wide) 12.dp else 0.dp)
            ) {
                if (!wide) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "닫기") }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                // 가로 화면: 제목·닫기는 단계 틀의 왼쪽 칸이 그린다(내용이 높이를 다 쓰게).
                CompositionLocalProvider(LocalWizardChrome provides if (wide) WizardChrome(title, onDismiss) else null) {
                    content()
                }
            }
        }
    }
}

/** 가로 화면에서 단계 틀 없이 내용만 보일 때(세는 중 등) 제목과 닫기. 세로에서는 아무것도 그리지 않는다. */
@Composable
internal fun WizardWideHeader() {
    val chrome = LocalWizardChrome.current ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(chrome.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        IconButton(onClick = chrome.onDismiss) { Icon(Icons.Default.Close, contentDescription = "닫기") }
    }
}

/**
 * 단계 틀
 * - 세로: 위에 '단계 이름 · n/전체'와 진행 막대, 가운데 [content], 아래 [이전] [다음/마지막 단추].
 * - 가로: 왼쪽 칸에 창 제목·닫기, 세로 단계 목록(지난 단계는 체크, 지금 단계는 강조, 지난 단계를 누르면 돌아감),
 *   맨 아래 [이전] [다음]. 오른쪽은 [content] 가 높이를 전부 쓴다.
 * @param nextEnabled 이 단계에서 다음(또는 마지막 단추)을 누를 수 있는지
 */
@Composable
internal fun ColumnScope.WizardFrame(
    steps: List<String>,
    step: Int,
    onStep: (Int) -> Unit,
    nextEnabled: Boolean,
    finishLabel: String,
    finishColors: ButtonColors = ButtonDefaults.buttonColors(),
    onFinish: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    // 뒤로 가기는 이전 단계로. 첫 단계에서만 창을 닫는다.
    BackHandler(enabled = step > 0) { onStep(step - 1) }
    val last = step == steps.lastIndex
    val chrome = LocalWizardChrome.current
    if (chrome != null) {
        val railWidth = if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 1000) 260.dp else 210.dp
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(modifier = Modifier.width(railWidth).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(chrome.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, modifier = Modifier.weight(1f))
                    IconButton(onClick = chrome.onDismiss) { Icon(Icons.Default.Close, contentDescription = "닫기") }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    steps.forEachIndexed { i, name -> WizardRailStep(i, name, current = i == step, done = i < step, onClick = if (i < step) ({ onStep(i) }) else null) }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (step > 0) {
                    OutlinedButton(onClick = { onStep(step - 1) }, shape = RectangleShape, modifier = Modifier.fillMaxWidth().height(44.dp)) { Text("이전") }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Button(
                    onClick = { if (last) onFinish() else onStep(step + 1) },
                    enabled = nextEnabled,
                    shape = RectangleShape,
                    colors = if (last) finishColors else ButtonDefaults.buttonColors(),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text(if (last) finishLabel else "다음", fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
            VerticalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) { content() }
        }
        return
    }
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(steps[step], style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text("${step + 1} / ${steps.size}", style = AppStyles.textCardSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        steps.indices.forEach { i ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
            )
        }
    }
    Column(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }
    Spacer(modifier = Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (step > 0) {
            OutlinedButton(onClick = { onStep(step - 1) }, shape = RectangleShape, modifier = Modifier.weight(1f).height(52.dp)) { Text("이전") }
        }
        Button(
            onClick = { if (last) onFinish() else onStep(step + 1) },
            enabled = nextEnabled,
            shape = RectangleShape,
            colors = if (last) finishColors else ButtonDefaults.buttonColors(),
            modifier = Modifier.weight(2f).height(52.dp)
        ) { Text(if (last) finishLabel else "다음", fontWeight = FontWeight.Bold) }
    }
}

/** 가로 단계 칸의 한 줄: 번호(지난 단계는 체크) · 이름. 지금 단계는 왼쪽 강조 선과 굵은 강조색 */
@Composable
private fun WizardRailStep(index: Int, name: String, current: Boolean, done: Boolean, onClick: (() -> Unit)?) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(if (current) accent.copy(alpha = 0.08f) else androidx.compose.ui.graphics.Color.Transparent),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(if (current) accent else androidx.compose.ui.graphics.Color.Transparent))
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .padding(vertical = 10.dp)
                .size(22.dp)
                .background(if (current || done) accent else MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (done) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
            else Text("${index + 1}", style = AppStyles.textCardSmall, fontWeight = FontWeight.Bold,
                color = if (current) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
            color = when {
                current -> accent
                done -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
    }
}

/** 선호 캐릭터 단계: 순위·사진·이름 목록과 '맞아요' 체크 */
@Composable
internal fun ColumnScope.FavoritesReviewStep(
    favorites: List<Pair<String, String>>, // (이름, 사진)
    note: String?,
    confirmText: String,
    checked: Boolean,
    onCheck: (Boolean) -> Unit
) {
    note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp)) }
    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
        if (favorites.isEmpty()) item {
            Text("선호 캐릭터가 없습니다", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
        }
        itemsIndexed(favorites, key = { _, f -> f.first }) { i, (name, avatar) ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(28.dp))
                CharaAvatar(avatar, 40)
                Spacer(modifier = Modifier.width(12.dp))
                Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    CheckConfirmRow(confirmText, checked, onCheck)
}

private enum class KindFilter(val label: String) { ALL("전체"), OFFICIAL("공식"), FAN("2차창작") }
private enum class StateFilter(val label: String) { ALL("전체"), GOTTEN("보유"), PENDING("구매예정"), NOT_GOTTEN("미보유") }

/**
 * 굿즈 단계: 첫 줄에 캐릭터 아이콘(확인하면 초록 체크), 누르면 아래에 그 캐릭터 굿즈 목록.
 * 목록은 검색과 공식/2차창작 · 보유 상태로 거를 수 있다. 캐릭터마다 확인 체크를 건다.
 */
@Composable
internal fun ColumnScope.GoodsReviewStep(
    goods: List<ReviewGoods>,
    avatars: Map<String, String>,
    checked: Set<String>,
    onCheck: (String, Boolean) -> Unit,
    onCheckAll: () -> Unit
) {
    val charas = remember(goods) {
        goods.flatMap { it.charaNames }.groupingBy { it }.eachCount()
            .entries.sortedWith(compareBy<Map.Entry<String, Int>> { it.key == ReviewGoods.NO_CHARA }.thenByDescending { it.value }.thenBy { it.key })
            .map { it.key to it.value }
    }
    var current by rememberSaveable(goods) { mutableStateOf(charas.firstOrNull()?.first) }
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(KindFilter.ALL) }
    var state by rememberSaveable { mutableStateOf(StateFilter.ALL) }

    if (charas.isEmpty()) {
        Text("담긴 굿즈가 없습니다", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
        return
    }
    val wide = isWideLayout()

    /** 캐릭터 한 칸: 사진(확인하면 초록 체크) · 이름 · 개수. 누르면 그 캐릭터 굿즈 */
    val charaCell: @Composable (String, Int) -> Unit = { name, count ->
        val isCurrent = name == current
        Column(
            modifier = Modifier.width(64.dp).clickable { current = name },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box {
                CharaAvatar(
                    avatars[name].orEmpty(), 52,
                    modifier = if (isCurrent) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier
                )
                if (name in checked) {
                    GreenCheck(
                        true,
                        Modifier.align(Alignment.BottomEnd).size(20.dp).background(MaterialTheme.colorScheme.surface, CircleShape),
                        contentDescription = "$name 확인함"
                    )
                }
            }
            Text(
                name, style = AppStyles.textCardSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text("${count}개", style = AppStyles.textCardSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val progress: @Composable () -> Unit = {
        Text(
            "캐릭터 ${charas.size}명 중 ${charas.count { it.first in checked }}명 확인",
            style = AppStyles.textCardSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (wide) {
        // 가로: 왼쪽에 캐릭터 세로 목록, 오른쪽에 그 캐릭터 굿즈(목록이 높이를 다 쓰게)
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(modifier = Modifier.width(96.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                progress()
                TextButton(onClick = onCheckAll, shape = RectangleShape, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("모두 확인") }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(charas, key = { it.first }) { (name, count) -> charaCell(name, count) }
                }
            }
            VerticalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                current?.let { name -> GoodsReviewList(goods, name, query, { query = it }, kind, { kind = it }, state, { state = it }, wide = true) }
                Spacer(modifier = Modifier.height(8.dp))
                current?.let { name -> CheckConfirmRow("$name 굿즈 확인했어요", name in checked, { onCheck(name, it) }) }
            }
        }
        return
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f)) { progress() }
        TextButton(onClick = onCheckAll, shape = RectangleShape) { Text("모두 확인") }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
        items(charas, key = { it.first }) { (name, count) -> charaCell(name, count) }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 6.dp))

    val name = current ?: return
    GoodsReviewList(goods, name, query, { query = it }, kind, { kind = it }, state, { state = it }, wide = false)
    Spacer(modifier = Modifier.height(8.dp))
    CheckConfirmRow("$name 굿즈 확인했어요", name in checked, { onCheck(name, it) })
}

/**
 * 한 캐릭터의 굿즈 목록: 검색 · 종류(전체·공식·2차창작) · 상태(모든 상태·보유·구매예정·미보유) · 굿즈 줄.
 * 세로는 검색 / 종류 / 상태를 세 줄로, 가로([wide])는 종류와 상태를 한 줄에.
 */
@Composable
private fun ColumnScope.GoodsReviewList(
    goods: List<ReviewGoods>,
    name: String,
    query: String,
    onQuery: (String) -> Unit,
    kind: KindFilter,
    onKind: (KindFilter) -> Unit,
    state: StateFilter,
    onState: (StateFilter) -> Unit,
    wide: Boolean
) {
    val words = remember(query) { query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() } }
    val list = remember(goods, name, words, kind, state) {
        goods.filter { name in it.charaNames }
            .filter { kind == KindFilter.ALL || (kind == KindFilter.OFFICIAL) == it.official }
            .filter {
                when (state) {
                    StateFilter.ALL -> true
                    StateFilter.GOTTEN -> it.status == GoodsStatus.GOTTEN
                    StateFilter.PENDING -> it.status == GoodsStatus.PENDING
                    StateFilter.NOT_GOTTEN -> it.status == GoodsStatus.NOT_GOTTEN
                }
            }
            .filter { g ->
                val text = listOf(g.series, g.chara, g.category, g.memo, g.price).joinToString(" ").lowercase()
                words.all { it in text }
            }
    }
    val kindChips: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
        items(KindFilter.entries, key = { "k" + it.name }) { k ->
            FilterChip(selected = kind == k, onClick = { onKind(k) }, label = { Text(k.label) }, shape = RectangleShape)
        }
    }
    val stateChips: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
        items(StateFilter.entries, key = { "s" + it.name }) { st ->
            FilterChip(selected = state == st, onClick = { onState(st) }, label = { Text(if (st == StateFilter.ALL) "모든 상태" else st.label) }, shape = RectangleShape)
        }
    }
    // 검색·필터도 목록과 함께 스크롤해, 짧은 화면에서도 굿즈 목록이 넉넉히 보이게 한다.
    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
        item(key = "__search") {
            SearchField(query = query, onQueryChange = onQuery, placeholder = "$name 굿즈 검색")
            if (wide) {
                // 가로: 종류와 상태를 한 줄에(사이에 세로 선)
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    kindChips()
                    item(key = "__sep") { VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant) }
                    stateChips()
                }
            } else {
                // 1줄: 검색, 2줄: 종류(전체·공식·2차창작), 3줄: 상태(모든 상태·보유·구매예정·미보유)
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) { kindChips() }
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) { stateChips() }
            }
        }
        if (list.isEmpty()) item {
            Text("조건에 맞는 굿즈가 없습니다", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
        }
        items(list.size) { i ->
            val g = list[i]
            GoodsListItem(
                imgPath = g.img,
                series = (if (g.official) "공식 · " else "2차창작 · ") + g.series,
                chara = g.chara,
                category = g.category,
                price = g.price,
                isGotten = g.status == GoodsStatus.GOTTEN,
                gottenStatus = g.status.asGottenStatus(),
                memo = g.memo,
                quantity = g.quantity
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** 캐릭터 이름 목록(굿즈 단계와 같은 기준) */
internal fun reviewCharas(goods: List<ReviewGoods>): Set<String> = goods.flatMap { it.charaNames }.toSet()

