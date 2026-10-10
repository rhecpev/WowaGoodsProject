package com.example.wowagoodsproject.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 보유 상태별 필터. "전체 12 · 보유 3 ..." 처럼 한 줄짜리 칩을 가로 스크롤로 늘어놓는다.
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
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
                    .padding(start = if (onToggleSelection != null) 4.dp else 16.dp, end = 8.dp)
            ) { selectionContent() }
            return@Row
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .padding(start = if (onToggleSelection != null) 0.dp else 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            chips()
        }
    }
}

@Composable
private fun CountFilterChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    /** 세로 줄에 쌓을 때 켠다. 칩이 줄 폭을 다 쓰고, 이름은 왼쪽, 숫자는 오른쪽 끝에 붙는다. */
    fillWidth: Boolean = false
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
        label = {
            Row(
                modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, maxLines = 1)
                if (fillWidth) Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$count",
                    maxLines = 1,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        !enabled -> LocalContentColor.current
                        selected -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }
        }
    )
}
