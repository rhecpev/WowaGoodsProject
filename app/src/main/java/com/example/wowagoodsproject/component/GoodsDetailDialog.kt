package com.example.wowagoodsproject.component

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
 * 큰 이미지 → 시리즈(누르면 이동)·캐릭터 → 가격/카테고리/메모 → 보유 상태 세그먼트 → 세트 정보 순.
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
    onSeriesClick: (String) -> Unit = {}
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val onSelectStatus: (GoodsStatus) -> Unit = { target ->
        shownStatus = target
        applyStatusChange(status, target, onToggleGotten, onSetPending)
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
