package com.example.wowagoodsproject.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 다중 선택 모드를 켜고 끄는 버튼. 켜져 있으면 강조 색으로 채운다. */
@Composable
fun MultiSelectToggle(
    selectionMode: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalIconToggleButton(
        checked = selectionMode,
        onCheckedChange = { onToggle() },
        modifier = modifier.size(36.dp),
        colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            checkedContainerColor = MaterialTheme.colorScheme.primary,
            checkedContentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Icon(
            imageVector = Icons.Default.Checklist,
            contentDescription = if (selectionMode) "다중 선택 끝내기" else "다중 선택",
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * 다중 선택 중일 때 목록 위에 붙는 줄: 선택 개수 / 전체 선택, 그리고 보유·미보유·구매예정 일괄 변경 버튼.
 * 구매예정을 누르면 구매 정보 입력 다이얼로그를 먼저 띄운다.
 * [vertical] 이면(가로 화면) 목록 옆에 세우는 세로 줄로 그리고, 버튼도 위아래로 쌓는다.
 * [showSummary] 를 끄면 선택 개수 / 전체 선택 줄을 빼고 버튼만 그린다.
 * 그 줄을 필터 칩 자리에 따로 넣는 화면([BulkSelectionSummary])에서 쓴다.
 * [embedded] 는 [vertical] 일 때만 쓴다. 따로 세우는 줄이 아니라 왼쪽 필터 줄 안(칩 자리)에 넣을 때 켜며,
 * 바탕·폭·스크롤을 스스로 잡지 않고 감싸는 줄의 것을 따른다.
 *
 * @param selectedItems 화면에 보이면서 선택된 굿즈
 * @param onApply 고른 상태와(구매예정일 때만) 입력한 구매 정보. 손대지 않은 항목은 null 이라 기존 값을 둔다.
 */
@Composable
fun BulkStatusBar(
    selectedItems: List<GoodsItem>,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onApply: (GoodsStatus, PurchaseInfoEdit?) -> Unit,
    vertical: Boolean = false,
    showSummary: Boolean = true,
    embedded: Boolean = false
) {
    var showPurchaseDialog by remember { mutableStateOf(false) }
    val count = selectedItems.size

    if (showPurchaseDialog) {
        // 고른 굿즈들의 값이 하나로 같을 때만 미리 채워 준다.
        val common = PurchaseInfo(
            purchaseStore = selectedItems.map { it.purchaseStore }.distinct().singleOrNull() ?: "",
            purchaseDate = selectedItems.map { it.purchaseDate }.distinct().singleOrNull() ?: "",
            receiveDate = selectedItems.map { it.receiveDate }.distinct().singleOrNull() ?: ""
        )
        PurchaseInfoDialog(
            initial = common,
            title = "구매예정으로 표시",
            confirmText = "구매예정으로",
            targetCount = count,
            requireChange = false,
            returnAll = false,
            onDismiss = { showPurchaseDialog = false },
            onConfirm = { edit ->
                showPurchaseDialog = false
                onApply(GoodsStatus.PENDING, edit)
            }
        )
    }

    val statusButtons = listOf(
        GoodsStatus.GOTTEN to "보유",
        GoodsStatus.NOT_GOTTEN to "미보유",
        GoodsStatus.PENDING to "구매예정"
    )
    val onStatusClick: (GoodsStatus) -> Unit = { status ->
        if (status == GoodsStatus.PENDING) showPurchaseDialog = true else onApply(status, null)
    }

    if (vertical) {
        val stacked: @Composable ColumnScope.() -> Unit = {
            Text(
                text = if (count > 0) "${count}개 선택됨" else "굿즈를 눌러\n선택하세요",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = if (count > 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = if (allSelected) onClearSelection else onSelectAll) {
                Text(if (allSelected) "선택 해제" else "전체 선택")
            }
            statusButtons.forEach { (status, label) ->
                OutlinedButton(
                    onClick = { onStatusClick(status) },
                    enabled = count > 0,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = statusColor(status)),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = count > 0),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(label, maxLines = 1)
                }
            }
        }
        if (embedded) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = stacked
            )
            return
        }
        Surface(
            modifier = Modifier
                .width(VERTICAL_BULK_WIDTH)
                .fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = stacked
            )
        }
        return
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
            if (showSummary) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BulkSelectionSummary(count, allSelected, onSelectAll, onClearSelection)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                statusButtons.forEach { (status, label) ->
                    val color = statusColor(status)
                    OutlinedButton(
                        onClick = { onStatusClick(status) },
                        enabled = count > 0,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = count > 0),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(label, maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * 다중 선택 중의 안내 한 줄: "N개 선택됨"(아직 없으면 고르라는 안내) + 전체 선택/선택 해제.
 * [BulkStatusBar] 의 첫 줄이고, 세로 화면에서는 잠긴 필터 칩 대신 그 자리에 넣어 쓴다.
 */
@Composable
fun RowScope.BulkSelectionSummary(
    count: Int,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit
) {
    Text(
        text = if (count > 0) "${count}개 선택됨" else "굿즈를 눌러 선택하세요",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = if (count > 0) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier.weight(1f)
    )
    TextButton(onClick = if (allSelected) onClearSelection else onSelectAll) {
        Text(if (allSelected) "선택 해제" else "전체 선택")
    }
}

/**
 * 한 화면이 다중 선택 일괄 변경에 넘기는 값 묶음.
 *
 * @param selectedItems 화면에 보이면서 선택된 굿즈
 * @param allSelected 화면에 보이는 굿즈가 모두 선택됐는지
 * @param onApply 고른 상태와(구매예정일 때만) 입력한 구매 정보
 */
class BulkSelection(
    val selectedItems: List<GoodsItem>,
    val allSelected: Boolean,
    val onSelectAll: () -> Unit,
    val onClearSelection: () -> Unit,
    val onApply: (GoodsStatus, PurchaseInfoEdit?) -> Unit
)

/**
 * 다중 선택 중에 잠긴 필터 칩 대신 그 자리에 넣는 내용([FilterBar] 의 selectionContent).
 * 세로 화면은 안내 한 줄(버튼은 [BulkStatusButtons] 로 목록 위에 따로 둔다),
 * [vertical] 이면(가로 화면의 왼쪽 줄) 안내와 버튼을 모두 위아래로 쌓는다.
 */
@Composable
fun BulkSelectionSlot(bulk: BulkSelection, vertical: Boolean) {
    if (vertical) {
        BulkStatusBar(
            selectedItems = bulk.selectedItems,
            allSelected = bulk.allSelected,
            onSelectAll = bulk.onSelectAll,
            onClearSelection = bulk.onClearSelection,
            onApply = bulk.onApply,
            vertical = true,
            embedded = true
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BulkSelectionSummary(bulk.selectedItems.size, bulk.allSelected, bulk.onSelectAll, bulk.onClearSelection)
        }
    }
}

/** 세로 화면에서 목록 바로 위에 두는 보유·미보유·구매예정 버튼 줄. 안내 줄은 [BulkSelectionSlot] 이 맡는다. */
@Composable
fun BulkStatusButtons(bulk: BulkSelection) {
    BulkStatusBar(
        selectedItems = bulk.selectedItems,
        allSelected = bulk.allSelected,
        onSelectAll = bulk.onSelectAll,
        onClearSelection = bulk.onClearSelection,
        onApply = bulk.onApply,
        showSummary = false
    )
}

private val VERTICAL_BULK_WIDTH = 124.dp

/** 일괄 변경 버튼 줄([BulkStatusButtons])과 목록을 담는 틀. 안에 줄 → 목록 순서로 넣는다. */
@Composable
fun BulkStatusArea(content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) { content() }
}

/**
 * 한 화면의 다중 선택 상태. 굿즈 id 로 기억한다.
 * 필터로 가려진 굿즈가 선택에 남아 있어도 [visible] 로 화면에 보이는 것만 골라 쓴다.
 */
class GoodsSelection {
    var mode by mutableStateOf(false)
        private set
    var ids by mutableStateOf(emptySet<Int>())
        private set

    fun toggleMode() {
        mode = !mode
        ids = emptySet()
    }

    fun exit() {
        mode = false
        ids = emptySet()
    }

    fun toggle(id: Int) {
        ids = if (id in ids) ids - id else ids + id
    }

    fun selectAll(allIds: Collection<Int>) {
        ids = allIds.toSet()
    }

    fun clear() {
        ids = emptySet()
    }

    fun <T> visible(items: List<T>, idOf: (T) -> Int): List<T> = items.filter { idOf(it) in ids }
}

@Composable
fun rememberGoodsSelection() = remember { GoodsSelection() }

/** 목록 항목의 선택 표시(동그라미 / 체크) */
@Composable
fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    Icon(
        imageVector = if (selected) Icons.Default.CheckCircle else Icons.Outlined.Circle,
        contentDescription = if (selected) "선택됨" else "선택 안 됨",
        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        modifier = modifier
            .size(24.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
    )
}

/** 일괄 변경 뒤 보여줄 안내 문구 */
fun bulkStatusMessage(count: Int, status: GoodsStatus): String = when (status) {
    GoodsStatus.GOTTEN -> "${count}개를 보유로 바꿨습니다"
    GoodsStatus.NOT_GOTTEN -> "${count}개를 미보유로 바꿨습니다"
    GoodsStatus.PENDING -> "${count}개를 구매예정으로 바꿨습니다"
}
