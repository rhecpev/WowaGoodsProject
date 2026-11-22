package com.example.wowagoodsproject.screen.betray

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.example.wowagoodsproject.component.EmptyState
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.ListModeViewModel
import com.example.wowagoodsproject.component.SearchField
import com.example.wowagoodsproject.component.SubTabStrip
import com.example.wowagoodsproject.component.hasChara
import com.example.wowagoodsproject.db.betray.BetrayGenreEntity
import com.example.wowagoodsproject.db.betray.saveSeries
import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.db.betray.BetrayGoodsEntity
import com.example.wowagoodsproject.db.betray.BetrayKind
import com.example.wowagoodsproject.navigation.TopBar
import com.example.wowagoodsproject.navigation.TopBarAction
import com.example.wowagoodsproject.screen.mypage.SettingsRow
import com.example.wowagoodsproject.ui.theme.AppStyles

/*
 * 배신 굿즈: 마이페이지의 '배신 굿즈' 버튼으로 들어가는 앱 속 앱.
 * 장르 목록 → 장르를 고르면 명조 쪽과 같은 다섯 탭(공식·캐릭터별·2차창작·검색·마이페이지)이 그 장르만 보여 준다.
 */

/** 진입점. 장르를 고르기 전에는 장르 목록, 고른 뒤에는 그 장르의 탭 화면을 보여 준다. */
@Composable
fun BetrayRoot(widthSizeClass: WindowWidthSizeClass, onExit: () -> Unit) {
    var openGenreId by rememberSaveable { mutableStateOf<Int?>(null) }
    // 알림으로 열 굿즈가 있으면 그 장르로 들어간다.
    val openRequest by BetrayOpenRequest.goods.collectAsState()
    LaunchedEffect(openRequest) { openRequest?.let { openGenreId = it.genreId } }
    val genreId = openGenreId
    if (genreId == null) {
        BetrayGenreListScreen(onBack = onExit, onOpen = { openGenreId = it.genreId })
    } else {
        val vm: BetrayViewModel = viewModel(key = "betray_$genreId") { BetrayViewModel(genreId) }
        BetrayGenreApp(widthSizeClass = widthSizeClass, viewModel = vm, onExit = { openGenreId = null })
    }
}

// ── 장르 목록 ─────────────────────────────────────────────────────────

