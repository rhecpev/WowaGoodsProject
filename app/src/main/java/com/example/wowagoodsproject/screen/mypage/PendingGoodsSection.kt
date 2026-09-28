package com.example.wowagoodsproject.screen.mypage

import com.example.wowagoodsproject.PendingGoodsNotifier
import androidx.compose.material.icons.filled.MoveToInbox
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocalShipping
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
import com.example.wowagoodsproject.component.PurchaseInfo
import com.example.wowagoodsproject.component.PurchaseInfoDialog
import com.example.wowagoodsproject.component.PurchaseInfoEdit
import com.example.wowagoodsproject.component.encodeGoodsImagePath
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.ui.theme.AppStyles

/**
 * 구매예정 굿즈 목록.
 * 줄을 눌러 여러 개를 고른 뒤 구입처/구매일/수령예정일을 한 번에 적어 넣거나 배송 시작으로 표시한다.
 * 줄마다 있는 배송 시작/취소 버튼으로 하나씩도 바꿀 수 있다.
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
    onApply: (officialIds: Set<Int>, fanIds: Set<Int>, edit: PurchaseInfoEdit) -> Unit,
    isFiltered: Boolean = false,
    /** 줄마다 있는 배송 버튼. 배송 전이면 시작, 배송 중이면 취소한다. */
    onToggleShipping: (GoodsItem) -> Unit = {},
    /** 고른 굿즈 중 아직 배송 전인 것들을 오늘 날짜로 배송 시작한다. */
    onStartShipping: (officialIds: Set<Int>, fanIds: Set<Int>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var showPurchaseDialog by remember { mutableStateOf(false) }

    val totalCount = officialGoods.size + fanGoods.size

    // 필터로 가려진 굿즈가 선택에 남아 있어도 화면에 보이는 것만 다룬다.
    val visibleOfficial = officialGoods.filter { it.goodsId in selectedOfficialIds }
    val visibleFan = fanGoods.filter { it.fanGoodsId in selectedFanIds }
    val selectedCount = visibleOfficial.size + visibleFan.size
    val allSelected = totalCount > 0 && selectedCount == totalCount

    // 고른 굿즈들의 현재 값. 하나로 같을 때만 다이얼로그에 미리 채워 준다.
    val selectedItems: List<GoodsItem> = visibleOfficial + visibleFan
    val notShippedCount = selectedItems.count { it.shippingDate.isEmpty() }
    val commonInfo = PurchaseInfo(
        purchaseStore = selectedItems.map { it.purchaseStore }.distinct().singleOrNull() ?: "",
        receiveDate = selectedItems.map { it.receiveDate }.distinct().singleOrNull() ?: "",
        purchaseDate = selectedItems.map { it.purchaseDate }.distinct().singleOrNull() ?: ""
    )

    if (showPurchaseDialog) {
        PurchaseInfoDialog(
            initial = commonInfo,
            targetCount = selectedCount,
            onDismiss = { showPurchaseDialog = false },
            onConfirm = { edit ->
                showPurchaseDialog = false
                onApply(
                    visibleOfficial.map { it.goodsId }.toSet(),
                    visibleFan.map { it.fanGoodsId }.toSet(),
                    edit
                )
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
                    text = if (isFiltered) "조건에 맞는 굿즈가 없습니다"
                    else "구매예정으로 표시한 굿즈가 없습니다",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isFiltered) "위에서 필터를 바꾸거나 지워 보세요"
                    else "굿즈 상세에서 '구매예정'을 고르면 여기에 모입니다",
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
                text = when {
                    selectedCount > 0 -> "${selectedCount}개 선택됨"
                    isFiltered -> "조건에 맞는 ${totalCount}개"
                    else -> "총 ${totalCount}개"
                },
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
                    onToggle = { onToggleOfficial(goods.goodsId) },
                    onToggleShipping = { onToggleShipping(goods) }
                )
            }
            items(fanGoods, key = { "fan-${it.fanGoodsId}" }) { goods ->
                PendingGoodsRow(
                    goods = goods,
                    isFanGoods = true,
                    selected = goods.fanGoodsId in selectedFanIds,
                    onToggle = { onToggleFan(goods.fanGoodsId) },
                    onToggleShipping = { onToggleShipping(goods) }
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppStyles.paddingLarge),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 이미 배송 중인 굿즈는 시작일을 그대로 두고, 배송 전인 것만 오늘로 시작한다.
                OutlinedButton(
                    onClick = {
                        onStartShipping(
                            visibleOfficial.map { it.goodsId }.toSet(),
                            visibleFan.map { it.fanGoodsId }.toSet()
                        )
                    },
                    enabled = notShippedCount > 0,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalShipping,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (notShippedCount > 0) "${notShippedCount}개 배송 시작" else "배송 시작", maxLines = 1)
                }
                Button(
                    onClick = { showPurchaseDialog = true },
                    enabled = selectedCount > 0,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (selectedCount > 0) "${selectedCount}개 구매 정보" else "굿즈를 골라 주세요", maxLines = 1)
                }
            }
        }
    }
}

