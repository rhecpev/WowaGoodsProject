package com.example.wowagoodsproject.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import coil.compose.rememberAsyncImagePainter
import com.example.wowagoodsproject.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.ui.theme.AppStyles

private enum class FilterTab(val label: String, val placeholder: String) {
    CHARA("캐릭터", "캐릭터 이름 검색"),
    SERIES("시리즈", "시리즈 검색"),
    CATEGORY("카테고리", "카테고리 검색")
}

/**
 * 캐릭터/시리즈/카테고리 탭으로 굿즈 목록을 거르는 필터 팝업.
 * [showChara] 가 false 면(이미 한 캐릭터만 보는 화면) 캐릭터 탭을 뺀다.
 * [onSeriesSelect] 를 넘기고 고를 시리즈가 있을 때만 시리즈 탭을 둔다.
 */
@Composable
fun GoodsFilterDialog(
    @Suppress("UNUSED_PARAMETER") widthSizeClass: WindowWidthSizeClass,
    charaList: List<CharaEntity>,
    categoryList: List<String>,
    selectedCharaFilter: String?,
    selectedCategoryFilter: String?,
    onCharaSelect: (String?) -> Unit,
    onCategorySelect: (String?) -> Unit,
    onClearFilter: () -> Unit,
    onDismiss: () -> Unit,
    seriesList: List<String> = emptyList(),
    selectedSeriesFilter: String? = null,
    onSeriesSelect: ((String?) -> Unit)? = null,
    showChara: Boolean = true,
    /**
     * 지금 화면이 보여 주는 굿즈(공식 먼저, 2차창작 뒤). 넘기면 카테고리·시리즈 칸의 대표 사진을 이 안에서만 고른다.
     * null 이면 DB 의 모든 굿즈에서 고른다(굿즈 목록이 아닌 화면).
     */
    scopeGoods: List<GoodsItem>? = null
) {
    // 대표 사진은 '그 칸을 고르면 실제로 목록에 나올 굿즈' 에서 고른다.
    // 그래서 다른 탭에 걸어 둔 필터(시리즈·캐릭터·카테고리)도 먼저 적용한다.
    val charaMatch: (GoodsItem) -> Boolean = { g -> selectedCharaFilter == null || hasChara(g.chara, selectedCharaFilter) }
    val categoryScope = remember(scopeGoods, selectedSeriesFilter, selectedCharaFilter) {
        scopeGoods?.filter { g -> (selectedSeriesFilter == null || g.series.trim() == selectedSeriesFilter) && charaMatch(g) }
    }
    val seriesScope = remember(scopeGoods, selectedCategoryFilter, selectedCharaFilter) {
        scopeGoods?.filter { g -> (selectedCategoryFilter == null || g.category == selectedCategoryFilter) && charaMatch(g) }
    }
    val tabs = listOfNotNull(
        FilterTab.CHARA.takeIf { showChara },
        // 걸어 둔 시리즈 필터는 목록이 비어도 풀 수 있게 탭을 남긴다.
        FilterTab.SERIES.takeIf { onSeriesSelect != null && (seriesList.isNotEmpty() || selectedSeriesFilter != null) },
        FilterTab.CATEGORY
    )
    var dialogTab by remember { mutableIntStateOf(0) }
    val tabIndex = dialogTab.coerceIn(0, tabs.lastIndex)
    val currentTab = tabs[tabIndex]
    var filterSearch by remember { mutableStateOf("") }
    val changeTab: (Int) -> Unit = { dialogTab = it; filterSearch = "" }

    val filteredCharaList = charaList
        // 선호 캐릭터를 정해 둔 우선순위대로 먼저, 나머지는 이름순.
        .sortedWith(
            compareByDescending<CharaEntity> { it.charaIsFavorite }
                .thenBy { if (it.charaIsFavorite) it.charaFavoriteOrder else 0 }
                .thenBy { it.charaNm }
        )
        .filter { it.charaNm.contains(filterSearch, ignoreCase = true) }
    val filteredSeriesList = seriesList.filter { it.contains(filterSearch, ignoreCase = true) }
    val filteredCategoryList = categoryList.filter { it.contains(filterSearch, ignoreCase = true) }

    val isActive: (FilterTab) -> Boolean = { tab ->
        when (tab) {
            FilterTab.CHARA -> selectedCharaFilter != null
            FilterTab.SERIES -> selectedSeriesFilter != null
            FilterTab.CATEGORY -> selectedCategoryFilter != null
        }
    }

    FilterDialogFrame(
        title = "필터",
        onDismiss = onDismiss,
        onClear = if (tabs.any(isActive)) {
            { onClearFilter(); onDismiss() }
        } else null
    ) {
        if (tabs.size > 1) {
            SecondaryTabRow(
                selectedTabIndex = tabIndex,
                containerColor = Color.Transparent
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = tabIndex == index,
                        onClick = { changeTab(index) },
                        text = { Text(if (isActive(tab)) "${tab.label} ·" else tab.label) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        SearchField(
            query = filterSearch,
            onQueryChange = { filterSearch = it },
            placeholder = currentTab.placeholder
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .swipeToChangeTab(tabIndex, tabs.size, changeTab)
        ) {
            when (currentTab) {
                FilterTab.CHARA -> CharaChoiceGrid(
                    charas = filteredCharaList,
                    selectedName = selectedCharaFilter,
                    onSelect = { chara ->
                        onCharaSelect(if (selectedCharaFilter == chara.charaNm) null else chara.charaNm)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxSize()
                )
                FilterTab.SERIES -> SeriesChoiceGrid(
                    seriesNames = filteredSeriesList,
                    scopeGoods = seriesScope,
                    selected = selectedSeriesFilter,
                    onSelect = { s ->
                        onSeriesSelect?.invoke(if (selectedSeriesFilter == s) null else s)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxSize()
                )
                FilterTab.CATEGORY -> CategoryChoiceGrid(
                    categories = filteredCategoryList,
                    scopeGoods = categoryScope,
                    selected = selectedCategoryFilter,
                    onSelect = { cat ->
                        onCategorySelect(if (selectedCategoryFilter == cat) null else cat)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * 필터 팝업 공통 틀. 굿즈 상세처럼 화면 아래에서 올라오는 바텀시트로 띄운다.
 * 제목 + 닫기(X) / 내용 / (필터가 걸려 있을 때만) 필터 해제 버튼.
 * content 는 ColumnScope 이므로 목록에 Modifier.weight(1f) 를 주면 남은 높이를 채운다.
 * 목록을 스크롤하던 손짓이 맨 위에 닿아도 시트가 닫히지 않고, 처음부터 맨 위에서 끌어내릴 때만 닫힌다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDialogFrame(
    title: String,
    onDismiss: () -> Unit,
    onClear: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val keepSheet = remember { KeepSheetWhileScrollingConnection() }
    // 가로 화면에서 검색어를 치는 동안은 손잡이·제목·필터 해제를 접어, 남은 낮은 높이에 검색창과 목록이 들어가게 한다.
    val typing = isTypingInLandscape()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = if (typing) null else ({ BottomSheetDefaults.DragHandle() }),
        // 앱 전체에서 둥근 박스를 걷어내는 흐름에 맞춰 시트 위 모서리도 각지게 둔다. 손잡이가 시트임을 알려 준다.
        shape = RectangleShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 목록이 짧아도 시트 높이가 들쭉날쭉하지 않게 높이를 정해 둔다.
                .fillMaxHeight(if (isLandscape) 1f else 0.8f)
                .nestedScroll(keepSheet)
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
        ) {
            if (!typing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "닫기")
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }
            content()
            if (onClear != null && !typing) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("필터 해제") }
            }
        }
    }
}

/**
 * 원형 아바타 + 이름으로 캐릭터를 고르는 그리드. 선택된 캐릭터는 테두리와 체크로 표시한다.
 * 여러 명을 고르는 곳에서는 [selectedNames] 로 선택 목록을 넘긴다.
 */
@Composable
fun CharaChoiceGrid(
    charas: List<CharaEntity>,
    selectedName: String?,
    onSelect: (CharaEntity) -> Unit,
    modifier: Modifier = Modifier,
    selectedNames: Set<String> = emptySet()
) {
    if (charas.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("검색 결과가 없습니다", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 84.dp),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(charas, key = { it.charaNm }) { chara ->
            CharaChoiceItem(
                chara = chara,
                selected = chara.charaNm == selectedName || chara.charaNm in selectedNames,
                onClick = { onSelect(chara) }
            )
        }
    }
}

@Composable
private fun CharaChoiceItem(
    chara: CharaEntity,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Box {
            Image(
                painter = rememberAsyncImagePainter(model = chara.charaUrl.ifEmpty { null }),
                contentDescription = null,
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(
                        if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        else Modifier
                    ),
                contentScale = ContentScale.Crop
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "선택됨",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            } else if (chara.charaIsFavorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "선호 캐릭터",
                        tint = AppStyles.colorFavorite,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = chara.charaNm,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 시리즈를 고르는 그리드. 칸마다 시리즈 DB의 그림, 이름, 출시일을 보여 주고 최신 시리즈부터 놓는다.
 * 시리즈 DB에 없는 이름(2차창작에 직접 적은 시리즈 등)은 맨 뒤에 가나다순으로 두고,
 * 시리즈 DB 그림이 없으면 그 시리즈로 등록된 굿즈 사진(선호 캐릭터 우선순위)을 쓴다.
 */
@Composable
private fun SeriesChoiceGrid(
    seriesNames: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** 대표 사진 후보 굿즈. null 이면 DB 전체. */
    scopeGoods: List<GoodsItem>? = null
) {
    val allSeries by remember { App.seriesDatabase.seriesDao().getAllFlow() }
        .collectAsState(initial = emptyList())
    val seriesByName = remember(allSeries) { allSeries.associateBy { it.seriesNm.trim() } }
    // 시리즈 DB에 그림이 없는 시리즈는 그 시리즈로 등록된 굿즈 사진(선호 우선순위)으로 채운다.
    val goodsCovers by rememberCoverImages(seriesNames, scopeGoods, ::seriesCoverImages)
    val ordered = remember(seriesNames, seriesByName) {
        seriesNames.sortedWith(
            compareBy<String> { seriesByName[it] == null }
                .thenByDescending { seriesByName[it]?.seriesDate.orEmpty() }
                .thenBy { it }
        )
    }

    if (ordered.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("검색 결과가 없습니다", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(ordered, key = { it }) { name ->
            val isSelected = name == selected
            val info = seriesByName[name]
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                    )
                    .clickable { onSelect(name) }
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 그림 칸. 선택되면 강조색 테두리와 오른쪽 아래 체크를 단다.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .then(
                            if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary)
                            else Modifier
                        )
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(
                            model = info?.seriesUrl?.ifEmpty { null }
                                ?: goodsCovers[name]?.let { goodsImageModel(it) }
                        ),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        contentScale = ContentScale.Fit
                    )
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .size(22.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "선택됨",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                // 이름이 한 줄이든 두 줄이든 칸 높이가 같게 두 줄 높이를 잡는다.
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = info?.seriesDate.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * 필터 그리드 칸의 대표 사진을 고른다. [pick] 은 [categoryCoverImages]·[seriesCoverImages].
 * [scope] 가 있으면 그 굿즈에서만, 없으면 DB 의 모든 굿즈(공식 먼저)에서 고른다. 선호 순서는 DB 에서 읽는다.
 * 고르기 전까지는 빈 지도다.
 */
@Composable
private fun rememberCoverImages(
    keys: List<String>,
    scope: List<GoodsItem>?,
    pick: (List<String>, List<GoodsItem>, List<String>) -> Map<String, String>
): State<Map<String, String>> = produceState(initialValue = emptyMap(), keys, scope) {
    value = withContext(Dispatchers.IO) {
        val favorites = App.charaDatabase.charaDao().getAll()
            .filter { it.charaIsFavorite }
            .sortedWith(compareBy<CharaEntity> { it.charaFavoriteOrder }.thenBy { it.charaNm })
            .map { it.charaNm }
        val goods: List<GoodsItem> = scope
            ?: (App.database.goodsDao().getAll() + App.fanDatabase.fanGoodsDao().getAll())
        pick(keys, goods, favorites)
    }
}

/**
 * 카테고리를 고르는 그리드. 칸마다 대표 사진 + 카테고리 이름.
 * 대표 사진은 [scopeGoods] 중 그 카테고리에서 선호 우선순위가 가장 높은 캐릭터의 굿즈 사진,
 * 선호 캐릭터 굿즈가 없으면 그 카테고리의 아무 굿즈 사진이다([categoryCoverImages]).
 */
@Composable
fun CategoryChoiceGrid(
    categories: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 대표 사진 후보 굿즈: 이 화면에서 그 카테고리를 고르면 나올 굿즈(다른 필터까지 적용한 것).
     * null 이면 DB 전체에서 고른다.
     */
    scopeGoods: List<GoodsItem>? = null
) {
    val covers by rememberCoverImages(categories, scopeGoods, ::categoryCoverImages)

    if (categories.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("검색 결과가 없습니다", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 96.dp),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(categories, key = { it }) { cat ->
            val isSelected = cat == selected
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(cat) }
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 박스 없는 기준에 맞춰 사진 칸은 각지게, 선택되면 강조색 테두리 + 체크.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .then(
                            if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary)
                            else Modifier
                        )
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(model = covers[cat]?.let { goodsImageModel(it) }),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        contentScale = ContentScale.Fit
                    )
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .size(22.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "선택됨",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = cat,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** 카테고리를 고르는 목록. 선택된 줄은 옅은 강조 배경 + 체크로 표시한다. */
@Composable
fun CategoryChoiceList(
    categories: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (categories.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("검색 결과가 없습니다", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(categories, key = { it }) { cat ->
            val isSelected = cat == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                    )
                    .clickable { onSelect(cat) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = cat,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "선택됨",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}
