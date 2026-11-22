package com.example.wowagoodsproject.screen.betray

import com.example.wowagoodsproject.component.PickerSplitBody
import com.example.wowagoodsproject.component.sameContentAs
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wowagoodsproject.App
import com.example.wowagoodsproject.component.CATEGORY_SET
import com.example.wowagoodsproject.component.CharaChoiceGrid
import com.example.wowagoodsproject.component.FilterDialogFrame
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.QuantityStepper
import com.example.wowagoodsproject.component.SearchField
import com.example.wowagoodsproject.component.StatusSegmentedButtons
import com.example.wowagoodsproject.db.betray.BetrayGoodsEntity
import com.example.wowagoodsproject.db.betray.BetrayKind
import com.example.wowagoodsproject.db.betray.copyBetrayImage
import com.example.wowagoodsproject.db.betray.saveSeries
import com.example.wowagoodsproject.db.betray.withStatus
import com.example.wowagoodsproject.db.betray.deleteUnusedImages
import com.example.wowagoodsproject.db.betray.withPurchaseInfo
import com.example.wowagoodsproject.navigation.TopBar
import com.example.wowagoodsproject.screen.fan.CategoryPickerDialog
import com.example.wowagoodsproject.screen.fan.FormSection
import com.example.wowagoodsproject.screen.fan.PhotoPicker
import com.example.wowagoodsproject.screen.fan.PickerField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 등록 화면의 입력값. 화면을 돌려도 남도록 뷰모델에 둔다. */
class BetrayAddViewModel : ViewModel() {
    val kind = MutableStateFlow(BetrayKind.OFFICIAL)
    val series = MutableStateFlow("")
    val price = MutableStateFlow("")
    val category = MutableStateFlow("")
    val charas = MutableStateFlow<List<String>>(emptyList())
    val status = MutableStateFlow(GoodsStatus.GOTTEN)
    val quantity = MutableStateFlow(1)
    val memo = MutableStateFlow("")
    /** 구매예정일 때의 구매 정보 */
    val purchaseStore = MutableStateFlow("")
    val purchaseDate = MutableStateFlow("")
    val receiveDate = MutableStateFlow("")
    val imageUri = MutableStateFlow<Uri?>(null)
    val isSaving = MutableStateFlow(false)
    /** 등록 화면을 새로 열 때마다 늘어난다(단계를 처음부터) */
    val resetCount = MutableStateFlow(0)

    /** 고치는 굿즈. null 이면 새로 등록 */
    val editing = MutableStateFlow<BetrayGoodsEntity?>(null)
    /** 불러온 때의 사진. 이 사진 그대로면 다시 복사하지 않는다 */
    private var originalImageUri: Uri? = null

    /** 고칠 굿즈로 입력 칸을 채운다(등록과 같은 화면을 요약부터 연다). */
    fun loadForEdit(g: BetrayGoodsEntity) {
        resetCount.value += 1
        kind.value = g.kind
        series.value = g.goodsSeries; price.value = g.goodsPrice; category.value = g.goodsCategory
        charas.value = com.example.wowagoodsproject.db.betray.charaNames(g.goodsChara)
        status.value = g.status; quantity.value = g.goodsQuantity; memo.value = g.goodsMemo
        purchaseStore.value = g.goodsPurchaseStore; purchaseDate.value = g.goodsPurchaseDate; receiveDate.value = g.goodsReceiveDate
        originalImageUri = g.goodsImgPath.takeIf { it.isNotEmpty() }?.let { Uri.fromFile(File(it)) }
        imageUri.value = originalImageUri
        editing.value = g
    }

    /** 고치는 중에 처음 불러온 값에서 바뀐 것이 있는지(나갈 때 물을지) */
    fun hasEditChanges(): Boolean {
        val g = editing.value ?: return false
        return series.value != g.goodsSeries || price.value != g.goodsPrice || category.value != g.goodsCategory ||
            charas.value.joinToString(",") != com.example.wowagoodsproject.db.betray.charaNames(g.goodsChara).joinToString(",") ||
            status.value != g.status || quantity.value != g.goodsQuantity || memo.value != g.goodsMemo ||
            imageUri.value != originalImageUri || purchaseStore.value != g.goodsPurchaseStore ||
            purchaseDate.value != g.goodsPurchaseDate || receiveDate.value != g.goodsReceiveDate
    }

