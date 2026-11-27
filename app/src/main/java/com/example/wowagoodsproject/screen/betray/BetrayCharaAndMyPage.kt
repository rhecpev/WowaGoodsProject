package com.example.wowagoodsproject.screen.betray

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.rememberAsyncImagePainter
import com.example.wowagoodsproject.component.CharaShareGauge
import com.example.wowagoodsproject.component.EmptyState
import com.example.wowagoodsproject.component.FavoriteCollectionList
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.LineTab
import com.example.wowagoodsproject.component.SearchField
import com.example.wowagoodsproject.component.SidePanelLayout
import com.example.wowagoodsproject.component.charaShares
import com.example.wowagoodsproject.component.mergeFavoriteOrder
import com.example.wowagoodsproject.component.rememberCharaColors
import com.example.wowagoodsproject.db.betray.BetrayGoodsEntity
import com.example.wowagoodsproject.navigation.TopBar
import com.example.wowagoodsproject.screen.mypage.SettingsRow
import kotlinx.coroutines.launch
import com.example.wowagoodsproject.ui.theme.AppStyles

/*
 * 배신 굿즈의 캐릭터별 탭과 마이페이지. 화면 모양은 명조 쪽(CharacterScreen, MyPageScreen)과 같게 맞췄다.
 * 명조 쪽 화면 파일은 건드리지 않고, 공용 부품(component 패키지)만 함께 쓴다. 데이터는 배신 굿즈 DB 것만 쓴다.
 */

// ── 캐릭터별 탭 ─────────────────────────────────────────────────────

/** 가로 화면에서 이름 검색창과 전체/선호 버튼을 세우는 줄의 폭(명조 캐릭터별과 같다) */
private val CHARA_SEARCH_PANEL_WIDTH = 176.dp

/**
 * 캐릭터 목록. 명조 캐릭터별 탭과 같은 모양: 검색 + 전체/선호 선 탭, 선호 캐릭터·다른 캐릭터 구역,
 * 줄마다 사진·이름·보유율 게이지·하트, 선호가 둘 이상이면 ≡ 손잡이로 끌어 순서를 바꾼다.
 */
