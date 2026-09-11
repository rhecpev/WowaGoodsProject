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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.ui.theme.AppStyles

/** 캐릭터/카테고리 두 탭으로 굿즈 목록을 거르는 필터 팝업 */
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
    onDismiss: () -> Unit
) {
    var dialogTab by remember { mutableIntStateOf(0) }
    var filterSearch by remember { mutableStateOf("") }

    val filteredCharaList = charaList
        .sortedWith(compareByDescending<CharaEntity> { it.charaIsFavorite }.thenBy { it.charaNm })
        .filter { it.charaNm.contains(filterSearch, ignoreCase = true) }

    val filteredCategoryList = categoryList
        .filter { it.contains(filterSearch, ignoreCase = true) }

    FilterDialogFrame(
        title = "필터",
        onDismiss = onDismiss,
        onClear = if (selectedCharaFilter != null || selectedCategoryFilter != null) {
            { onClearFilter(); onDismiss() }
        } else null
    ) {
        SecondaryTabRow(
            selectedTabIndex = dialogTab,
            containerColor = Color.Transparent
        ) {
            Tab(
                selected = dialogTab == 0,
                onClick = { dialogTab = 0; filterSearch = "" },
                text = { Text(if (selectedCharaFilter != null) "캐릭터 ·" else "캐릭터") }
            )
            Tab(
                selected = dialogTab == 1,
                onClick = { dialogTab = 1; filterSearch = "" },
                text = { Text(if (selectedCategoryFilter != null) "카테고리 ·" else "카테고리") }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        SearchField(
            query = filterSearch,
            onQueryChange = { filterSearch = it },
            placeholder = if (dialogTab == 0) "캐릭터 이름 검색" else "카테고리 검색",
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (dialogTab == 0) {
            CharaChoiceGrid(
                charas = filteredCharaList,
                selectedName = selectedCharaFilter,
                onSelect = { chara ->
                    onCharaSelect(if (selectedCharaFilter == chara.charaNm) null else chara.charaNm)
                    onDismiss()
                },
                modifier = Modifier.weight(1f)
            )
        } else {
            CategoryChoiceList(
                categories = filteredCategoryList,
                selected = selectedCategoryFilter,
                onSelect = { cat ->
                    onCategorySelect(if (selectedCategoryFilter == cat) null else cat)
                    onDismiss()
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 필터 팝업 공통 틀: 제목 + 닫기(X) / 내용 / (필터가 걸려 있을 때만) 필터 해제 버튼.
 * content 는 ColumnScope 이므로 목록에 Modifier.weight(1f) 를 주면 남은 높이를 채운다.
 */
@Composable
fun FilterDialogFrame(
    title: String,
    onDismiss: () -> Unit,
    onClear: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(0.92f)
                .fillMaxHeight(if (isLandscape) 0.9f else 0.75f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp)
            ) {
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
                content()
                if (onClear != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onClear,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("필터 해제") }
                }
            }
        }
    }
}

/** 원형 아바타 + 이름으로 캐릭터를 고르는 그리드. 선택된 캐릭터는 테두리와 체크로 표시한다. */
@Composable
fun CharaChoiceGrid(
    charas: List<CharaEntity>,
    selectedName: String?,
    onSelect: (CharaEntity) -> Unit,
    modifier: Modifier = Modifier
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
                selected = chara.charaNm == selectedName,
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
