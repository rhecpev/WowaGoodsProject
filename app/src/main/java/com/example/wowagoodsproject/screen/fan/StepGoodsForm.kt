package com.example.wowagoodsproject.screen.fan

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.wowagoodsproject.component.FilterDialogFrame
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.QuantityStepper
import com.example.wowagoodsproject.component.SearchField
import com.example.wowagoodsproject.component.StatusSegmentedButtons
import com.example.wowagoodsproject.navigation.TopBar
import com.example.wowagoodsproject.screen.betray.UnderlineNameField
import com.example.wowagoodsproject.ui.theme.AppStyles
import java.io.File

/*
 * 굿즈 등록(명조 2차창작·배신 굿즈 공통). 한 칸씩 차례로 서서히 열린다:
 *   (배신 굿즈만) 공식/2차창작 → 시리즈 → 카테고리 → 가격 → 캐릭터 → 보유 여부(개수, 구매예정이면 구매 정보) + 메모(선택)
 * '다음' 단추 없이 값을 고르거나 적으면 바로 다음 칸이 열린다(글 칸은 키보드의 다음/완료로 비워 둔 채 넘길 수 있고,
 * 고르기 창은 고르지 않고 닫아도 넘어간다). 마지막 보유 여부까지 열리면 가로로 꽉 찬 '다음' 단추가 나온다.
 * 그 단추를 누르면 굿즈 상세처럼 밑줄만 남은 요약으로 바뀌고, 맨 위에 사진 넣기가 나타나 그쪽으로 올라간다.
 * 그 뒤 '저장'으로 저장한다. 요약의 줄을 누르면 그 칸을 다시 고친다.
 * 입력한 채로 뒤로 가면 정말 저장하지 않을지 묻는다.
 */

/** 등록 단계 */
enum class GoodsStep(val label: String) {
    KIND("종류"), SERIES("시리즈"), CATEGORY("카테고리"), PRICE("가격"), CHARA("캐릭터"), MEMO("메모"), STATUS("보유 여부"), DONE("")
}

/** 시리즈를 비워 둔 굿즈의 이름(목록·고르기·요약 공통) */
const val UNCATEGORIZED = "미분류"