    /**
     * 고친 내용을 그 굿즈 줄에 저장한다. 등록일·종류·배송 시작일은 그대로, 구매예정이 아니면 구매 정보도 지우지 않는다.
     * 사진을 바꾸거나 뺐으면 예전 사진은 아무도 쓰지 않을 때 지운다.
     */
    private suspend fun update(context: Context, old: BetrayGoodsEntity) {
        val dao = App.betrayDatabase.betrayDao()
        // 고치는 사이 지워졌으면 사진을 복사하기 전에 멈춘다(저장된 척하지 않고, 쓰이지 않는 사진도 남기지 않는다).
        val fresh = dao.goodsById(old.goodsId) ?: throw IllegalStateException("그사이 지워진 굿즈입니다")
        val uri = imageUri.value
        val img = when {
            uri == null -> ""
            uri == originalImageUri -> fresh.goodsImgPath
            else -> copyBetrayImage(context, uri)
        }
        val pending = status.value == GoodsStatus.PENDING
        // 저장하지 못하면 방금 복사한 새 사진은 지운다(쓰이지 않는 사진을 남기지 않는다).
        val copied = img.takeIf { it.isNotEmpty() && it != fresh.goodsImgPath }
        try { dao.updateGoods(
            fresh.copy(
                goodsSeries = series.value.trim(),
                goodsPrice = price.value.trim(),
                goodsChara = charas.value.joinToString(","),
                goodsCategory = category.value.trim(),
                goodsImgPath = img,
                goodsMemo = memo.value.trim(),
                goodsQuantity = if (status.value == GoodsStatus.NOT_GOTTEN) 1 else quantity.value,
                goodsPurchaseStore = if (pending) purchaseStore.value.trim() else fresh.goodsPurchaseStore,
                goodsPurchaseDate = if (pending) purchaseDate.value else fresh.goodsPurchaseDate,
                goodsReceiveDate = if (pending) receiveDate.value else fresh.goodsReceiveDate
            ).withStatus(status.value)
        ) } catch (e: Exception) {
            copied?.let { java.io.File(it).delete() }
            throw e
        }
        if (fresh.goodsImgPath.isNotEmpty() && fresh.goodsImgPath != img) dao.deleteUnusedImages(listOf(fresh.goodsImgPath))
    }

    /** 등록 화면을 새로 열 때 이전 입력을 지우고 [kind]·[series] 를 미리 채운다. */
    fun reset(kind: BetrayKind, series: String) {
        editing.value = null
        this.kind.value = kind
        resetCount.value += 1
        this.series.value = series
        price.value = ""; category.value = ""; charas.value = emptyList()
        status.value = GoodsStatus.GOTTEN; quantity.value = 1; memo.value = ""; imageUri.value = null
        purchaseStore.value = ""; purchaseDate.value = ""; receiveDate.value = ""
    }

    fun toggleChara(name: String) {
        val n = name.trim()
        if (n.isEmpty() || n.contains(',')) return
        charas.value = if (n in charas.value) charas.value - n else charas.value + n
    }

    fun save(context: Context, genreId: Int, onSuccess: () -> Unit) {
        if (isSaving.value) return
        isSaving.value = true
        viewModelScope.launch {
            val ok = try {
                editing.value?.let { old ->
                    update(context, old)
                    return@launch run { isSaving.value = false; onSuccess() }
                }
                val img = imageUri.value?.let { copyBetrayImage(context, it) }.orEmpty()
                // 저장하지 못하면 방금 복사한 사진은 지운다.
                try { App.betrayDatabase.betrayDao().insertGoods(
                    BetrayGoodsEntity(
                        genreId = genreId,
                        goodsKind = kind.value.name,
                        goodsRegDate = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date()),
                        goodsSeries = series.value.trim(),
                        goodsPrice = price.value.trim(),
                        goodsChara = charas.value.joinToString(","),
                        goodsCategory = category.value.trim(),
                        goodsImgPath = img,
                        goodsMemo = memo.value.trim(),
                        goodsQuantity = quantity.value
                    ).withStatus(status.value).let { g ->
                        if (status.value == GoodsStatus.PENDING) g.withPurchaseInfo(
                            com.example.wowagoodsproject.component.PurchaseInfo(purchaseStore.value.trim(), purchaseDate.value, receiveDate.value)
                        ) else g
                    }
                ) } catch (e: Exception) {
                    if (img.isNotEmpty()) java.io.File(img).delete()
                    throw e
                }
                true
            } catch (e: Exception) {
                Toast.makeText(context, "저장 실패: ${e.message}", Toast.LENGTH_LONG).show()
                false
            } finally {
                isSaving.value = false
            }
            if (ok) onSuccess()
        }
    }
}

