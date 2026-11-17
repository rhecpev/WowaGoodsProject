package com.example.wowagoodsproject.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.ui.theme.AppStyles
import java.io.File

/**
 * 세트 굿즈와 구성품 목록.
 * 제목 줄에 세트 정보(사진·이름·시리즈·가격·보유 수)를 늘 보여 준다.
 * 세로 화면: 위에 고른 구성품 정보 + 상태 선택, 아래에 구성품 목록, 맨 아래 일괄 변경.
 * 가로 화면: 왼쪽 목록, 오른쪽 상세. 일괄 변경은 제목 줄에 둔다.
 * 사진은 누르면 전체 화면으로 크게 본다.
 *
 * [onSavePendingInfo]/[onBulkSavePending] 을 넘기면 '구매예정'을 고를 때 구매 정보 입력 다이얼로그를 먼저 띄운다.
 */
@Composable
fun SetGoodsDetailDialog(
    setGoods: GoodsEntity,
    components: List<GoodsEntity>,
    initialComponent: GoodsEntity? = null,
    onDismiss: () -> Unit,
    onToggleGotten: (GoodsEntity) -> Unit,
    onSetPending: (GoodsEntity) -> Unit = {},
    onBulkSetStatus: (GoodsStatus) -> Unit = {},
    highlightChara: String? = null,
    highlightCategory: String? = null,
    onSavePendingInfo: ((GoodsEntity, PurchaseInfo) -> Unit)? = null,
    onBulkSavePending: ((PurchaseInfo) -> Unit)? = null
){
    // 구매 정보를 받는 중인 대상. 구성품 하나면 그 굿즈, 일괄 변경이면 null 인 채로 bulkPending 이 켜진다.
    var pendingTarget by remember { mutableStateOf<GoodsEntity?>(null) }
    var bulkPending by remember { mutableStateOf(false) }

    var selectedComponent by remember(initialComponent) { mutableStateOf(initialComponent) }
    val currentComponent = selectedComponent?.let { selected ->
        components.find { it.goodsId == selected.goodsId }
    }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val gottenCount = components.count { it.isGotten }

    val sortedComponents = remember(components, highlightChara, highlightCategory) {
        components.sortedWith(
            compareByDescending<GoodsEntity> { component ->
                when {
                    (highlightChara != null && hasChara(component.chara, highlightChara)) &&
                            (highlightCategory != null && component.category == highlightCategory) -> 2
                    (highlightChara != null && hasChara(component.chara, highlightChara)) ||
                            (highlightCategory != null && component.category == highlightCategory) -> 1
                    else -> 0
                }
            }.thenByDescending { component ->
                when (component.status) {
                    GoodsStatus.GOTTEN -> 2
                    GoodsStatus.PENDING -> 1
                    else -> 0
                }
            }
        )
    }

    val onChangeStatus: (GoodsEntity, GoodsStatus) -> Unit = { comp, target ->
        if (target == GoodsStatus.PENDING && onSavePendingInfo != null) {
            pendingTarget = comp
        } else {
            applyStatusChange(
                current = comp.status,
                target = target,
                onToggleGotten = { onToggleGotten(comp) },
                onSetPending = { onSetPending(comp) }
            )
        }
    }
    val onBulkChange: (GoodsStatus) -> Unit = { target ->
        if (target == GoodsStatus.PENDING && onBulkSavePending != null) bulkPending = true
        else onBulkSetStatus(target)
    }

    pendingTarget?.let { comp ->
        if (onSavePendingInfo != null) {
            PurchaseInfoDialog(
                initial = comp.purchaseInfo,
                title = if (comp.status == GoodsStatus.PENDING) "구매 정보 수정" else "구매예정으로 표시",
                confirmText = if (comp.status == GoodsStatus.PENDING) "저장" else "구매예정으로",
                onDismiss = { pendingTarget = null },
                onConfirm = { edit ->
                    pendingTarget = null
                    onSavePendingInfo(comp, edit.applyTo(comp.purchaseInfo))
                }
            )
        }
    }
    // 사진을 누르면 크게 본다. 세트 사진과 구성품 사진 모두.
    var fullImage by remember { mutableStateOf<Any?>(null) }
    fullImage?.let { model ->
        FullScreenImageViewer(model = model, onDismiss = { fullImage = null })
    }

    if (bulkPending && onBulkSavePending != null) {
        // 구성품들의 값이 하나로 같을 때만 미리 채워 준다.
        val common = PurchaseInfo(
            purchaseStore = components.map { it.purchaseStore }.distinct().singleOrNull() ?: "",
            receiveDate = components.map { it.receiveDate }.distinct().singleOrNull() ?: "",
            purchaseDate = components.map { it.purchaseDate }.distinct().singleOrNull() ?: ""
        )
        PurchaseInfoDialog(
            initial = common,
            title = "구성품 ${components.size}개 구매예정",
            confirmText = "구매예정으로",
            onDismiss = { bulkPending = false },
            onConfirm = { edit ->
                bulkPending = false
                onBulkSavePending(edit.applyTo(common))
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RectangleShape, // 앱 전체 기준: 팝업 틀도 각지게
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp)
            ) {
                // 세트 정보는 구성품을 골랐든 안 골랐든 늘 제목 줄에 보인다. 사진을 누르면 크게 본다.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SetInfoHeader(
                        setGoods = setGoods,
                        componentTotal = components.size,
                        componentGotten = gottenCount,
                        onImageClick = { fullImage = it },
                        modifier = Modifier.weight(1f)
                    )
                    // 가로 화면은 높이가 모자라서 일괄 변경을 제목 줄에 붙여 왼쪽 상세 칸 높이를 확보한다.
                    if (isLandscape) {
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "구성품 ${components.size}개 일괄 변경",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            BulkStatusButtons(components = components, onSelect = onBulkChange)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "닫기")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                if (isLandscape) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // 왼쪽 구성품 목록, 오른쪽 고른 구성품 상세.
                        ComponentList(
                            components = sortedComponents,
                            selectedId = currentComponent?.goodsId,
                            highlightChara = highlightChara,
                            highlightCategory = highlightCategory,
                            onSelect = { selectedComponent = it },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        // 사진·정보는 위에서 스크롤하고, 보유 상태 버튼은 칸 맨 아래에 고정해 스크롤 없이 누른다.
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                if (currentComponent != null) {
                                    SelectedComponentDetail(
                                        comp = currentComponent,
                                        large = true,
                                        onChangeStatus = { onChangeStatus(currentComponent, it) },
                                        onImageClick = { fullImage = it },
                                        onEditPurchase = if (onSavePendingInfo != null) {
                                            { pendingTarget = currentComponent }
                                        } else null
                                    )
                                } else {
                                    SelectHint(large = true)
                                }
                            }
                            if (currentComponent != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                StatusSegmentedButtons(
                                    current = currentComponent.status,
                                    onSelect = { onChangeStatus(currentComponent, it) }
                                )
                            }
                        }
                    }
                } else {
                    if (currentComponent != null) {
                        SelectedComponentDetail(
                            comp = currentComponent,
                            large = false,
                            onChangeStatus = { onChangeStatus(currentComponent, it) },
                            onImageClick = { fullImage = it },
                            onEditPurchase = if (onSavePendingInfo != null) {
                                { pendingTarget = currentComponent }
                            } else null
                        )
                    } else {
                        SelectHint(large = false)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    ComponentList(
                        components = sortedComponents,
                        selectedId = currentComponent?.goodsId,
                        highlightChara = highlightChara,
                        highlightCategory = highlightCategory,
                        onSelect = { selectedComponent = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "구성품 ${components.size}개 일괄 변경",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    BulkStatusButtons(components = components, onSelect = onBulkChange)
                }
            }
        }
    }
}

