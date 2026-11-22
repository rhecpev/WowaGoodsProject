package com.example.wowagoodsproject.screen.mypage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.wowagoodsproject.ui.theme.AppStyles

/**
 * 추출 전 확인 시트. 백업에 들어갈 보유·구매예정 굿즈, 2차창작, 선호 캐릭터를 캐릭터별로 보여 주고,
 * 맞으면 추출한다. 되살릴 것이 없거나 굿즈 데이터를 받지 않은 것 같으면 위에 경고를 띄운다.
 *
 * @param preview null 이면 아직 세는 중
 */
@Composable
fun ExportPreviewSheet(
    preview: ExportPreview?,
    onExport: () -> Unit,
    onDismiss: () -> Unit,
    /** 배신 굿즈 장르. 하나라도 있으면 '전체 추출 / 장르 선택'을 고를 수 있다. */
    genres: List<MyPageViewModel.ExportGenre> = emptyList(),
    selection: ExportSelection = ExportSelection.ALL,
    onSelectionChange: (ExportSelection) -> Unit = {}
) {
    WizardScreen(title = "추출할 데이터 확인", onDismiss = onDismiss) {
        if (preview == null) {
            WizardWideHeader()
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@WizardScreen
        }

        val hasFavorites = preview.favorites.isNotEmpty()
        val hasGoods = preview.reviewGoods.isNotEmpty()
        val betray = preview.betrayReview
        // 단계: 담기는 내용 → (명조) 선호 캐릭터 → 명조 굿즈 → (배신 굿즈) 담기는 장르마다 굿즈 확인
        val kinds: List<ExportStep> = remember(hasFavorites, hasGoods, betray) {
            buildList {
                add(ExportStep.Summary)
                if (hasFavorites) add(ExportStep.Favorites)
                if (hasGoods) add(ExportStep.Goods)
                betray.forEach { add(ExportStep.Genre(it)) }
            }
        }
        val steps = kinds.map {
            when (it) {
                ExportStep.Summary -> "담기는 내용"
                ExportStep.Favorites -> STEP_FAVORITES
                ExportStep.Goods -> if (betray.isEmpty()) STEP_GOODS else "명조 굿즈"
                is ExportStep.Genre -> "${it.review.genreName} 굿즈"
            }
        }
        var step by remember { mutableIntStateOf(0) }
        if (step > steps.lastIndex) step = steps.lastIndex
        var favoritesOk by remember { mutableStateOf(false) }
        val allCharas = remember(preview.reviewGoods) { reviewCharas(preview.reviewGoods) }
        var checked by remember { mutableStateOf(emptySet<String>()) }
        // 배신 굿즈는 장르마다 캐릭터 확인을 따로 둔다(장르 번호 → 확인한 캐릭터).
        var betrayChecked by remember { mutableStateOf(emptyMap<Int, Set<String>>()) }
        val nothingPicked = !selection.includeWuwa && selection.genreIds?.isEmpty() == true
        val warn = preview.isEmpty || (selection.includeWuwa && preview.goodsDataMissing)
        WizardFrame(
            steps = steps,
            step = step,
            onStep = { step = it },
            // 선호 캐릭터는 '맞아요', 굿즈는 모든 캐릭터를 확인해야 다음/추출을 누를 수 있다.
            nextEnabled = when (val k = kinds[step]) {
                ExportStep.Summary -> !nothingPicked
                ExportStep.Favorites -> favoritesOk
                ExportStep.Goods -> checked.containsAll(allCharas)
                is ExportStep.Genre -> betrayChecked[k.review.genreId].orEmpty().containsAll(reviewCharas(k.review.goods))
            },
            finishLabel = if (warn) "그래도 추출하기" else "이대로 추출하기",
            finishColors = if (warn) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
            onFinish = onExport
        ) {
            when (val k = kinds[step]) {
                ExportStep.Favorites -> {
                    StepTitle("명조", "선호 캐릭터", betray = false)
                    FavoritesReviewStep(
                        favorites = preview.favorites.map { it to preview.avatars[it].orEmpty() },
                        note = null,
                        confirmText = "이 선호 캐릭터 목록이 맞아요",
                        checked = favoritesOk,
                        onCheck = { favoritesOk = it }
                    )
                }
                ExportStep.Goods -> {
                    StepTitle("명조", "굿즈", betray = false)
                    GoodsReviewStep(
                        goods = preview.reviewGoods,
                        avatars = preview.avatars,
                        checked = checked,
                        onCheck = { name, on -> checked = if (on) checked + name else checked - name },
                        onCheckAll = { checked = allCharas }
                    )
                }
                is ExportStep.Genre -> {
                    val id = k.review.genreId
                    val mine = betrayChecked[id].orEmpty()
                    StepTitle("배신 굿즈", "${k.review.genreName} 굿즈", betray = true)
                    // 장르마다 다른 단계라 캐릭터 선택·검색이 섞이지 않게 장르 번호로 묶는다.
                    key(id) {
                        GoodsReviewStep(
                            goods = k.review.goods,
                            avatars = k.review.avatars,
                            checked = mine,
                            onCheck = { name, on -> betrayChecked = betrayChecked + (id to (if (on) mine + name else mine - name)) },
                            onCheckAll = { betrayChecked = betrayChecked + (id to reviewCharas(k.review.goods)) }
                        )
                    }
                }
                ExportStep.Summary -> SummaryStep(preview, genres, selection, onSelectionChange)
            }
        }
    }
}

private sealed interface ExportStep {
    data object Summary : ExportStep
    data object Favorites : ExportStep
    data object Goods : ExportStep
    data class Genre(val review: ExportPreview.BetrayReview) : ExportStep
}

