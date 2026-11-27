package com.example.wowagoodsproject.screen.betray

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.wowagoodsproject.component.BulkSelection
import com.example.wowagoodsproject.component.BulkSelectionSlot
import com.example.wowagoodsproject.component.BulkStatusArea
import com.example.wowagoodsproject.component.BulkStatusButtons
import com.example.wowagoodsproject.component.CategoryChoiceGrid
import com.example.wowagoodsproject.component.CharaChoiceGrid
import com.example.wowagoodsproject.component.EmptyState
import com.example.wowagoodsproject.component.FilterBar
import com.example.wowagoodsproject.component.FilterDialogFrame
import com.example.wowagoodsproject.component.FilterType
import com.example.wowagoodsproject.component.GoodsDetailDialog
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.GridColumnsBar
import com.example.wowagoodsproject.component.ListModeViewModel
import com.example.wowagoodsproject.component.SearchField
import com.example.wowagoodsproject.component.SidePanelLayout
import com.example.wowagoodsproject.component.bulkStatusMessage
import com.example.wowagoodsproject.component.purchaseInfo
import com.example.wowagoodsproject.component.rememberGoodsSelection
import com.example.wowagoodsproject.component.rememberGridColumns
import com.example.wowagoodsproject.db.betray.BetrayGoodsEntity
import com.example.wowagoodsproject.db.betray.BetrayKind
import com.example.wowagoodsproject.db.betray.charaNames
import com.example.wowagoodsproject.navigation.TopBar
import com.example.wowagoodsproject.navigation.TopBarAction
import com.example.wowagoodsproject.screen.search.FilterPicker
import com.example.wowagoodsproject.screen.search.hasWords
import com.example.wowagoodsproject.screen.search.resultSection
import com.example.wowagoodsproject.screen.search.searchWords
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * 배신 굿즈 굿즈 검색. 명조 굿즈 검색(SearchScreen)과 같은 모양:
 * 검색창 → 캐릭터·카테고리 고르기(밑줄형) → 보유 상태 필터·다중 선택 → 공식 / 2차창작 구역으로 나눈 결과.
 * 데이터는 이 장르 배신 굿즈만 쓴다.
 */

/** 가로 화면에서 캐릭터·카테고리 고르기 버튼을 세우는 줄의 폭(명조 검색과 같다) */
private val PICKER_COLUMN_WIDTH = 140.dp

