package com.example.wowagoodsproject.screen.mypage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.wowagoodsproject.component.BeforeAfterImages
import com.example.wowagoodsproject.component.FullScreenImageViewer
import com.example.wowagoodsproject.component.ReleaseNotes
import com.example.wowagoodsproject.component.ReleaseSection
import com.example.wowagoodsproject.navigation.TopBar
import com.example.wowagoodsproject.ui.theme.AppStyles

/**
 * 변경점: 버전마다 달라진 점을 최신 버전부터 보여 준다. 마이페이지 → 앱 정보 → 변경점.
 * 지금 버전은 펼쳐 두고, 지난 버전은 머리 줄을 눌러 펼친다. 내용은 업데이트 후 처음 뜨는 안내 창과 같다([ReleaseNotes]).
 */
@Composable
fun ReleaseNotesScreen(onNavigateBack: () -> Unit) {
    BackHandler(onBack = onNavigateBack)
    val versions = remember { ReleaseNotes.allVersions() }
    val current = ReleaseNotes.currentVersion
    var open by rememberSaveable { mutableStateOf(setOf(current)) }
    var zoomed by remember { mutableStateOf<Int?>(null) }
    zoomed?.let { res -> FullScreenImageViewer(model = res, onDismiss = { zoomed = null }) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = "변경점", subtitle = "버전별로 달라진 점", onBack = onNavigateBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
            items(versions, key = { it.first }) { (version, sections) ->
                val expanded = version in open
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 버전 머리 줄: 누르면 펼치고 접는다
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { open = if (expanded) open - version else open + version }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "v$version",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (version == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        if (version == current) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("지금 버전", style = AppStyles.textCardSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            sections.joinToString(" · ") { it.title.substringAfter(' ').trim() },
                            style = AppStyles.textCardSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false).padding(horizontal = 8.dp),
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "v$version 접기" else "v$version 펼치기",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (expanded) {
                        Column(modifier = Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            sections.forEach { ReleaseSectionBlock(it, onZoom = { res -> zoomed = res }) }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

/** 변경점 한 묶음: 제목 · (이전/이번 화면 사진) · 점 목록 */
@Composable
private fun ReleaseSectionBlock(section: ReleaseSection, onZoom: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(section.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        if (section.beforeImage != null || section.afterImage != null) {
            BeforeAfterImages(before = section.beforeImage, after = section.afterImage, onZoom = onZoom)
        }
        section.items.forEach { item ->
            Row {
                Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(14.dp))
                Text(item, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