/** 첫 단계: 경고 · 담기는 내용 · 배신 굿즈 범위 */
@Composable
private fun ColumnScope.SummaryStep(
    preview: ExportPreview,
    genres: List<MyPageViewModel.ExportGenre>,
    selection: ExportSelection,
    onSelectionChange: (ExportSelection) -> Unit
) {
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (selection.includeWuwa && preview.goodsDataMissing) {
                item {
                    Warning(
                        "굿즈 데이터가 ${preview.officialTotal}개뿐입니다. 데이터를 다 받지 못한 것 같아요. " +
                                "수동 업데이트를 한 뒤 보유 현황을 확인하고 추출하세요."
                    )
                }
            }
            if (preview.isEmpty) {
                item {
                    Warning("보유·구매예정 굿즈, 2차창작, 선호 캐릭터가 하나도 없어요. 이 백업을 불러와도 되살릴 기록이 없습니다.")
                }
            }
            if (!selection.includeWuwa) item {
                SummaryRow("명조", "담지 않음", "")
            }
            if (selection.includeWuwa) item {
                SummaryRow(
                    "공식 굿즈",
                    "보유 ${preview.officialGotten} · 구매예정 ${preview.officialPending}",
                    ""
                )
            }
            if (selection.includeWuwa) item {
                SummaryRow(
                    "비공식",
                    "${preview.fanTotal}개",
                    buildString {
                        append("보유 ${preview.fanGotten} · 구매예정 ${preview.fanPending}")
                        if (preview.photoCount > 0) append(" · 사진 ${preview.photoCount}장 (${StorageCleaner.formatBytes(preview.photoBytes)})")
                    }
                )
            }
            if (selection.includeWuwa) item {
                SummaryRow(
                    "선호 캐릭터",
                    "${preview.favorites.size}명",
                    preview.favorites.joinToString(", ").ifEmpty { "없음" }
                )
            }
            if (genres.isNotEmpty()) {
                item {
                    SummaryRow(
                        "배신 굿즈",
                        "${preview.betrayGoods}개",
                        if (preview.betrayGenres == 0) "담지 않음" else "장르 ${preview.betrayGenres}개 · 보유 ${preview.betrayGotten}"
                    )
                }
                item {
                    GenreSelection(genres = genres, selection = selection, onSelectionChange = onSelectionChange)
                }
            } else if (preview.betrayGenres > 0) {
                item {
                    SummaryRow(
                        "배신 굿즈",
                        "${preview.betrayGoods}개",
                        "장르 ${preview.betrayGenres}개 · 보유 ${preview.betrayGotten}"
                    )
                }
            }
        }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun SummaryRow(title: String, value: String, detail: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (detail.isNotEmpty()) Text(text = detail, style = AppStyles.textCardSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun Warning(text: String) {
    val color: Color = MaterialTheme.colorScheme.error
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = AppStyles.textCardSmall, color = color)
    }
}

/**
 * 배신 굿즈를 어디까지 담을지: 전체 추출(모든 장르, 불러오면 배신 굿즈 전체를 바꾼다) /
 * 장르 선택(고른 장르만, 불러오면 그 장르만 바꾸거나 더하고 다른 장르는 그대로).
 */
@Composable
private fun GenreSelection(
    genres: List<MyPageViewModel.ExportGenre>,
    selection: ExportSelection,
    onSelectionChange: (ExportSelection) -> Unit
) {
    val picked = selection.genreIds
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = picked == null,
                onClick = { onSelectionChange(ExportSelection.ALL) },
                shape = SegmentedButtonDefaults.itemShape(0, 2, androidx.compose.foundation.shape.RoundedCornerShape(0.dp))
            ) { Text("전체 추출") }
            SegmentedButton(
                selected = picked != null,
                // 처음 고를 때는 명조만 체크된 채로 시작한다.
                onClick = { if (picked == null) onSelectionChange(ExportSelection(includeWuwa = true, genreIds = emptySet())) },
                shape = SegmentedButtonDefaults.itemShape(1, 2, androidx.compose.foundation.shape.RoundedCornerShape(0.dp))
            ) { Text("장르 선택") }
        }
        Text(
            text = if (picked == null) "명조 데이터와 배신 굿즈 모든 장르를 담습니다. 불러오면 배신 굿즈 전체가 이 파일 것으로 바뀝니다."
            else "체크한 것만 담습니다. 불러오면 같은 이름 장르는 바뀌고, 없는 장르는 더해지며, 다른 장르는 그대로입니다.",
            style = AppStyles.textCardSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
        if (picked != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = selection.includeWuwa,
                        role = Role.Checkbox,
                        onValueChange = { on -> onSelectionChange(selection.copy(includeWuwa = on)) }
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = selection.includeWuwa, onCheckedChange = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("명조", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("공식·비공식·선호 캐릭터", style = AppStyles.textCardSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            genres.forEach { g ->
                val checked = g.genreId in picked
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = checked,
                            role = Role.Checkbox,
                            onValueChange = { on -> onSelectionChange(selection.copy(genreIds = if (on) picked + g.genreId else picked - g.genreId)) }
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(g.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("굿즈 ${g.goods}개", style = AppStyles.textCardSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (picked.isEmpty()) {
                Text(
                    if (selection.includeWuwa) "장르를 하나도 고르지 않으면 배신 굿즈는 담지 않습니다(불러와도 배신 굿즈는 그대로)."
                    else "담을 것을 하나 이상 체크하세요.",
                    style = AppStyles.textCardSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
