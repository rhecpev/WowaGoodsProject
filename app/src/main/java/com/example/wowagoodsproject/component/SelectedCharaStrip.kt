package com.example.wowagoodsproject.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.wowagoodsproject.ui.theme.AppStyles
import java.io.File

/** 캐릭터 사진(주소나 앱 폴더 경로)을 Coil 에 넘길 값으로. 없으면 null */
fun charaImageModel(path: String): Any? = when {
    path.isEmpty() -> null
    path.startsWith("http") -> path
    else -> File(path)
}

/** 동그란 캐릭터 사진. 사진이 없으면 사람 모양 */
@Composable
fun CharaAvatar(path: String, size: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val model = charaImageModel(path)
        if (model == null) {
            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size((size / 2).dp))
        } else {
            Image(painter = rememberAsyncImagePainter(model), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

/**
 * 고른 캐릭터를 한 줄로 옆으로 늘어놓는다(캐릭터 고르기 창의 검색창 아래).
 * 길어져서 넘치면 옆으로 스크롤되고, 그때만 아래에 스크롤 막대를 보여 준다. 누르면 그 캐릭터를 뺀다.
 * 아직 아무도 안 골랐어도 자리(줄 높이·스크롤 막대 자리)를 미리 잡아 두어, 고를 때 아래 목록이 밀리지 않는다.
 * @param items (이름, 사진)
 */
@Composable
fun SelectedCharaStrip(
    items: List<Pair<String, String>>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** false 면 아무도 안 골랐을 때 자리를 잡지 않는다(등록 화면의 캐릭터 칸) */
    reserveSpace: Boolean = true
) {
    if (items.isEmpty() && !reserveSpace) return
    val state = rememberLazyListState()
    Column(modifier = modifier.fillMaxWidth()) {
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(STRIP_HEIGHT)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Text("고른 캐릭터가 여기에 모여요", style = AppStyles.textCardSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(BAR_SPACE))
            return@Column
        }
        LazyRow(
            state = state,
            modifier = Modifier.fillMaxWidth().height(STRIP_HEIGHT).semantics { contentDescription = "고른 캐릭터" },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(items, key = { it.first }) { (name, avatar) ->
                Column(
                    modifier = Modifier.width(56.dp).clickable { onRemove(name) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box {
                        CharaAvatar(avatar, 44)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.inverseSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "$name 빼기", tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(12.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(name, style = AppStyles.textCardSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // 넘칠 때만 보이는 가로 스크롤 막대: 보이는 칸 비율만큼의 길이, 앞쪽 칸 위치만큼 밀린다.
        val bar by remember(state) {
            derivedStateOf {
                val info = state.layoutInfo
                val total = info.totalItemsCount
                val visible = info.visibleItemsInfo
                if (total == 0 || visible.isEmpty() || (!state.canScrollForward && !state.canScrollBackward)) null
                else {
                    val itemSize = visible.first().size.coerceAtLeast(1)
                    val viewport = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
                    val content = (itemSize + 12) * total
                    val fraction = (viewport.toFloat() / content).coerceIn(0.1f, 1f)
                    val scrolled = state.firstVisibleItemIndex * (itemSize + 12) + state.firstVisibleItemScrollOffset
                    val start = (scrolled.toFloat() / content).coerceIn(0f, 1f - fraction)
                    start to fraction
                }
            }
        }
        // 스크롤 막대 자리는 늘 잡아 두고, 넘칠 때만 막대를 그린다.
        val b = bar
        if (b == null) Spacer(modifier = Modifier.height(BAR_SPACE))
        b?.let { (start, fraction) ->
            Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(3.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (start > 0f) Spacer(modifier = Modifier.weight(start))
                    Box(modifier = Modifier.weight(fraction).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                    val rest = 1f - start - fraction
                    if (rest > 0.0001f) Spacer(modifier = Modifier.weight(rest))
                }
            }
        }
    }
}

/** 고른 캐릭터 줄의 높이(사진 44 + 이름 + 위아래 여백) */
private val STRIP_HEIGHT = 76.dp
/** 스크롤 막대 자리(위 여백 4 + 막대 3) */
private val BAR_SPACE = 7.dp
