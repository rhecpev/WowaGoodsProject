package com.example.wowagoodsproject.screen.search

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.wowagoodsproject.component.BulkStatusButtons
import com.example.wowagoodsproject.component.BulkSelectionSlot
import com.example.wowagoodsproject.component.BulkSelection
import com.example.wowagoodsproject.component.GridColumnsBar
import com.example.wowagoodsproject.component.rememberGridColumns
import com.example.wowagoodsproject.component.BulkStatusArea
import com.example.wowagoodsproject.component.CATEGORY_SET
import com.example.wowagoodsproject.component.CategoryChoiceList
import com.example.wowagoodsproject.component.CharaChoiceGrid
import com.example.wowagoodsproject.component.EmptyState
import com.example.wowagoodsproject.component.FilterBar
import com.example.wowagoodsproject.component.FilterDialogFrame
import com.example.wowagoodsproject.component.FilterType
import com.example.wowagoodsproject.component.GoodsDetailDialog
import com.example.wowagoodsproject.component.GoodsDetailViewModel
import com.example.wowagoodsproject.component.GoodsGridItem
import com.example.wowagoodsproject.component.GoodsItem
import com.example.wowagoodsproject.component.GoodsListItem
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.ListModeViewModel
import com.example.wowagoodsproject.component.SearchField
import com.example.wowagoodsproject.component.SetGoodsDetailDialog
import com.example.wowagoodsproject.component.SidePanelLayout
import com.example.wowagoodsproject.component.asGottenStatus
import com.example.wowagoodsproject.component.bulkStatusMessage
import com.example.wowagoodsproject.component.findSetGoods
import com.example.wowagoodsproject.component.getSetComponents
import com.example.wowagoodsproject.component.purchaseInfo
import com.example.wowagoodsproject.component.rememberGoodsSelection
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.navigation.TopBar
import com.example.wowagoodsproject.navigation.TopBarAction

/**
 * 굿즈 검색 탭. 공식/2차창작 굿즈를 한꺼번에 찾는다.
 * 맨 위 검색창에 글자를 넣으면 시리즈·캐릭터·카테고리·메모·가격에 그 낱말이 모두 들어 있는 굿즈를 찾는다.
 * 그 아래 왼쪽에서 캐릭터, 오른쪽에서 카테고리를 고르면 둘 다 맞는 굿즈만 남긴다. 셋 중 하나만 써도 된다.
 * 그 결과를 보유 상태로 한 번 더 거를 수 있고, 다중 선택으로 여러 굿즈의 상태를 한꺼번에 바꿀 수 있다.
 */
