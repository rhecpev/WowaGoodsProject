package com.example.wowagoodsproject.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 보유 상태별 필터. "전체 12 · 보유 3 ..." 처럼 박스 없는 선 탭([LineTab])을 가로 스크롤로 늘어놓는다.
 * [onToggleSelection] 을 넘기면 맨 왼쪽에 다중 선택 버튼을 둔다.
 * 다중 선택 중([selectionMode])에는 필터를 바꾸면 고른 굿즈가 목록에서 사라질 수 있어 칩을 잠근다.
 * [selectionContent] 를 넘기면 다중 선택 중에는 잠긴 칩 대신 그 내용을 칩 자리에 보여준다.
 * 가로로 놓을 때는 한 줄짜리, [vertical] 일 때는 위아래로 쌓이는 내용을 넘겨야 한다.
 * [vertical] 이면(가로 화면) 화면 왼쪽에 세우는 세로 줄로 그려, 목록이 쓸 높이를 차지하지 않는다.
 */
@Composable
fun FilterBar(
    filterType: FilterType,
    onFilterChange: (FilterType) -> Unit,
    goodsList: List<GoodsItem>,
    selectionMode: Boolean = false,
    onToggleSelection: (() -> Unit)? = null,
    vertical: Boolean = false,
    selectionContent: (@Composable () -> Unit)? = null
) {
    val options = listOf(
        Triple(FilterType.ALL, "전체", goodsList.size),
        Triple(FilterType.GOTTEN, "보유", goodsList.count { it.isGotten }),
        Triple(FilterType.NOT_GOTTEN, "미보유", goodsList.count { it.status == GoodsStatus.NOT_GOTTEN }),
        Triple(FilterType.PENDING, "구매예정", goodsList.count { it.status == GoodsStatus.PENDING })
    )

    FilterRow(
        selectionMode = selectionMode,
        onToggleSelection = onToggleSelection,
        vertical = vertical,
        selectionContent = selectionContent
    ) {
        options.forEach { (type, label, count) ->
            CountFilterChip(
                label = label,
                count = count,
                selected = filterType == type,
                onClick = { onFilterChange(type) },
                enabled = !selectionMode,
                fillWidth = vertical
            )
        }
    }
}

/**
 * 구매예정 목록의 구매 정보 필터. [FilterBar] 처럼 하나만 고를 수 있다.
 * [selected] 가 null 이면 "전체" 가 켜진 상태다.
 * 숫자는 구매 정보 필터를 걸기 전 [goodsList] 에서 센 값이다.
 * [vertical], [selectionContent] 는 [FilterBar] 와 같다.
 */
@Composable
fun PendingInfoFilterBar(
    selected: PendingInfoFilter?,
    onSelect: (PendingInfoFilter?) -> Unit,
    goodsList: List<GoodsItem>,
    selectionMode: Boolean = false,
    onToggleSelection: (() -> Unit)? = null,
    vertical: Boolean = false,
    selectionContent: (@Composable () -> Unit)? = null
) {
    FilterRow(
        selectionMode = selectionMode,
        onToggleSelection = onToggleSelection,
        vertical = vertical,
        selectionContent = selectionContent
    ) {
        CountFilterChip(
            label = "전체",
            count = goodsList.size,
            selected = selected == null,
            onClick = { onSelect(null) },
            enabled = !selectionMode,
            fillWidth = vertical
        )
        PendingInfoFilter.entries.forEach { filter ->
            CountFilterChip(
                label = filter.label,
                count = goodsList.count(filter.matches),
                selected = filter == selected,
                onClick = { onSelect(filter) },
                enabled = !selectionMode,
                fillWidth = vertical
            )
        }
    }
}

/**
 * 변경된 굿즈의 처리 여부 필터. 처리 = 그 화면에서 다이얼로그로 굿즈를 고쳐 확인 표시가 붙은 기록.
 * 선언 순서가 곧 탭 순서다(전체 → 미처리 → 처리). 순서 값을 저장하지 않으므로 바꿔도 된다.
 */
enum class ProcessFilter(val label: String) {
    ALL("전체"),
    TODO("미처리"),
    DONE("처리")
}

