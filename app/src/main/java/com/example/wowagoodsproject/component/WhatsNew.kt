package com.example.wowagoodsproject.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import com.example.wowagoodsproject.R
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.wowagoodsproject.BuildConfig

/**
 * 변경점 한 묶음. 제목 아래에 항목을 점으로 늘어놓는다.
 * [beforeImage]/[afterImage] 를 주면 제목 아래에 이전 버전·이번 버전 화면 사진을 나란히 보여 준다(한쪽만 있어도 된다).
 */
data class ReleaseSection(
    val title: String,
    val items: List<String>,
    @DrawableRes val beforeImage: Int? = null,
    @DrawableRes val afterImage: Int? = null
)

/**
 * 버전별 변경점. 새 버전을 낼 때 여기에 추가하면
 * 업데이트 후 처음 앱을 열 때 한 번 보여 주고, 마이페이지 → 앱 정보 → 변경점에서 다시 볼 수 있다.
 */
object ReleaseNotes {

    private val notes: Map<String, List<ReleaseSection>> = mapOf(
        "3.0" to listOf(
            ReleaseSection(
                "✨ 새 디자인",
                beforeImage = R.drawable.whatsnew_3_0_design_before,
                afterImage = R.drawable.whatsnew_3_0_design_after,
                items = listOf(
                    "둥근 상자를 걷어내고 선으로 나누는 깔끔한 디자인으로 바뀌었어요",
                    "필터·탭·보유 상태 버튼이 밑줄 탭으로 바뀌었어요",
                    "검색창은 밑줄 하나, 필터·굿즈 상세는 아래에서 올라오는 시트로 열려요",
                    "보유·미보유·구매예정 표시와 소식·변경 기록·세트 구성품 줄에 색 선이 붙어 한눈에 구분돼요",
                    "시리즈가 바뀌는 곳에 이중선이 들어가요 (모든 테마에서 잘 보이는 색)"
                )
            ),
            ReleaseSection(
                "📊 마이페이지",
                beforeImage = R.drawable.whatsnew_3_0_mypage_before,
                afterImage = R.drawable.whatsnew_3_0_mypage_after,
                items = listOf(
                    "보유 굿즈의 캐릭터 비율 게이지가 생겼어요. 선호 캐릭터는 사진에서 뽑은 색으로, 나머지는 '누구 외 몇 명'으로 묶어요",
                    "선호 캐릭터 수집률을 캐릭터별로 한눈에 봐요",
                    "용량 정리: 업데이트 이력·읽은 소식·처리한 변경 기록·이미지 캐시·남은 임시 파일 중 지워도 되는 것만 골라 지워요. 얼마나 비울 수 있는지도 알려 줘요",
                    "데이터 추출 중에 진행 상황이 보여요",
                    "업데이트 이력을 한 번에 지울 수 있어요"
                )
            ),
            ReleaseSection(
                "❤️ 선호 캐릭터·캐릭터별",
                beforeImage = R.drawable.whatsnew_3_0_chara_before,
                afterImage = R.drawable.whatsnew_3_0_chara_after,
                items = listOf(
                    "선호 캐릭터 순서를 정할 수 있어요. ≡ 를 잡거나 줄을 꾹 눌러 끌어 옮겨요",
                    "정한 순서는 캐릭터 목록·필터·게이지 색에 쓰이고 백업에도 담겨요",
                    "캐릭터별 목록이 사진·이름과 수집 게이지·퍼센트가 한 줄에 보이는 리스트로 바뀌었어요"
                )
            ),
            ReleaseSection(
                "🔍 필터·검색",
                beforeImage = R.drawable.whatsnew_3_0_filter_before,
                afterImage = R.drawable.whatsnew_3_0_filter_after,
                items = listOf(
                    "굿즈 목록을 시리즈로도 거를 수 있어요 (보유·구매예정·캐릭터별·2차창작)",
                    "시리즈·카테고리 필터가 그림 그리드로 바뀌었어요. 그림은 지금 보는 굿즈 중 선호 캐릭터 굿즈 사진으로 골라요",
                    "가로 화면에서 검색어를 칠 때는 머리 부분을 접어 검색창과 결과가 가려지지 않아요"
                )
            ),
            ReleaseSection(
                "🧩 세트·2차창작",
                afterImage = R.drawable.whatsnew_3_0_set_after,
                items = listOf(
                    "세트 팝업 위에 세트 정보가 늘 보이고, 사진을 누르면 크게 볼 수 있어요",
                    "세트 구성품이 구매예정이면 구매 정보도 보여 주고 고칠 수 있어요",
                    "가로 화면에서 구성품 목록은 왼쪽, 상세는 오른쪽이고 보유 버튼은 아래에 고정돼요. 구성품이 많으면 스크롤바로 옮겨요",
                    "2차창작 등록에서 수량을 정할 수 있고, 등록 버튼은 오른쪽 위로 옮겼어요",
                    "변경점을 옆으로 넘겨 봐요"
                )
            ),
            ReleaseSection(
                "🛠 고친 점",
                listOf(
                    "가로 화면 구매예정 목록에서 날짜가 잘리던 문제",
                    "밝은 테마에서 소식·변경 기록 항목 경계가 잘 안 보이던 문제",
                    "이미지 캐시가 지나치게 커지던 문제 (최대 100MB로 제한)",
                    "사진 형식만 바뀐 굿즈가 '이미지 변경' 소식으로 쌓이던 문제"
                )
            )
        ),
        "2.6" to listOf(
            ReleaseSection(
                "🔒 보유 기록 지키기",
                listOf(
                    "굿즈마다 바뀌지 않는 번호가 생겨, 가격·메모·사진이 바뀌어도 보유·구매예정 기록이 그대로 남아요",
                    "업데이트 후 처음 데이터를 받을 때 기존 굿즈에 번호가 붙어요 (보유 기록은 그대로)",
                    "원본 데이터가 잘못 올라와도 보유 기록이 지워지지 않게 막았어요"
                )
            ),
            ReleaseSection(
                "🔄 변경된 굿즈",
                listOf(
                    "마이페이지 → 변경된 굿즈에서 데이터 업데이트로 바뀐 보유·구매예정 굿즈를 모아 봐요",
                    "카드를 눌러 상태·수량을 고치면 처리로 넘어가요 (전체·처리·미처리)",
                    "처리한 기록만 한 번에 지울 수 있어요"
                )
            ),
            ReleaseSection(
                "💾 백업·불러오기",
                listOf(
                    "업데이트 후 백업을 새로 해 두세요. 새 백업은 굿즈 번호까지 담아 데이터가 바뀌어도 정확히 되살려요",
                    "1.x~2.0에서 만든 옛 백업도 보유 기록을 불러와요",
                    "불러온 뒤 몇 개를 되살렸는지 알려 줘요",
                    "메신저·클라우드로 받은 백업 파일도 고를 수 있어요",
                    "백업 파일이 깨져 있으면 아무것도 바꾸지 않아요"
                )
            ),
            ReleaseSection(
                "🛠 고친 점",
                listOf(
                    "\"양양\"을 고르면 \"양양현령\" 굿즈까지 보이던 문제 (이름 일부만 같은 캐릭터)",
                    "아주 예전 버전에서 바로 올리면 앱이 켜지지 않던 문제",
                    "2차창작 사진 저장에 실패하면 앱이 꺼지던 문제",
                    "2차창작을 지워도 사진 파일이 남던 문제",
                    "인터넷이 느릴 때 앱 첫 화면이 오래 멈추던 문제",
                    "굿즈 상태를 바꿀 때 시리즈 화면이 버벅이던 문제",
                    "안드로이드 9 이하에서 백업 공유가 실패하던 문제"
                )
            )
        ),
        "2.5" to listOf(
            ReleaseSection(
                "📰 소식 바로가기",
                listOf(
                    "바로가기를 누르면 그 굿즈 위치로 이동해 반짝여서 바로 보이게",
                    "시리즈에서 뒤로가기를 누르면 소식으로 돌아오고, 보던 필터와 위치를 그대로 유지",
                    "가격·메모만 바뀐 굿즈가 신규 굿즈로도 알림이 가던 문제 수정"
                )
            ),
            ReleaseSection(
                "🛠 고친 점",
                listOf(
                    "다른 탭에서 시리즈로 이동하면 공식 탭에 걸려 있던 필터가 남아 있던 문제 수정",
                    "소식에서 바로가기 후 마이페이지 탭을 누르면 소식 화면이 다시 뜨던 문제 수정",
                    "시리즈를 빠르게 바꿀 때 이전 시리즈 굿즈가 보일 수 있던 문제 수정"
                )
            )
        ),
        "2.4" to listOf(
            ReleaseSection(
                "🔎 굿즈 검색 탭",
                listOf(
                    "캐릭터·카테고리를 골라 공식과 2차창작 굿즈를 한 번에 찾기",
                    "보유 상태 필터와 다중 선택으로 찾은 굿즈를 바로 정리"
                )
            ),
            ReleaseSection(
                "🖼 그리드 보기",
                listOf(
                    "한 줄에 놓는 개수를 1~10개로 조절",
                    "보기 방식과 개수를 화면마다 따로 기억 (앱을 다시 켜도 유지)",
                    "구매예정 굿즈에도 그리드 보기 추가"
                )
            ),
            ReleaseSection(
                "📱 가로 화면",
                listOf(
                    "탭·필터를 왼쪽 세로 줄로 옮겨 목록을 넓게",
                    "마이페이지를 두 칸으로 배치",
                    "구매 정보 입력 창과 달력이 가로 화면에서 잘리던 문제 수정",
                    "화면을 돌려도 보던 화면을 그대로 유지",
                    "캐릭터 굿즈 화면에서도 하단 메뉴 표시"
                )
            ),
            ReleaseSection(
                "✅ 다중 선택",
                listOf(
                    "선택 안내와 변경 버튼을 필터 자리에 표시해 목록을 더 넓게",
                    "다중 선택 중에는 탭과 필터가 바뀌지 않게 잠금"
                )
            ),
            ReleaseSection(
                "📦 굿즈 상세",
                listOf(
                    "이미지는 그대로 두고 정보만 스크롤",
                    "스크롤하다가 창이 닫히던 문제 수정"
                )
            ),
            ReleaseSection(
                "🛒 구매예정 굿즈",
                listOf(
                    "목록에 시리즈명을 크게, 그 아래 캐릭터 - 카테고리 - 메모 표시",
                    "구매 정보 필터를 하나씩 고르는 방식으로 변경"
                )
            ),
            ReleaseSection(
                "🔔 알림",
                listOf(
                    "신규 굿즈 알림을 캐릭터별로 따로, 캐릭터 얼굴과 함께 표시",
                    "상태 표시줄 알림 아이콘 변경",
                    "자동 업데이트를 오전 5시 정각에 맞추고, 실패하면 매시 정각에 다시 시도"
                )
            ),
            ReleaseSection(
                "🛠 수정",
                listOf("달력이 기기 언어와 상관없이 한국어로 나오게")
            )
        ),
        "2.3" to listOf(
            ReleaseSection(
                "🛒 구매예정 굿즈",
                listOf(
                    "구입처 미정 · 구매일 미정 · 수령일 미정 · 배송 중 · 배송 전 필터 (여러 개 함께 선택)",
                    "굿즈를 누르면 상세 창에서 구매 정보 입력과 배송 시작/취소",
                    "다중 선택을 다른 탭과 같은 방식으로 변경"
                )
            ),
            ReleaseSection(
                "📦 굿즈 상세",
                listOf("구매예정 굿즈는 구매 정보와 배송 상태를 맨 위에 표시")
            ),
            ReleaseSection(
                "🎨 2차창작 굿즈 등록",
                listOf(
                    "등록 화면 디자인 개편",
                    "카테고리·캐릭터를 검색해서 고르기",
                    "등록 버튼을 화면 아래에 고정"
                )
            ),
            ReleaseSection(
                "🔔 알림",
                listOf("신규 굿즈 알림을 캐릭터별로 묶어서 어떤 캐릭터인지 바로 보이게")
            ),
            ReleaseSection(
                "🛠 수정",
                listOf("가로 모드에서 일부 글자색이 세로 모드와 다르던 문제")
            )
        ),
        "2.2" to listOf(
            ReleaseSection(
                "🔍 검색·필터",
                listOf(
                    "공식 탭에서 시리즈 제목 검색",
                    "소식을 캐릭터·카테고리로 필터",
                    "좌우로 밀어서 탭 이동"
                )
            ),
            ReleaseSection(
                "✅ 다중 선택",
                listOf("목록 위 선택 버튼으로 여러 굿즈의 상태를 한 번에 변경")
            ),
            ReleaseSection(
                "🔢 수량",
                listOf("굿즈 상세에서 보유·구매예정 수량 입력 (미보유로 바꾸면 1개로 초기화)")
            ),
            ReleaseSection(
                "🛒 구매예정",
                listOf(
                    "구입처·구매일·수령예정일 기록",
                    "마이페이지 → 구매예정 굿즈에서 배송 시작 표시"
                )
            ),
            ReleaseSection(
                "🔔 알림",
                listOf(
                    "수령예정일이 지나거나 배송 후 일주일이 지나면 3일마다 알림",
                    "선호 캐릭터 신규 굿즈·보유 굿즈 변경 알림",
                    "알림에서 굿즈별로 끄기 가능"
                )
            ),
            ReleaseSection(
                "🌙 자동 업데이트",
                listOf("매일 새벽 5시쯤 자동 업데이트 (앱을 켤 때 기다리지 않아요)")
            )
        )
    )