@Composable
fun SearchScreen(
    widthSizeClass: WindowWidthSizeClass,
    viewModel: SearchViewModel = viewModel(),
    detailViewModel: GoodsDetailViewModel = viewModel(),
    listModeViewModel: ListModeViewModel = viewModel(),
    onNavigateToSeries: (String) -> Unit = {}
) {
    val query by viewModel.query.collectAsState()
    val charaFilter by viewModel.charaFilter.collectAsState()
    val categoryFilter by viewModel.categoryFilter.collectAsState()
    val statusFilter by viewModel.statusFilter.collectAsState()
    val allCharaList by viewModel.allCharaList.collectAsState()
    val officialGoods by viewModel.officialGoods.collectAsState()
    val fanGoods by viewModel.fanGoods.collectAsState()
    val selectedGoods by detailViewModel.selectedGoods.collectAsState()
    val isGridMode by listModeViewModel.isGridMode.collectAsState()
    val gridColumns = rememberGridColumns(listModeViewModel, widthSizeClass)
    val isLandscape = widthSizeClass != WindowWidthSizeClass.Compact
    val context = LocalContext.current
    // 공식과 2차창작이 한 목록에 섞여 id 가 겹칠 수 있으므로, 2차창작은 음수로 바꿔 기억한다(selectionKey).
    val selection = rememberGoodsSelection()
    BackHandler(enabled = selection.mode) { selection.exit() }

    var selectedSetGoods by remember { mutableStateOf<GoodsEntity?>(null) }
    var setDialogComponent by remember { mutableStateOf<GoodsEntity?>(null) }
    var showCharaDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }

    // 세트 굿즈는 다른 목록처럼 노출하지 않고 구성품만 보여준다.
    val listedOfficial = remember(officialGoods) { officialGoods.filter { it.category != CATEGORY_SET } }
    val words = remember(query) { searchWords(query) }
    val hasFilter = words.isNotEmpty() || charaFilter != null || categoryFilter != null
    // 검색어에 맞는 굿즈. 캐릭터·카테고리 결과와 고를 수 있는 값 모두 이 안에서 찾는다.
    val textMatched = remember(listedOfficial, fanGoods, words) {
        listedOfficial.filter { it.hasWords(words) } to fanGoods.filter { it.hasWords(words) }
    }
    val officialResults = remember(textMatched, charaFilter, categoryFilter) {
        if (!hasFilter) emptyList()
        else textMatched.first.filter { it.hasChara(charaFilter) && it.hasCategory(categoryFilter) }
    }
    val fanResults = remember(textMatched, charaFilter, categoryFilter) {
        if (!hasFilter) emptyList()
        else textMatched.second.filter { it.hasChara(charaFilter) && it.hasCategory(categoryFilter) }
    }
    // 화면에 실제로 보여 줄 목록: 위 결과를 보유 상태로 한 번 더 거른다.
    val shownOfficial = remember(officialResults, statusFilter) { officialResults.filter { it.hasStatus(statusFilter) } }
    val shownFan = remember(fanResults, statusFilter) { fanResults.filter { it.hasStatus(statusFilter) } }

    // 고를 수 있는 값은 다른 쪽 필터에 맞는 굿즈에 실제로 있는 것만 보여 주어, 결과가 없는 조합을 고르지 않게 한다.
    val charaChoices = remember(textMatched, categoryFilter, allCharaList) {
        val names = (textMatched.first + textMatched.second)
            .filter { it.hasCategory(categoryFilter) }
            .flatMap { it.charaNames() }
            .toSet()
        allCharaList.filter { it.charaNm in names }
            .sortedWith(compareByDescending<CharaEntity> { it.charaIsFavorite }.thenBy { it.charaNm })
    }
    val categoryChoices = remember(textMatched, charaFilter) {
        (textMatched.first + textMatched.second)
            .filter { it.hasChara(charaFilter) }
            .map { it.category }
            .filter { it.isNotEmpty() && it != CATEGORY_SET }
            .distinct()
            .sorted()
    }

    if (showCharaDialog) {
        var search by remember { mutableStateOf("") }
        FilterDialogFrame(
            title = "캐릭터",
            onDismiss = { showCharaDialog = false },
            onClear = if (charaFilter != null) {
                { viewModel.setCharaFilter(null); showCharaDialog = false }
            } else null
        ) {
            SearchField(
                query = search,
                onQueryChange = { search = it },
                placeholder = "캐릭터 이름 검색",
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
            Spacer(modifier = Modifier.height(12.dp))
            CharaChoiceGrid(
                charas = charaChoices.filter { it.charaNm.contains(search, ignoreCase = true) },
                selectedName = charaFilter,
                onSelect = { chara ->
                    viewModel.setCharaFilter(if (charaFilter == chara.charaNm) null else chara.charaNm)
                    showCharaDialog = false
                },
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showCategoryDialog) {
        var search by remember { mutableStateOf("") }
        FilterDialogFrame(
            title = "카테고리",
            onDismiss = { showCategoryDialog = false },
            onClear = if (categoryFilter != null) {
                { viewModel.setCategoryFilter(null); showCategoryDialog = false }
            } else null
        ) {
            SearchField(
                query = search,
                onQueryChange = { search = it },
                placeholder = "카테고리 검색",
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
            Spacer(modifier = Modifier.height(12.dp))
            CategoryChoiceList(
                categories = categoryChoices.filter { it.contains(search, ignoreCase = true) },
                selected = categoryFilter,
                onSelect = { cat ->
                    viewModel.setCategoryFilter(if (categoryFilter == cat) null else cat)
                    showCategoryDialog = false
                },
                modifier = Modifier.weight(1f)
            )
        }
    }

    // 상태를 바꾼 뒤에도 상세 시트가 최신 값을 보여주도록 목록에서 다시 찾는다.
    val displayedGoods = selectedGoods?.let { selected ->
        when (selected) {
            is GoodsEntity -> officialGoods.find { it.goodsId == selected.goodsId } ?: selected
            is FanGoodsEntity -> fanGoods.find { it.fanGoodsId == selected.fanGoodsId } ?: selected
            else -> selected
        }
    }

    displayedGoods?.let { goods ->
        val official = goods as? GoodsEntity
        val fan = goods as? FanGoodsEntity
        val parentSet = official?.let { findSetGoods(it, officialGoods) }
        val setComponents = parentSet?.let { getSetComponents(it, officialGoods) } ?: emptyList()
        GoodsDetailDialog(
            imgPath = goods.imgPath,
            series = goods.series,
            chara = goods.chara,
            category = goods.category,
            price = goods.price,
            isGotten = goods.isGotten,
            memo = goods.memo,
            setGoods = parentSet,
            setComponentTotal = setComponents.size,
            setComponentGotten = setComponents.count { it.isGotten },
            onSetClick = {
                setDialogComponent = official
                selectedSetGoods = parentSet
            },
            onDismiss = { detailViewModel.dismissDialog() },
            onToggleGotten = {
                official?.let { viewModel.toggleOfficialGotten(it) }
                fan?.let { viewModel.toggleFanGotten(it) }
            },
            isPending = goods.status == GoodsStatus.PENDING,
            purchaseInfo = goods.purchaseInfo,
            quantity = goods.quantity,
            onQuantityChange = { q ->
                official?.let { viewModel.setOfficialQuantity(it, q) }
                fan?.let { viewModel.setFanQuantity(it, q) }
            },
            onSavePendingInfo = { info ->
                official?.let { viewModel.saveOfficialPendingInfo(it, info) }
                fan?.let { viewModel.saveFanPendingInfo(it, info) }
            },
            onSetPending = {
                official?.let { viewModel.setOfficialPending(it) }
                fan?.let { viewModel.setFanPending(it) }
            },
            onDelete = {},
            showDelete = false,
            onSeriesClick = { seriesName ->
                detailViewModel.dismissDialog()
                onNavigateToSeries(seriesName)
            }
        )
    }

    selectedSetGoods?.let { setGoods ->
        SetGoodsDetailDialog(
            setGoods = setGoods,
            components = getSetComponents(setGoods, officialGoods),
            initialComponent = setDialogComponent,
            onDismiss = { selectedSetGoods = null; setDialogComponent = null },
            onToggleGotten = { component -> viewModel.toggleOfficialGotten(component) },
            onSetPending = { component -> viewModel.setOfficialPending(component) },
            onBulkSetStatus = { status ->
                viewModel.bulkSetOfficialStatus(setGoods, status)
                selectedSetGoods = null
                setDialogComponent = null
            },
            highlightChara = charaFilter,
            highlightCategory = categoryFilter,
            onSavePendingInfo = { component, info -> viewModel.saveOfficialPendingInfo(component, info) },
            onBulkSavePending = { info ->
                viewModel.bulkSetOfficialStatus(setGoods, GoodsStatus.PENDING, info)
                selectedSetGoods = null
                setDialogComponent = null
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = "굿즈 검색",
            actions = {
                TopBarAction(
                    icon = if (isGridMode) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                    contentDescription = "보기 방식 전환",
                    onClick = { listModeViewModel.toggleGridMode() }
                )
            }
        )

        // 다중 선택으로 고른 굿즈와 일괄 변경 동작. 공식과 2차창작을 함께 다룬다.
        val pickedOfficial = selection.visible(shownOfficial) { it.selectionKey() }
        val pickedFan = selection.visible(shownFan) { it.selectionKey() }
        val pickedCount = pickedOfficial.size + pickedFan.size
        val shownCount = shownOfficial.size + shownFan.size
        val bulk = BulkSelection(
            selectedItems = pickedOfficial + pickedFan,
            allSelected = shownCount > 0 && pickedCount == shownCount,
            onSelectAll = {
                selection.selectAll(shownOfficial.map { it.selectionKey() } + shownFan.map { it.selectionKey() })
            },
            onClearSelection = { selection.clear() },
            onApply = { status, edit ->
                viewModel.applyStatus(pickedOfficial, pickedFan, status, edit)
                selection.clear()
                Toast.makeText(context, bulkStatusMessage(pickedCount, status), Toast.LENGTH_SHORT).show()
            }
        )

        SearchField(
            query = query,
            onQueryChange = { viewModel.setQuery(it) },
            placeholder = "시리즈, 캐릭터, 카테고리, 메모로 검색",
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
        )

        // 가로 화면에서는 캐릭터·카테고리 고르기와 상태 필터를 왼쪽 세로 줄들로 옮겨 목록이 쓸 높이를 넓힌다.
        // 상태 필터 줄이 맨 왼쪽, 캐릭터·카테고리 줄이 그 오른쪽에 선다.
        SidePanelLayout(
            sidePanel = isLandscape,
            tabsWidth = PICKER_COLUMN_WIDTH,
            tabs = {
                val charaPicker: @Composable (Modifier) -> Unit = { modifier ->
                    FilterPicker(
                        label = "캐릭터",
                        value = charaFilter,
                        onClick = { showCharaDialog = true },
                        onClear = { viewModel.setCharaFilter(null) },
                        modifier = modifier
                    )
                }
                val categoryPicker: @Composable (Modifier) -> Unit = { modifier ->
                    FilterPicker(
                        label = "카테고리",
                        value = categoryFilter,
                        onClick = { showCategoryDialog = true },
                        onClear = { viewModel.setCategoryFilter(null) },
                        modifier = modifier
                    )
                }
                if (isLandscape) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        charaPicker(Modifier.fillMaxWidth())
                        categoryPicker(Modifier.fillMaxWidth())
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        charaPicker(Modifier.weight(1f))
                        categoryPicker(Modifier.weight(1f))
                    }
                }
            },
            panel = {
                FilterBar(
                    filterType = statusFilter,
                    onFilterChange = { viewModel.setStatusFilter(it) },
                    goodsList = officialResults + fanResults,
                    selectionMode = selection.mode,
                    onToggleSelection = { selection.toggleMode() },
                    vertical = isLandscape,
                    // 다중 선택 중에는 잠긴 필터 칩 대신 그 자리에 선택 조작을 보여준다.
                    selectionContent = { BulkSelectionSlot(bulk, vertical = isLandscape) }
                )
            }
        ) {
            GridColumnsBar(listModeViewModel, widthSizeClass)
            BulkStatusArea {
                // 안내 줄은 필터 칩 자리에 있다. 가로 화면에서는 버튼도 왼쪽 줄 안에 있어 여기에 둘 것이 없다.
                if (selection.mode && !isLandscape) BulkStatusButtons(bulk)

                when {
                    !hasFilter -> EmptyState(
                        icon = Icons.Default.Search,
                        title = "굿즈를 검색해 보세요",
                        message = "검색어를 넣거나 캐릭터·카테고리를 고르면\n공식 굿즈와 2차창작 굿즈를 한꺼번에 찾습니다.\n여러 낱말을 띄어 쓰면 모두 들어간 굿즈만 나옵니다."
                    )
                    shownOfficial.isEmpty() && shownFan.isEmpty() -> EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = "검색 결과가 없습니다",
                        message = "다른 검색어를 넣거나 조건을 바꿔 보세요"
                    )
                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        val onClick: (GoodsItem) -> Unit = { item ->
                            if (selection.mode) selection.toggle(item.selectionKey()) else detailViewModel.selectGoods(item)
                        }
                        val selectedOf: (GoodsItem) -> Boolean? = { item ->
                            if (selection.mode) item.selectionKey() in selection.ids else null
                        }
                        resultSection(
                            "공식", shownOfficial, isGridMode, gridColumns,
                            { "official_${it.goodsId}" }, selectedOf, onClick
                        )
                        resultSection(
                            "2차창작", shownFan, isGridMode, gridColumns,
                            { "fan_${it.fanGoodsId}" }, selectedOf, onClick
                        )
                    }
                }
            }
        }
    }
}

