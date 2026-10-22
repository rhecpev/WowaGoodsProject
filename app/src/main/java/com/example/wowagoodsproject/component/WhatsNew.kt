package com.example.wowagoodsproject.component

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

/** 변경점 한 묶음. 제목 아래에 항목을 점으로 늘어놓는다. */
data class ReleaseSection(val title: String, val items: List<String>)

/**
 * 버전별 변경점. 새 버전을 낼 때 여기에 추가하면
 * 업데이트 후 처음 앱을 열 때 한 번 보여 주고, 마이페이지 → 앱 정보 → 변경점에서 다시 볼 수 있다.
 */
object ReleaseNotes {

    private val notes: Map<String, List<ReleaseSection>> = mapOf(
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("v$version 업데이트 안내") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                sections.forEach { section ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("확인") }
        }
    )
}