@Composable
internal fun BetraySearch(
    genreName: String,
    goods: List<BetrayGoodsEntity>,
    viewModel: BetrayViewModel,
    listMode: ListModeViewModel,
    widthSizeClass: WindowWidthSizeClass,
    onSeriesClick: (BetrayGoodsEntity) -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val favorites by viewModel.favorites.collectAsState()
    val registeredCharas by viewModel.charas.collectAsState()
    val isGridMode by listMode.isGridMode.collectAsState()
    val gridColumns = rememberGridColumns(listMode, widthSizeClass)
    val isLandscape = widthSizeClass != WindowWidthSizeClass.Compact
    val selection = rememberGoodsSelection()
    BackHandler(enabled = selection.mode) { selection.exit() }

    var query by rememberSaveable { mutableStateOf("") }
    var charaFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var statusFilter by rememberSaveable { mutableStateOf(FilterType.ALL) }
    var showCharaDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<Int?>(null) }

    fun BetrayGoodsEntity.hasChara(name: String?) = name == null || name in charaNames(goodsChara)
    fun BetrayGoodsEntity.hasCategory(name: String?) = name == null || goodsCategory == name
    fun BetrayGoodsEntity.hasStatus(type: FilterType) = when (type) {
        FilterType.ALL -> true
        FilterType.GOTTEN -> status == GoodsStatus.GOTTEN
        FilterType.NOT_GOTTEN -> status == GoodsStatus.NOT_GOTTEN
        FilterType.PENDING -> status == GoodsStatus.PENDING
    }

    val words = remember(query) { searchWords(query) }
    val hasFilter = words.isNotEmpty() || charaFilter != null || categoryFilter != null
    val textMatched = remember(goods, words) { goods.filter { it.hasWords(words) } }
    val results = remember(textMatched, charaFilter, categoryFilter, hasFilter) {
        if (!hasFilter) emptyList() else textMatched.filter { it.hasChara(charaFilter) && it.hasCategory(categoryFilter) }
    }
    val shown = remember(results, statusFilter) { sortForList(results.filter { it.hasStatus(statusFilter) }) }
    val shownOfficial = shown.filter { it.kind == BetrayKind.OFFICIAL }
    val shownFan = shown.filter { it.kind == BetrayKind.FAN }

    // 고를 수 있는 값은 다른 쪽 필터에 맞는 굿즈에 실제로 있는 것만(명조 검색과 같다).
    val charaChoices = remember(textMatched, categoryFilter, favorites, registeredCharas) {
        val names = textMatched.filter { it.hasCategory(categoryFilter) }.flatMap { charaNames(it.goodsChara) }.toSet()
        betrayCharas(goods, favorites, registeredCharas).filter { it.name in names }.mapIndexed { i, c -> c.asCharaEntity(i + 1) }
    }
    val categoryChoices = remember(textMatched, charaFilter) {
        textMatched.filter { it.hasChara(charaFilter) }.map { it.goodsCategory }.filter { it.isNotBlank() }.distinct().sorted()
    }

    if (showCharaDialog) {
        var search by remember { mutableStateOf("") }
        FilterDialogFrame(
            title = "캐릭터",
            onDismiss = { showCharaDialog = false },
            onClear = if (charaFilter != null) ({ charaFilter = null; showCharaDialog = false }) else null
        ) {
            SearchField(query = search, onQueryChange = { search = it }, placeholder = "캐릭터 이름 검색")
            Spacer(modifier = Modifier.height(12.dp))
            CharaChoiceGrid(
                charas = charaChoices.filter { it.charaNm.contains(search, ignoreCase = true) },
                selectedName = charaFilter,
                onSelect = { c -> charaFilter = if (charaFilter == c.charaNm) null else c.charaNm; showCharaDialog = false },
                modifier = Modifier.weight(1f)
            )
        }
    }
    if (showCategoryDialog) {
        var search by remember { mutableStateOf("") }
        FilterDialogFrame(
            title = "카테고리",
            onDismiss = { showCategoryDialog = false },
            onClear = if (categoryFilter != null) ({ categoryFilter = null; showCategoryDialog = false }) else null
        ) {
            SearchField(query = search, onQueryChange = { search = it }, placeholder = "카테고리 검색")
            Spacer(modifier = Modifier.height(12.dp))
            CategoryChoiceGrid(
                categories = categoryChoices.filter { it.contains(search, ignoreCase = true) },
                scopeGoods = textMatched.filter { it.hasChara(charaFilter) },
                selected = categoryFilter,
                onSelect = { cat -> categoryFilter = if (categoryFilter == cat) null else cat; showCategoryDialog = false },
                modifier = Modifier.weight(1f)
            )
        }
    }

    // 상세: 상태를 바꾼 뒤에도 최신 값을 보이도록 id 로 다시 찾는다.
    goods.find { it.goodsId == selectedId }?.let { g ->
        GoodsDetailDialog(
            itemKey = com.example.wowagoodsproject.component.goodsIdentity(g),
            imgPath = g.imgPath,
            series = g.series,
            chara = g.chara,
            category = g.category,
            price = g.price,
            isGotten = g.isGotten,
            isPending = g.status == GoodsStatus.PENDING,
            memo = g.memo,
            onDismiss = { selectedId = null },
            onToggleGotten = { viewModel.toggleGotten(g) },
            onSetPending = { viewModel.togglePending(g) },
            onEdit = { BetrayEditRequest.goods.value = g },
            onDelete = { viewModel.delete(g); selectedId = null },
            onSeriesClick = { selectedId = null; onSeriesClick(g) },
            purchaseInfo = g.purchaseInfo,
            onSavePendingInfo = { info -> viewModel.savePending(g, info) },
            shippingDate = g.shippingDate,
            onToggleShipping = {
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date())
                viewModel.setShipping(g, if (g.shippingDate.isEmpty()) today else "")
            },
            quantity = g.quantity,
            onQuantityChange = { q -> viewModel.setQuantity(g, q) }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = "굿즈 검색",
            subtitle = genreName,
            onBack = onExit,
            actions = {
                TopBarAction(
                    icon = if (isGridMode) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                    contentDescription = "보기 방식 전환",
                    onClick = { listMode.toggleGridMode() }
                )
            }
        )
        val picked = selection.visible(shown) { it.goodsId }
        val bulk = BulkSelection(
            selectedItems = picked,
            allSelected = shown.isNotEmpty() && picked.size == shown.size,
            onSelectAll = { selection.selectAll(shown.map { it.goodsId }) },
            onClearSelection = { selection.clear() },
            onApply = { status, edit ->
                viewModel.applyStatus(picked, status, edit)
                selection.clear()
                Toast.makeText(context, bulkStatusMessage(picked.size, status), Toast.LENGTH_SHORT).show()
            }
        )
        SearchField(
            query = query,
            onQueryChange = { query = it },
            placeholder = "시리즈, 캐릭터, 카테고리, 메모로 검색",
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
        )
        SidePanelLayout(
            sidePanel = isLandscape,
            tabsWidth = PICKER_COLUMN_WIDTH,
            tabs = {
                val charaPicker: @Composable (Modifier) -> Unit = { m ->
                    FilterPicker(label = "캐릭터", value = charaFilter, onClick = { showCharaDialog = true }, onClear = { charaFilter = null }, modifier = m)
                }
                val categoryPicker: @Composable (Modifier) -> Unit = { m ->
                    FilterPicker(label = "카테고리", value = categoryFilter, onClick = { showCategoryDialog = true }, onClear = { categoryFilter = null }, modifier = m)
                }
                if (isLandscape) {
                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        charaPicker(Modifier.fillMaxWidth())
                        categoryPicker(Modifier.fillMaxWidth())
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        charaPicker(Modifier.weight(1f))
                        categoryPicker(Modifier.weight(1f))
                    }
                }
            },
            panel = {
                FilterBar(
                    filterType = statusFilter,
                    onFilterChange = { statusFilter = it },
                    goodsList = results,
                    selectionMode = selection.mode,
                    onToggleSelection = { selection.toggleMode() },
                    vertical = isLandscape,
                    selectionContent = { BulkSelectionSlot(bulk, vertical = isLandscape) }
                )
            }
        ) {
            GridColumnsBar(listMode, widthSizeClass)
            BulkStatusArea {
                if (selection.mode && !isLandscape) BulkStatusButtons(bulk)
                when {
                    !hasFilter -> EmptyState(
                        icon = Icons.Default.Search,
                        title = "굿즈를 검색해 보세요",
                        message = "검색어를 넣거나 캐릭터·카테고리를 고르면\n공식 굿즈와 2차창작 굿즈를 한꺼번에 찾습니다.\n여러 낱말을 띄어 쓰면 모두 들어간 굿즈만 나옵니다."
                    )
                    shown.isEmpty() -> EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = "검색 결과가 없습니다",
                        message = "다른 검색어를 넣거나 조건을 바꿔 보세요"
                    )
                    // 보기 방식을 바꾸면 줌 애니메이션으로 넘긴다(ViewSwitchAnimation.kt)
                    else -> com.example.wowagoodsproject.component.GridListSwitch(isGridMode) { grid ->
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            val onClick: (BetrayGoodsEntity) -> Unit = { g ->
                                if (selection.mode) selection.toggle(g.goodsId) else selectedId = if (selectedId == g.goodsId) null else g.goodsId
                            }
                            val selectedOf: (BetrayGoodsEntity) -> Boolean? = { g -> if (selection.mode) g.goodsId in selection.ids else null }
                            resultSection("공식", shownOfficial, grid, gridColumns, { "official_${it.goodsId}" }, selectedOf, onClick)
                            resultSection("2차창작", shownFan, grid, gridColumns, { "fan_${it.goodsId}" }, selectedOf, onClick)
                        }
                    }
                }
            }
        }
    }
}