/** 구성품 상태가 이미 하나로 맞춰져 있으면 그 칸을 선택된 상태로 보여준다(섞여 있으면 선택 없음). */
@Composable
private fun BulkStatusButtons(components: List<GoodsEntity>, onSelect: (GoodsStatus) -> Unit) {
    StatusSegmentedButtons(
        current = components.map { it.status }.distinct().singleOrNull(),
        onSelect = onSelect
    )
}

/** 굿즈 이미지 경로를 Coil/전체화면 뷰어가 쓰는 모델로 바꾼다. 없으면 null. */
internal fun goodsImageModel(imgPath: String): Any? {
    val encodedPath = encodeGoodsImagePath(imgPath)
    return when {
        encodedPath.startsWith("http") -> encodedPath
        encodedPath.isNotEmpty() -> File(encodedPath)
        else -> null
    }
}

/** 둥근 모서리 이미지 칸. 이미지가 있으면 눌러서 크게 본다. */
@Composable
private fun ZoomableThumb(
    model: Any?,
    onClick: (Any) -> Unit,
    modifier: Modifier,
    corner: Int,
    innerPadding: Int
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = model != null) { model?.let(onClick) }
    ) {
        Image(
            painter = rememberAsyncImagePainter(model = model),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding.dp),
            contentScale = ContentScale.Fit
        )
    }
}

