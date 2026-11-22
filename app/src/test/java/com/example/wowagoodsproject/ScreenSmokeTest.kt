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
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import com.example.wowagoodsproject.db.betray.BetrayGenreEntity
import com.example.wowagoodsproject.db.betray.BetrayGoodsEntity
import com.example.wowagoodsproject.db.changed.ChangedGoodsEntity
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
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
        // 배신 굿즈: 장르 하나에 공식·2차창작 굿즈
        val betray = App.betrayDatabase.betrayDao()
        val genre = betray.insertGenre(BetrayGenreEntity(genreNm = "원신")).toInt()
        // 사진이 있는 장르(파일이 없어도 그리다 꺼지면 안 된다)
        betray.insertGenre(BetrayGenreEntity(genreNm = "블루아카", genreOrder = 2,
            genreImgPath = "/nope/betray_genre_banner.jpg", genreIconPath = "/nope/betray_genre_icon.jpg"))
        betray.insertGoods(BetrayGoodsEntity(genreId = genre, goodsKind = "OFFICIAL", goodsRegDate = "2026-11-01",
            goodsSeries = "2주년 팝업", goodsChara = "푸리나", goodsCategory = "아크릴", goodsStatus = "GOTTEN"))
        betray.insertGoods(BetrayGoodsEntity(genreId = genre, goodsKind = "OFFICIAL", goodsRegDate = "2026-11-02",
            goodsSeries = "2주년 팝업", goodsChara = "푸리나,나히다", goodsCategory = "포스터", goodsStatus = "PENDING"))
        betray.insertGoods(BetrayGoodsEntity(genreId = genre, goodsKind = "FAN", goodsRegDate = "2026-11-03",
            goodsSeries = "코믹", goodsChara = "나히다", goodsCategory = "엽서"))
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

    /** [last] 면 맨 나중 노드(가장 위에 뜬 다이얼로그 안의 것)를 누른다. */
    private fun tapDesc(desc: String, last: Boolean = false): Boolean {
        val nodes = compose.onAllNodesWithContentDescription(desc).filter(hasClickAction())
        if (nodes.fetchSemanticsNodes().isEmpty()) { log += "  - [$desc]"; return false }
        nodes.clickFirst(last); settle(); log += "  + [$desc]"; return true
    }

    private fun exists(text: String): Boolean = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    /** 열린 카메라/갤러리 메뉴를 닫는다: 갤러리를 고르면 메뉴가 닫히고, 테스트에서는 갤러리 앱 요청만 기록된다. */
    private fun dismissPopup() {
        tap("갤러리", last = true)
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

        // 비공식 탭: 시리즈 카드(코믹월드·미분류) → 시리즈(필터·보기 방식) → '+' 메뉴의 시리즈 / 굿즈
        tap("비공식")
        assertTrue("전체 굿즈 카드는 없다", !exists("전체 굿즈"))
        assertTrue("시리즈를 비운 굿즈는 미분류 카드", exists("미분류"))
        assertTrue("2차창작 시리즈 카드", tap("코믹월드"))
        tapDesc("필터"); tapDesc("닫기")
        tapDesc("보기 방식 전환"); tapDesc("보기 방식 전환")
        tapDesc("뒤로")
        assertTrue("+ 메뉴", tapDesc("추가"))
        assertTrue("+ 메뉴의 시리즈", tap("시리즈", last = true))
        assertTrue("시리즈 이름 칸", exists("이름"))
        tapDesc("뒤로")
        assertTrue("+ 메뉴", tapDesc("추가"))
        if (tap("굿즈", last = true)) {
            // 시리즈부터 한 칸씩: 고르기 창은 닫아도 넘어가고, 글은 적으면 넘어간다. 마지막 '다음' → 요약
            assertTrue("2차창작 시리즈 칸", tap("시리즈"))
            assertTrue("시리즈 고르기의 시리즈 추가", exists("시리즈 추가"))
            tapDesc("닫기", last = true)
            assertTrue("카테고리 칸", tap("카테고리"))
            // 카테고리는 필수: 고르지 않고 닫으면 넘어가지 않는다
            assertTrue("카테고리 고르기", tap("엽서", last = true))
            // 가로 화면은 가격을 오른쪽 숫자 패드로 적는다(키보드 없음)
            if (tapDesc("지우기", last = true)) { listOf("5", "0", "0", "0").forEach { tap(it, last = true) }; assertTrue("숫자 패드 완료", tap("완료", last = true)) }
            else { compose.onAllNodes(hasSetTextAction()).onLast().performTextInput("5000원"); settle() }
            assertTrue("캐릭터 칸", tap("캐릭터"))
            assertTrue("캐릭터 고르기 완료", tap("완료", last = true))
            compose.onAllNodes(hasSetTextAction()).onLast().performTextInput("메모"); settle()
            assertTrue("마지막 다음", tap("다음", last = true))
            assertTrue("요약의 보유 여부", exists("미보유"))
            tapDesc("뒤로")
            assertTrue("나가기 확인", tap("나가기", last = true))
        }

        // 굿즈 검색 탭 → 캐릭터·카테고리 고르기
        tap("굿즈 검색")
        tap("캐릭터"); tapDesc("닫기")
        tap("카테고리"); tapDesc("닫기")

        // 마이페이지: 첫 화면(게이지·수집률) → 용량 정리 → 구매예정 → 보유 굿즈 → 변경된 굿즈 → 업데이트 이력
        tap("마이페이지")
        if (tap("용량 정리")) tapDesc("닫기")
        // 데이터 추출 확인 시트: 배신 굿즈 전체 추출 / 장르 선택(체크박스)
        assertTrue("데이터 추출", tap("데이터 추출"))
        // 가로 휴대폰처럼 낮은 화면에서는 시트 목록 아래쪽에 있으므로 사람처럼 스크롤해서 찾는다.
        runCatching { compose.onAllNodes(hasScrollToNodeAction()).onLast().performScrollToNode(hasText("장르 선택")) }
        settle()
        assertTrue("장르 선택", tap("장르 선택", last = true))
        runCatching { compose.onAllNodes(hasScrollToNodeAction()).onLast().performScrollToNode(hasText("원신")) }
        settle()
        assertTrue("장르 체크 해제", tap("원신", last = true))
        assertTrue("전체 추출로 되돌리기", tap("전체 추출", last = true))
        tapDesc("닫기")
        if (tap("구매예정 굿즈")) { tapDesc("보기 방식 전환"); tapDesc("필터"); tapDesc("닫기"); tapDesc("뒤로") }
        if (tap("목록 보기")) { tapDesc("필터"); tapDesc("닫기"); tapDesc("뒤로") }
        if (tap("변경된 굿즈")) { tap("미처리"); tap("처리"); tapDesc("뒤로") }
        if (tap("업데이트 이력")) tapDesc("뒤로")

        // 배신 굿즈: 장르 목록 → 장르 → 다섯 탭을 모두 열어 본다
        assertTrue("배신 굿즈 버튼", tap("배신 굿즈"))
        assertTrue("장르 열기", tap("원신"))
        assertTrue("공식 시리즈 열기", tap("2주년 팝업"))
        tapDesc("필터"); tapDesc("닫기")
        tapDesc("보기 방식 전환"); tapDesc("보기 방식 전환")
        // 시리즈 머리말의 편집 → 시리즈 편집 화면(장르 편집과 같은 구성)
        assertTrue("배신 굿즈 시리즈 편집", tapDesc("시리즈 편집"))
        assertTrue("시리즈 편집 화면", exists("시리즈 편집"))
        tapDesc("뒤로")
        tapDesc("뒤로")
        tap("캐릭터별")
        // 캐릭터 추가 화면(배신 굿즈 전용)
        assertTrue("캐릭터 추가", tap("캐릭터 추가"))
        // 캐릭터명을 적으면 선호/비선호가, 고르면 아이콘이 나타난다. 사진 없는 아이콘을 누르면 바로 카메라/갤러리.
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("새캐릭터")
        settle()
        assertTrue("선호 고르기", tap("선호"))
        assertTrue("캐릭터 아이콘", tapDesc("캐릭터 사진"))
        assertTrue("사진 없는 아이콘 메뉴는 카메라/갤러리", exists("카메라") && exists("갤러리"))
        dismissPopup()
        tapDesc("뒤로")
        if (tap("푸리나")) {
            tap("2차창작 0", substring = true); tapDesc("필터"); tapDesc("닫기")
            assertTrue("캐릭터 편집", tapDesc("캐릭터 편집"))
            tapDesc("뒤로")
            tapDesc("뒤로")
        }
        // 명조와 같은 하단: 2차창작 시리즈(코믹)는 비공식 탭에, 공식 탭에는 없다
        assertTrue("비공식 탭", tap("비공식"))
        assertTrue("2차창작 시리즈는 비공식 탭에", tap("코믹"))
        tapDesc("뒤로")
        assertTrue("공식 탭", tap("공식"))
        assertFalse("공식 탭에는 2차창작 시리즈가 없다", exists("코믹"))
        // '+' → [굿즈 · 시리즈] 에서 굿즈
        if (tapDesc("추가") && tap("굿즈", last = true)) {
            // 공식 탭의 '+' 라 종류는 묻지 않는다: 시리즈·카테고리·가격 → 캐릭터 고르기(캐릭터 추가 다이얼로그) → 메모 → 보유 완료 → 요약
            assertTrue("공식 탭에서 열면 공식 굿즈 등록", exists("공식 굿즈 등록"))
            assertTrue("시리즈 칸", tap("시리즈")); tapDesc("닫기", last = true)
            assertTrue("카테고리 칸", tap("카테고리")); assertTrue("카테고리 고르기(필수)", tap("엽서", last = true))
            if (tapDesc("지우기", last = true)) { listOf("3", "0", "0", "0").forEach { tap(it, last = true) }; assertTrue("숫자 패드 완료", tap("완료", last = true)) }
            else { compose.onAllNodes(hasSetTextAction()).onLast().performTextInput("3000원"); settle() }
            assertTrue("캐릭터 칸", tap("캐릭터"))
            assertTrue("캐릭터 고르기의 캐릭터 추가", tap("캐릭터 추가", last = true))
            assertTrue("캐릭터 추가 다이얼로그 닫기", tapDesc("뒤로", last = true))
            tap("완료", last = true)
            compose.onAllNodes(hasSetTextAction()).onLast().performTextInput("메모"); settle()
            assertTrue("마지막 다음", tap("다음", last = true))
            assertFalse("종류 칸은 없다", exists("종류"))
            // 입력한 채로 뒤로 → 정말 나갈지 묻는다
            tapDesc("뒤로")
            assertTrue("저장 안 하고 나가기 확인", exists("저장하지 않고 나갈까요?"))
            assertTrue("나가기", tap("나가기", last = true))
        }
        // 배신 굿즈 검색: 명조 검색과 같은 모양(검색창 · 캐릭터/카테고리 고르기 · 안내)
        tap("굿즈 검색")
        assertTrue("배신 검색 안내", exists("굿즈를 검색해 보세요"))
        assertTrue("배신 검색 캐릭터 고르기", tap("캐릭터"))
        tapDesc("닫기")
        assertTrue("배신 검색 카테고리 고르기", tap("카테고리"))
        tapDesc("닫기")
        tap("마이페이지")
        if (tap("보유 굿즈")) tapDesc("뒤로")
        if (tap("구매예정 굿즈")) tapDesc("뒤로")
        // 이 장르만 추출: 명조 마이페이지와 같은 '추출할 데이터 확인' 단계 화면
        assertTrue("장르 데이터 추출", tap("데이터 추출"))
        assertTrue("명조와 같은 추출 확인 화면", exists("추출할 데이터 확인"))
        assertTrue("장르 추출 닫기", tapDesc("닫기", last = true))
        assertTrue("장르 안에서 장르 편집", tap("장르 편집"))
        assertTrue("이름이 있으면 카메라/갤러리 버튼", exists("카메라") && exists("갤러리"))
        tapDesc("뒤로")
        assertTrue("장르 목록으로", tap("다른 장르로"))
        // 장르 목록: 원 보기 ↔ 그리드 보기, 메뉴의 편집, 새 장르
        assertTrue("아이콘으로 보기", tapDesc("아이콘으로 보기"))
        assertTrue("원 보기 메뉴", tapDesc("장르 메뉴"))
        assertTrue("편집 열기", tap("편집", last = true))
        assertTrue("사진 없는 장르는 카메라/갤러리 버튼", exists("카메라") && exists("갤러리"))
        tapDesc("뒤로")
        assertTrue("배경으로 보기", tapDesc("배경으로 보기"))
        // 사진이 있는 장르(블루아카): 배경 + 연필 아이콘 → 사진 변경 / 위치·크기 변경 → 사진 변경 → 카메라/갤러리
        compose.onAllNodesWithContentDescription("장르 메뉴").filter(hasClickAction())[1].performClick()
        settle()
        assertTrue("블루아카 편집", tap("편집", last = true))
        assertTrue("배경 사진", compose.onAllNodesWithContentDescription("배경 사진").fetchSemanticsNodes().isNotEmpty())
        assertTrue("아이콘 연필", tapDesc("아이콘 편집"))
        assertTrue("위치·크기 변경 메뉴", exists("위치·크기 변경"))
        assertTrue("사진 변경", tap("변경", last = true))
        assertTrue("사진 변경 → 카메라/갤러리", exists("카메라") && exists("갤러리"))
        dismissPopup()
        tapDesc("뒤로")
        assertTrue("장르 + 메뉴", tapDesc("장르 추가"))
        assertTrue("복구 메뉴", exists("복구"))
        assertTrue("+ 메뉴의 추가", tap("추가", last = true))
        tapDesc("뒤로")
        back()
        println("SMOKE $qualifiers\n" + log.joinToString("\n"))
    }
}