    private const val PREFS = "wowa_prefs"
    private const val KEY_SEEN_VERSION = "whats_new_seen_version"

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /** 지금 버전의 변경점. 적어 둔 게 없으면 null. */
    fun forCurrentVersion(): List<ReleaseSection>? = notes[currentVersion]

    /** 이 버전으로 업데이트한 뒤 아직 변경점을 보여 주지 않았는지 */
    fun shouldShow(context: Context): Boolean =
        forCurrentVersion() != null && seenVersion(context) != currentVersion

    /** 봤다고 기록한다. 첫 설치처럼 보여 줄 필요가 없을 때도 부른다. */
    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SEEN_VERSION, currentVersion)
            .apply()
    }

    private fun seenVersion(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SEEN_VERSION, null)
}

/** 변경점 다이얼로그. 내용이 길어 스크롤된다. */
@Composable
fun WhatsNewDialog(
    version: String,
    sections: List<ReleaseSection>,
    onDismiss: () -> Unit
) {
    // 섹션 하나가 한 페이지. 옆으로 밀어 넘기거나 아래 이전/다음 버튼으로 넘긴다.
    val pagerState = rememberPagerState { sections.size }
    val scope = rememberCoroutineScope()
    // 사진이 있는 섹션이 있으면 페이지를 화면 높이에 맞춰 키운다(낮은 가로 화면에서는 줄어들고 페이지 안에서 스크롤).
    val hasImages = sections.any { it.beforeImage != null || it.afterImage != null }
    val screenHeight = LocalConfiguration.current.screenHeightDp
    // 팝업의 제목·페이지 표시·버튼·여백이 약 250dp 를 쓰므로, 그만큼은 남겨야 낮은 가로 화면에서도 아래가 잘리지 않는다.
    val pageHeight = if (hasImages) {
        minOf(screenHeight * 0.6f, screenHeight - 250f).dp.coerceIn(120.dp, 560.dp)
    } else 320.dp
    var zoomed by remember { mutableStateOf<Int?>(null) }
    zoomed?.let { res -> FullScreenImageViewer(model = res, onDismiss = { zoomed = null }) }
    val page = pagerState.currentPage
    val isLast = page >= sections.lastIndex

    AlertDialog(
        // 앱 전체 기준: 팝업도 둥근 모서리 없이 각지게.
        shape = RectangleShape,
        onDismissRequest = onDismiss,
        title = { Text("v$version 업데이트 안내") },
        text = {
            Column {
                HorizontalPager(
                    state = pagerState,
                    pageSpacing = 16.dp,
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(pageHeight)
                ) { index ->
                    val section = sections[index]
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        if (section.beforeImage != null || section.afterImage != null) {
                            BeforeAfterImages(
                                before = section.beforeImage,
                                after = section.afterImage,
                                onZoom = { zoomed = it }
                            )
                        }
                        section.items.forEach { item ->
                            Row {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(14.dp)
                                )
                                Text(
                                    text = item,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                if (sections.size > 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                    // 페이지 표시: 짧은 선을 늘어놓고 지금 페이지만 길고 진하게.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        sections.indices.forEach { i ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .width(if (i == page) 20.dp else 10.dp)
                                    .height(3.dp)
                                    .background(
                                        if (i == page) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant
                                    )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${page + 1} / ${sections.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        },
        dismissButton = if (page > 0) {
            {
                TextButton(onClick = { scope.launch { pagerState.animateScrollToPage(page - 1) } }) {
                    Text("이전")
                }
            }
        } else null,
        confirmButton = {
            TextButton(onClick = {
                if (isLast) onDismiss() else scope.launch { pagerState.animateScrollToPage(page + 1) }
            }) { Text(if (isLast) "확인" else "다음") }
        }
    )
}

/**
 * 변경점 페이지의 비포·애프터 사진. 이전 버전(회색 선)·이번 버전(강조색 선) 이름표 아래에 화면 사진을 나란히 둔다.
 * 한쪽만 있으면 그 사진만 절반 폭으로 둔다. 누르면 전체 화면으로 크게 본다.
 */
@Composable
private fun BeforeAfterImages(
    @DrawableRes before: Int?,
    @DrawableRes after: Int?,
    onZoom: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        listOf(before to "이전", after to "이번 버전").forEach { (res, label) ->
            Column(modifier = Modifier.weight(1f)) {
                if (res != null) {
                    val isAfter = res == after
                    val lineColor = if (isAfter) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = lineColor
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp, bottom = 4.dp)
                            .height(2.dp)
                            .background(lineColor)
                    )
                    Image(
                        painter = painterResource(res),
                        contentDescription = if (isAfter) "이번 버전 화면" else "이전 버전 화면",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            .clickable { onZoom(res) }
                    )
                }
            }
        }
    }
}