/** 썸네일 + 굿즈 정보 + 구입처/구매일/수령예정일(+배송) + 배송 버튼 + 체크박스 한 줄 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PendingGoodsRow(
    goods: GoodsItem,
    isFanGoods: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
    onToggleShipping: () -> Unit
) {
    val encodedPath = encodeGoodsImagePath(goods.imgPath)
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date()) }
    // 날짜를 "yyyy-MM-dd" 문자열로 저장하므로 문자열 비교가 곧 날짜 비교다.
    val overdue = goods.receiveDate.isNotEmpty() && goods.receiveDate < today
    val shipping = goods.shippingDate.isNotEmpty()
    val shippingOverdue = PendingGoodsNotifier.isShippingOverdue(goods.shippingDate)
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
                    text = goods.category.ifEmpty { goods.series } +
                            if (goods.quantity > 1) " ×${goods.quantity}" else "",
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
                // 칩 세 개가 한 줄에 안 들어가는 좁은 화면에서는 다음 줄로 넘긴다.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PurchaseChip(
                        icon = Icons.Default.Storefront,
                        text = goods.purchaseStore.ifEmpty { "구입처 미정" },
                        filled = goods.purchaseStore.isNotEmpty()
                    )
                    PurchaseChip(
                        icon = Icons.Default.CalendarMonth,
                        text = goods.purchaseDate.ifEmpty { "구매일 미정" },
                        filled = goods.purchaseDate.isNotEmpty()
                    )
                    PurchaseChip(
                        icon = Icons.Default.MoveToInbox,
                        text = when {
                            goods.receiveDate.isEmpty() -> "수령일 미정"
                            overdue -> "${goods.receiveDate} 지남"
                            else -> goods.receiveDate
                        },
                        filled = goods.receiveDate.isNotEmpty(),
                        warning = overdue
                    )
                    if (shipping) {
                        PurchaseChip(
                            icon = Icons.Default.LocalShipping,
                            text = if (shippingOverdue) "배송 ${goods.shippingDate}~ 일주일 지남"
                            else "배송 중 ${goods.shippingDate}~",
                            filled = true,
                            warning = shippingOverdue
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                // 줄을 누르면 선택이 바뀌므로, 배송 버튼은 따로 눌리는 작은 버튼으로 둔다.
                if (shipping) {
                    OutlinedButton(
                        onClick = onToggleShipping,
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("배송 취소", style = AppStyles.textCardSmall)
                    }
                } else {
                    FilledTonalButton(
                        onClick = onToggleShipping,
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalShipping,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("배송 시작", style = AppStyles.textCardSmall)
                    }
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
    filled: Boolean,
    warning: Boolean = false
) {
    val color = when {
        warning -> MaterialTheme.colorScheme.error
        filled -> AppStyles.colorPending
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
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