/** 제목 줄의 세트 정보: 사진(누르면 크게) + 세트 이름 + 시리즈·가격 + 구성품 보유 수. */
@Composable
private fun SetInfoHeader(
    setGoods: GoodsEntity,
    componentTotal: Int,
    componentGotten: Int,
    onImageClick: (Any) -> Unit,
    modifier: Modifier = Modifier
) {
    val ownedColor = when {
        componentTotal > 0 && componentGotten == componentTotal -> AppStyles.colorGotten
        componentGotten > 0 -> AppStyles.colorPartialGotten
        else -> AppStyles.colorNotGotten
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        ZoomableThumb(
            model = goodsImageModel(setGoods.imgPath),
            onClick = onImageClick,
            modifier = Modifier.size(64.dp),
            corner = 0,
            innerPadding = 4
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = setGoods.memo.ifEmpty { CATEGORY_SET },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOf(setGoods.series, setGoods.price).filter { it.isNotEmpty() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "구성품 ${componentTotal}개 · ${componentGotten}/${componentTotal} 보유",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = ownedColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 구성품을 아직 고르지 않았을 때 상세 칸의 안내. */
@Composable
private fun SelectHint(large: Boolean) {
    Text(
        text = if (large) "왼쪽 구성품을 누르면 여기서 보유 상태를 바꿀 수 있어요"
        else "아래 구성품을 누르면 여기서 보유 상태를 바꿀 수 있어요",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun SelectedComponentDetail(
    comp: GoodsEntity,
    large: Boolean,
    onChangeStatus: (GoodsStatus) -> Unit,
    onImageClick: (Any) -> Unit,
    /** 구매예정 구성품의 구매 정보 고치기. null 이면 수정 버튼 없이 보여 주기만 한다. */
    onEditPurchase: (() -> Unit)? = null
) {
    val model = goodsImageModel(comp.imgPath)

    Column {
        if (large) {
            // 가로 화면: 보유 상태 버튼은 감싸는 칸 맨 아래에 따로 고정해 둔다(여기서는 그리지 않는다).
            Text(
                text = comp.category,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            ZoomableThumb(
                model = model,
                onClick = onImageClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                corner = 0,
                innerPadding = 8
            )
            Spacer(modifier = Modifier.height(4.dp))
            DetailRow(label = "캐릭터", value = comp.chara)
            DetailRow(label = "가격", value = comp.price)
            if (comp.memo.isNotEmpty()) DetailRow(label = "메모", value = comp.memo)
            if (comp.status == GoodsStatus.PENDING) {
                Spacer(modifier = Modifier.height(8.dp))
                ComponentPurchaseInfo(comp = comp, onEdit = onEditPurchase)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ZoomableThumb(
                    model = model,
                    onClick = onImageClick,
                    modifier = Modifier.size(84.dp),
                    corner = 0,
                    innerPadding = 4
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = comp.category,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = comp.chara,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = comp.price,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }
            if (comp.status == GoodsStatus.PENDING) {
                Spacer(modifier = Modifier.height(8.dp))
                ComponentPurchaseInfo(comp = comp, onEdit = onEditPurchase)
            }
            Spacer(modifier = Modifier.height(12.dp))
            StatusSegmentedButtons(current = comp.status, onSelect = onChangeStatus)
        }
    }
}

/**
 * 구매예정 구성품의 구매 정보(굿즈 상세 시트와 같은 항목). 제목 줄 오른쪽 '수정'으로 고친다.
 * 정하지 않은 값은 '미정'. 배송은 시작했을 때만 '배송 중 (날짜~)'.
 */
@Composable
private fun ComponentPurchaseInfo(comp: GoodsEntity, onEdit: (() -> Unit)?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "구매 정보",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            if (onEdit != null) {
                TextButton(onClick = onEdit, shape = RectangleShape) { Text("수정") }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        DetailRow(label = "구입처", value = comp.purchaseStore.ifEmpty { "미정" })
        DetailRow(label = "구매일", value = comp.purchaseDate.ifEmpty { "미정" })
        DetailRow(label = "수령예정일", value = comp.receiveDate.ifEmpty { "미정" })
        DetailRow(
            label = "배송",
            value = if (comp.shippingDate.isEmpty()) "배송 전" else "배송 중 (${comp.shippingDate}~)"
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ComponentList(
    components: List<GoodsEntity>,
    selectedId: Int?,
    highlightChara: String?,
    highlightCategory: String?,
    onSelect: (GoodsEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "구성품 ${components.size}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        // 구성품이 많아 넘치면 오른쪽 스크롤바로 끌어 옮긴다.
        val listState = rememberLazyListState()
        Row(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            items(components, key = { it.goodsId }) { component ->
                val isSelected = selectedId == component.goodsId
                val isHighlighted = when {
                    highlightChara != null && highlightCategory != null ->
                        hasChara(component.chara, highlightChara) && component.category == highlightCategory
                    highlightChara != null -> hasChara(component.chara, highlightChara)
                    highlightCategory != null -> component.category == highlightCategory
                    else -> false
                }
                val encodedPath = encodeGoodsImagePath(component.imgPath)
                // 소식 탭처럼 박스 없이 왼쪽에 보유 상태 색 세로선을 긋고 줄 아래를 얇은 선으로 나눈다.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .background(
                            when {
                                isSelected -> MaterialTheme.colorScheme.secondaryContainer
                                isHighlighted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                else -> Color.Transparent
                            }
                        )
                        .clickable { onSelect(component) }
                ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(component.status.asGottenStatus().color())
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .alpha(if (component.status == GoodsStatus.NOT_GOTTEN) 0.45f else 1f)
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
                            text = component.category,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = component.price,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    GottenStatusBadge(status = component.status.asGottenStatus())
                }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        ListScrollbar(state = listState)
        }
    }
}