/**
 * 다중 선택에서 굿즈를 구분하는 값. 공식과 2차창작의 id 가 겹치지 않게 2차창작은 음수로 바꾼다.
 */
private fun GoodsItem.selectionKey(): Int = when (this) {
    is GoodsEntity -> goodsId
    is FanGoodsEntity -> -fanGoodsId
    else -> 0
}

private fun GoodsItem.hasStatus(type: FilterType): Boolean = when (type) {
    FilterType.ALL -> true
    FilterType.GOTTEN -> isGotten
    FilterType.NOT_GOTTEN -> status == GoodsStatus.NOT_GOTTEN
    FilterType.PENDING -> status == GoodsStatus.PENDING
}

/** 가로 화면에서 캐릭터·카테고리 고르기 버튼을 세우는 줄의 폭 */
private val PICKER_COLUMN_WIDTH = 140.dp

/** 굿즈의 캐릭터 칸("A, B")을 이름별로 나눈다. */
private fun GoodsItem.charaNames(): List<String> =
    chara.split(",").map { it.trim() }.filter { it.isNotEmpty() }

/** [name] 이 null 이면 거르지 않는다. */
private fun GoodsItem.hasChara(name: String?): Boolean = name == null || name in charaNames()

/** [name] 이 null 이면 거르지 않는다. */
private fun GoodsItem.hasCategory(name: String?): Boolean = name == null || category == name

