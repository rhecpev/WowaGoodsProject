package com.example.wowagoodsproject.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import com.example.wowagoodsproject.db.official.GoodsEntity

/*
 * 그리드 ↔ 리스트 전환(초안 2): 굿즈 사진이 제 자리로 날아가는 공유 요소 전환.
 * - 리스트 → 그리드: 왼쪽 썸네일이 그리드 칸 자리로 날아가며 커지고, 그리드 글자는 사진이 거의 도착한 뒤 나타난다.
 * - 그리드 → 리스트: 칸의 사진이 줄 왼쪽으로 미끄러지며 작아지고, 오른쪽 글자가 뒤따라 나타난다.
 * - 보고 있던 굿즈부터 읽는 순서로 조금씩 늦게 출발해(굿즈마다 [SNAKE_STAGGER_MS]) 물결처럼 이어진다.
 * - 바꿔도 보고 있던 굿즈가 있는 자리를 그대로 보여 준다(두 화면에 같은 굿즈가 있어야 사진이 날아갈 곳이 있다).
 * 화면에 보이던 굿즈만 날아가고, 새로 보이는 굿즈는 제자리에서 나타난다.
 */

/** 사진 한 장이 날아가는 시간(ms) */
const val SNAKE_MOVE_MS = 360
/** 굿즈마다 늦게 출발하는 간격(ms) */
const val SNAKE_STAGGER_MS = 25
/** 늦춤을 주는 굿즈 수 상한(이보다 뒤는 같은 때 출발한다. 너무 오래 끌지 않게) */
const val SNAKE_STAGGER_MAX = 10
/** 글자가 나타나는 시간(ms) */
const val SNAKE_TEXT_MS = 180
/** 한 줄 개수를 바꾼 뒤 이만큼 그 값에 머물러야 목록을 그 개수로 바꾼다(ms) */
const val GRID_COLUMNS_SETTLE_MS = 500L

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GoodsListContent(
    goods: List<GoodsEntity>,
    isGridMode: Boolean,
    gridColumns: Int,
    onGoodsClick: (GoodsEntity) -> Unit,
    /** null 이면 선택 모드가 아니다. */
    selectedIds: Set<Int>? = null,
    /** 스크롤해서 반짝이며 보여 줄 굿즈 id */
    focusId: Int? = null,
    /** [focusId] 굿즈의 반짝임이 끝났을 때 */
    onFocusShown: () -> Unit = {}
) {
    if (goods.isEmpty()) {
        EmptyState(icon = Icons.Outlined.Inventory2, title = "굿즈가 없습니다")
        return
    }

    // 한 줄 개수 슬라이더를 끄는 동안은 목록을 그대로 두고, 한 값에 [GRID_COLUMNS_SETTLE_MS] 동안 머물면 그 개수로 한 번만 전환한다.
    // (빠르게 여러 칸을 넘기면 전환 애니메이션이 겹쳐 버벅인다)
    val requestedColumns = gridColumns
    val settledColumns = remember { mutableIntStateOf(requestedColumns) }
    LaunchedEffect(requestedColumns) {
        if (settledColumns.intValue != requestedColumns) {
            kotlinx.coroutines.delay(GRID_COLUMNS_SETTLE_MS)
            settledColumns.intValue = requestedColumns
        }
    }
    @Suppress("NAME_SHADOWING")
    val gridColumns = settledColumns.intValue

    // 보기 = (그리드인지, 한 줄 개수). 리스트는 개수와 상관없이 하나다.
    // 바꿀 때 두 목록이 잠깐 함께 그려지므로 스크롤 상태는 보기마다 따로 둔다.
    val view = GoodsView(isGridMode, if (isGridMode) gridColumns.coerceAtLeast(1) else 0)
    // 화면을 돌려도(태블릿 등) 보던 자리가 남게 저장한다
    val states = androidx.compose.runtime.saveable.rememberSaveable(saver = GoodsViewStatesSaver) { mutableMapOf<GoodsView, LazyListState>() }
    fun stateOf(v: GoodsView) = states.getOrPut(v) { LazyListState() }
    val listState = stateOf(view)
    // 격자는 시리즈가 바뀌면 새 줄에서 시작한다(줄마다 칸 수가 다를 수 있음).
    val rows = remember(goods, gridColumns) { chunkBySeries(goods, gridColumns) }

    // 보기를 바꾸면(그리드 ↔ 리스트, 한 줄 개수) 보던 굿즈가 새 화면 맨 위에 오게 옮겨 둔다.
    // 보던 굿즈 = 이전 화면에서 위가 잘리지 않고 다 보이는 첫 굿즈. 위에 반쯤 걸친 굿즈는 위로 밀려나 사라진다(날아가지 않는다).
    // 새 화면이 처음 그려지기 전에 자리를 잡아야 사진이 날아갈 곳이 맞으므로 그리는 중에 바로 요청한다.
    var lastView by remember { mutableStateOf(view) }
    /** 이번 전환에서 늦춤의 기준이 되는 굿즈 순번(보던 굿즈) */
    var anchorIndex by remember { mutableIntStateOf(0) }
    if (lastView != view) {
        val old = stateOf(lastView)
        val firstFull = old.layoutInfo.visibleItemsInfo.firstOrNull { it.offset >= 0 }?.index ?: old.firstVisibleItemIndex
        val anchorId = if (lastView.grid) chunkBySeries(goods, lastView.columns).getOrNull(firstFull)?.firstOrNull()?.goodsId
        else goods.getOrNull(firstFull)?.goodsId
        val goodsIndex = goods.indexOfFirst { it.goodsId == anchorId }.coerceAtLeast(0)
        anchorIndex = goodsIndex
        if (view.grid) listState.requestScrollToItem(rows.indexOfFirst { row -> row.any { it.goodsId == anchorId } }.coerceAtLeast(0))
        else listState.requestScrollToItem(goodsIndex)
        lastView = view
    }

    // 격자 모드는 한 줄이 한 항목이다.
    val focusRow = focusId?.let { id ->
        if (isGridMode) rows.indexOfFirst { row -> row.any { it.goodsId == id } }
        else goods.indexOfFirst { it.goodsId == id }
    }?.takeIf { it >= 0 }
    LaunchedEffect(focusId, focusRow) {
        if (focusRow == null) return@LaunchedEffect
        listState.scrollToItem(focusRow)
        // 화면 가운데쯤 오도록 맞춘다. 목록 끝 쪽 항목은 더 내려갈 수 없어 그 자리에 둔다.
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == focusRow }
        if (item != null) {
            val centered = (info.viewportSize.height - item.size) / 2
            listState.animateScrollBy((item.offset - centered).toFloat())
        }
    }

    val indexOf = remember(goods) { goods.withIndex().associate { (i, g) -> g.goodsId to i } }
    /** 보던 굿즈부터 읽는 순서로 조금씩 늦게 */
    fun delayOf(goodsId: Int): Int =
        ((indexOf[goodsId] ?: 0) - anchorIndex).coerceIn(0, SNAKE_STAGGER_MAX) * SNAKE_STAGGER_MS

    // 칸 너비는 목록이 한 번만 재서 칸마다 넘긴다(칸마다 재면 보기를 바꿀 때 처음 그리는 시간이 길어진다).
    ProvideGoodsCardImageSize(gridColumns) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val listWidth = maxWidth
    // 날아가는 사진이 목록 밖(상단바·필터 위)으로 삐져나가 그려지지 않게 목록 영역으로 자른다.
    SharedTransitionLayout(modifier = Modifier.clipToBounds()) {
        AnimatedContent(
            targetState = view,
            // 화면 바탕(구분선·칸 배경)은 금방 바뀌고, 움직임은 사진과 글자가 맡는다.
            transitionSpec = { fadeIn(tween(SNAKE_MOVE_MS / 2)) togetherWith fadeOut(tween(SNAKE_MOVE_MS / 2)) },
            label = "goodsSnake"
        ) { shown ->
            val grid = shown.grid
            val scope: AnimatedVisibilityScope = this
            // 나가는 화면은 자기 한 줄 개수로 그린다(개수를 바꾸는 중에도 원래 모양 그대로 사라지게)
            val shownRows = remember(goods, shown.columns) { if (shown.grid) chunkBySeries(goods, shown.columns) else emptyList() }
            val shownColumns = shown.columns.coerceAtLeast(1)
            val compactCells = listWidth / shownColumns < GRID_COMPACT_WIDTH
            /** 사진: 같은 굿즈의 다른 보기 자리에서 날아온다 */
            @Composable
            fun image(id: Int): Modifier = with(this@SharedTransitionLayout) {
                Modifier.sharedElement(
                    sharedContentState = rememberSharedContentStateCompat("goods-img-$id"),
                    animatedVisibilityScope = scope,
                    boundsTransform = BoundsTransform { _, _ -> tween(SNAKE_MOVE_MS, delayOf(id), FastOutSlowInEasing) }
                )
            }
            /** 글자: 나갈 때는 바로 흐려지고, 들어올 때는 사진이 거의 도착한 뒤 나타난다 */
            fun text(id: Int): Modifier = with(scope) {
                Modifier.animateEnterExit(
                    enter = fadeIn(tween(SNAKE_TEXT_MS, delayOf(id) + SNAKE_MOVE_MS / 2)),
                    exit = fadeOut(tween(80))
                )
            }
            if (grid) {
                LazyColumn(modifier = Modifier.fillMaxSize(), state = stateOf(shown)) {
                    itemsIndexed(shownRows) { rowIndex, rowItems ->
                        if (isSeriesStartRow(shownRows, rowIndex)) SeriesBoundaryDivider()
                        Row(modifier = Modifier.fillMaxWidth()) {
                            rowItems.forEach { item ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .then(if (focusId != null) Modifier.highlightFlash(active = item.goodsId == focusId, onFinished = onFocusShown) else Modifier)
                                ) {
                                    GoodsGridItem(
                                        imgPath = item.imgPath,
                                        series = item.series,
                                        chara = item.chara,
                                        category = item.category,
                                        price = item.price,
                                        isGotten = item.isGotten,
                                        gottenStatus = item.status.asGottenStatus(),
                                        memo = item.memo,
                                        quantity = item.quantity,
                                        selected = selectedIds?.let { item.goodsId in it },
                                        imageModifier = image(item.goodsId),
                                        textModifier = text(item.goodsId),
                                        compact = compactCells,
                                        onClick = { onGoodsClick(item) }
                                    )
                                }
                            }
                            repeat(shownColumns - rowItems.size) {
                                Box(modifier = Modifier.weight(1f))
                            }
                        }
                        // 다음 줄이 다른 시리즈면 그 줄 위의 이중선이 구분을 맡는다.
                        if (rowIndex < shownRows.lastIndex && !isSeriesStartRow(shownRows, rowIndex + 1)) {
                            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), state = stateOf(shown)) {
                    itemsIndexed(goods) { index, item ->
                        Box(modifier = if (focusId != null) Modifier.highlightFlash(active = item.goodsId == focusId, onFinished = onFocusShown) else Modifier) {
                            GoodsListItem(
                                imgPath = item.imgPath,
                                series = item.series,
                                chara = item.chara,
                                category = item.category,
                                price = item.price,
                                isGotten = item.isGotten,
                                gottenStatus = item.status.asGottenStatus(),
                                memo = item.memo,
                                quantity = item.quantity,
                                selected = selectedIds?.let { item.goodsId in it },
                                imageModifier = image(item.goodsId),
                                textModifier = text(item.goodsId),
                                onClick = { onGoodsClick(item) }
                            )
                        }

                        if (index < goods.lastIndex) {
                            if (isSeriesStart(goods, index + 1)) SeriesBoundaryDivider()
                            else HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
    }
    }
}

/** 목록 보기 하나: 그리드인지와 한 줄 개수(리스트는 0) */
private data class GoodsView(val grid: Boolean, val columns: Int)

/** 보기마다의 스크롤 자리 저장: [그리드(1/0), 한 줄 개수, 첫 항목, 어긋남] 묶음들 */
private val GoodsViewStatesSaver = androidx.compose.runtime.saveable.Saver<MutableMap<GoodsView, LazyListState>, ArrayList<Int>>(
    save = { map ->
        ArrayList<Int>().apply {
            map.forEach { (v, st) -> addAll(listOf(if (v.grid) 1 else 0, v.columns, st.firstVisibleItemIndex, st.firstVisibleItemScrollOffset)) }
        }
    },
    restore = { list ->
        list.chunked(4).associate { (g, c, i, o) -> GoodsView(g == 1, c) to LazyListState(i, o) }.toMutableMap()
    }
)

/** 공유 요소 열쇠. rememberSharedContentState 는 그리는 중에만 부를 수 있어 따로 둔다 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.rememberSharedContentStateCompat(key: String): SharedTransitionScope.SharedContentState =
    rememberSharedContentState(key)