@Composable
internal fun BetrayCharaList(
    subtitle: String,
    charas: List<BetrayChara>,
    isLandscape: Boolean,
    onOpen: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onReorderFavorites: (List<String>) -> Unit,
    onAdd: () -> Unit,
    onExit: () -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showFavoriteOnly by rememberSaveable { mutableStateOf(false) }

    val filtered = charas
        .filter { !showFavoriteOnly || it.favorite }
        .filter { it.name.contains(searchQuery, ignoreCase = true) }
        .sortedBy { it.name }
    val favoriteCharas = filtered.filter { it.favorite }.sortedWith(compareBy<BetrayChara> { it.favoriteOrder }.thenBy { it.name })
    val otherCharas = filtered.filterNot { it.favorite }
    val favoriteTotal = charas.count { it.favorite }
    val allFavoriteNames = charas.filter { it.favorite }.sortedWith(compareBy<BetrayChara> { it.favoriteOrder }.thenBy { it.name }).map { it.name }

    // ── 선호 순서 끌어서 바꾸기(명조 캐릭터별과 같은 방식) ──
    val gridState = rememberLazyGridState()
    val dragScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val edgePx = with(LocalDensity.current) { 56.dp.toPx() }
    var dragPreview by remember { mutableStateOf<List<BetrayChara>?>(null) }
    var dragName by remember { mutableStateOf<String?>(null) }
    var dragPointer by remember { mutableStateOf(Offset.Zero) }
    var dragGrab by remember { mutableStateOf(Offset.Zero) }
    var dragEndedAt by remember { mutableLongStateOf(0L) }
    val latestFavorites by rememberUpdatedState(favoriteCharas)
    LaunchedEffect(favoriteCharas) { if (dragName == null) dragPreview = null }
    val shownFavorites = dragPreview ?: favoriteCharas
    val shownFullOrder = dragPreview?.let { mergeFavoriteOrder(allFavoriteNames, it.map { c -> c.name }) } ?: allFavoriteNames

    val startDrag: (BetrayChara, Offset) -> Unit = start@{ chara, local ->
        val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == chara.name } ?: return@start
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        dragName = chara.name
        dragPreview = latestFavorites
        dragGrab = local
        dragPointer = Offset(info.offset.x + local.x, info.offset.y + local.y)
    }
    val moveDrag: (Offset) -> Unit = move@{ delta ->
        val name = dragName ?: return@move
        val order = dragPreview ?: return@move
        dragPointer += delta
        val target = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            val key = info.key as? String
            key != null && key != name && order.any { it.name == key } &&
                dragPointer.y >= info.offset.y && dragPointer.y < info.offset.y + info.size.height
        }?.key as? String
        if (target != null) {
            val from = order.indexOfFirst { it.name == name }
            val to = order.indexOfFirst { it.name == target }
            if (from >= 0 && to >= 0) dragPreview = order.toMutableList().apply { add(to, removeAt(from)) }
        }
        val viewport = gridState.layoutInfo.viewportSize.height
        if (dragPointer.y < edgePx) dragScope.launch { gridState.scrollBy(-24f) }
        else if (dragPointer.y > viewport - edgePx) dragScope.launch { gridState.scrollBy(24f) }
    }
    val endDrag: () -> Unit = {
        val order = dragPreview
        val changed = order != null && order.map { it.name } != latestFavorites.map { it.name }
        if (changed) onReorderFavorites(mergeFavoriteOrder(allFavoriteNames, order!!.map { it.name })) else dragPreview = null
        dragName = null
        dragEndedAt = SystemClock.uptimeMillis()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = "캐릭터별", subtitle = subtitle, onBack = onExit, actions = {
            TextButton(
                onClick = onAdd,
                shape = RectangleShape,
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("캐릭터 추가", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        })
        SidePanelLayout(
            sidePanel = isLandscape,
            panelWidth = CHARA_SEARCH_PANEL_WIDTH,
            panel = {
                val allChip: @Composable (Modifier) -> Unit = { modifier ->
                    LineTab(
                        label = "전체",
                        count = charas.size,
                        selected = !showFavoriteOnly,
                        onClick = { showFavoriteOnly = false },
                        vertical = isLandscape,
                        modifier = modifier
                    )
                }
                val favoriteChip: @Composable (Modifier) -> Unit = { modifier ->
                    LineTab(
                        label = "선호",
                        count = favoriteTotal,
                        selected = showFavoriteOnly,
                        onClick = { showFavoriteOnly = true },
                        vertical = isLandscape,
                        leadingIcon = {
                            Icon(
                                imageVector = if (showFavoriteOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = AppStyles.colorFavorite,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = modifier
                    )
                }
                Column(modifier = Modifier.padding(horizontal = if (isLandscape) 8.dp else 16.dp, vertical = 8.dp)) {
                    SearchField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholder = if (isLandscape) "이름 검색" else "캐릭터 이름 검색"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isLandscape) {
                        allChip(Modifier.fillMaxWidth())
                        favoriteChip(Modifier.fillMaxWidth())
                    } else {
                        Row {
                            allChip(Modifier)
                            favoriteChip(Modifier)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        ) {
            if (filtered.isEmpty()) {
                when {
                    searchQuery.isNotEmpty() -> EmptyState(icon = Icons.Default.SearchOff, title = "'$searchQuery' 검색 결과가 없습니다")
                    showFavoriteOnly -> EmptyState(
                        icon = Icons.Default.FavoriteBorder,
                        title = "선호 캐릭터가 없습니다",
                        message = "캐릭터 줄의 하트를 눌러 선호 캐릭터로 등록하세요"
                    )
                    else -> EmptyState(
                        icon = Icons.Default.SearchOff,
                        title = "캐릭터가 없습니다",
                        message = "오른쪽 위 '캐릭터 추가'로 만들거나, 굿즈를 등록할 때 적은 캐릭터가 여기에 모입니다"
                    )
                }
            } else {
                val showSections = favoriteCharas.isNotEmpty() && otherCharas.isNotEmpty()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(1),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (showSections) sectionHeader("선호 캐릭터", favoriteCharas.size)
                    items(shownFavorites, key = { it.name }) { item ->
                        val dragging = dragName == item.name
                        val canReorder = allFavoriteNames.size >= 2
                        val itemModifier = if (dragging) {
                            val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == item.name }
                            Modifier
                                .zIndex(1f)
                                .graphicsLayer {
                                    if (info != null) translationY = dragPointer.y - dragGrab.y - info.offset.y
                                    shadowElevation = 8.dp.toPx()
                                }
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        } else Modifier.animateItem()
                        Box(
                            modifier = itemModifier.then(
                                if (canReorder) Modifier.pointerInput(item.name) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { startDrag(item, it) },
                                        onDrag = { change, delta -> change.consume(); moveDrag(delta) },
                                        onDragEnd = endDrag,
                                        onDragCancel = endDrag
                                    )
                                } else Modifier
                            )
                        ) {
                            BetrayCharaRow(
                                chara = item,
                                onClick = { if (SystemClock.uptimeMillis() - dragEndedAt > 400) onOpen(item.name) },
                                onToggleFavorite = { onToggleFavorite(item.name) },
                                orderNumber = if (canReorder) shownFullOrder.indexOf(item.name) + 1 else null,
                                handleModifier = Modifier.pointerInput(item.name) {
                                    detectDragGestures(
                                        onDragStart = { local -> startDrag(item, Offset(local.x + 4.dp.toPx(), local.y + 10.dp.toPx())) },
                                        onDrag = { change, delta -> change.consume(); moveDrag(delta) },
                                        onDragEnd = endDrag,
                                        onDragCancel = endDrag
                                    )
                                }
                            )
                        }
                    }
                    if (showSections) sectionHeader("다른 캐릭터", otherCharas.size)
                    items(otherCharas, key = { it.name }) { item ->
                        BetrayCharaRow(
                            chara = item,
                            onClick = { onOpen(item.name) },
                            onToggleFavorite = { onToggleFavorite(item.name) }
                        )
                    }
                }
            }
        }
    }
}

private fun LazyGridScope.sectionHeader(title: String, count: Int) {
    item(key = "__header_$title", span = { GridItemSpan(maxLineSpan) }) {
        Row(modifier = Modifier.padding(top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.width(6.dp))
            Text("$count", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ReorderHandle(orderNumber: Int, charaNm: String, modifier: Modifier) {
    Row(
        modifier = modifier
            .height(48.dp)
            .semantics { contentDescription = "$charaNm 순서 ${orderNumber}번, 끌어서 바꾸기" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Text(
            text = "$orderNumber",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 22.dp)
        )
    }
}

/**
 * 캐릭터 목록의 한 줄(명조 캐릭터별 줄과 같은 모양).
 * 왼쪽: (선호면 ≡ 손잡이 + 순서 번호) + 사진 + 이름. 오른쪽: 보유율 게이지 + 보유/전체와 퍼센트, 맨 끝에 하트.
 */
@Composable
private fun BetrayCharaRow(
    chara: BetrayChara,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    orderNumber: Int? = null,
    handleModifier: Modifier = Modifier
) {
    val gotten = chara.gotten
    val total = chara.total
    val progress = if (total > 0) gotten.toFloat() / total else 0f
    val isComplete = total > 0 && gotten == total
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                if (orderNumber != null) {
                    ReorderHandle(orderNumber = orderNumber, charaNm = chara.name, modifier = handleModifier)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Image(
                    painter = rememberAsyncImagePainter(model = chara.cover.ifEmpty { null }),
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = chara.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.width(112.dp)) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = if (isComplete) AppStyles.colorGotten else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$gotten / $total",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isComplete) AppStyles.colorGotten else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isComplete) AppStyles.colorGotten else MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (chara.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (chara.favorite) "선호 해제" else "선호 캐릭터로 등록",
                    tint = if (chara.favorite) AppStyles.colorFavorite else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

// ── 마이페이지 ──────────────────────────────────────────────────────

/**
 * 배신 굿즈 마이페이지. 명조 마이페이지처럼 맨 위에 보유 굿즈 요약(총 개수 + 캐릭터 비율 게이지 + 선호 캐릭터 수집률),
 * 그 아래 굿즈·장르 묶음.
 */
@Composable
internal fun BetrayMyPage(
    genreName: String,
    goods: List<BetrayGoodsEntity>,
    charas: List<BetrayChara>,
    isLandscape: Boolean,
    onOpenSection: (String) -> Unit,
    onEdit: () -> Unit,
    onExit: () -> Unit,
    /** 이 장르만 추출. 확인 창에 보일 내용(null 이면 세는 중)과 동작 */
    exportPreview: com.example.wowagoodsproject.screen.mypage.ExportPreview? = null,
    /** 추출 범위(이 장르만) */
    exportSelection: com.example.wowagoodsproject.screen.mypage.ExportSelection = com.example.wowagoodsproject.screen.mypage.ExportSelection(includeWuwa = false),
    isExporting: Boolean = false,
    exportProgress: Float = 0f,
    onLoadExportPreview: () -> Unit = {},
    onExport: () -> Unit = {},
    /** 이 장르로 데이터 입력(파일 고르기) */
    onImport: () -> Unit = {}
) {
    val gottenGoods = remember(goods) { goods.filter { it.status == GoodsStatus.GOTTEN } }
    var showExport by remember { mutableStateOf(false) }
    // 명조 마이페이지 데이터 추출과 같은 화면 묶음(담기는 내용 → 이 장르 굿즈 확인 → 추출 중)
    com.example.wowagoodsproject.screen.mypage.ExportFlow(
        show = showExport,
        preview = exportPreview,
        selection = exportSelection,
        isExporting = isExporting,
        exportProgress = exportProgress,
        onExport = { showExport = false; onExport() },
        onDismiss = { showExport = false }
    )
    val pending = goods.count { it.status == GoodsStatus.PENDING }
    val favoritesInOrder = remember(charas) {
        charas.filter { it.favorite }.sortedWith(compareBy<BetrayChara> { it.favoriteOrder }.thenBy { it.name }).map { it.name }
    }
    // 게이지·색·사진은 명조 쪽 공용 부품이 캐릭터 목록(CharaEntity 모양)으로 받는다. 화면에서만 잠깐 바꿔 쓴다.
    val charaEntities = remember(charas) { charas.mapIndexed { i, c -> c.asCharaEntity(i + 1) } }

    val summaryCard: @Composable () -> Unit = {
        val shares = remember(gottenGoods, favoritesInOrder) { charaShares(gottenGoods, favoritesInOrder, onlyFavorites = true) }
        val colorOrder = remember(shares, favoritesInOrder) {
            val topNames = shares.top.map { it.name }
            favoritesInOrder.filter { it in topNames } + topNames.filterNot { it in favoritesInOrder }
        }
        val charaColors by rememberCharaColors(colorOrder, charaEntities)
        BetraySummaryCard(count = gottenGoods.size, onClick = { onOpenSection("gotten") }) {
            val avatars = remember(charas) { charas.associate { it.name to it.cover } }
            // 범례를 공식/2차창작으로 나눠 보인다(합계만 보이면 굿즈 탭을 합친 뒤라 숫자가 헷갈린다).
            val breakdown = remember(gottenGoods) { charaKindBreakdown(gottenGoods) }
            CharaShareGauge(shares = shares, colors = charaColors, avatars = avatars, breakdown = breakdown)
            Spacer(modifier = Modifier.height(20.dp))
            // 선호 캐릭터 수집률(캐릭터별 탭의 퍼센트와 같은 값)
            FavoriteCollectionList(
                favorites = favoritesInOrder.mapNotNull { name -> charaEntities.find { it.charaNm == name } },
                collection = remember(charas) { charas.associate { it.name to (it.gotten to it.total) } },
                colors = charaColors,
                // 배신 굿즈는 공식·2차창작을 함께 센다.
                title = "선호 캐릭터 수집률"
            )
        }
    }
    val pendingRow: @Composable () -> Unit = {
        SettingsRow(
            icon = Icons.Default.ShoppingCart,
            title = "구매예정 굿즈",
            trailing = if (pending > 0) {
                { StatusPill(text = "${pending}개", color = AppStyles.colorPending) }
            } else null,
            onClick = { onOpenSection("pending") }
        )
    }
    val editRow: @Composable () -> Unit = {
        SettingsRow(icon = Icons.Default.Edit, title = "장르 편집", onClick = onEdit)
    }
    val exportRow: @Composable () -> Unit = {
        SettingsRow(
            icon = Icons.Default.FileDownload,
            title = "데이터 추출",
            showChevron = false,
            onClick = { onLoadExportPreview(); showExport = true }
        )
    }
    val importRow: @Composable () -> Unit = {
        SettingsRow(
            icon = Icons.Default.FileUpload,
            title = "데이터 입력",
            showChevron = false,
            onClick = onImport
        )
    }
    val switchRow: @Composable () -> Unit = {
        SettingsRow(icon = Icons.Default.SwapHoriz, title = "다른 장르로", onClick = onExit)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = "마이페이지", subtitle = genreName, onBack = onExit)
        if (isLandscape) {
            // 가로 화면: 요약은 한 줄을 다 쓰고, 나머지는 두 칸씩 나란히(명조 마이페이지와 같다).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(AppStyles.paddingLarge),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                summaryCard()
                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) { UnderlinedCell(fillHeight = true, content = pendingRow) }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) { UnderlinedCell(fillHeight = true, content = editRow) }
                }
                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) { UnderlinedCell(fillHeight = true, content = exportRow) }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) { UnderlinedCell(fillHeight = true, content = importRow) }
                }
                UnderlinedCell(content = switchRow)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(AppStyles.paddingLarge),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                summaryCard()
                Group(title = "굿즈") { pendingRow() }
                Group(title = "장르") {
                    editRow()
                    IndentedDivider()
                    exportRow()
                    IndentedDivider()
                    importRow()
                    IndentedDivider()
                    switchRow()
                }
            }
        }
    }
}

/** 맨 위 보유 굿즈 요약(명조 마이페이지와 같은 모양). 누르면 보유 굿즈 목록으로 들어간다. */
@Composable
private fun BetraySummaryCard(count: Int, onClick: () -> Unit, gauge: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("보유 굿즈", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.width(8.dp))
                Text("$count", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.weight(1f))
                Text("목록 보기", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            gauge()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** 제목 + 위아래 얇은 선 사이에 줄들을 묶는다(명조 마이페이지의 묶음과 같다). */
@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(content = content)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** 가로 화면의 한 칸: 내용 아래에 얇은 선. [fillHeight] 면 옆 칸과 높이를 맞추고 가운데에 둔다(스크롤 안 단독 칸은 false). */
@Composable
private fun UnderlinedCell(fillHeight: Boolean = false, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)) {
        Column(modifier = if (fillHeight) Modifier.weight(1f) else Modifier, verticalArrangement = Arrangement.Center) { content() }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun IndentedDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun StatusPill(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text = text, style = AppStyles.textCardSmall.copy(fontWeight = FontWeight.Bold), color = color, maxLines = 1)
}
