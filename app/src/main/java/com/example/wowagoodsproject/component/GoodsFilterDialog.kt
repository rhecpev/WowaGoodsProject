package com.example.wowagoodsproject.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.ui.theme.AppStyles

@Composable
fun GoodsFilterDialog(
    widthSizeClass: WindowWidthSizeClass,
    charaList: List<CharaEntity>,
    categoryList: List<String>,
    selectedCharaFilter: String?,
    selectedCategoryFilter: String?,
    onCharaSelect: (String?) -> Unit,
    onCategorySelect: (String?) -> Unit,
    onClearFilter: () -> Unit,
    onDismiss: () -> Unit
) {
    val isLandscape = widthSizeClass != WindowWidthSizeClass.Compact
    var dialogTab by remember { mutableIntStateOf(0) }
    var filterSearch by remember { mutableStateOf("") }

    val filteredCharaList = charaList
        .sortedWith(compareByDescending<CharaEntity> { it.charaIsFavorite }.thenBy { it.charaNm })
        .map { it.charaNm }
        .filter { it.contains(filterSearch, ignoreCase = true) }

    val filteredCategoryList = categoryList
        .filter { it.contains(filterSearch, ignoreCase = true) }

    Dialog(
        onDismissRequest = { onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.85f else 0.9f)
                .fillMaxHeight(if (isLandscape) 0.85f else 0.7f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            if (isLandscape) {
                Row(modifier = Modifier.padding(AppStyles.paddingLarge)) {
                    Column(
                        modifier = Modifier
                            .width(200.dp)
                            .fillMaxHeight()
                    ) {
                        Text(text = "필터", style = AppStyles.textCardTitle)
                        Spacer(modifier = Modifier.height(AppStyles.paddingMedium))
                        TabRow(selectedTabIndex = dialogTab) {
                            Tab(
                                selected = dialogTab == 0,
                                onClick = { dialogTab = 0; filterSearch = "" },
                                text = { Text("캐릭터") }
                            )
                            Tab(
                                selected = dialogTab == 1,
                                onClick = { dialogTab = 1; filterSearch = "" },
                                text = { Text("카테고리") }
                            )
                        }
                        Spacer(modifier = Modifier.height(AppStyles.paddingMedium))
                        OutlinedTextField(
                            value = filterSearch,
                            onValueChange = { filterSearch = it },
                            label = { Text("검색") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                if (filterSearch.isNotEmpty()) {
                                    IconButton(onClick = { filterSearch = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "초기화")
                                    }
                                }
                            }
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Column(verticalArrangement = Arrangement.spacedBy(AppStyles.paddingMedium)) {
                            OutlinedButton(
                                onClick = { onClearFilter(); onDismiss() },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("필터 해제") }
                            Button(
                                onClick = { onDismiss() },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("닫기") }
                        }
                    }
                    Spacer(modifier = Modifier.width(AppStyles.paddingMedium))
                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight(),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Spacer(modifier = Modifier.width(AppStyles.paddingMedium))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        FilterContent(
                            dialogTab = dialogTab,
                            charaList = charaList,
                            filteredCharaList = filteredCharaList,
                            filteredCategoryList = filteredCategoryList,
                            selectedCharaFilter = selectedCharaFilter,
                            selectedCategoryFilter = selectedCategoryFilter,
                            onCharaSelect = { onCharaSelect(it); onDismiss() },
                            onCategorySelect = { onCategorySelect(it); onDismiss() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.padding(AppStyles.paddingLarge)) {
                    Text(text = "필터", style = AppStyles.textCardTitle)
                    Spacer(modifier = Modifier.height(AppStyles.paddingMedium))
                    TabRow(selectedTabIndex = dialogTab) {
                        Tab(
                            selected = dialogTab == 0,
                            onClick = { dialogTab = 0; filterSearch = "" },
                            text = { Text("캐릭터") }
                        )
                        Tab(
                            selected = dialogTab == 1,
                            onClick = { dialogTab = 1; filterSearch = "" },
                            text = { Text("카테고리") }
                        )
                    }
                    Spacer(modifier = Modifier.height(AppStyles.paddingMedium))
                    OutlinedTextField(
                        value = filterSearch,
                        onValueChange = { filterSearch = it },
                        label = { Text("검색") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            if (filterSearch.isNotEmpty()) {
                                IconButton(onClick = { filterSearch = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "초기화")
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(AppStyles.paddingMedium))
                    FilterContent(
                        dialogTab = dialogTab,
                        charaList = charaList,
                        filteredCharaList = filteredCharaList,
                        filteredCategoryList = filteredCategoryList,
                        selectedCharaFilter = selectedCharaFilter,
                        selectedCategoryFilter = selectedCategoryFilter,
                        onCharaSelect = { onCharaSelect(it); onDismiss() },
                        onCategorySelect = { onCategorySelect(it); onDismiss() },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(AppStyles.paddingMedium))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppStyles.paddingMedium)
                    ) {
                        OutlinedButton(
                            onClick = { onClearFilter(); onDismiss() },
                            modifier = Modifier.weight(1f)
                        ) { Text("필터 해제") }
                        Button(
                            onClick = { onDismiss() },
                            modifier = Modifier.weight(1f)
                        ) { Text("닫기") }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterContent(
    dialogTab: Int,
    charaList: List<CharaEntity>,
    filteredCharaList: List<String>,
    filteredCategoryList: List<String>,
    selectedCharaFilter: String?,
    selectedCategoryFilter: String?,
    onCharaSelect: (String?) -> Unit,
    onCategorySelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    when (dialogTab) {
        0 -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = modifier
            ) {
                items(filteredCharaList) { charaNm ->
                    val charaEntity = charaList.find { it.charaNm == charaNm }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable {
                                onCharaSelect(if (selectedCharaFilter == charaNm) null else charaNm)
                            }
                            .background(
                                color = when {
                                    selectedCharaFilter == charaNm -> MaterialTheme.colorScheme.primaryContainer
                                    charaEntity?.charaIsFavorite == true -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    else -> Color.Transparent
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(AppStyles.paddingSmall)
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(
                                model = charaEntity?.charaUrl?.ifEmpty { null }
                            ),
                            contentDescription = null,
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = charaNm, style = AppStyles.textCardSmall)
                    }
                }
            }
        }
        1 -> {
            LazyColumn(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredCategoryList) { cat ->
                    Column {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onCategorySelect(if (selectedCategoryFilter == cat) null else cat)
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedCategoryFilter == cat)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Text(
                                text = cat,
                                modifier = Modifier.padding(AppStyles.paddingMedium),
                                color = if (selectedCategoryFilter == cat)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}