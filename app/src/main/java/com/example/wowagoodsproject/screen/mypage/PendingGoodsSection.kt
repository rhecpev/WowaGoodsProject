package com.example.wowagoodsproject.screen.mypage

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.wowagoodsproject.component.GoodsItem
import com.example.wowagoodsproject.component.encodeGoodsImagePath
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.ui.theme.AppStyles
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** 구매일 저장 형식. DatePicker 가 돌려주는 UTC 자정 밀리초와 이 문자열을 서로 변환해 쓴다. */
private const val PURCHASE_DATE_PATTERN = "yyyy-MM-dd"

private fun purchaseDateFormat() = SimpleDateFormat(PURCHASE_DATE_PATTERN, Locale.KOREA).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

private fun formatPurchaseDate(millis: Long): String = purchaseDateFormat().format(millis)

private fun parsePurchaseDate(date: String): Long? =
    if (date.isEmpty()) null else runCatching { purchaseDateFormat().parse(date)?.time }.getOrNull()

/**
 * 구매예정 굿즈 목록.
 * 줄을 눌러 여러 개를 고른 뒤 구매일/구입처를 한 번에 적어 넣는다.
 */
@Composable
fun PendingGoodsSection(
    officialGoods: List<GoodsEntity>,
    fanGoods: List<FanGoodsEntity>,
    selectedOfficialIds: Set<Int>,
    selectedFanIds: Set<Int>,
    onToggleOfficial: (Int) -> Unit,
    onToggleFan: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onApply: (purchaseDate: String?, purchaseStore: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPurchaseDialog by remember { mutableStateOf(false) }

    val totalCount = officialGoods.size + fanGoods.size
    val selectedCount = selectedOfficialIds.size + selectedFanIds.size
    val allSelected = totalCount > 0 && selectedCount == totalCount

    // 고른 굿즈들의 현재 값. 하나로 같을 때만 다이얼로그에 미리 채워 준다.
    val selectedItems: List<GoodsItem> =
        officialGoods.filter { it.goodsId in selectedOfficialIds } +
                fanGoods.filter { it.fanGoodsId in selectedFanIds }
    val commonDate = selectedItems.map { it.purchaseDate }.distinct().singleOrNull() ?: ""
    val commonStore = selectedItems.map { it.purchaseStore }.distinct().singleOrNull() ?: ""

    if (showPurchaseDialog) {
        PurchaseInfoDialog(
            targetCount = selectedCount,
            initialDate = commonDate,
            initialStore = commonStore,
            onDismiss = { showPurchaseDialog = false },
            onConfirm = { date, store ->
                showPurchaseDialog = false
                onApply(date, store)
            }
        )
    }

    if (totalCount == 0) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "구매예정으로 표시한 굿즈가 없습니다",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "굿즈 상세에서 '구매예정'을 고르면 여기에 모입니다",
                    style = AppStyles.textCardSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppStyles.paddingLarge, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (selectedCount > 0) "${selectedCount}개 선택됨" else "총 ${totalCount}개",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (selectedCount > 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = if (allSelected) onClearSelection else onSelectAll) {
                Text(if (allSelected) "선택 해제" else "전체 선택")
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(
                start = AppStyles.paddingLarge,
                end = AppStyles.paddingLarge,
                bottom = AppStyles.paddingMedium
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(officialGoods, key = { "official-${it.goodsId}" }) { goods ->
                PendingGoodsRow(
                    goods = goods,
                    isFanGoods = false,
                    selected = goods.goodsId in selectedOfficialIds,
                    onToggle = { onToggleOfficial(goods.goodsId) }
                )
            }
            items(fanGoods, key = { "fan-${it.fanGoodsId}" }) { goods ->
                PendingGoodsRow(
                    goods = goods,
                    isFanGoods = true,
                    selected = goods.fanGoodsId in selectedFanIds,
                    onToggle = { onToggleFan(goods.fanGoodsId) }
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp
        ) {
            Button(
                onClick = { showPurchaseDialog = true },
                enabled = selectedCount > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppStyles.paddingLarge)
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (selectedCount > 0) "${selectedCount}개에 구매 정보 입력"
                    else "굿즈를 골라 주세요"
                )
            }
        }
    }
}

/** 썸네일 + 굿즈 정보 + 구매일/구입처 + 체크박스 한 줄 */
@Composable
private fun PendingGoodsRow(
    goods: GoodsItem,
    isFanGoods: Boolean,
    selected: Boolean,
    onToggle: () -> Unit
) {
    val encodedPath = encodeGoodsImagePath(goods.imgPath)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            // 체크박스만이 아니라 줄 전체를 눌러도 선택되게 한다.
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() }),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
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
                    text = goods.category.ifEmpty { goods.series },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(
                        if (isFanGoods) "2차창작" else goods.series,
                        goods.chara
                    ).filter { it.isNotEmpty() }.joinToString(" · "),
                    style = AppStyles.textCardSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PurchaseChip(
                        icon = Icons.Default.CalendarMonth,
                        text = goods.purchaseDate.ifEmpty { "구매일 미정" },
                        filled = goods.purchaseDate.isNotEmpty()
                    )
                    PurchaseChip(
                        icon = Icons.Default.Storefront,
                        text = goods.purchaseStore.ifEmpty { "구입처 미정" },
                        filled = goods.purchaseStore.isNotEmpty()
                    )
                }
            }
            Checkbox(checked = selected, onCheckedChange = null)
        }
    }
}

@Composable
private fun PurchaseChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    filled: Boolean
) {
    val color =
        if (filled) AppStyles.colorPending else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .background(color.copy(alpha = if (filled) 0.14f else 0.06f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = text,
            style = AppStyles.textCardSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 구매일/구입처 입력 다이얼로그.
 * 손대지 않은 항목은 null 로 돌려줘서 기존 값을 그대로 둔다(일괄 적용 때 섞인 값이 덮이지 않게).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseInfoDialog(
    targetCount: Int,
    initialDate: String,
    initialStore: String,
    onDismiss: () -> Unit,
    onConfirm: (purchaseDate: String?, purchaseStore: String?) -> Unit
) {
    var date by remember { mutableStateOf(initialDate) }
    var store by remember { mutableStateOf(initialStore) }
    var dateTouched by remember { mutableStateOf(false) }
    var storeTouched by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = parsePurchaseDate(date)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            date = formatPurchaseDate(it)
                            dateTouched = true
                        }
                        showDatePicker = false
                    }
                ) { Text("확인") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("취소") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("구매 정보 입력") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "고른 ${targetCount}개 굿즈에 적용됩니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 달력으로만 고르는 값이라 입력창 대신 누를 수 있는 줄로 보여준다.
                Surface(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "구매일",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = date.ifEmpty { "날짜 선택" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (date.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (date.isNotEmpty()) {
                            IconButton(onClick = { date = ""; dateTouched = true }) {
                                Icon(Icons.Default.Clear, contentDescription = "구매일 지우기")
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = store,
                    onValueChange = { store = it; storeTouched = true },
                    label = { Text("구입처") },
                    placeholder = { Text("예: 알리익스프레스, 홍대 팝업") },
                    leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        if (dateTouched) date else null,
                        if (storeTouched) store.trim() else null
                    )
                },
                enabled = dateTouched || storeTouched
            ) { Text("적용") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}