/**
 * 배신 굿즈 등록. 공식/2차창작부터 고르고, 그다음 2차창작 등록과 같은 차례로 한 칸씩 연다([StepGoodsForm]).
 * 캐릭터는 이 장르에 이미 나온 캐릭터·직접 추가한 캐릭터에서 고르거나 '캐릭터 추가'로 만든다.
 */
@Composable
fun BetrayAddScreen(
    genreId: Int,
    existing: List<BetrayGoodsEntity>,
    /** 직접 추가한 캐릭터. 굿즈가 아직 없어도 캐릭터 고르기에 나온다. */
    registeredCharas: List<com.example.wowagoodsproject.db.betray.BetrayCharaEntity> = emptyList(),
    /** 등록한 시리즈. 굿즈가 아직 없어도 시리즈 고르기에 나온다. */
    registeredSeries: List<com.example.wowagoodsproject.db.betray.BetraySeriesEntity> = emptyList(),
    viewModel: BetrayAddViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val kind by viewModel.kind.collectAsState()
    val resetCount by viewModel.resetCount.collectAsState()
    val series by viewModel.series.collectAsState()
    val price by viewModel.price.collectAsState()
    val category by viewModel.category.collectAsState()
    val charas by viewModel.charas.collectAsState()
    val status by viewModel.status.collectAsState()
    val quantity by viewModel.quantity.collectAsState()
    val memo by viewModel.memo.collectAsState()
    val purchaseStore by viewModel.purchaseStore.collectAsState()
    val purchaseDate by viewModel.purchaseDate.collectAsState()
    val receiveDate by viewModel.receiveDate.collectAsState()
    val imageUri by viewModel.imageUri.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val editingGoods by viewModel.editing.collectAsState()

    // 저장 전에 이 장르·같은 종류에 같은 굿즈(시리즈·캐릭터·카테고리·가격·메모가 모두 같은)가 있으면 보여 주고 묻는다.
    var duplicates by remember { mutableStateOf<List<com.example.wowagoodsproject.component.GoodsItem>?>(null) }
    val save = { viewModel.save(context, genreId, onNavigateBack) }
    duplicates?.let { list ->
        com.example.wowagoodsproject.component.DuplicateGoodsDialog(
            duplicates = list,
            onAddAnyway = { duplicates = null; save() },
            onDismiss = { duplicates = null }
        )
    }

    var showChara by remember { mutableStateOf(false) }

    // 이 장르에서 이미 쓴 카테고리를 먼저, 명조 쪽 카테고리도 고를 수 있게 함께 둔다.
    val categories by produceState(initialValue = emptyList<String>(), existing) {
        val own = existing.map { it.goodsCategory }.filter { it.isNotBlank() }
        val wuwa = withContext(Dispatchers.IO) {
            App.database.goodsDao().getAllCategories() + App.fanDatabase.fanGoodsDao().getFanCategories()
        }
        value = (own.distinct().sorted() + wuwa.distinct().sorted()).distinct().filter { it != CATEGORY_SET && it.isNotBlank() }
    }
    val seriesOptions = remember(existing, registeredSeries) {
        com.example.wowagoodsproject.screen.fan.seriesOptionsOf(
            registeredSeries.map { it.seriesNm to it.seriesImgPath },
            existing.map { it.goodsSeries }
        )
    }
    val knownCharas = remember(existing, registeredCharas) { betrayCharas(existing, emptyList(), registeredCharas) }
    val photoLaunch = rememberPhotoLaunch { viewModel.imageUri.value = it }

    // 캐릭터 고르기 안의 '캐릭터 추가'. null 이면 닫힘, 아니면 이름 칸에 미리 채울 값
    var addCharaPrefill by remember { mutableStateOf<String?>(null) }
    if (showChara) {
        BetrayCharaPicker(
            known = knownCharas,
            selected = charas.toSet(),
            onToggle = { viewModel.toggleChara(it) },
            onAddChara = { prefill -> addCharaPrefill = prefill },
            onDismiss = { showChara = false }
        )
    }
    addCharaPrefill?.let { prefill ->
        // 캐릭터별 탭의 '캐릭터 추가'와 같은 화면을 다이얼로그로 띄운다. 저장하면 목록이 다시 불러와지고 그 캐릭터를 고른다.
        val charaVm: BetrayCharaEditViewModel = androidx.lifecycle.viewmodel.compose.viewModel(key = "betrayCharaAddFromGoods_$genreId")
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { charaVm.close(); addCharaPrefill = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                BetrayCharaEditScreen(
                    genreId = genreId,
                    genreName = "",
                    target = CharaEditTarget("", "", registered = false, prefill = prefill),
                    viewModel = charaVm,
                    onClose = { addCharaPrefill = null },
                    onSaved = { saved ->
                        if (saved !in charas) viewModel.toggleChara(saved)
                        addCharaPrefill = null
                    },
                    onDeleted = { addCharaPrefill = null }
                )
            }
        }
    }
    // 시리즈 고르기 안의 '시리즈 추가'
    var addSeriesPrefill by remember { mutableStateOf<String?>(null) }
    addSeriesPrefill?.let { prefill ->
        val seriesVm: SeriesEditViewModel = androidx.lifecycle.viewmodel.compose.viewModel(key = "betraySeriesAddFromGoods_$genreId")
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { seriesVm.close(); addSeriesPrefill = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                SeriesEditScreen(
                    key = "betraySeriesFromGoods:$genreId:$prefill",
                    target = null,
                    prefill = prefill,
                    photoPrefix = "betray_series_",
                    viewModel = seriesVm,
                    persist = { n, b, i, d -> App.betrayDatabase.betrayDao().saveSeries(genreId, null, n, b, i, d) },
                    onSaved = { saved -> viewModel.series.value = saved; addSeriesPrefill = null },
                    onClose = { addSeriesPrefill = null }
                )
            }
        }
    }

    // 배신 굿즈 등록의 캐릭터 칸은 예전 아이콘(사람 여럿). 명조 캐릭터 그림은 명조 2차창작 등록에서만 쓴다.
    androidx.compose.runtime.CompositionLocalProvider(com.example.wowagoodsproject.screen.fan.LocalCharaRoleIcon provides false) {
    com.example.wowagoodsproject.screen.fan.StepGoodsForm(
        // 종류는 들어온 탭(공식/비공식)으로 정해져 있어 묻지 않는다(명조 비공식 등록과 같다).
        title = (if (kind == BetrayKind.FAN) "비공식 굿즈 " else "공식 굿즈 ") + if (editingGoods != null) "수정" else "등록",
        startAtSummary = editingGoods != null,
        hasChanges = if (editingGoods != null) viewModel.hasEditChanges() else null,
        onLeave = onNavigateBack,
        kindOptions = null,
        series = series,
        onSeries = { viewModel.series.value = it },
        seriesOptions = seriesOptions,
        onAddSeries = { addSeriesPrefill = it },
        category = category,
        categories = categories,
        // 카테고리 그리드 대표 사진은 이 장르 굿즈에서
        categoryScope = existing,
        onCategory = { viewModel.category.value = it },
        price = price,
        onPrice = { viewModel.price.value = it },
        charas = charas,
        charaAvatar = { n -> knownCharas.find { it.name == n }?.cover.orEmpty() },
        onRemoveChara = { viewModel.toggleChara(it) },
        onPickChara = { showChara = true },
        charaPickerOpen = showChara,
        memo = memo,
        onMemo = { viewModel.memo.value = it },
        status = status,
        onStatus = { viewModel.status.value = it },
        purchaseStore = purchaseStore, onPurchaseStore = { viewModel.purchaseStore.value = it },
        purchaseDate = purchaseDate, onPurchaseDate = { viewModel.purchaseDate.value = it },
        receiveDate = receiveDate, onReceiveDate = { viewModel.receiveDate.value = it },
        showPending = true,
        quantity = quantity,
        onQuantity = { viewModel.quantity.value = it.coerceIn(1, 99) },
        imageUri = imageUri,
        onCamera = photoLaunch.camera,
        onGallery = photoLaunch.gallery,
        onUrl = photoLaunch.url,
        onRemoveImage = { viewModel.imageUri.value = null },
        isSaving = isSaving,
        onSave = {
            val key = com.example.wowagoodsproject.component.goodsContentKey(series, charas.joinToString(","), category, price, memo)
            val editingId = viewModel.editing.value?.goodsId
            val same = existing.filter { it.kind == kind && it.goodsId != editingId }.sameContentAs(key)
            if (same.isEmpty()) save() else duplicates = same
        },
        resetKey = resetCount
    )
    }
}