/** 시리즈 고르기 목록의 한 줄. [image] 는 시리즈 배경 사진(없으면 빈 문자열) */
data class SeriesOption(val name: String, val image: String = "")

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun StepGoodsForm(
    title: String,
    /** 화면을 닫는다(저장하지 않음) */
    onLeave: () -> Unit,
    /** 종류 고르기(배신 굿즈만). null 이면 종류 칸이 없다 */
    kindOptions: List<String>? = null,
    /** 고른 종류. 아직 안 골랐으면 null */
    kindIndex: Int? = null,
    onKind: (Int) -> Unit = {},
    series: String,
    onSeries: (String) -> Unit,
    seriesOptions: List<SeriesOption>,
    /** '시리즈 추가' 화면을 연다(이름 칸에 채울 검색어) */
    onAddSeries: (String) -> Unit,
    category: String,
    categories: List<String>,
    onCategory: (String) -> Unit,
    /** 카테고리 그리드의 대표 사진을 고를 굿즈. null 이면 명조 공식·2차창작 전체 */
    categoryScope: List<com.example.wowagoodsproject.component.GoodsItem>? = null,
    price: String,
    onPrice: (String) -> Unit,
    charas: List<String>,
    /** 캐릭터 이름 → 사진(주소나 경로). 입력 칸에 이름과 함께 사진을 놓는다 */
    charaAvatar: (String) -> String = { "" },
    onRemoveChara: (String) -> Unit,
    /** 캐릭터 고르기 창을 연다(창은 부르는 쪽이 띄운다) */
    onPickChara: () -> Unit,
    /** 캐릭터 고르기 창이 열려 있는지. 닫히면 다음 칸으로 넘어간다 */
    charaPickerOpen: Boolean = false,
    memo: String,
    onMemo: (String) -> Unit,
    status: GoodsStatus,
    onStatus: (GoodsStatus) -> Unit,
    /** 구매예정일 때의 구매 정보 */
    purchaseStore: String = "",
    onPurchaseStore: (String) -> Unit = {},
    purchaseDate: String = "",
    onPurchaseDate: (String) -> Unit = {},
    receiveDate: String = "",
    onReceiveDate: (String) -> Unit = {},
    /** 구매예정도 고를 수 있는지 */
    showPending: Boolean,
    quantity: Int,
    onQuantity: (Int) -> Unit,
    imageUri: Uri?,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onUrl: () -> Unit,
    onRemoveImage: () -> Unit,
    isSaving: Boolean,
    onSave: () -> Unit,
    /** 화면을 새로 열 때마다 바뀌는 값(단계를 처음부터) */
    resetKey: Any? = null,
    /**
     * 수정: 단계를 거치지 않고 마지막 요약(사진 + 내용 줄) 화면부터 연다. 줄을 눌러 그 칸만 고친다.
     * 나갈 때는 [hasChanges] 일 때만 묻는다.
     */
    startAtSummary: Boolean = false,
    /** 수정에서 바뀐 것이 있는지(나갈 때 물을지). null 이면 입력이 하나라도 있으면 묻는다(등록) */
    hasChanges: Boolean? = null
) {
    val steps = remember(kindOptions != null) {
        buildList {
            if (kindOptions != null) add(GoodsStep.KIND)
            // 메모는 따로 열리지 않고 보유 여부와 함께 나온다(적지 않아도 된다).
            addAll(listOf(GoodsStep.SERIES, GoodsStep.CATEGORY, GoodsStep.PRICE, GoodsStep.CHARA, GoodsStep.STATUS, GoodsStep.DONE))
        }
    }
    /** 지금까지 연 단계(steps 의 번호). 마지막(DONE)에 닿으면 요약 */
    var reached by rememberSaveable(resetKey) { mutableIntStateOf(if (startAtSummary) steps.lastIndex else 0) }
    /** 요약에서 다시 고치는 칸 */
    var editing by rememberSaveable(resetKey) { mutableStateOf<GoodsStep?>(null) }
    val current = steps[reached]
    val done = current == GoodsStep.DONE
    fun advanceFrom(step: GoodsStep) {
        if (editing == step) { editing = null; return }
        val i = steps.indexOf(step)
        if (i == reached && reached < steps.lastIndex) reached = i + 1
    }

    // 필수값: 카테고리 · 가격. 비어 있으면 다음 칸으로 넘어가지 않고 저장도 막는다.
    val missingRequired = buildList {
        if (category.isBlank()) add(GoodsStep.CATEGORY.label)
        if (price.isBlank()) add(GoodsStep.PRICE.label)
    }

    val hasInput = hasChanges ?: (series.isNotBlank() || category.isNotBlank() || price.isNotBlank() || charas.isNotEmpty() ||
        memo.isNotBlank() || imageUri != null || (kindOptions != null && kindIndex != null))
    var confirmLeave by remember { mutableStateOf(false) }
    val back = {
        when {
            editing != null -> editing = null
            hasInput -> confirmLeave = true
            else -> onLeave()
        }
    }
    BackHandler(onBack = back)
    if (confirmLeave) {
        AlertDialog(
            shape = RectangleShape,
            onDismissRequest = { confirmLeave = false },
            title = { Text("저장하지 않고 나갈까요?") },
            text = { Text(if (startAtSummary) "고친 내용이 저장되지 않습니다." else "입력한 내용이 모두 사라집니다.") },
            confirmButton = {
                TextButton(
                    onClick = { confirmLeave = false; onLeave() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("나가기") }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("계속 입력") } }
        )
    }

    var showSeries by remember { mutableStateOf(false) }
    var showCategory by remember { mutableStateOf(false) }
    if (showSeries) {
        SeriesPickerDialog(
            options = seriesOptions,
            selected = series,
            onSelect = { onSeries(it); showSeries = false; advanceFrom(GoodsStep.SERIES) },
            onAdd = { q -> showSeries = false; onAddSeries(q) },
            onDismiss = { showSeries = false; advanceFrom(GoodsStep.SERIES) }
        )
    }
    if (showCategory) {
        CategoryPickerDialog(
            categories = categories,
            selected = category,
            onSelect = { onCategory(it); showCategory = false; advanceFrom(GoodsStep.CATEGORY) },
            // 고르지 않고 닫으면 카테고리 칸에 머문다(필수값)
            onDismiss = { showCategory = false; if (category.isNotBlank()) advanceFrom(GoodsStep.CATEGORY) },
            scopeGoods = categoryScope
        )
    }
    // 시리즈 칸에서 '시리즈 추가'로 새 시리즈를 만들어 오면 다음 칸으로 넘어간다.
    var seriesAtStep by remember(current) { mutableStateOf(series) }
    LaunchedEffect(series) {
        if (current == GoodsStep.SERIES && editing == null && series.isNotBlank() && series != seriesAtStep) advanceFrom(GoodsStep.SERIES)
        if (editing == GoodsStep.SERIES && series != seriesAtStep) editing = null
        seriesAtStep = series
    }

    // 시리즈 상세에서 '+'로 와서 시리즈가 이미 채워져 있으면 고를 것 없이 바로 다음 칸을 연다.
    // (위의 '바뀌면 다음 칸'은 처음부터 채워진 값에는 바뀌는 순간이 없어 열리지 않았다)
    LaunchedEffect(current, series) {
        if (current == GoodsStep.SERIES && editing == null && !startAtSummary && series.isNotBlank()) advanceFrom(GoodsStep.SERIES)
    }

    // 글을 적으면 바로 다음 칸
    LaunchedEffect(price) { if (current == GoodsStep.PRICE && editing == null && price.isNotBlank()) advanceFrom(GoodsStep.PRICE) }
    // 가격 단위(원 · 위안 · 엔). 금액이 비어 있어도 고른 단위를 기억한다. 새로 등록하면 원, 고치면 저장된 단위.
    // 끝이 세 단위가 아닌 예전 값("$5" 등)은 단위 없이 적힌 그대로 둔다.
    var priceUnit by rememberSaveable(resetKey) {
        mutableStateOf(com.example.wowagoodsproject.component.splitPrice(price).second ?: if (price.isBlank()) "원" else null)
    }
    // 가로 화면: 가격 칸은 키보드 대신 오른쪽에 숫자 패드를 띄운다(키보드가 화면 절반을 덮지 않게).
    val landscape = com.example.wowagoodsproject.component.isSidePanel()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var pricePadOpen by remember { mutableStateOf(false) }
    // 가격 칸이 화면에 있을 때만(요약에서 다른 줄로 옮기면 칸이 사라져도 포커스 소식이 안 올 수 있다)
    val priceFieldShown = if (done) editing == GoodsStep.PRICE else steps.indexOf(GoodsStep.PRICE) in 0..reached
    val showPricePad = landscape && pricePadOpen && priceFieldShown
    val priceAmount = com.example.wowagoodsproject.component.splitPrice(price).let { (a, u) -> if (u != null) a else price }
    val closePricePad = {
        pricePadOpen = false
        focusManager.clearFocus()
        if (editing == GoodsStep.PRICE && price.isNotBlank()) editing = null
        else if (price.isNotBlank()) advanceFrom(GoodsStep.PRICE)
    }

    // 캐릭터 고르기 창을 닫으면 다음 칸
    var charaPickerWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(charaPickerOpen) {
        if (charaPickerOpen) charaPickerWasOpen = true
        else if (charaPickerWasOpen) {
            charaPickerWasOpen = false
            advanceFrom(GoodsStep.CHARA)
        }
    }

    val scroll = rememberScrollState()
    // 다 채우면 맨 위(사진 넣기)로 올라간다.
    LaunchedEffect(done) { if (done) scroll.animateScrollTo(0) }

    /** 한 단계의 입력 칸. [active] 면 열리자마자 화면 안으로 끌어오고, 고치는 중이면 가로로 꽉 찬 '확인' 단추가 붙는다. */
    @Composable
    fun StepField(step: GoodsStep, active: Boolean) {
        val focus = remember { FocusRequester() }
        val bring = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
        // 새 칸이 화면 아래로 밀려 안 보이는 일이 없게, 자리를 잡은 뒤 보이는 곳으로 올린다.
        if (active) LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(30)
            runCatching { bring.bringIntoView() }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bring)
                // 가로 화면에서 가격 말고 다른 칸을 누르면 숫자 패드를 닫는다
                .then(
                    if (landscape && step != GoodsStep.PRICE) Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            if (pricePadOpen) { pricePadOpen = false; focusManager.clearFocus() }
                        }
                    } else Modifier
                )
                .padding(top = 20.dp)
        ) {
            when (step) {
                GoodsStep.KIND -> {
                    StepLabel(step.label, step)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        kindOptions.orEmpty().forEachIndexed { i, label ->
                            SegmentedButton(
                                selected = kindIndex == i,
                                onClick = { onKind(i); advanceFrom(GoodsStep.KIND) },
                                shape = SegmentedButtonDefaults.itemShape(i, kindOptions!!.size, RoundedCornerShape(0.dp))
                            ) { Text(label) }
                        }
                    }
                }
                GoodsStep.SERIES -> UnderlinePicker(
                    step.label,
                    series.ifEmpty { if (steps.indexOf(GoodsStep.SERIES) < reached || editing == GoodsStep.SERIES) UNCATEGORIZED else "" },
                    onClick = { showSeries = true },
                    leadingIcon = { StepIcon(step) }
                )
                GoodsStep.CATEGORY -> UnderlinePicker(step.label, category, onClick = { showCategory = true }, leadingIcon = { StepIcon(step) })
                // 가격: 금액은 칸에, 단위는 오른쪽에서 고른다. 저장은 "금액+단위"(예: 59위안)
                GoodsStep.PRICE -> Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    val amount = priceAmount
                    Box(modifier = Modifier.weight(1f)) {
                        UnderlineNameField(
                            value = amount,
                            onValueChange = { typed ->
                                // 단위까지 쳤으면(59위안) 나눠서 단위도 바꾼다
                                val (a, u) = com.example.wowagoodsproject.component.splitPrice(typed)
                                if (u != null) priceUnit = u
                                onPrice(com.example.wowagoodsproject.component.joinPrice(if (u != null) a else typed, u ?: priceUnit))
                            },
                            label = step.label,
                            focusRequester = focus,
                            imeAction = ImeAction.Next,
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                            readOnly = landscape,
                            onFocusChange = { if (landscape) pricePadOpen = it },
                            // 비어 있으면 넘어가지 않는다(필수값)
                            onImeAction = { if (price.isNotBlank()) advanceFrom(GoodsStep.PRICE) },
                            leadingIcon = { StepIcon(step) }
                        )
                    }
                    com.example.wowagoodsproject.component.PriceUnitPicker(
                        selected = priceUnit,
                        onSelect = { unit ->
                            priceUnit = unit
                            if (amount.isNotBlank()) onPrice(com.example.wowagoodsproject.component.joinPrice(amount, unit))
                        },
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }
                // 캐릭터: 밑줄 없이 아이콘(+제목)과 같은 줄에 고른 캐릭터를 놓는다. 줄을 누르면 고르기 창.
                GoodsStep.CHARA -> Box(modifier = Modifier.fillMaxWidth().clickable(onClick = onPickChara)) {
                    com.example.wowagoodsproject.screen.betray.FieldWithIcon({ StepIcon(step) }, label = step.label, centerAligned = true) {
                        if (charas.isEmpty()) {
                            Text(
                                "눌러서 캐릭터 고르기",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            // 캐릭터 고르기 창의 '고른 캐릭터' 줄과 같은 모양(동그란 사진 + 이름, 누르면 빼기)
                            com.example.wowagoodsproject.component.SelectedCharaStrip(
                                items = charas.map { it to charaAvatar(it) },
                                onRemove = onRemoveChara,
                                reserveSpace = false
                            )
                        }
                    }
                }
                GoodsStep.MEMO -> UnderlineNameField(
                    value = memo, onValueChange = onMemo, label = "메모",
                    focusRequester = focus, imeAction = ImeAction.Done, onImeAction = { if (editing == GoodsStep.MEMO) editing = null },
                    leadingIcon = { StepIcon(step) }
                )
                GoodsStep.STATUS -> {
                    StepLabel(step.label, step)
                    StatusSegmentedButtons(current = status, onSelect = onStatus, showPending = showPending)
                    if (status != GoodsStatus.NOT_GOTTEN) {
                        Spacer(modifier = Modifier.height(12.dp))
                        QuantityStepper(quantity = quantity, onChange = onQuantity)
                    }
                    // 구매예정이면 구매 정보(구입처·구매일·수령 예정일)
                    if (status == GoodsStatus.PENDING) {
                        Spacer(modifier = Modifier.height(20.dp))
                        // 구입처: 지금까지 적은 구입처를 칸 아래 드롭다운으로(구매 정보 입력 창과 같은 부품)
                        val stores = com.example.wowagoodsproject.component.rememberPurchaseStores()
                        var storeFocused by remember { mutableStateOf(false) }
                        var suggestOpen by remember { mutableStateOf(true) }
                        com.example.wowagoodsproject.component.StoreSuggestBox(
                            expanded = storeFocused && suggestOpen,
                            query = purchaseStore,
                            stores = stores,
                            onPick = { onPurchaseStore(it); suggestOpen = false },
                            onDismiss = { suggestOpen = false }
                        ) {
                            UnderlineNameField(
                                value = purchaseStore,
                                onValueChange = { onPurchaseStore(it); suggestOpen = true },
                                label = "구입처",
                                leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(24.dp)) },
                                onFocusChange = { storeFocused = it }
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        com.example.wowagoodsproject.component.DatePickField(
                            label = "구매일", icon = Icons.Default.Event, date = purchaseDate, onDateChange = onPurchaseDate, labelUnderIcon = true
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        com.example.wowagoodsproject.component.DatePickField(
                            label = "수령일", icon = Icons.Default.LocalShipping, date = receiveDate, onDateChange = onReceiveDate, labelUnderIcon = true
                        )
                    }
                    // 처음 채울 때는 메모(선택)도 함께, 그리고 가로로 꽉 찬 '다음'
                    if (editing == null) {
                        // 칸 사이 간격은 위쪽 칸들(시리즈·카테고리·가격)과 같게
                        Spacer(modifier = Modifier.height(20.dp))
                        UnderlineNameField(value = memo, onValueChange = onMemo, label = "메모", imeAction = ImeAction.Done, leadingIcon = { StepIcon(GoodsStep.MEMO) })
                        Spacer(modifier = Modifier.height(28.dp))
                        WideButton("다음") { advanceFrom(GoodsStep.STATUS) }
                    }
                    // 구매예정으로 바꾸면 구매일이 비어 있을 때 오늘 날짜를 넣어 둔다.
                    // 처음 열 때(수정으로 연 구매예정 굿즈 등)는 건드리지 않고, 상태를 바꿨을 때만.
                    var lastStatus by remember { mutableStateOf(status) }
                    LaunchedEffect(status) {
                        if (status != lastStatus && status == GoodsStatus.PENDING && purchaseDate.isEmpty()) {
                            onPurchaseDate(com.example.wowagoodsproject.component.todayDateString())
                        }
                        lastStatus = status
                    }
                    // 구매예정으로 바꿔 칸이 늘어나면 늘어난 곳까지 보이게
                    LaunchedEffect(status) {
                        kotlinx.coroutines.delay(30)
                        runCatching { bring.bringIntoView() }
                    }
                }
                GoodsStep.DONE -> Unit
            }
            if (editing == step && step != GoodsStep.KIND && step != GoodsStep.SERIES && step != GoodsStep.CATEGORY) {
                Spacer(modifier = Modifier.height(12.dp))
                // 요약에서 가격을 지운 채로는 확인을 누를 수 없다(필수값)
                WideButton("확인", enabled = step != GoodsStep.PRICE || price.isNotBlank()) { editing = null }
            }
        }
        // 글을 적는 칸은 열리자마자 바로 적을 수 있게
        if (active && (step == GoodsStep.PRICE || step == GoodsStep.MEMO)) {
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = title,
            onBack = back,
            hideWhileTyping = false
        )
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
        if (landscape && done) {
            // 가로 요약: 왼쪽 사진 | 오른쪽 입력 정보
            val photoVisible = remember { MutableTransitionState(false).apply { targetState = true } }
            AnimatedVisibility(
                visibleState = photoVisible,
                enter = fadeIn(tween(500)),
                modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 16.dp, top = 12.dp, bottom = 24.dp)
            ) {
                PhotoPicker(
                    imageUri = imageUri,
                    onCamera = onCamera,
                    onGallery = onGallery,
                    onUrl = onUrl,
                    onRemove = onRemoveImage,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .imePadding()
                .verticalScroll(scroll)
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
                .widthIn(max = 640.dp)
        ) {
            if (!done) {
                steps.take(reached + 1).filter { it != GoodsStep.DONE }.forEach { step ->
                    key(step) {
                        val visible = remember { MutableTransitionState(false).apply { targetState = true } }
                        AnimatedVisibility(visibleState = visible, enter = fadeIn(tween(400))) {
                            StepField(step, active = step == current)
                        }
                    }
                }
            } else {
                // 요약: 맨 위 사진 넣기 + 밑줄만 남은 줄들(굿즈 상세처럼). 줄을 누르면 그 칸을 다시 고친다.
                val visible = remember { MutableTransitionState(false).apply { targetState = true } }
                AnimatedVisibility(visibleState = visible, enter = fadeIn(tween(500))) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        if (!landscape) {
                            PhotoPicker(
                                imageUri = imageUri,
                                onCamera = onCamera,
                                onGallery = onGallery,
                                onUrl = onUrl,
                                onRemove = onRemoveImage,
                                modifier = Modifier.fillMaxWidth().height(240.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        val summarySteps = steps.filter { it != GoodsStep.DONE }.let { l ->
                            l.take(l.indexOf(GoodsStep.STATUS)) + GoodsStep.MEMO + GoodsStep.STATUS
                        }
                        summarySteps.forEach { step ->
                            if (editing == step) {
                                StepField(step, active = true)
                            } else {
                                val statusColor = when (status) {
                                    GoodsStatus.GOTTEN -> AppStyles.colorGotten
                                    GoodsStatus.PENDING -> AppStyles.colorPending
                                    GoodsStatus.NOT_GOTTEN -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                SummaryRow(
                                    icon = { StepIcon(step) },
                                    label = step.label,
                                    value = when (step) {
                                        GoodsStep.KIND -> kindIndex?.let { kindOptions?.getOrNull(it) }.orEmpty()
                                        GoodsStep.SERIES -> series.ifEmpty { UNCATEGORIZED }
                                        GoodsStep.CATEGORY -> category.ifBlank { "필수 입력" }
                                        GoodsStep.PRICE -> price.ifBlank { "필수 입력" }
                                        GoodsStep.CHARA -> charas.joinToString(", ")
                                        GoodsStep.MEMO -> memo
                                        GoodsStep.STATUS -> when (status) {
                                            GoodsStatus.GOTTEN -> "보유 · ${quantity}개"
                                            GoodsStatus.PENDING -> "구매예정 · ${quantity}개"
                                            GoodsStatus.NOT_GOTTEN -> "미보유"
                                        }
                                        GoodsStep.DONE -> ""
                                    },
                                    valueColor = when {
                                        step == GoodsStep.STATUS -> statusColor
                                        step.label in missingRequired -> MaterialTheme.colorScheme.error
                                        else -> null
                                    },
                                    // 구매 정보는 옆으로 잇지 않고 아래로 한 줄씩
                                    extraLines = if (step == GoodsStep.STATUS && status == GoodsStatus.PENDING) listOfNotNull(
                                        purchaseStore.takeIf { it.isNotBlank() }?.let { "구입처" to it },
                                        purchaseDate.takeIf { it.isNotBlank() }?.let { "구매일" to it },
                                        receiveDate.takeIf { it.isNotBlank() }?.let { "수령 예정일" to it }
                                    ) else emptyList(),
                                    onClick = {
                                        editing = step
                                        // 고르기 칸은 줄을 누르자마자 고르기 창을 연다(고르거나 닫으면 다시 요약)
                                        when (step) {
                                            GoodsStep.SERIES -> showSeries = true
                                            GoodsStep.CATEGORY -> showCategory = true
                                            GoodsStep.CHARA -> onPickChara()
                                            else -> Unit
                                        }
                                    }
                                )
                            }
                        }
                        // 저장은 보유 여부 아래
                        Spacer(modifier = Modifier.height(24.dp))
                        if (missingRequired.isNotEmpty()) {
                            Text(
                                when {
                                    missingRequired.size == 2 -> "카테고리와 가격을 입력해야 저장할 수 있어요"
                                    category.isBlank() -> "카테고리를 골라야 저장할 수 있어요"
                                    else -> "가격을 입력해야 저장할 수 있어요"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        Button(
                            onClick = onSave,
                            enabled = editing == null && !isSaving && missingRequired.isEmpty(),
                            shape = RectangleShape,
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            if (isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            else Text("저장", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        // 가로 화면 가격 칸의 숫자 패드(오른쪽)
        AnimatedVisibility(
            visible = showPricePad,
            enter = androidx.compose.animation.expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
            exit = androidx.compose.animation.shrinkHorizontally(shrinkTowards = Alignment.Start) + androidx.compose.animation.fadeOut(),
            modifier = Modifier.fillMaxHeight()
        ) {
            PriceNumberPad(
                amount = priceAmount,
                unit = priceUnit,
                onType = { onPrice(com.example.wowagoodsproject.component.joinPrice(it, priceUnit)) },
                onDone = closePricePad,
                modifier = Modifier.width(300.dp).fillMaxHeight()
            )
        }
        }
    }
}

/**
 * 가로 화면 가격 입력용 숫자 패드. 1~9, 소수점, 0, 지우기, 그리고 가로로 꽉 찬 '완료'.
 * [onType] 에는 새 금액(단위 없이)을 준다.
 */
@Composable
private fun PriceNumberPad(
    amount: String,
    unit: String?,
    onType: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 지금 적힌 금액
            Text(
                if (amount.isEmpty()) "0" else amount + (unit ?: ""),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (amount.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)
            )
            val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(".", "0", "DEL"))
            rows.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { key ->
                        FilledTonalButton(
                            onClick = {
                                when (key) {
                                    "DEL" -> onType(amount.dropLast(1))
                                    "." -> if ('.' !in amount) onType(if (amount.isEmpty()) "0." else "$amount.")
                                    else -> onType(if (amount == "0") key else amount + key)
                                }
                            },
                            shape = RectangleShape,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            if (key == "DEL") Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "지우기")
                            else Text(key, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
            Button(
                onClick = onDone,
                shape = RectangleShape,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("완료", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        }
    }
}

/** 가로로 꽉 찬, 다른 칸과 구분되는 강조 단추 */
@Composable
private fun WideButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RectangleShape,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
    ) { Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
}

@Composable
private fun StepLabel(text: String, step: GoodsStep? = null) {
    Row(modifier = Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (step != null) {
            androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) { StepIcon(step) }
            Spacer(modifier = Modifier.width(14.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 밑줄 하나로 된 고르기 칸. 비어 있으면 [label] 이 크게, 고르면 작은 이름표 아래에 값이 보인다. 누르면 고르기 창.
 */
@Composable
internal fun UnderlinePicker(
    label: String,
    value: String,
    onClick: () -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null,
    extra: @Composable () -> Unit = {}
) {
    val labelUnderIcon = leadingIcon != null
    Box(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) { com.example.wowagoodsproject.screen.betray.FieldWithIcon(leadingIcon, label = if (labelUnderIcon) label else null) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (labelUnderIcon) {
            // 제목은 아이콘 아래에 있으니 값만(비어 있으면 같은 높이의 빈 줄)
            Text(
                value.ifEmpty { " " },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else if (value.isEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(label, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.padding(vertical = 8.dp))
        } else {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(vertical = 8.dp))
        }
        extra()
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
    } }
}

/**
 * 캐릭터 칸에 명조 캐릭터 그림(옆얼굴 선 그림)을 쓸지. 명조 2차창작 등록은 true,
 * 배신 굿즈 등록은 false 로 내려보내 예전 아이콘(사람 여럿)을 쓴다.
 */
internal val LocalCharaRoleIcon = androidx.compose.runtime.staticCompositionLocalOf { true }

/** 굿즈 등록 칸 아이콘. 캐릭터는 명조면 캐릭터 그림, 배신 굿즈면 사람 여럿 아이콘. 나머지는 머티리얼 아이콘 */
@Composable
internal fun StepIcon(step: GoodsStep) {
    if (step == GoodsStep.CHARA && !LocalCharaRoleIcon.current) {
        Icon(Icons.Outlined.Groups, contentDescription = null, modifier = Modifier.size(24.dp))
        return
    }
    if (step == GoodsStep.CHARA) {
        Icon(
            androidx.compose.ui.res.painterResource(com.example.wowagoodsproject.navigation.CHARA_ICON_RES),
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        return
    }
    val icon = when (step) {
        GoodsStep.KIND -> Icons.Default.Style
        GoodsStep.SERIES -> Icons.Default.Collections
        GoodsStep.CATEGORY -> Icons.Default.Category
        GoodsStep.PRICE -> Icons.Default.Sell
        GoodsStep.MEMO -> Icons.Default.EditNote
        GoodsStep.STATUS -> Icons.Default.Inventory2
        else -> null
    } ?: return
    Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
}

/** 요약 한 줄: 이름표 · 값, 아래 얇은 밑줄. 값이 없으면 '-' */
@Composable
private fun SummaryRow(
    /** 이름표 왼쪽 아이콘(입력 칸과 같은 것) */
    icon: (@Composable () -> Unit)? = null,
    label: String,
    value: String,
    onClick: () -> Unit,
    /** 값 글자색(보유 여부: 보유·구매예정·미보유 색). null 이면 기본 */
    valueColor: androidx.compose.ui.graphics.Color? = null,
    /** 값 아래로 한 줄씩 쌓을 내용(구매 정보) */
    /** (이름, 값). 이름은 굵게, 값은 모두 같은 자리에서 시작한다 */
    extraLines: List<Pair<String, String>> = emptyList()
) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.Top) {
            if (icon != null) {
                Box(modifier = Modifier.padding(end = 14.dp).size(24.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) { icon() }
                }
            }
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(88.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    value.ifEmpty { "-" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (valueColor != null) FontWeight.Bold else FontWeight.Medium,
                    color = valueColor ?: if (value.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                extraLines.forEach { (name, v) ->
                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.width(EXTRA_LABEL_WIDTH)
                        )
                        Text(v, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}


/**
 * 시리즈 고르기. 맨 위 '시리즈 추가'(검색어가 이름 칸에 채워진다), '시리즈 없음', 그리고 등록·사용한 시리즈(아이콘과 함께).
 */
@Composable
internal fun SeriesPickerDialog(
    options: List<SeriesOption>,
    selected: String,
    onSelect: (String) -> Unit,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    val q = search.trim()
    FilterDialogFrame(title = "시리즈 선택", onDismiss = onDismiss, onClear = null) {
        val prefill = if (q.isNotEmpty() && options.none { it.name.equals(q, ignoreCase = true) }) q else ""
        val shown = options.filter { it.name.contains(q, ignoreCase = true) }
        // 낮은 화면에서도 목록이 눌려 안 보이지 않게 미분류·검색·추가·목록을 한 목록으로 함께 스크롤한다.
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            item(key = "__uncategorized") {
                // 시리즈 없이 두기: 크게 '미분류'
                OutlinedButton(
                    onClick = { onSelect("") },
                    shape = RectangleShape,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        if (selected.isBlank()) 2.dp else 1.dp,
                        if (selected.isBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                ) {
                    if (selected.isBlank()) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(UNCATEGORIZED, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            item(key = "__label") {
                Text(
                    "시리즈 선택",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }
            item(key = "__search") {
                SearchField(query = search, onQueryChange = { search = it }, placeholder = "시리즈 검색")
                Spacer(modifier = Modifier.height(12.dp))
            }
            item(key = "__add") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable { onAdd(prefill) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        if (prefill.isNotEmpty()) "시리즈 추가 · '$prefill'" else "시리즈 추가",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            items(shown, key = { it.name }) { o ->
                SeriesOptionRow(o, selected = selected.trim() == o.name, onClick = { onSelect(o.name) })
            }
        }
    }
}

@Composable
private fun SeriesOptionRow(option: SeriesOption, selected: Boolean, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.width(56.dp).height(36.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (option.image.isNotEmpty()) {
                    Image(painter = rememberAsyncImagePainter(File(option.image)), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Outlined.CollectionsBookmark, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(option.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (selected) Icon(Icons.Default.Check, contentDescription = "선택됨", tint = MaterialTheme.colorScheme.primary)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** 등록한 시리즈(배경 사진)와 굿즈에 적힌 시리즈 이름을 합친 고르기 목록. 등록한 것이 먼저, 이름순. */
fun seriesOptionsOf(registered: List<Pair<String, String>>, used: List<String>): List<SeriesOption> {
    val reg = registered.map { SeriesOption(it.first, it.second) }.sortedBy { it.name }
    val rest = used.map { it.trim() }.filter { it.isNotEmpty() && reg.none { r -> r.name == it } }.distinct().sorted().map { SeriesOption(it) }
    return reg + rest
}

/** 요약의 구매 정보 이름 칸 폭("수령 예정일"이 들어가는 폭) */
private val EXTRA_LABEL_WIDTH = 84.dp
