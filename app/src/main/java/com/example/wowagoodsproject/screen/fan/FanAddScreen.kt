package com.example.wowagoodsproject.screen.fan

import com.example.wowagoodsproject.component.PickerSplitBody
import com.example.wowagoodsproject.component.sameContentAs
import kotlinx.coroutines.launch
import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.db.fan.saveSeries

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.example.wowagoodsproject.component.CATEGORY_SET
import com.example.wowagoodsproject.component.CategoryChoiceList
import com.example.wowagoodsproject.component.CharaChoiceGrid
import com.example.wowagoodsproject.component.FilterDialogFrame
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.QuantityStepper
import com.example.wowagoodsproject.component.SearchField
import com.example.wowagoodsproject.component.StatusSegmentedButtons
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.navigation.TopBar
import java.io.File

/**
 * 2차창작 굿즈 등록. 한 칸씩 차례로 연다: 시리즈 → 카테고리 → 가격 → 캐릭터 → 메모 → 보유 여부(개수).
 * 다 채우면 요약과 맨 위 사진 넣기가 나오고, 헤더 오른쪽 위 '저장'으로 저장한다([StepGoodsForm]).
 */
@Composable
fun FanAddScreen(
    onNavigateBack: () -> Unit,
    /** 시리즈 상세에서 '+'로 왔으면 그 시리즈를 미리 채운다. */
    initialSeries: String = "",
    /** 있으면 그 굿즈를 고친다: 등록과 같은 화면을 마지막 요약(사진 + 내용)부터 연다 */
    editGoodsId: Int? = null,
    viewModel: FanAddViewModel = viewModel()
) {
    val context = LocalContext.current
    val editing by viewModel.editing.collectAsState()
    if (editGoodsId != null) {
        LaunchedEffect(editGoodsId) { viewModel.loadForEdit(editGoodsId) }
        val editMissing by viewModel.editMissing.collectAsState()
        LaunchedEffect(editMissing) {
            if (editMissing) {
                android.widget.Toast.makeText(context, "지워진 굿즈라 고칠 수 없습니다", android.widget.Toast.LENGTH_SHORT).show()
                onNavigateBack()
            }
        }
        // 불러오기 전에는 빈 요약이 잠깐 보이지 않게 기다린다.
        if (editing?.fanGoodsId != editGoodsId) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return
        }
    }
    // 화면을 돌려도 사용자가 바꾼 값을 덮지 않도록 처음 한 번만 채운다.
    var seriesPrefilled by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!seriesPrefilled) {
            if (initialSeries.isNotEmpty()) viewModel.onSeriesChange(initialSeries)
            seriesPrefilled = true
        }
    }
    val price by viewModel.price.collectAsState()
    val series by viewModel.series.collectAsState()
    val selectedCharas by viewModel.selectedCharas.collectAsState()
    val category by viewModel.category.collectAsState()
    val imageUri by viewModel.imageUri.collectAsState()
    val charaList by viewModel.charaList.collectAsState()
    val categoryList by viewModel.categoryList.collectAsState()
    val status by viewModel.status.collectAsState()
    val purchaseStore by viewModel.purchaseStore.collectAsState()
    val purchaseDate by viewModel.purchaseDate.collectAsState()
    val receiveDate by viewModel.receiveDate.collectAsState()
    val memo by viewModel.memo.collectAsState()
    val quantity by viewModel.quantity.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val seriesOptions by viewModel.seriesOptions.collectAsState()

    // 저장 전에 같은 굿즈(시리즈·캐릭터·카테고리·가격·메모가 모두 같은 비공식 굿즈)가 있으면 보여 주고 묻는다.
    val saveScope = androidx.compose.runtime.rememberCoroutineScope()
    var duplicates by remember { mutableStateOf<List<com.example.wowagoodsproject.component.GoodsItem>?>(null) }
    // 겹침을 확인하는 중. 이때도 저장 버튼을 막아, 빠르게 두 번 누르면 같은 굿즈가 두 개 저장되지 않게 한다.
    var checkingDuplicates by remember { mutableStateOf(false) }
    val save = { viewModel.insert(context = context, onSuccess = onNavigateBack) }
    duplicates?.let { list ->
        com.example.wowagoodsproject.component.DuplicateGoodsDialog(
            duplicates = list,
            onAddAnyway = { duplicates = null; save() },
            confirmText = if (editGoodsId != null) "그래도 저장" else "그래도 추가",
            onDismiss = { duplicates = null }
        )
    }

    var showCharaDialog by remember { mutableStateOf(false) }
    // 시리즈 고르기 안의 '시리즈 추가'. null 이면 닫힘, 아니면 이름 칸에 미리 채울 값
    var addSeriesPrefill by remember { mutableStateOf<String?>(null) }
    val photoLaunch = com.example.wowagoodsproject.screen.betray.rememberPhotoLaunch { viewModel.onImageSelected(it) }

    if (showCharaDialog) {
        CharaPickerDialog(
            charas = charaList,
            selected = selectedCharas.toSet(),
            onToggle = { viewModel.toggleChara(it) },
            onDismiss = { showCharaDialog = false }
        )
    }
    addSeriesPrefill?.let { prefill ->
        // 2차창작 탭의 '시리즈 추가'와 같은 화면을 다이얼로그로 띄운다. 저장하면 그 시리즈를 고른다.
        val seriesVm: com.example.wowagoodsproject.screen.betray.SeriesEditViewModel = viewModel(key = "fanSeriesAddFromGoods")
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { seriesVm.close(); addSeriesPrefill = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                com.example.wowagoodsproject.screen.betray.SeriesEditScreen(
                    key = "fanSeriesFromGoods:$prefill",
                    target = null,
                    prefill = prefill,
                    subtitle = "비공식",
                    photoPrefix = "fan_series_",
                    viewModel = seriesVm,
                    persist = { n, b, i, d -> App.fanDatabase.fanGoodsDao().saveSeries(null, n, b, i, d) },
                    onSaved = { saved -> viewModel.onSeriesChange(saved); addSeriesPrefill = null },
                    onClose = { addSeriesPrefill = null }
                )
            }
        }
    }

    StepGoodsForm(
        title = if (editGoodsId != null) "비공식 굿즈 수정" else "비공식 굿즈 등록",
        startAtSummary = editGoodsId != null,
        hasChanges = if (editGoodsId != null) viewModel.hasEditChanges() else null,
        onLeave = onNavigateBack,
        series = series,
        onSeries = { viewModel.onSeriesChange(it) },
        seriesOptions = seriesOptions,
        onAddSeries = { addSeriesPrefill = it },
        category = category,
        categories = categoryList,
        onCategory = { viewModel.onCategoryChange(it) },
        price = price,
        onPrice = { viewModel.onPriceChange(it) },
        charas = selectedCharas,
        charaAvatar = { n -> charaList.find { it.charaNm == n }?.charaUrl.orEmpty() },
        onRemoveChara = { viewModel.toggleChara(it) },
        onPickChara = { showCharaDialog = true },
        charaPickerOpen = showCharaDialog,
        memo = memo,
        onMemo = { viewModel.onMemoChange(it) },
        status = status,
        onStatus = { viewModel.onStatusChange(it) },
        purchaseStore = purchaseStore, onPurchaseStore = { viewModel.purchaseStore.value = it },
        purchaseDate = purchaseDate, onPurchaseDate = { viewModel.purchaseDate.value = it },
        receiveDate = receiveDate, onReceiveDate = { viewModel.receiveDate.value = it },
        showPending = true,
        quantity = quantity,
        onQuantity = { viewModel.onQuantityChange(it) },
        imageUri = imageUri,
        onCamera = photoLaunch.camera,
        onGallery = photoLaunch.gallery,
        onUrl = photoLaunch.url,
        onRemoveImage = { viewModel.onImageSelected(null) },
        isSaving = isSaving || checkingDuplicates,
        onSave = save@{
            if (isSaving || checkingDuplicates || duplicates != null) return@save
            // '미분류'라고 적으면 저장할 때 시리즈를 비우므로(savedSeries), 비교할 때도 똑같이 비운다.
            val keySeries = series.takeUnless { it.trim().equals(NO_FAN_SERIES, ignoreCase = true) } ?: ""
            val key = com.example.wowagoodsproject.component.goodsContentKey(keySeries, selectedCharas.joinToString(","), category, price, memo)
            // 고치는 중에 내용(시리즈·캐릭터·카테고리·가격·메모)은 그대로면 겹침을 다시 묻지 않는다(상태만 바꾼 경우 등).
            val original = editing?.let { g ->
                com.example.wowagoodsproject.component.goodsContentKey(
                    g.fanGoodsSeries, com.example.wowagoodsproject.db.betray.charaNames(g.fanGoodsChara).joinToString(","),
                    g.fanGoodsCategory, g.fanGoodsPrice, g.fanGoodsMemo
                )
            }
            if (editGoodsId != null && original == key) save()
            else {
                checkingDuplicates = true
                saveScope.launch {
                    try {
                        val same = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            // 고치는 중이면 자기 자신은 빼고 견준다.
                            App.fanDatabase.fanGoodsDao().getAll().filter { it.fanGoodsId != editGoodsId }.sameContentAs(key)
                        }
                        // save() 가 저장 중 표시를 먼저 켠 뒤에 확인 중 표시를 끈다(버튼이 잠깐 풀리는 틈이 없게).
                        if (same.isEmpty()) save() else duplicates = same
                    } finally {
                        checkingDuplicates = false
                    }
                }
            }
        }
    )
}

