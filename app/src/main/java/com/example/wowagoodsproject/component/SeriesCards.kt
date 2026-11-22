package com.example.wowagoodsproject.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.navigation.TopBar
import com.example.wowagoodsproject.navigation.TopBarAction
import com.example.wowagoodsproject.ui.theme.AppStyles
import java.io.File

/*
 * 직접 등록하는 굿즈(명조 2차창작·배신 굿즈)의 시리즈 목록. 명조 공식 시리즈 목록과 같은 모양:
 * 폭을 다 쓰는 카드(위 사진, 아래 이름·날짜, 구분선, 등장 캐릭터 칩), 시리즈 제목 검색, 캐릭터로 시리즈 찾기.
 */

/** 시리즈 카드 안의 캐릭터 한 칸 */
data class SeriesCharaInfo(
    val name: String,
    /** 사진 주소나 경로. 없으면 빈 문자열 */
    val avatar: String,
    val favorite: Boolean,
    /** 선호 우선순위(작을수록 앞). 선호가 아니면 0 */
    val favoriteOrder: Int,
    val gotten: Int,
    val total: Int
)

/** 시리즈 카드 한 장 */
data class SeriesCard(
    val name: String,
    /** 카드 사진: 등록한 배경 사진, 없으면 그 시리즈 굿즈 사진. 없으면 빈 문자열 */
    val cover: String,
    val total: Int,
    val gotten: Int,
    val pending: Int,
    /** 사용자가 등록한 시리즈인지(이름·사진을 고칠 수 있다) */
    val registered: Boolean,
    /** 가장 최근 등록한 굿즈 날짜(정렬·표시용) */
    val latest: String,
    /** 등장 캐릭터(선호 먼저, 굿즈 많은 순) */
    val charas: List<SeriesCharaInfo> = emptyList()
)

/** 사진 경로나 주소를 Coil 에 넘길 값으로 */
private fun model(path: String): Any? = when {
    path.isEmpty() -> null
    path.startsWith("http") -> path
    else -> File(path)
}

/**
 * 굿즈 목록을 시리즈 카드로 묶는다. [registered] 는 등록한 시리즈(이름 → 배경 사진). 등록만 하고 굿즈가 없는 시리즈도 카드가 된다.
 * [charaInfo] 는 캐릭터 이름 → (사진, 선호 우선순위 또는 null).
 * 최근 등록 순, 등록만 한 시리즈는 이름순으로 뒤에. 미분류는 언제나 맨 아래.
 */
fun <T> seriesCards(
    goods: List<T>,
    seriesOf: (T) -> String,
    statusOf: (T) -> GoodsStatus,
    imageOf: (T) -> String,
    dateOf: (T) -> String,
    charaOf: (T) -> String = { "" },
    registered: Map<String, String>,
    /** 등록한 시리즈의 날짜. 있으면 카드 날짜·정렬에 굿즈 최근 등록일 대신 쓴다 */
    registeredDates: Map<String, String> = emptyMap(),
    charaInfo: (String) -> Pair<String, Int?> = { "" to null },
    noSeriesName: String,
    /** 대소문자만 다른 시리즈 이름을 한 카드로 묶는다(2차창작: 등록 시리즈도 대소문자를 가리지 않는다). 이름은 등록한 것을 먼저 쓴다 */
    ignoreCase: Boolean = false
): List<SeriesCard> {
    val canonical = HashMap<String, String>()
    if (ignoreCase) {
        registered.keys.forEach { canonical.putIfAbsent(it.trim().lowercase(), it) }
        goods.forEach { g -> seriesOf(g).trim().takeIf { it.isNotEmpty() }?.let { canonical.putIfAbsent(it.lowercase(), it) } }
    }
    fun key(g: T): String {
        val raw = seriesOf(g).trim().ifEmpty { return noSeriesName }
        return if (ignoreCase) canonical[raw.lowercase()] ?: raw else raw
    }
    val byName = goods.groupBy { key(it) }
    val names = byName.keys + registered.keys.filter { it !in byName }
    return names.map { name ->
        val list = byName[name].orEmpty()
        val counts = linkedMapOf<String, IntArray>() // 이름 → [보유, 전체]
        list.forEach { g ->
            charaOf(g).split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { c ->
                val a = counts.getOrPut(c) { IntArray(2) }
                if (statusOf(g) == GoodsStatus.GOTTEN) a[0]++
                a[1]++
            }
        }
        val charas = counts.map { (c, a) ->
            val (avatar, fav) = charaInfo(c)
            SeriesCharaInfo(c, avatar, fav != null, fav ?: 0, a[0], a[1])
        }.sortedWith(
            compareByDescending<SeriesCharaInfo> { it.favorite }
                .thenBy { if (it.favorite) it.favoriteOrder else 0 }
                .thenByDescending { it.total }
                .thenBy { it.name }
        )
        SeriesCard(
            name = name,
            cover = registered[name].orEmpty().ifEmpty { list.firstOrNull { imageOf(it).isNotEmpty() }?.let(imageOf).orEmpty() },
            total = list.size,
            gotten = list.count { statusOf(it) == GoodsStatus.GOTTEN },
            pending = list.count { statusOf(it) == GoodsStatus.PENDING },
            registered = name in registered,
            latest = registeredDates[name]?.takeIf { it.isNotBlank() } ?: list.maxOfOrNull(dateOf).orEmpty(),
            charas = charas
        )
    }.sortedWith(
        // 미분류는 정렬 기준과 상관없이 맨 아래
        compareBy<SeriesCard> { it.name == noSeriesName }
            .thenByDescending { it.latest }
            .thenBy { it.name }
    )
}