/** 검색창 글자를 띄어쓰기로 나눈 낱말들(소문자). 비어 있으면 검색어로 거르지 않는다. */
internal fun searchWords(query: String): List<String> =
    query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }

/** 낱말이 모두 시리즈·캐릭터·카테고리·메모·가격 어딘가에 들어 있는지. 대소문자는 가리지 않는다. */
internal fun GoodsItem.hasWords(words: List<String>): Boolean {
    if (words.isEmpty()) return true
    val text = listOf(series, chara, category, memo, price).joinToString(" ").lowercase()
    return words.all { it in text }
}

/**
 * 필터 하나를 고르는 버튼. 고르기 전에는 이름([label])만, 고른 뒤에는 고른 값과 지우기(X)를 보여준다.
 * 누르면 고르는 팝업을 연다.
 */
@Composable
private fun FilterPicker(
    label: String,
    value: String?,
    onClick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = value != null
    Surface(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value ?: label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "$label 필터 해제",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
        }
    }
}

/**
 * 구역 제목(이름 + 개수)과 그 아래 굿즈들. 결과가 없으면 구역째로 생략한다.
 * 격자 보기에서는 한 줄에 [gridColumns] 개씩 놓는다.
 */
private fun <T : GoodsItem> LazyListScope.resultSection(
    title: String,
    goods: List<T>,
    isGridMode: Boolean,
    gridColumns: Int,
    key: (T) -> String,
    /** 다중 선택 중이면 이 굿즈가 선택됐는지, 아니면 null */
    selectedOf: (T) -> Boolean?,
    onClick: (T) -> Unit
) {
    if (goods.isEmpty()) return
    item(key = "__header_$title") {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${goods.size}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (isGridMode) {
        items(goods.chunked(gridColumns), key = { row -> key(row.first()) }) { rowItems ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { item ->
                    Box(modifier = Modifier.weight(1f)) {
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
                            selected = selectedOf(item),
                            onClick = { onClick(item) }
                        )
                    }
                }
                repeat(gridColumns - rowItems.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }
        return
    }
    items(goods, key = key) { item ->
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
            selected = selectedOf(item),
            onClick = { onClick(item) }
        )
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}
