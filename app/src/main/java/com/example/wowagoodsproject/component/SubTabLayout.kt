package com.example.wowagoodsproject.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 화면 안의 세부 탭 + 그 아래(옆) 내용.
 * 세로 화면에서는 탭을 위에 가로로 늘어놓고, [vertical] 이면(가로 화면) 왼쪽에 세로로 쌓아
 * 낮은 화면에서 내용이 쓸 높이를 탭이 차지하지 않게 한다.
 */
@Composable
fun SubTabLayout(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    vertical: Boolean,
    modifier: Modifier = Modifier,
    tabTextStyle: TextStyle = MaterialTheme.typography.titleSmall,
    content: @Composable () -> Unit
) {
    if (vertical) {
        Row(modifier = modifier) {
            Column(
                modifier = Modifier
                    .width(VERTICAL_TAB_WIDTH)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                SubTabStrip(tabs, selectedIndex, onSelect, vertical = true, tabTextStyle = tabTextStyle)
            }
            VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) { content() }
        }
    } else {
        Column(modifier = modifier) {
            SubTabStrip(tabs, selectedIndex, onSelect, vertical = false, tabTextStyle = tabTextStyle)
            content()
        }
    }
}

/**
 * 세부 탭 줄만 그린다. [vertical] 이면 칸을 위아래로 쌓는다(스크롤은 감싸는 쪽이 맡는다).
 * [enabled] 가 false 면 눌러도 바뀌지 않는다.
 */
@Composable
fun SubTabStrip(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    vertical: Boolean,
    enabled: Boolean = true,
    tabTextStyle: TextStyle = MaterialTheme.typography.titleSmall
) {
    if (vertical) {
        Column(modifier = Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, label ->
                VerticalTab(
                    label = label,
                    selected = selectedIndex == index,
                    enabled = enabled,
                    textStyle = tabTextStyle,
                    onClick = { onSelect(index) }
                )
            }
        }
    } else {
        SecondaryTabRow(selectedTabIndex = selectedIndex) {
            tabs.forEachIndexed { index, label ->
                Tab(
                    selected = selectedIndex == index,
                    onClick = { onSelect(index) },
                    enabled = enabled,
                    text = { Text(label, style = tabTextStyle, maxLines = 1) }
                )
            }
        }
    }
}

/**
 * 탭·필터 같은 조작부([panel])와 목록([content])을 담는 틀.
 * 세로 화면은 조작부를 목록 위에 두고, [sidePanel] 이면(가로 화면) 왼쪽 세로 줄로 옮긴다.
 * 조작부 안의 것들은 같은 방향 값을 받아 스스로 가로/세로 모양을 바꿔 그려야 한다.
 * [tabs] 를 넘기면 세부 탭 줄도 함께 둔다: 세로 화면은 조작부 위에, 가로 화면은 조작부 오른쪽의 별도 세로 줄에.
 * 가로 화면에서는 보유 상태 필터 같은 조작부가 항상 맨 왼쪽에 온다.
 * 탭이 아닌 넓은 것을 넣을 때는 [tabsWidth] 로 그 줄의 폭을 정한다. 조작부 줄의 폭은 [panelWidth].
 */
@Composable
fun SidePanelLayout(
    sidePanel: Boolean,
    panel: @Composable () -> Unit,
    tabs: (@Composable () -> Unit)? = null,
    tabsWidth: Dp = VERTICAL_TAB_WIDTH,
    panelWidth: Dp = SIDE_PANEL_WIDTH,
    content: @Composable () -> Unit
) {
    if (sidePanel) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .width(panelWidth)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) { panel() }
            VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            if (tabs != null) {
                Column(
                    modifier = Modifier
                        .width(tabsWidth)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) { tabs() }
                VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) { content() }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            tabs?.invoke()
            panel()
            content()
        }
    }
}

private val SIDE_PANEL_WIDTH = 124.dp

private val VERTICAL_TAB_WIDTH = 88.dp

/** 세로로 쌓는 탭 한 칸. 고른 칸은 강조색 글자 + 오른쪽 가장자리의 표시 줄로 알린다. */
@Composable
private fun VerticalTab(
    label: String,
    selected: Boolean,
    textStyle: TextStyle,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = textStyle,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}