/**
 * 시리즈 목록 화면 본체(명조 공식 시리즈 목록과 같은 모양).
 * 오른쪽 위 검색(시리즈 제목) · 필터(캐릭터로 시리즈 찾기) + [actions].
 * @param charaChoices 캐릭터 필터 팝업에 보일 캐릭터
 * @param leading 맨 앞에 둘 카드(예: 2차창작 '전체 굿즈')
 */
@Composable
fun SeriesListPage(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    cards: List<SeriesCard>,
    charaChoices: List<CharaEntity>,
    isLandscape: Boolean,
    onOpen: (String) -> Unit,
    emptyTitle: String,
    emptyMessage: String? = null,
    leading: SeriesCard? = null,
    onOpenLeading: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var charaFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var showCharaFilter by remember { mutableStateOf(false) }
    var charaQuery by remember { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }

    if (showCharaFilter) {
        FilterDialogFrame(
            title = "캐릭터로 시리즈 찾기",
            onDismiss = { showCharaFilter = false },
            onClear = if (charaFilter != null) ({ charaFilter = null; showCharaFilter = false }) else null
        ) {
            SearchField(query = charaQuery, onQueryChange = { charaQuery = it }, placeholder = "캐릭터 이름 검색")
            Spacer(modifier = Modifier.height(12.dp))
            CharaChoiceGrid(
                charas = charaChoices.filter { it.charaNm.contains(charaQuery, ignoreCase = true) },
                selectedName = charaFilter,
                onSelect = { c ->
                    charaFilter = if (charaFilter == c.charaNm) null else c.charaNm
                    showCharaFilter = false
                },
                modifier = Modifier.weight(1f)
            )
        }
    }

    val shown = cards.filter { c ->
        c.name.contains(query.trim(), ignoreCase = true) && (charaFilter == null || c.charas.any { it.name == charaFilter })
    }
    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = title,
            subtitle = subtitle,
            onBack = onBack,
            actions = {
                TopBarAction(
                    icon = Icons.Default.Search,
                    contentDescription = "시리즈 제목 검색",
                    onClick = {
                        // 닫을 때는 검색어도 지워서 보이지 않는 조건이 남지 않게 한다.
                        if (showSearch) query = ""
                        showSearch = !showSearch
                    },
                    active = showSearch || query.isNotEmpty()
                )
                TopBarAction(
                    icon = Icons.Default.FilterList,
                    contentDescription = "캐릭터 필터",
                    onClick = { showCharaFilter = true },
                    active = charaFilter != null
                )
                actions()
            }
        )
        if (showSearch) {
            SearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = "시리즈 제목 검색",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).focusRequester(searchFocus)
            )
            LaunchedEffect(Unit) { if (query.isEmpty()) runCatching { searchFocus.requestFocus() } }
        }
        ActiveFilterChips(filters = listOfNotNull(charaFilter?.let { ActiveFilter(it) { charaFilter = null } }))
        val filtering = query.isNotBlank() || charaFilter != null
        val lead = leading?.takeIf { !filtering }
        if (shown.isEmpty() && lead == null) {
            if (filtering) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (query.isNotBlank()) "'${query.trim()}' 검색 결과가 없습니다" else "'$charaFilter' 캐릭터가 나온 시리즈가 없습니다",
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            } else {
                EmptyState(icon = Icons.Outlined.CollectionsBookmark, title = emptyTitle, message = emptyMessage)
            }
            return@Column
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            val all = listOfNotNull(lead) + shown
            itemsIndexed(all, key = { i, c -> if (i == 0 && lead != null) "__leading" else c.name }) { index, card ->
                SeriesListCard(card, isLandscape, onClick = { if (index == 0 && lead != null) onOpenLeading() else onOpen(card.name) })
                if (index < all.lastIndex) {
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(AppStyles.paddingMedium))
                }
            }
        }
    }
}