@Composable
private fun BetrayGenreListScreen(onBack: () -> Unit, onOpen: (BetrayGenreEntity) -> Unit) {
    val vm: BetrayGenresViewModel = viewModel()
    val rows by vm.rows.collectAsState()
    val viewMode by vm.viewMode.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }

    // 편집 중인 장르. 0 이면 새 장르, null 이면 목록
    var editId by rememberSaveable { mutableStateOf<Int?>(null) }
    var deleteTarget by remember { mutableStateOf<BetrayGenresViewModel.GenreRow?>(null) }
    var reordering by remember { mutableStateOf(false) }

    val editing = editId
    if (editing != null) {
        val target = if (editing == 0) null else rows?.firstOrNull { it.genre.genreId == editing }?.genre
        if (editing == 0 || target != null) {
            val editVm: BetrayGenreEditViewModel = viewModel(key = "betrayGenreEdit")
            BetrayGenreEditScreen(genre = target, viewModel = editVm, onClose = { editId = null; vm.refresh() })
            return
        }
        // 편집하려던 장르가 사라졌으면(목록을 다 읽은 뒤에도 없으면) 목록으로 돌아간다.
        if (rows != null) LaunchedEffect(Unit) { editId = null }
    }
    BackHandler(onBack = onBack)

    // 복구: 파일 고르기 → 장르별로 무엇이 바뀌는지 확인 → 배신 굿즈만 합치기로 불러오기
    val context = androidx.compose.ui.platform.LocalContext.current
    val restoreLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.inspectRestore(context, it) } }
    val restorePlan by vm.restorePlan.collectAsState()
    val isRestoring by vm.isRestoring.collectAsState()
    val restoreProgress by vm.restoreProgress.collectAsState()
    val restoreInspect by vm.inspectProgress.collectAsState()
    // 명조 마이페이지 데이터 입력과 같은 화면 묶음. 명조 전체 추출 파일이면 명조 항목도 보이지만 처음에는 배신 굿즈만 켜 둔다.
    com.example.wowagoodsproject.screen.mypage.ImportFlow(
        inspectProgress = restoreInspect,
        plan = restorePlan,
        isImporting = isRestoring,
        importProgress = restoreProgress,
        betrayTitle = "배신 굿즈 장르 불러오기",
        initialSelection = setOf(com.example.wowagoodsproject.screen.mypage.ImportPart.BETRAY),
        onImport = { uri, selected -> vm.restore(context, uri, selected) },
        onDismiss = { vm.dismissRestore() }
    )

    deleteTarget?.let { row ->
        AlertDialog(
            shape = RectangleShape,
            onDismissRequest = { deleteTarget = null },
            title = { Text("'${row.genre.genreNm}' 장르를 삭제할까요?") },
            text = { Text("이 장르의 굿즈 ${row.total}개와 사진, 선호 캐릭터가 모두 지워집니다. 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(
                    onClick = { vm.deleteGenre(row.genre); deleteTarget = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("취소") } }
        )
    }

    // 순서 변경 중: 장르를 끌어 옮긴다. 손을 떼면 그 순서를 저장한다. 뒤로 가기는 순서 변경만 끝낸다.
    BackHandler(enabled = reordering) { reordering = false }
    val gridState = rememberLazyGridState()
    // 끄는 동안 보일 순서. 끄는 손이 같은 상태를 계속 보도록 한 번만 만들고, 저장된 목록이 바뀌면 맞춘다.
    var order by remember { mutableStateOf(rows.orEmpty()) }
    var dragId by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(rows) { if (dragId == null) order = rows.orEmpty() }
    // 끄는 손가락 위치(목록 기준)와, 잡은 지점(장르 칸 안 기준)
    var dragPointer by remember { mutableStateOf(Offset.Zero) }
    var dragGrab by remember { mutableStateOf(Offset.Zero) }
    fun startDrag(id: Int, local: Offset) {
        val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } ?: return
        dragId = id
        dragGrab = local
        dragPointer = Offset(info.offset.x + local.x, info.offset.y + local.y)
    }
    fun moveDrag(delta: Offset) {
        val id = dragId ?: return
        dragPointer += delta
        val items = gridState.layoutInfo.visibleItemsInfo
        val cur = items.firstOrNull { it.key == id } ?: return
        // 끄는 칸의 가운데가 들어간 다른 칸 자리로 옮긴다.
        val cx = dragPointer.x - dragGrab.x + cur.size.width / 2f
        val cy = dragPointer.y - dragGrab.y + cur.size.height / 2f
        val target = items.firstOrNull {
            it.key != id && cx >= it.offset.x && cx <= it.offset.x + it.size.width && cy >= it.offset.y && cy <= it.offset.y + it.size.height
        } ?: return
        val from = order.indexOfFirst { it.genre.genreId == id }
        val to = order.indexOfFirst { it.genre.genreId == target.key }
        if (from < 0 || to < 0) return
        order = order.toMutableList().apply { add(to, removeAt(from)) }
    }
    fun endDrag() {
        if (dragId == null) return
        dragId = null
        // 바뀐 장르만 저장된다.
        vm.setOrder(order.map { it.genre.genreId })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = "배신 굿즈",
            subtitle = "다른 장르의 굿즈를 따로 모아 둡니다",
            onBack = onBack,
            actions = {
                // 장르 추가: 새로 만들기 / 복구(장르 마이페이지에서 추출한 파일)
                Box {
                    var addMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { addMenu = true }) {
                        Icon(Icons.Default.Add, contentDescription = "장르 추가", tint = MaterialTheme.colorScheme.primary)
                    }
                    DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("추가") },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                            onClick = { addMenu = false; editId = 0 }
                        )
                        DropdownMenuItem(
                            text = { Text("복구") },
                            leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) },
                            onClick = {
                                addMenu = false
                                restoreLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/x-zip", "application/octet-stream"))
                            }
                        )
                    }
                }
            }
        )
        val list = rows
        // 헤더와 목록 사이 한 줄: 왼쪽은 보기 전환(배경 / 아이콘), 오른쪽은 순서 변경(장르가 둘 이상일 때만, '완료'로 끝난다).
        if (!list.isNullOrEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GenreViewToggle(
                    mode = viewMode,
                    onMode = { vm.setViewMode(it) }
                )
                Spacer(modifier = Modifier.weight(1f))
                if (list.size >= 2) {
                    TextButton(onClick = { endDrag(); reordering = !reordering }, shape = RectangleShape) {
                        Icon(if (reordering) Icons.Default.Check else Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (reordering) "완료" else "순서 변경", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> EmptyState(
                icon = Icons.Outlined.Category,
                title = "아직 장르가 없습니다",
                message = "오른쪽 위 '+'로 새 장르를 만들거나 백업에서 복구하세요"
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = if (viewMode == GenreViewMode.GRID) 160.dp else 104.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(if (viewMode == GenreViewMode.GRID) 12.dp else 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(if (reordering) order else list, key = { it.genre.genreId }) { row ->
                    val id = row.genre.genreId
                    val menu: @Composable () -> Unit = {
                        if (reordering) {
                            // 순서 변경 중에는 메뉴 대신 끌 수 있다는 표시
                            Icon(
                                Icons.Default.DragIndicator,
                                contentDescription = "끌어서 옮기기",
                                modifier = Modifier
                                    .padding(8.dp)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                                    .padding(4.dp)
                            )
                        } else GenreMenu(
                            onEdit = { editId = id },
                            onDelete = { deleteTarget = row }
                        )
                    }
                    val itemModifier = if (reordering && dragId == id) {
                        val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
                        Modifier
                            .zIndex(1f)
                            .graphicsLayer {
                                if (info != null) {
                                    translationX = dragPointer.x - dragGrab.x - info.offset.x
                                    translationY = dragPointer.y - dragGrab.y - info.offset.y
                                }
                                scaleX = 1.05f
                                scaleY = 1.05f
                                alpha = 0.9f
                            }
                    } else Modifier.animateItem()
                    val dragModifier = if (reordering) Modifier.pointerInput(id) {
                        detectDragGestures(
                            onDragStart = { startDrag(id, it) },
                            onDrag = { change, delta -> change.consume(); moveDrag(delta) },
                            onDragEnd = { endDrag() },
                            onDragCancel = { endDrag() }
                        )
                    } else Modifier
                    val onClick: (() -> Unit)? = if (reordering) null else ({ onOpen(row.genre) })
                    Box(modifier = itemModifier.then(dragModifier)) {
                        if (viewMode == GenreViewMode.GRID) GenreGridCard(row, onClick = onClick, menu = menu)
                        else GenreCircleCard(row, onClick = onClick, menu = menu)
                    }
                }
            }
        }
    }
}

/**
 * 장르 목록 보기 전환: [배경] [아이콘] 두 칸. 고른 쪽이 진하게 칠해진다.
 * 아이콘 보기는 동그라미 셋 아이콘(마이페이지의 사람 아이콘과 헷갈리지 않게).
 */
@Composable
private fun GenreViewToggle(mode: GenreViewMode, onMode: (GenreViewMode) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        listOf(
            Triple(GenreViewMode.GRID, "배경", Icons.Default.GridView),
            Triple(GenreViewMode.CIRCLE, "아이콘", Icons.Default.Workspaces)
        ).forEach { (m, label, icon) ->
            val selected = mode == m
            val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            TextButton(
                onClick = { onMode(m) },
                shape = RectangleShape,
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.semantics { contentDescription = if (m == GenreViewMode.GRID) "배경으로 보기" else "아이콘으로 보기" }
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = color)
            }
        }
    }
}