/** 사진 칸. 비어 있으면 카메라/갤러리 버튼을, 사진이 있으면 오른쪽 위에 다시 고르기/지우기 버튼을 둔다. */
@Composable
internal fun PhotoPicker(
    imageUri: Uri?,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onUrl: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        if (imageUri != null) {
            Box {
                Image(
                    painter = rememberAsyncImagePainter(imageUri),
                    contentDescription = "선택한 사진",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Fit
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PhotoActionButton(Icons.Default.PhotoCamera, "카메라로 다시 찍기", onCamera)
                    PhotoActionButton(Icons.Default.PhotoLibrary, "갤러리에서 다시 고르기", onGallery)
                    PhotoActionButton(Icons.Default.Link, "URL 로 다시 넣기", onUrl)
                    PhotoActionButton(Icons.Default.Close, "사진 지우기", onRemove)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "굿즈 사진을 추가하세요",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onCamera) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("카메라")
                    }
                    FilledTonalButton(onClick = onGallery) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("갤러리")
                    }
                    FilledTonalButton(onClick = onUrl) {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("URL")
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoActionButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f)
        )
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp))
    }
}

/** 제목(+개수) 아래에 입력란을 모아 두는 구역. 굿즈 상세 팝업의 구역 제목과 같은 모양이다. */
@Composable
internal fun FormSection(
    title: String,
    count: Int? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Spacer(modifier = Modifier.height(20.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (count != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Column(content = content)
}

/** 입력란처럼 생겼지만 누르면 선택 팝업을 여는 칸 */
@Composable
internal fun PickerField(
    value: String,
    label: String,
    placeholder: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    // readOnly 입력란은 클릭을 먹어 버리므로 눌림 이벤트를 직접 받아 팝업을 연다.
    // 팝업을 닫은 뒤 칸이 포커스된 채로 남지 않게 포커스도 풀어 준다.
    val interaction = remember { MutableInteractionSource() }
    val focusManager = LocalFocusManager.current
    val latestOnClick by rememberUpdatedState(onClick)
    LaunchedEffect(interaction) {
        interaction.interactions.collect {
            if (it is PressInteraction.Release) {
                focusManager.clearFocus()
                latestOnClick()
            }
        }
    }
    com.example.wowagoodsproject.component.UnderlineTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
        singleLine = true,
        interactionSource = interaction,
        modifier = Modifier.fillMaxWidth()
    )
}

/** 고른 캐릭터(아바타 + 이름, 눌러서 빼기) + 끝에 '추가' 칩 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedCharaChips(
    selected: List<String>,
    charaList: List<CharaEntity>,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        selected.forEach { name ->
            val url = charaList.find { it.charaNm == name }?.charaUrl.orEmpty()
            InputChip(
                selected = true,
                onClick = { onRemove(name) },
                label = { Text(name) },
                avatar = {
                    Image(
                        painter = rememberAsyncImagePainter(model = url.ifEmpty { null }),
                        contentDescription = null,
                        modifier = Modifier
                            .size(InputChipDefaults.AvatarSize)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                    )
                },
                trailingIcon = {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "$name 빼기",
                        modifier = Modifier.size(InputChipDefaults.IconSize)
                    )
                }
            )
        }
        AssistChip(
            onClick = onAdd,
            label = { Text(if (selected.isEmpty()) "캐릭터 선택" else "추가") },
            leadingIcon = {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
            }
        )
    }
}

/**
 * 카테고리 고르기. 필터의 카테고리처럼 대표 사진 그리드로 고른다([CategoryChoiceGrid]).
 * 목록에 없는 이름을 치면 맨 위에 '새로 추가' 줄이 생긴다.
 * @param scopeGoods 대표 사진을 고를 굿즈. null 이면 명조 공식·2차창작 전체
 */
@Composable
internal fun CategoryPickerDialog(
    categories: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    scopeGoods: List<com.example.wowagoodsproject.component.GoodsItem>? = null
) {
    var search by remember { mutableStateOf("") }
    val query = search.trim()
    val filtered = categories.filter { it.contains(query, ignoreCase = true) }
    val canAdd = query.isNotEmpty() && categories.none { it.equals(query, ignoreCase = true) }

    FilterDialogFrame(title = "카테고리", onDismiss = onDismiss, onClear = null) {
        SearchField(
            query = search,
            onQueryChange = { search = it },
            placeholder = "검색하거나 새 카테고리 입력"
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (canAdd) {
            val blocked = query == CATEGORY_SET
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable(enabled = !blocked) { onSelect(query) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = if (blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (blocked) "'$query' 는 새로 추가할 수 없습니다" else "'$query' 새로 추가",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        com.example.wowagoodsproject.component.CategoryChoiceGrid(
            categories = filtered,
            selected = selected.ifEmpty { null },
            onSelect = onSelect,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            scopeGoods = scopeGoods
        )
    }
}

/** 캐릭터 여러 명 고르기. 선호 캐릭터가 먼저 나오고, 누를 때마다 넣고 뺀다. */
@Composable
private fun CharaPickerDialog(
    charas: List<CharaEntity>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filtered = charas
        .sortedWith(compareByDescending<CharaEntity> { it.charaIsFavorite }.thenBy { it.charaNm })
        .filter { it.charaNm.contains(search.trim(), ignoreCase = true) }

    FilterDialogFrame(
        title = if (selected.isEmpty()) "캐릭터 선택" else "캐릭터 선택 · ${selected.size}명",
        onDismiss = onDismiss,
        onClear = null
    ) {
      // 세로: 위 조작 → 목록 → 완료. 낮은 가로 화면: 조작은 왼쪽, 목록은 오른쪽(목록이 0 높이로 사라지지 않게)
      PickerSplitBody(top = {
        SearchField(
            query = search,
            onQueryChange = { search = it },
            placeholder = "캐릭터 이름 검색"
        )
        // 고른 캐릭터를 검색창 아래 한 줄로(넘치면 옆으로 스크롤)
        com.example.wowagoodsproject.component.SelectedCharaStrip(
            items = selected.map { n -> n to (charas.find { it.charaNm == n }?.charaUrl.orEmpty()) },
            onRemove = onToggle,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
      }, list = { m ->
        CharaChoiceGrid(
            charas = filtered,
            selectedName = null,
            selectedNames = selected,
            onSelect = { onToggle(it.charaNm) },
            modifier = m
        )
      }, bottom = {
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("완료") }
      })
    }
}