/** 이미 쓴 시리즈를 눌러 채우는 칩 줄 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionChips(items: List<String>, selected: String, onPick: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { s ->
            FilterChip(selected = s == selected, onClick = { onPick(s) }, label = { Text(s) })
        }
    }
}

/** 고른 캐릭터 칩(눌러서 빼기) + '추가' 칩 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedNames(names: List<String>, onRemove: (String) -> Unit, onAdd: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        names.forEach { n ->
            InputChip(
                selected = true,
                onClick = { onRemove(n) },
                label = { Text(n) },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "$n 빼기", modifier = Modifier.size(InputChipDefaults.IconSize)) }
            )
        }
        AssistChip(
            onClick = onAdd,
            label = { Text(if (names.isEmpty()) "캐릭터 선택" else "추가") },
            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) }
        )
    }
}

/**
 * 캐릭터 여러 명 고르기. 이 장르에 이미 나온 캐릭터가 사진과 함께 나오고,
 * 목록에 없는 이름을 치면 맨 위 '새로 추가' 줄로 넣는다.
 */
@Composable
private fun BetrayCharaPicker(
    known: List<BetrayChara>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    /** '캐릭터 추가' 화면을 연다. 넘기는 값은 이름 칸에 미리 채울 검색어 */
    onAddChara: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    val query = search.trim()
    // 이번에 새로 넣은 이름도 목록에 보이게 합친다.
    val all = remember(known, selected) {
        known.mapIndexed { i, c -> c.asCharaEntity(i + 1) } +
            selected.filter { s -> known.none { it.name == s } }.mapIndexed { i, s ->
                com.example.wowagoodsproject.db.character.CharaEntity(charaId = known.size + i + 1, charaNm = s)
            }
    }
    val filtered = all.filter { it.charaNm.contains(query, ignoreCase = true) }
    // 검색어가 아직 없는 이름이면 '캐릭터 추가' 화면의 이름 칸에 채워 준다.
    val prefill = if (query.isNotEmpty() && !query.contains(',') && all.none { it.charaNm.equals(query, ignoreCase = true) }) query else ""

    FilterDialogFrame(
        title = if (selected.isEmpty()) "캐릭터 선택" else "캐릭터 선택 · ${selected.size}명",
        onDismiss = onDismiss,
        onClear = null
    ) {
      // 세로: 위 조작 → 목록 → 완료. 낮은 가로 화면: 조작은 왼쪽, 목록은 오른쪽(목록이 0 높이로 사라지지 않게)
      PickerSplitBody(top = {
        SearchField(query = search, onQueryChange = { search = it }, placeholder = "캐릭터 검색")
        // 고른 캐릭터를 검색창 아래 한 줄로(넘치면 옆으로 스크롤)
        com.example.wowagoodsproject.component.SelectedCharaStrip(
            items = selected.map { n -> n to (known.find { it.name == n }?.cover.orEmpty()) },
            onRemove = onToggle,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        // 캐릭터 추가: 캐릭터별 탭의 '캐릭터 추가'와 같은 화면(이름·사진)을 다이얼로그로 연다.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable { onAddChara(prefill); search = "" }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                if (prefill.isNotEmpty()) "캐릭터 추가 · '$prefill'" else "캐릭터 추가",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
      }, list = { m ->
        if (all.isEmpty()) {
            Box(modifier = m, contentAlignment = Alignment.Center) {
                Text("'캐릭터 추가'로 캐릭터를 만들어 고르세요", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            CharaChoiceGrid(
                charas = filtered,
                selectedName = null,
                selectedNames = selected,
                onSelect = { onToggle(it.charaNm) },
                modifier = m
            )
        }
      }, bottom = {
        Button(onClick = onDismiss, shape = RectangleShape, modifier = Modifier.fillMaxWidth()) { Text("완료") }
      })
    }
}
