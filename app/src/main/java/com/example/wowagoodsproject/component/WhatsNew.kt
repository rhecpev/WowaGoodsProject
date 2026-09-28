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