/** 공식 시리즈 카드와 같은 모양의 카드. 가로 화면은 사진을 왼쪽 절반에. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesListCard(card: SeriesCard, isLandscape: Boolean, onClick: () -> Unit) {
    val info = buildString {
        if (card.latest.isNotEmpty()) append(card.latest).append(" / ")
        append("보유 ${card.gotten}/${card.total}")
    }
    val image: @Composable (Modifier) -> Unit = { m ->
        Box(modifier = m.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            val mdl = model(card.cover)
            if (mdl != null) {
                Image(painter = rememberAsyncImagePainter(mdl), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else {
                Icon(Icons.Outlined.CollectionsBookmark, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    val body: @Composable (Boolean) -> Unit = { horizontalChips ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(card.name, style = AppStyles.textCardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(modifier = Modifier.width(8.dp))
            Text(info, style = AppStyles.textCardSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(AppStyles.paddingSmall))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(AppStyles.paddingSmall))
        if (card.charas.isEmpty()) {
            Text("등록된 캐릭터가 없습니다", style = AppStyles.textCardSmall)
        } else if (horizontalChips) {
            LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppStyles.paddingMedium)) {
                items((card.charas.size + 1) / 2) { i ->
                    Column(verticalArrangement = Arrangement.spacedBy(AppStyles.paddingSmall)) {
                        card.charas.getOrNull(i * 2)?.let { SeriesCharaChip(it, horizontal = true) }
                        card.charas.getOrNull(i * 2 + 1)?.let { SeriesCharaChip(it, horizontal = true) }
                    }
                }
            }
        } else {
            LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppStyles.paddingMedium)) {
                items(card.charas, key = { it.name }) { SeriesCharaChip(it, horizontal = false) }
            }
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        if (isLandscape) {
            Row(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                image(Modifier.fillMaxWidth(0.5f).fillMaxHeight())
                Column(modifier = Modifier.weight(1f).padding(AppStyles.paddingMedium)) { body(true) }
            }
        } else {
            Column {
                image(Modifier.fillMaxWidth().height(200.dp))
                Column(modifier = Modifier.padding(AppStyles.paddingMedium)) { body(false) }
            }
        }
    }
}

/** 시리즈 카드 안의 캐릭터 한 칸: 동그란 사진 + 이름 + (보유/전체). 공식 시리즈 카드와 같은 모양. */
@Composable
private fun SeriesCharaChip(chara: SeriesCharaInfo, horizontal: Boolean) {
    val favorite = chara.favorite
    val avatar: @Composable () -> Unit = {
        Box {
            Image(
                painter = rememberAsyncImagePainter(model = model(chara.avatar)),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(if (favorite) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
                contentScale = ContentScale.Crop
            )
            if (favorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = "선호 캐릭터", tint = AppStyles.colorFavorite, modifier = Modifier.size(11.dp))
                }
            }
        }
    }
    val texts: @Composable (Alignment.Horizontal) -> Unit = { align ->
        Column(horizontalAlignment = align) {
            Text(
                text = chara.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = AppStyles.textCardSubtitle,
                fontWeight = if (favorite) FontWeight.Bold else FontWeight.Normal,
                color = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text("${chara.gotten}/${chara.total}", style = AppStyles.textCardSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (horizontal) {
        Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            avatar(); Spacer(modifier = Modifier.width(8.dp)); texts(Alignment.Start)
        }
    } else {
        Column(modifier = Modifier.widthIn(min = 56.dp).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            avatar(); Spacer(modifier = Modifier.height(4.dp)); texts(Alignment.CenterHorizontally)
        }
    }
}
