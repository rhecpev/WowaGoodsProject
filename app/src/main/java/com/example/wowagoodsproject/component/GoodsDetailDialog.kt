package com.example.wowagoodsproject.component

import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.wowagoodsproject.ui.theme.AppStyles
import java.io.File

/**
 * 굿즈 상세. 화면 아래에서 올라오는 바텀시트로 띄운다.
 * 큰 이미지 → 시리즈(누르면 이동)·캐릭터 → 가격/카테고리/메모 → 보유 상태 세그먼트 → (구매 정보) → 세트 정보 순.
 *
 * [onSavePendingInfo] 를 넘기면 '구매예정'을 고를 때 구입처/구매일/수령예정일 입력 다이얼로그를 먼저 띄우고,
 * 확인하면 상태와 구매 정보를 함께 저장한다. 구매예정인 동안은 구매 정보를 보여주고 고칠 수 있다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoodsDetailDialog(
    imgPath: String,
    series: String,
    chara: String,
    category: String,
    price: String,
    isGotten: Boolean,
    isPending: Boolean = false,
    memo: String = "",
    setGoods: GoodsItem? = null,
    setComponentTotal: Int = 0,
    setComponentGotten: Int = 0,
    onSetClick: () -> Unit = {},
    onDismiss: () -> Unit,
    onToggleGotten: () -> Unit,
    onSetPending: () -> Unit,
    onDelete: () -> Unit,
    showDelete: Boolean = true,
    onSeriesClick: (String) -> Unit = {},
    purchaseInfo: PurchaseInfo = PurchaseInfo(),
    onSavePendingInfo: ((PurchaseInfo) -> Unit)? = null,
    /** 구매예정 굿즈의 배송 시작일. 빈 문자열이면 배송 전. */
    shippingDate: String = "",
    /** 넘기면 구매 정보 아래에 배송 시작/취소 버튼을 둔다. */
    onToggleShipping: (() -> Unit)? = null,
    quantity: Int = 1,
    onQuantityChange: ((Int) -> Unit)? = null
){
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    val encodedPath = encodeGoodsImagePath(imgPath)
    val imageModel: Any? = if (encodedPath.startsWith("http")) {
        encodedPath
    } else if (encodedPath.isNotEmpty()) {
        File(encodedPath)
    } else null

    val status = when {
        isGotten -> GoodsStatus.GOTTEN
        isPending -> GoodsStatus.PENDING
        else -> GoodsStatus.NOT_GOTTEN
    }
    // 누르자마자 선택이 옮겨간 것처럼 보이게 먼저 반영하고, DB 값이 돌아오면 그 값으로 맞춘다.
    var shownStatus by remember(status) { mutableStateOf(status) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showFullImage by remember { mutableStateOf(false) }
    var showPurchaseDialog by remember { mutableStateOf(false) }
    // 누르자마자 숫자가 바뀌어 보이게 먼저 반영하고, DB 값이 돌아오면 그 값으로 맞춘다.
    var shownQuantity by remember(quantity) { mutableIntStateOf(quantity) }
    val onChangeQuantity: ((Int) -> Unit)? = onQuantityChange?.let { save ->
        { q -> shownQuantity = q; save(q) }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val onSelectStatus: (GoodsStatus) -> Unit = { target ->
        if (target == GoodsStatus.PENDING && onSavePendingInfo != null) {
            // 구매 정보를 받은 뒤에 상태를 바꾼다. 취소하면 그대로 둔다.
            showPurchaseDialog = true
        } else {
            shownStatus = target
            applyStatusChange(status, target, onToggleGotten, onSetPending)
        }
    }
    val shownPurchaseInfo =
        if (shownStatus == GoodsStatus.PENDING && onSavePendingInfo != null) purchaseInfo else null

    if (showPurchaseDialog && onSavePendingInfo != null) {
        val editing = status == GoodsStatus.PENDING
        PurchaseInfoDialog(
            initial = purchaseInfo,
            title = if (editing) "구매 정보 수정" else "구매예정으로 표시",
            confirmText = if (editing) "저장" else "구매예정으로",
            onDismiss = { showPurchaseDialog = false },
            onConfirm = { edit ->
                showPurchaseDialog = false
                shownStatus = GoodsStatus.PENDING
                onSavePendingInfo(edit.applyTo(purchaseInfo))
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            title = { Text("굿즈를 삭제할까요?") },
            text = { Text("삭제한 굿즈는 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false; onDelete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("취소") }
            }
        )
    }

    if (showFullImage && imageModel != null) {
        FullScreenImageViewer(
            model = imageModel,
            onDismiss = { showFullImage = false }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            if (isLandscape) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    HeroImage(
                        model = imageModel,
                        status = shownStatus,
                        onClick = { showFullImage = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(280.dp)
                    )
                    DetailBody(
                        series = series,
                        chara = chara,
                        category = category,
                        price = price,
                        memo = memo,
                        status = shownStatus,
                        onSelectStatus = onSelectStatus,
                        purchaseInfo = shownPurchaseInfo,
                        onEditPurchase = { showPurchaseDialog = true },
                        shippingDate = shippingDate,
                        onToggleShipping = onToggleShipping,
                        quantity = shownQuantity,
                        onQuantityChange = onChangeQuantity,
                        setGoods = setGoods,
                        setComponentTotal = setComponentTotal,
                        setComponentGotten = setComponentGotten,
                        onSetClick = onSetClick,
                        showDelete = showDelete,
                        onDeleteClick = { showDeleteConfirm = true },
                        onSeriesClick = onSeriesClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                HeroImage(
                    model = imageModel,
                    status = shownStatus,
                    onClick = { showFullImage = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
                DetailBody(
                    series = series,
                    chara = chara,
                    category = category,
                    price = price,
                    memo = memo,
                    status = shownStatus,
                    onSelectStatus = onSelectStatus,
                    purchaseInfo = shownPurchaseInfo,
                    onEditPurchase = { showPurchaseDialog = true },
                    shippingDate = shippingDate,
                    onToggleShipping = onToggleShipping,
                    quantity = shownQuantity,
                    onQuantityChange = onChangeQuantity,
                    setGoods = setGoods,
                    setComponentTotal = setComponentTotal,
                    setComponentGotten = setComponentGotten,
                    onSetClick = onSetClick,
                    showDelete = showDelete,
                    onDeleteClick = { showDeleteConfirm = true },
                    onSeriesClick = onSeriesClick
                )
            }
        }
    }
}

@Composable
private fun HeroImage(
    model: Any?,
    status: GoodsStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = model != null, onClick = onClick)
    ) {
        Image(
            painter = rememberAsyncImagePainter(model = model),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentScale = ContentScale.Fit
        )
        GottenStatusBadge(
            status = status.asGottenStatus(),
            filled = true,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        )
    }
}

@Composable
private fun DetailBody(
    series: String,
    chara: String,
    category: String,
    price: String,
    memo: String,
    status: GoodsStatus,
    onSelectStatus: (GoodsStatus) -> Unit,
    purchaseInfo: PurchaseInfo?,
    onEditPurchase: () -> Unit,
    shippingDate: String,
    onToggleShipping: (() -> Unit)?,
    quantity: Int,
    onQuantityChange: ((Int) -> Unit)?,
    setGoods: GoodsItem?,
    setComponentTotal: Int,
    setComponentGotten: Int,
    onSetClick: () -> Unit,
    showDelete: Boolean,
    onDeleteClick: () -> Unit,
    onSeriesClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // 시리즈명은 링크처럼 보여주고 누르면 해당 시리즈 화면으로 이동한다.
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onSeriesClick(series) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = series,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "시리즈로 이동",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = chara,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // 구매예정 굿즈는 구매 정보(배송 포함)를 가장 먼저 보고 고치게 굿즈 정보보다 위에 둔다.
        if (purchaseInfo != null) {
            Spacer(modifier = Modifier.height(12.dp))
            PurchaseInfoSummary(
                info = purchaseInfo,
                onEdit = onEditPurchase,
                shippingDate = shippingDate,
                onToggleShipping = onToggleShipping
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                InfoLine(label = "가격", value = price.ifEmpty { "-" })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                InfoLine(label = "카테고리", value = category.ifEmpty { "-" })
                if (memo.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    InfoLine(label = "메모", value = memo)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "보유 상태",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        StatusSegmentedButtons(current = status, onSelect = onSelectStatus)

        // 수량은 가지고 있거나 살 예정인 굿즈에만 의미가 있다.
        if (onQuantityChange != null && status != GoodsStatus.NOT_GOTTEN) {
            Spacer(modifier = Modifier.height(12.dp))
            QuantityStepper(quantity = quantity, onChange = onQuantityChange)
        }

        if (setGoods != null) {
            Spacer(modifier = Modifier.height(20.dp))
            SetGoodsSummary(
                setGoods = setGoods,
                componentTotal = setComponentTotal,
                componentGotten = setComponentGotten,
                onClick = onSetClick
            )
        }

        if (showDelete) {
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                onClick = onDeleteClick,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("이 굿즈 삭제")
            }
        }
    }
}

private const val MAX_QUANTITY = 99

/** 수량 − / + 버튼. 가운데 숫자를 누르면 직접 입력한다. */
@Composable
private fun QuantityStepper(quantity: Int, onChange: (Int) -> Unit) {
    var showInput by remember { mutableStateOf(false) }
    if (showInput) {
        QuantityInputDialog(
            initial = quantity,
            onDismiss = { showInput = false },
            onConfirm = { showInput = false; if (it != quantity) onChange(it) }
        )
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "수량",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onChange(quantity - 1) }, enabled = quantity > 1) {
                Icon(Icons.Default.Remove, contentDescription = "수량 줄이기")
            }
            Text(
                text = "${quantity}개",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(min = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showInput = true }
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            )
            IconButton(onClick = { onChange(quantity + 1) }, enabled = quantity < MAX_QUANTITY) {
                Icon(Icons.Default.Add, contentDescription = "수량 늘리기")
            }
        }
    }
}

/** 수량 직접 입력. 숫자만 받고 1~[MAX_QUANTITY] 범위일 때만 확인할 수 있다. */
@Composable
private fun QuantityInputDialog(initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by remember {
        mutableStateOf(TextFieldValue("$initial", selection = TextRange(0, "$initial".length)))
    }
    val value = text.text.toIntOrNull()
    val valid = value != null && value in 1..MAX_QUANTITY
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("수량 입력") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { new ->
                    if (new.text.length <= 2 && new.text.all { it.isDigit() }) text = new
                },
                label = { Text("수량") },
                suffix = { Text("개") },
                supportingText = { Text("1~${MAX_QUANTITY}개") },
                isError = text.text.isNotEmpty() && !valid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) onConfirm(value!!) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value!!) }, enabled = valid) { Text("확인") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

/**
 * 구매예정 굿즈의 구입처/구매일/수령예정일. 오른쪽 위 버튼으로 고친다.
 * [onToggleShipping] 이 있으면 배송 상태 줄과 배송 시작/취소 버튼을 함께 보여 준다.
 */
@Composable
private fun PurchaseInfoSummary(
    info: PurchaseInfo,
    onEdit: () -> Unit,
    shippingDate: String = "",
    onToggleShipping: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "구매 정보",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onEdit) { Text("수정") }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                InfoLine(label = "구입처", value = info.purchaseStore.ifEmpty { "미정" })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                InfoLine(label = "구매일", value = info.purchaseDate.ifEmpty { "미정" })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                InfoLine(label = "수령예정일", value = info.receiveDate.ifEmpty { "미정" })
                if (onToggleShipping != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    InfoLine(
                        label = "배송",
                        value = if (shippingDate.isEmpty()) "배송 전" else "배송 중 (${shippingDate}~)"
                    )
                }
            }
        }
        if (onToggleShipping != null) {
            Spacer(modifier = Modifier.height(8.dp))
            if (shippingDate.isEmpty()) {
                FilledTonalButton(onClick = onToggleShipping, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = Icons.Default.LocalShipping,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("오늘 배송 시작")
                }
            } else {
                OutlinedButton(onClick = onToggleShipping, modifier = Modifier.fillMaxWidth()) {
                    Text("배송 취소")
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 이 굿즈가 속한 세트 굿즈 요약. 누르면 같은 세트의 굿즈 목록 팝업이 열린다. */
@Composable
private fun SetGoodsSummary(
    setGoods: GoodsItem,
    componentTotal: Int,
    componentGotten: Int,
    onClick: () -> Unit
) {
    val encodedPath = encodeGoodsImagePath(setGoods.imgPath)
    val setName = setGoods.memo.ifEmpty { CATEGORY_SET }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "세트 굿즈",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { onClick() },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(
                            model = if (encodedPath.isNotEmpty()) encodedPath else null
                        ),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = setName,
                        style = AppStyles.textCardSubtitle.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (setGoods.price.isNotEmpty()) {
                        Text(
                            text = setGoods.price,
                            style = AppStyles.textCardSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "구성품 ${componentTotal}개 · ${componentGotten}/${componentTotal} 보유",
                        style = AppStyles.textCardSmall,
                        color = when {
                            componentTotal > 0 && componentGotten == componentTotal -> AppStyles.colorGotten
                            componentGotten > 0 -> AppStyles.colorPartialGotten
                            else -> AppStyles.colorNotGotten
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "세트 구성품 보기",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    onValueClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppStyles.paddingSmall),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = AppStyles.textCardSubtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = AppStyles.textCardSubtitle.copy(
                textDecoration = if (onValueClick != null) TextDecoration.Underline else TextDecoration.None
            ),
            color = valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier
                .padding(start = AppStyles.paddingLarge)
                .then(
                    if (onValueClick != null) Modifier.clickable { onValueClick() }
                    else Modifier
                )
        )
    }
}