/**
 * 변경된 굿즈 목록의 처리 여부 필터. [FilterBar] 의 보유 상태 칩과 같은 자리·모양이다.
 * [doneCount] 는 처리된 기록 수, [totalCount] 는 전체 기록 수다.
 */
@Composable
fun ProcessFilterBar(
    selected: ProcessFilter,
    onSelect: (ProcessFilter) -> Unit,
    totalCount: Int,
    doneCount: Int
) {
    FilterRow(selectionMode = false, onToggleSelection = null) {
        ProcessFilter.entries.forEach { filter ->
            CountFilterChip(
                label = filter.label,
                count = when (filter) {
                    ProcessFilter.ALL -> totalCount
                    ProcessFilter.DONE -> doneCount
                    ProcessFilter.TODO -> totalCount - doneCount
                },
                selected = filter == selected,
                onClick = { onSelect(filter) }
            )
        }
    }
}

/**
 * 필터 줄의 틀. 다중 선택 버튼은 왼쪽에 고정하고, 칩들만 그 오른쪽에서 가로로 스크롤한다.
 * 칩이 많아 옆으로 밀어도 다중 선택 버튼은 항상 보인다.
 * [vertical] 이면 다중 선택 버튼을 맨 위에 두고 칩을 그 아래로 쌓는다. 폭과 스크롤은 감싸는 쪽([SidePanelLayout])이 정한다.
 * [selectionContent] 가 있으면 다중 선택 중에는 칩 대신 그것을 보여준다.
 */
@Composable
private fun FilterRow(
    selectionMode: Boolean,
    onToggleSelection: (() -> Unit)?,
    vertical: Boolean = false,
    selectionContent: (@Composable () -> Unit)? = null,
    chips: @Composable () -> Unit
) {
    if (vertical) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (onToggleSelection != null) {
                MultiSelectToggle(selectionMode = selectionMode, onToggle = onToggleSelection)
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (selectionMode && selectionContent != null) selectionContent() else chips()
        }
        return
    }
    Column {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onToggleSelection != null) {
            Box(modifier = Modifier.padding(start = 16.dp, end = 8.dp)) {
                MultiSelectToggle(selectionMode = selectionMode, onToggle = onToggleSelection)
            }
        }
        if (selectionMode && selectionContent != null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (onToggleSelection != null) 4.dp else 16.dp, end = 8.dp, bottom = 4.dp)
            ) { selectionContent() }
            return@Row
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .padding(start = if (onToggleSelection != null) 0.dp else 8.dp, end = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            chips()
        }
    }
    // 탭 아래 얇은 기준선. 고른 탭의 강조색 밑줄이 이 선 바로 위에 놓인다.
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun CountFilterChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    /** 세로 줄에 쌓을 때 켠다. 탭이 줄 폭을 다 쓰고, 이름은 왼쪽, 숫자는 오른쪽 끝에 붙는다. */
    fillWidth: Boolean = false
) {
    LineTab(
        label = label,
        count = count,
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        vertical = fillWidth
    )
}

/**
 * 박스 없는 선 탭. 글자만 두고 고른 탭에만 강조색 선을 긋는다.
 * 가로로 늘어놓을 때는 글자 아래 밑줄, [vertical] 로 세로 줄에 쌓을 때는 왼쪽 세로선.
 * 고른 탭은 글자가 진하고 굵어지며, 숫자는 늘 강조색이다. 잠기면([enabled] false) 흐리게 보인다.
 */
@Composable
fun LineTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    enabled: Boolean = true,
    vertical: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val indicator = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val labelColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val content: @Composable RowScope.() -> Unit = {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = labelColor,
            maxLines = 1
        )
        if (count != null) {
            if (vertical) Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }
    }
    val base = modifier
        .alpha(if (enabled) 1f else 0.38f)
        .clickable(enabled = enabled, role = Role.Tab, onClick = onClick)

    if (vertical) {
        Row(
            modifier = base
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(indicator)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    } else {
        Column(modifier = base.width(IntrinsicSize.Max)) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(indicator)
            )
        }
    }
}