/** 그리드 보기: 배경 사진(없으면 아이콘)을 직사각형으로 */
@Composable
private fun GenreGridCard(row: BetrayGenresViewModel.GenreRow, onClick: (() -> Unit)?, menu: @Composable () -> Unit) {
    // 사진 아래 장르명·굿즈 현황은 가운데 정렬(아이콘 보기와 같게)
    Column(
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            CoverBox(
                path = row.genre.genreImgPath.ifEmpty { row.genre.genreIconPath },
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f),
                emptyIcon = Icons.Outlined.Category
            )
            Box(modifier = Modifier.align(Alignment.TopEnd)) { menu() }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(row.genre.genreNm, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text("굿즈 ${row.total}개 · 보유 ${row.gotten}", style = AppStyles.textCardSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** 원 보기: 아이콘(없으면 배경 사진 가운데)을 원으로 */
@Composable
private fun GenreCircleCard(row: BetrayGenresViewModel.GenreRow, onClick: (() -> Unit)?, menu: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            CoverBox(
                path = row.genre.genreIconPath.ifEmpty { row.genre.genreImgPath },
                modifier = Modifier.padding(4.dp).size(84.dp).clip(CircleShape),
                emptyIcon = Icons.Outlined.Category
            )
            Box(modifier = Modifier.align(Alignment.TopEnd).offset(x = 12.dp, y = (-8).dp)) { menu() }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(row.genre.genreNm, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text("굿즈 ${row.total} · 보유 ${row.gotten}", style = AppStyles.textCardSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun GenreMenu(onEdit: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "장르 메뉴",
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                    .padding(2.dp)
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("편집") },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                onClick = { open = false; onEdit() }
            )
            DropdownMenuItem(
                text = { Text("삭제", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = { open = false; onDelete() }
            )
        }
    }
}

// ── 장르 안의 탭 화면 ───────────────────────────────────────────────

/** 장르 안의 탭. 공식·2차창작은 등록 방식이 같아서 '굿즈' 탭 하나로 합쳤다(굿즈의 종류 칸은 그대로 남는다). */
/**
 * 장르 안의 하단 탭. 명조 하단과 같은 다섯 칸·같은 순서(공식 · 비공식 · 굿즈 검색 · 캐릭터별 · 마이페이지).
 * 이름과 아이콘은 명조 하단 정의([BottomNavItem])를 그대로 쓴다.
 */
private enum class BetrayTab(val item: com.example.wowagoodsproject.navigation.BottomNavItem) {
    OFFICIAL(com.example.wowagoodsproject.navigation.BottomNavItem.Series),
    FAN(com.example.wowagoodsproject.navigation.BottomNavItem.FanArt),
    SEARCH(com.example.wowagoodsproject.navigation.BottomNavItem.Search),
    CHARA(com.example.wowagoodsproject.navigation.BottomNavItem.Character),
    MYPAGE(com.example.wowagoodsproject.navigation.BottomNavItem.MyPage);

    val label get() = item.label
    val selected get() = item.selectedIcon
    val unselected get() = item.unselectedIcon
    /** 배신 굿즈는 명조 캐릭터 그림을 쓰지 않고 머티리얼 아이콘(예전 아이콘)을 쓴다 */
    val iconRes: Int? get() = null
}

/** 공식·비공식 탭이 보여 줄 굿즈 종류 */
private val BetrayTab.kind: BetrayKind get() = if (this == BetrayTab.FAN) BetrayKind.FAN else BetrayKind.OFFICIAL
private fun tabOf(kind: BetrayKind) = if (kind == BetrayKind.FAN) BetrayTab.FAN else BetrayTab.OFFICIAL

/** 등록 화면을 열 때 미리 채울 값 */
private data class AddRequest(val kind: BetrayKind, val series: String)

@Composable
private fun BetrayGenreApp(widthSizeClass: WindowWidthSizeClass, viewModel: BetrayViewModel, onExit: () -> Unit) {
    val genre by viewModel.genre.collectAsState()
    val goods by viewModel.goods.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val isLandscape = widthSizeClass != WindowWidthSizeClass.Compact

    var tab by rememberSaveable { mutableStateOf(BetrayTab.OFFICIAL) }
    var openSeries by rememberSaveable { mutableStateOf<String?>(null) }      // 공식 탭에서 연 시리즈
    var openFanSeries by rememberSaveable { mutableStateOf<String?>(null) }   // 비공식 탭에서 연 시리즈
    fun openSeriesOf(kind: BetrayKind) = if (kind == BetrayKind.FAN) openFanSeries else openSeries
    fun setOpenSeries(kind: BetrayKind, name: String?) { if (kind == BetrayKind.FAN) openFanSeries = name else openSeries = name }
    var openChara by rememberSaveable { mutableStateOf<String?>(null) }    // 캐릭터별 탭에서 연 캐릭터
    var mySection by rememberSaveable { mutableStateOf<String?>(null) }   // 마이페이지에서 연 목록("gotten"/"pending")
    var addRequest by remember { mutableStateOf<AddRequest?>(null) }
    var editingGenre by rememberSaveable { mutableStateOf(false) }
    // 캐릭터 추가·편집. null 이면 닫힘, "" 이면 새 캐릭터, 그 밖에는 고칠 캐릭터 이름
    var charaEdit by rememberSaveable { mutableStateOf<String?>(null) }
    val registeredCharas by viewModel.charas.collectAsState()
    val registeredSeries by viewModel.series.collectAsState()
    // 시리즈 추가·편집. null 이면 닫힘, "" 이면 새 시리즈, 그 밖에는 고칠 시리즈 이름
    var seriesEdit by rememberSaveable { mutableStateOf<String?>(null) }
    val allBetrayCharas = remember(goods, favorites, registeredCharas) { betrayCharas(goods, favorites, registeredCharas) }
    /** 공식·비공식 탭의 시리즈 카드. 등록만 하고 굿즈가 없는 시리즈는 두 탭 모두에, 다른 종류에만 굿즈가 있는 시리즈는 그 탭에만 보인다. */
    fun seriesCardsOf(kind: BetrayKind) = run {
        val byName = allBetrayCharas.associateBy { it.name }
        val mine = goods.filter { it.kind == kind }
        val otherOnly = goods.filter { it.kind != kind }.map { it.goodsSeries.trim() }.toSet() - mine.map { it.goodsSeries.trim() }.toSet()
        com.example.wowagoodsproject.component.seriesCards(
            goods = mine,
            seriesOf = { it.goodsSeries },
            statusOf = { it.status },
            imageOf = { it.goodsImgPath },
            dateOf = { it.goodsRegDate },
            charaOf = { it.goodsChara },
            registered = registeredSeries.filter { it.seriesNm !in otherOnly }.associate { it.seriesNm to it.seriesImgPath },
            registeredDates = registeredSeries.associate { it.seriesNm to it.seriesDate },
            charaInfo = { n -> byName[n]?.let { c -> c.cover to (if (c.favorite) c.favoriteOrder else null) } ?: ("" to null) },
            noSeriesName = NO_SERIES
        )
    }
    val exportPreview by viewModel.exportPreview.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val addVm: BetrayAddViewModel = viewModel(key = "betrayAdd_${viewModel.genreId}")

    // 데이터 입력: 파일 고르기 → 이 장르와 이름이 같은 장르만 확인 → 합치기로 불러오기(없으면 거부)
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.inspectImport(context, it) } }
    val importPlan by viewModel.importPlan.collectAsState()
    val importInspect by viewModel.importInspect.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()
    // 명조 마이페이지 데이터 입력과 같은 화면 묶음
    com.example.wowagoodsproject.screen.mypage.ImportFlow(
        inspectProgress = importInspect,
        plan = importPlan,
        isImporting = isImporting,
        importProgress = importProgress,
        betrayTitle = "'${genre?.genreNm.orEmpty()}' 장르 불러오기",
        initialSelection = setOf(com.example.wowagoodsproject.screen.mypage.ImportPart.BETRAY),
        // 확인 창에서 장르 항목을 끄고 누르면 아무것도 바꾸지 않는다.
        onImport = { uri, selected ->
            if (com.example.wowagoodsproject.screen.mypage.ImportPart.BETRAY in selected) viewModel.importData(context, uri)
            else viewModel.dismissImport()
        },
        onDismiss = { viewModel.dismissImport() }
    )

    // 지워진 장르(다른 곳에서 지웠거나 백업을 불러와 없어짐)면 장르 목록으로 돌아간다.
    val genreLoaded = remember { mutableStateOf(false) }
    LaunchedEffect(genre) { if (genre != null) genreLoaded.value = true else if (genreLoaded.value) onExit() }

    val listMode: ListModeViewModel = viewModel(key = "betrayListMode") { ListModeViewModel("betray") }

    // 알림으로 열 굿즈가 이 장르 것이면 굿즈 탭의 그 시리즈를 연다(상세는 굿즈 목록이 띄운다).
    val openRequest by BetrayOpenRequest.goods.collectAsState()
    LaunchedEffect(openRequest) {
        val req = openRequest ?: return@LaunchedEffect
        if (req.genreId != viewModel.genreId) return@LaunchedEffect
        editingGenre = false; charaEdit = null; seriesEdit = null; addRequest = null
        tab = tabOf(req.kind); setOpenSeries(req.kind, seriesKey(req)); openChara = null; mySection = null
    }
    val favoriteNames = favorites.map { it.charaNm }

    fun startAdd(kind: BetrayKind, series: String = "") {
        addVm.reset(kind, series)
        addRequest = AddRequest(kind, series)
    }
    fun openGoodsSeries(name: String, kind: BetrayKind) {
        tab = tabOf(kind); setOpenSeries(kind, name); openChara = null; mySection = null
    }

    val currentGenre = genre
    if (editingGenre && currentGenre != null) {
        val editVm: BetrayGenreEditViewModel = viewModel(key = "betrayGenreEdit_${viewModel.genreId}")
        BetrayGenreEditScreen(genre = currentGenre, viewModel = editVm, onClose = { editingGenre = false })
        return
    }

    val editingSeries = seriesEdit
    if (editingSeries != null) {
        val seriesVm: SeriesEditViewModel = viewModel(key = "betraySeriesEdit_${viewModel.genreId}")
        val reg = registeredSeries.firstOrNull { it.seriesNm == editingSeries }
        val dao = App.betrayDatabase.betrayDao()
        val genreId = viewModel.genreId
        val target = editingSeries.ifEmpty { null }
        SeriesEditScreen(
            key = "betraySeries:$genreId:$editingSeries",
            target = target,
            imgPath = reg?.seriesImgPath.orEmpty(),
            iconPath = reg?.seriesIconPath.orEmpty(),
            date = reg?.seriesDate.orEmpty(),
            subtitle = genre?.genreNm,
            photoPrefix = "betray_series_",
            viewModel = seriesVm,
            persist = { n, b, i, d -> dao.saveSeries(genreId, target, n, b, i, d) },
            onSaved = { saved ->
                if (openSeries != null && openSeries == target) openSeries = saved
                if (openFanSeries != null && openFanSeries == target) openFanSeries = saved
                seriesEdit = null
            },
            onClose = { seriesEdit = null },
            onDelete = if (reg != null) ({ viewModel.deleteSeries(editingSeries); seriesEdit = null }) else null
        )
        return
    }

    val editingChara = charaEdit
    if (editingChara != null) {
        val charaVm: BetrayCharaEditViewModel = viewModel(key = "betrayCharaEdit_${viewModel.genreId}")
        val target = if (editingChara.isEmpty()) CharaEditTarget("", "", registered = false)
        else betrayCharas(goods, favorites, registeredCharas).firstOrNull { it.name == editingChara }?.editTarget(registeredCharas)
            ?: CharaEditTarget(editingChara, "", registered = false)
        BetrayCharaEditScreen(
            genreId = viewModel.genreId,
            genreName = genre?.genreNm.orEmpty(),
            target = target,
            viewModel = charaVm,
            onClose = { charaEdit = null },
            onSaved = { saved ->
                // 캐릭터 화면을 보던 중이었으면 바뀐 이름으로 계속 본다.
                if (openChara != null) openChara = saved
                charaEdit = null
            },
            onDeleted = { charaEdit = null }
        )
        return
    }

    // 굿즈 상세의 '이 굿즈 수정' → 등록 화면을 그 굿즈로 채워 요약부터 연다.
    val editRequest by BetrayEditRequest.goods.collectAsState()
    LaunchedEffect(editRequest) {
        val g = editRequest ?: return@LaunchedEffect
        BetrayEditRequest.goods.value = null
        if (g.genreId != viewModel.genreId) return@LaunchedEffect
        addVm.loadForEdit(g)
        addRequest = AddRequest(g.kind, g.goodsSeries)
    }

    val request = addRequest
    if (request != null) {
        BetrayAddScreen(
            genreId = viewModel.genreId,
            existing = goods,
            registeredCharas = registeredCharas,
            registeredSeries = registeredSeries,
            viewModel = addVm,
            onNavigateBack = { addRequest = null }
        )
        return
    }

    // 하위 화면(시리즈·캐릭터·마이페이지 목록)을 연 상태면 뒤로가기로 닫고, 탭 첫 화면이면 장르 목록으로 나간다.
    val subOpen = when (tab) {
        BetrayTab.OFFICIAL -> openSeries != null
        BetrayTab.FAN -> openFanSeries != null
        BetrayTab.CHARA -> openChara != null
        BetrayTab.MYPAGE -> mySection != null
        else -> false
    }
    BackHandler {
        when {
            subOpen -> when (tab) {
                BetrayTab.OFFICIAL -> openSeries = null
                BetrayTab.FAN -> openFanSeries = null
                BetrayTab.CHARA -> openChara = null
                BetrayTab.MYPAGE -> mySection = null
                else -> Unit
            }
            tab != BetrayTab.OFFICIAL -> tab = BetrayTab.OFFICIAL
            else -> onExit()
        }
    }

    val genreName = genre?.genreNm.orEmpty()
    val onSeriesClick: (BetrayGoodsEntity) -> Unit = { g -> openGoodsSeries(seriesKey(g), g.kind) }

    val content: @Composable () -> Unit = {
        when (tab) {
            BetrayTab.OFFICIAL, BetrayTab.FAN -> {
                val kind = tab.kind
                val series = openSeriesOf(kind)
                if (series == null) {
                    com.example.wowagoodsproject.component.SeriesListPage(
                        title = tab.label,
                        subtitle = genreName,
                        onBack = onExit,
                        cards = remember(goods, registeredSeries, allBetrayCharas, kind) { seriesCardsOf(kind) },
                        charaChoices = allBetrayCharas.mapIndexed { i, c -> c.asCharaEntity(i + 1) },
                        isLandscape = isLandscape,
                        onOpen = { setOpenSeries(kind, it) },
                        emptyTitle = "${tab.label} 굿즈가 없습니다",
                        emptyMessage = "오른쪽 위 '+'로 시리즈와 굿즈를 추가하세요",
                        actions = {
                            // '+' → [굿즈 · 시리즈] (명조 비공식 탭과 같다)
                            com.example.wowagoodsproject.component.AddMenuButton(
                                onGoods = { startAdd(kind, "") },
                                onSeries = { seriesEdit = "" }
                            )
                        }
                    )
                } else {
                    BetrayGoodsBrowser(
                        title = series,
                        subtitle = genreName,
                        goods = goods.filter { it.kind == kind && seriesKey(it) == series },
                        viewModel = viewModel,
                        listModeViewModel = listMode,
                        widthSizeClass = widthSizeClass,
                        emptyTitle = "이 시리즈에 굿즈가 없습니다",
                        onBack = { setOpenSeries(kind, null) },
                        showSeriesFilter = false,
                        actions = {
                            if (series != NO_SERIES) TopBarAction(icon = Icons.Default.Edit, contentDescription = "시리즈 편집", onClick = { seriesEdit = series })
                            // 시리즈 안에서는 '+' 가 바로 이 시리즈의 굿즈 등록
                            com.example.wowagoodsproject.component.AddMenuButton(
                                onGoods = { startAdd(kind, if (series == NO_SERIES) "" else series) },
                                onSeries = null
                            )
                        }
                    )
                }
            }
            BetrayTab.CHARA -> {
                val chara = openChara
                if (chara == null) {
                    BetrayCharaList(
                        subtitle = genreName,
                        charas = remember(goods, favorites, registeredCharas) { betrayCharas(goods, favorites, registeredCharas) },
                        isLandscape = isLandscape,
                        onOpen = { openChara = it },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onReorderFavorites = { viewModel.reorderFavorites(it) },
                        onAdd = { charaEdit = "" },
                        onExit = onExit
                    )
                } else {
                    var kindIndex by rememberSaveable(chara) { mutableIntStateOf(0) }
                    val kind = BetrayKind.entries[kindIndex]
                    BetrayGoodsBrowser(
                        title = chara,
                        subtitle = genreName,
                        goods = goods.filter { it.kind == kind && hasChara(it.goodsChara, chara) },
                        viewModel = viewModel,
                        listModeViewModel = listMode,
                        widthSizeClass = widthSizeClass,
                        emptyTitle = "${kind.label} 굿즈가 없습니다",
                        onBack = { openChara = null },
                        tabs = {
                            SubTabStrip(
                                tabs = BetrayKind.entries.map { k -> "${k.label} ${goods.count { it.kind == k && hasChara(it.goodsChara, chara) }}" },
                                selectedIndex = kindIndex,
                                onSelect = { kindIndex = it },
                                vertical = isLandscape
                            )
                        },
                        tabSwipe = Triple(kindIndex, BetrayKind.entries.size) { kindIndex = it },
                        onSeriesClick = onSeriesClick,
                        actions = {
                            val favorite = favoriteNames.contains(chara)
                            TopBarAction(
                                icon = Icons.Default.Edit,
                                contentDescription = "캐릭터 편집",
                                onClick = { charaEdit = chara }
                            )
                            TopBarAction(
                                icon = if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "선호 캐릭터",
                                onClick = { viewModel.toggleFavorite(chara) },
                                active = false
                            )
                        }
                    )
                }
            }
            BetrayTab.SEARCH -> BetraySearch(
                genreName = genreName,
                goods = goods,
                viewModel = viewModel,
                listMode = listMode,
                widthSizeClass = widthSizeClass,
                onSeriesClick = onSeriesClick,
                onExit = onExit
            )
            BetrayTab.MYPAGE -> {
                val section = mySection
                if (section == null) {
                    BetrayMyPage(
                        genreName = genreName,
                        goods = goods,
                        charas = remember(goods, favorites, registeredCharas) { betrayCharas(goods, favorites, registeredCharas) },
                        isLandscape = isLandscape,
                        onOpenSection = { mySection = it },
                        onEdit = { editingGenre = true },
                        onExit = onExit,
                        exportPreview = exportPreview,
                        exportSelection = com.example.wowagoodsproject.screen.mypage.ExportSelection.genreOnly(viewModel.genreId),
                        isExporting = isExporting,
                        exportProgress = exportProgress,
                        onLoadExportPreview = { viewModel.loadExportPreview() },
                        onExport = { viewModel.exportGenre(context) },
                        onImport = {
                            importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/x-zip", "application/octet-stream"))
                        }
                    )
                } else {
                    val status = if (section == "pending") GoodsStatus.PENDING else GoodsStatus.GOTTEN
                    var kindIndex by rememberSaveable(section) { mutableIntStateOf(0) }
                    val kind = BetrayKind.entries[kindIndex]
                    val inStatus = goods.filter { it.status == status }
                    val pending = status == GoodsStatus.PENDING
                    BetrayGoodsBrowser(
                        title = if (pending) "구매예정 굿즈" else "보유 굿즈",
                        subtitle = genreName,
                        // 구매예정은 명조 마이페이지처럼 공식·2차창작을 한 목록에, 보유는 탭으로 나눈다.
                        goods = if (pending) inStatus else inStatus.filter { it.kind == kind },
                        viewModel = viewModel,
                        listModeViewModel = listMode,
                        widthSizeClass = widthSizeClass,
                        emptyTitle = if (pending) "구매예정으로 표시한 굿즈가 없습니다" else "아직 보유한 굿즈가 없습니다",
                        onBack = { mySection = null },
                        pendingMode = pending,
                        tabs = if (pending) null else ({
                            SubTabStrip(
                                tabs = BetrayKind.entries.map { k -> "${k.label} ${inStatus.count { it.kind == k }}" },
                                selectedIndex = kindIndex,
                                onSelect = { kindIndex = it },
                                vertical = isLandscape
                            )
                        }),
                        tabSwipe = if (pending) null else Triple(kindIndex, BetrayKind.entries.size) { kindIndex = it },
                        onSeriesClick = onSeriesClick
                    )
                }
            }
        }
    }

    val onTabClick: (BetrayTab) -> Unit = { t ->
        // 지금 탭을 다시 누르면 그 탭의 첫 화면으로 돌아간다(명조 쪽 하단 메뉴와 같다).
        if (t == tab) {
            when (t) {
                BetrayTab.OFFICIAL -> openSeries = null
                BetrayTab.FAN -> openFanSeries = null
                BetrayTab.CHARA -> openChara = null
                BetrayTab.MYPAGE -> mySection = null
                else -> Unit
            }
        }
        tab = t
    }
    val navColors = MaterialTheme.colorScheme
    if (isLandscape) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail(containerColor = navColors.surface, contentColor = navColors.primary, modifier = Modifier.fillMaxHeight()) {
                Spacer(modifier = Modifier.weight(1f))
                BetrayTab.entries.forEach { t ->
                    NavigationRailItem(
                        selected = tab == t,
                        onClick = { onTabClick(t) },
                        label = { Text(t.label) },
                        icon = { com.example.wowagoodsproject.navigation.TabIcon(tab == t, t.selected, t.unselected, t.iconRes) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = navColors.primary, selectedTextColor = navColors.primary,
                            unselectedIconColor = navColors.primary.copy(alpha = 0.5f), unselectedTextColor = navColors.primary.copy(alpha = 0.5f),
                            indicatorColor = navColors.surfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) { content() }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }
            // 바깥 화면이 시스템 바 여백을 이미 주므로 여기서는 더하지 않는다.
            NavigationBar(containerColor = navColors.surface, contentColor = navColors.primary, windowInsets = WindowInsets(0, 0, 0, 0)) {
                BetrayTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { onTabClick(t) },
                        label = { Text(t.label) },
                        icon = { com.example.wowagoodsproject.navigation.TabIcon(tab == t, t.selected, t.unselected, t.iconRes) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = navColors.primary, selectedTextColor = navColors.primary,
                            unselectedIconColor = navColors.primary.copy(alpha = 0.5f), unselectedTextColor = navColors.primary.copy(alpha = 0.5f),
                            indicatorColor = navColors.surfaceVariant
                        )
                    )
                }
            }
        }
    }
}

// ── 공식 탭: 시리즈 그리드 ───────────────────────────────────────────


@Composable
private fun CoverBox(path: String, modifier: Modifier, emptyIcon: ImageVector = Icons.Outlined.Image) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (path.isEmpty()) {
            Icon(emptyIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Image(
                painter = rememberAsyncImagePainter(path),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

// ── 검색 탭 ─────────────────────────────────────────────────────────

// ── 마이페이지 탭 ───────────────────────────────────────────────────

