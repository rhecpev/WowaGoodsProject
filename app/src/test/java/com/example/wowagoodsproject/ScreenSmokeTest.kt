package com.example.wowagoodsproject

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.wowagoodsproject.db.changed.ChangedGoodsEntity
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.news.NewsEntity
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.db.patchnote.PatchNoteEntity
import com.example.wowagoodsproject.db.series.SeriesEntity
import com.example.wowagoodsproject.navigation.MainScreen
import com.example.wowagoodsproject.ui.theme.WowaGoodsProjectTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 화면 크기별 실행 점검. 휴대폰 세로·가로, 태블릿 세로·가로에서 데이터를 넣고
 * 모든 탭과 주요 팝업(필터 시트, 굿즈 상세, 세트 팝업, 용량 정리, 구매예정 목록)을 실제로 열어 그린다.
 * 그리는 중에 앱이 꺼지는 문제(측정 예외 등)가 있으면 여기서 실패한다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ScreenSmokeTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() = runBlocking {
        TestDb.setUp()
        val charaDao = App.charaDatabase.charaDao()
        charaDao.insert(CharaEntity(charaNm = "장리", charaIsFavorite = true, charaFavoriteOrder = 1))
        charaDao.insert(CharaEntity(charaNm = "금희", charaIsFavorite = true, charaFavoriteOrder = 2))
        charaDao.insert(CharaEntity(charaNm = "감심"))
        App.seriesDatabase.seriesDao().insert(SeriesEntity(seriesNm = "1.0버전", seriesDate = "2024-07-04", seriesCharas = "장리,금희,감심"))
        val goodsDao = App.database.goodsDao()
        fun g(uid: Int, chara: String, category: String, status: String, memo: String = "") = GoodsEntity(
            goodsUid = uid, goodsSeries = "1.0버전", goodsChara = chara, goodsCategory = category,
            goodsPrice = "59위안", goodsMemo = memo, goodsReleaseDate = "2024-07-04",
            goodsUrl = "https://example.invalid/$uid.png", goodsStatus = status,
            goodsIsGotten = status == "GOTTEN",
            goodsPurchaseStore = if (status == "PENDING") "트위터 통판" else "",
            goodsReceiveDate = if (status == "PENDING") "2026-12-01" else ""
        )
        goodsDao.insert(g(1, "장리", "키링", "GOTTEN"))
        goodsDao.insert(g(2, "장리", "아크릴", "PENDING"))
        goodsDao.insert(g(3, "금희", "키링", "NOT_GOTTEN"))
        goodsDao.insert(g(4, "장리,금희", "포스터", "GOTTEN"))
        goodsDao.insert(g(5, "감심", "세트", "NOT_GOTTEN", memo = "세트A"))   // 세트
        goodsDao.insert(g(6, "감심", "배지", "GOTTEN", memo = "세트A"))     // 구성품
        goodsDao.insert(g(7, "감심", "카드", "PENDING", memo = "세트A"))    // 구성품
        App.fanDatabase.fanGoodsDao().insert(
            FanGoodsEntity(fanGoodsSeries = "코믹월드", fanGoodsChara = "장리", fanGoodsCategory = "키링",
                fanGoodsStatus = "GOTTEN", fanGoodsIsGotten = true, fanGoodsQuantity = 2)
        )
        App.fanDatabase.fanGoodsDao().insert(
            FanGoodsEntity(fanGoodsChara = "금희", fanGoodsCategory = "엽서", fanGoodsStatus = "PENDING",
                fanGoodsShippingDate = "2026-11-10")
        )
        App.newsDatabase.newsDao().insertAll(listOf(
            NewsEntity(newsChara = "장리", newsCategory = "키링", newsSeries = "1.0버전", newsTime = 1, newsIsRead = false),
            NewsEntity(newsType = "CHANGED_GOODS", newsChara = "금희", newsDetail = "가격: 59 → 69", newsTime = 2, newsIsRead = true)
        ))
        App.changedGoodsDatabase.changedGoodsDao().insert(
            ChangedGoodsEntity(changedTime = 1, newChara = "장리", newCategory = "키링", newSeries = "1.0버전",
                changedDetail = "가격: 59위안 → 69위안", statusKept = true)
        )
        App.patchNoteDatabase.patchNoteDao().insert(PatchNoteEntity(patchContent = "[굿즈 추가] 장리 키링"))
        Unit
    }

    @After
    fun tearDown() = TestDb.tearDown()

    @Test fun 휴대폰_세로() = runAll("w411dp-h891dp-port")
    @Test fun 휴대폰_가로() = runAll("w891dp-h411dp-land")
    @Test fun 태블릿_세로() = runAll("w800dp-h1280dp-port")
    @Test fun 태블릿_가로() = runAll("w1280dp-h800dp-land")
    /** 기기 설정에서 글자를 크게 키운 경우(1.5배) */
    @Test fun 휴대폰_세로_큰글자() { RuntimeEnvironment.setFontScale(1.5f); runAll("w411dp-h891dp-port") }
    @Test fun 휴대폰_가로_큰글자() { RuntimeEnvironment.setFontScale(1.5f); runAll("w891dp-h411dp-land") }

    private fun settle() {
        repeat(6) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
            Thread.sleep(30) // IO 디스패처의 DB 읽기가 끝날 틈
        }
    }

    private fun clickable(text: String, substring: Boolean = false): SemanticsNodeInteractionCollection =
        compose.onAllNodesWithText(text, substring = substring).filter(hasClickAction())

    /** 화면 밖에 있으면 스크롤해 보이게 한 뒤 누른다(밖에 있는 채로 누르면 실제로는 눌리지 않는다). */
    private fun SemanticsNodeInteractionCollection.clickFirst(last: Boolean = false) {
        val node = if (last) onLast() else onFirst()
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    /** 누른 것 / 못 찾은 것 기록(어디까지 실제로 열어 봤는지 확인용) */
    private val log = mutableListOf<String>()

    /** [last] 면 맨 나중 노드(가장 위에 뜬 팝업·시트 안의 것)를 누른다. */
    private fun tap(text: String, substring: Boolean = false, last: Boolean = false): Boolean {
        val nodes = clickable(text, substring)
        if (nodes.fetchSemanticsNodes().isEmpty()) { log += "  - $text"; return false }
        nodes.clickFirst(last); settle(); log += "  + $text"; return true
    }

    private fun tapDesc(desc: String): Boolean {
        val nodes = compose.onAllNodesWithContentDescription(desc).filter(hasClickAction())
        if (nodes.fetchSemanticsNodes().isEmpty()) { log += "  - [$desc]"; return false }
        nodes.clickFirst(); settle(); log += "  + [$desc]"; return true
    }

    private fun back() {
        compose.runOnIdle { } // 다이얼로그·시트 닫기는 닫기 버튼으로 한다
    }

    private fun runAll(qualifiers: String) {
        RuntimeEnvironment.setQualifiers(qualifiers)
        compose.setContent { WowaGoodsProjectTheme { MainScreen() } }
        settle()

        // 공식(시리즈) 탭 → 시리즈 상세 → 굿즈 필터
        tap("공식")
        if (tap("1.0버전")) {
            tapDesc("필터"); tapDesc("닫기")
            tapDesc("보기 방식 전환"); tapDesc("보기 방식 전환")
        }

        // 캐릭터별 탭 → 캐릭터 상세 → 필터 시트 → 굿즈 상세 → 세트 팝업
        tap("캐릭터별")
        if (tap("장리")) {
            tapDesc("필터"); tapDesc("닫기")
            tapDesc("보기 방식 전환")   // 그리드
            tapDesc("보기 방식 전환")   // 리스트
            tapDesc("뒤로")
        }
        if (tap("감심")) {
            tap("배지", substring = true)   // 세트 구성품 → 굿즈 상세 시트
            tap("구성품 2개", substring = true, last = true)  // 상세 시트의 세트 굿즈 줄 → 세트 팝업
            tap("카드", substring = true, last = true)       // 세트 팝업에서 구매예정 구성품 고르기(구매 정보 칸)
            tapDesc("닫기")
            tapDesc("뒤로")
        }

        // 2차창작 탭 → 필터 → 등록 화면
        tap("2차창작")
        tapDesc("필터"); tapDesc("닫기")
        tapDesc("보기 방식 전환"); tapDesc("보기 방식 전환")
        if (tap("굿즈 등록")) tapDesc("뒤로")

        // 굿즈 검색 탭 → 캐릭터·카테고리 고르기
        tap("굿즈 검색")
        tap("캐릭터"); tapDesc("닫기")
        tap("카테고리"); tapDesc("닫기")

        // 마이페이지: 첫 화면(게이지·수집률) → 용량 정리 → 구매예정 → 보유 굿즈 → 변경된 굿즈 → 업데이트 이력
        tap("마이페이지")
        if (tap("용량 정리")) tapDesc("닫기")
        if (tap("구매예정 굿즈")) { tapDesc("보기 방식 전환"); tapDesc("필터"); tapDesc("닫기"); tapDesc("뒤로") }
        if (tap("목록 보기")) { tapDesc("필터"); tapDesc("닫기"); tapDesc("뒤로") }
        if (tap("변경된 굿즈")) { tap("미처리"); tap("처리"); tapDesc("뒤로") }
        if (tap("업데이트 이력")) tapDesc("뒤로")
        back()
        println("SMOKE $qualifiers\n" + log.joinToString("\n"))
    }
}
