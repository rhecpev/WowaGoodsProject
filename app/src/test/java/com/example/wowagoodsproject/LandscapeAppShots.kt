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

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File

/**
 * 앱 화면을 가로 휴대폰 · 가로 태블릿 크기로 띄워 탭·상세를 차례로 열고 build/layout-shots/app_* 로 남긴다(눈으로 확인용).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LandscapeAppShots {

    @get:Rule
    val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

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

    private fun settle() {
        repeat(6) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
            Thread.sleep(30)
        }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        settle()
        val dialog = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }
        val window = dialog?.window ?: compose.activity.window
        val view = window.decorView
        val bmp = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bmp)) }
        val dir = File("build/layout-shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun tap(text: String, substring: Boolean = false, last: Boolean = false): Boolean {
        val nodes = compose.onAllNodesWithText(text, substring = substring).filter(hasClickAction())
        if (nodes.fetchSemanticsNodes().isEmpty()) { println("SHOT miss $text"); return false }
        println("SHOT tap $text")
        val n = if (last) nodes.onLast() else nodes.onFirst()
        runCatching { n.performScrollTo() }
        n.performClick(); settle(); return true
    }

    private fun tapDesc(desc: String, last: Boolean = false): Boolean {
        val nodes = compose.onAllNodesWithContentDescription(desc).filter(hasClickAction())
        if (nodes.fetchSemanticsNodes().isEmpty()) { println("SHOT miss [$desc]"); return false }
        println("SHOT tap [$desc]")
        (if (last) nodes.onLast() else nodes.onFirst()).performClick(); settle(); return true
    }

    private fun detailOpen(where: String) {
        val n = compose.onAllNodesWithText("굿즈 정보").fetchSemanticsNodes().size
        println("SHOT detail $where=$n")
    }

    private fun runAll(q: String, p: String) {
        RuntimeEnvironment.setQualifiers(q)
        compose.setContent { WowaGoodsProjectTheme { MainScreen() } }
        settle()
        tap("공식"); shot("${p}_01_official")
        if (tap("1.0버전")) { shot("${p}_02_series"); if (tap("키링", substring = true)) { detailOpen("series"); shot("${p}_03_detail") }; tapDesc("닫기", last = true); tapDesc("뒤로") }
        tap("캐릭터별"); shot("${p}_04_charas")
        if (tap("장리")) {
            shot("${p}_05_chara"); tapDesc("필터"); shot("${p}_06_filter"); tapDesc("닫기", last = true)
            if (tap("아크릴", substring = true)) { detailOpen("chara"); shot("${p}_05b_chara_detail"); tapDesc("닫기", last = true) }
            tap("2차창작", substring = true)
            if (tap("키링", substring = true)) { detailOpen("chara_fan"); tapDesc("닫기", last = true) }
            tapDesc("뒤로")
        }
        tap("비공식"); shot("${p}_07_fan")
        if (tap("코믹월드")) { if (tap("키링", substring = true)) { detailOpen("fan"); shot("${p}_07b_fan_detail"); tapDesc("닫기", last = true) }; tapDesc("뒤로") }
        tap("굿즈 검색")
        runCatching { compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("키링"); settle() }
        shot("${p}_08_search")
        if (tap("장리", substring = true, last = true)) { detailOpen("search"); shot("${p}_08b_search_detail"); tapDesc("닫기", last = true) }
        tap("마이페이지"); shot("${p}_09_mypage")
        // 마이페이지 하위: 구매예정 굿즈
        tap("마이페이지")
        if (tap("구매예정 굿즈")) {
            shot("${p}_13_pending")
            compose.onAllNodesWithText("감심 - 카드", substring = true).onFirst().performClick(); settle(); detailOpen("pending"); shot("${p}_13b_pending_detail"); tapDesc("닫기", last = true)
            tapDesc("뒤로")
        }
        // 배신 굿즈: 장르 목록 → 장르 → 굿즈 상세
        if (tap("배신 굿즈")) {
            shot("${p}_14_betray_genres")
            if (tap("원신")) {
                shot("${p}_15_betray_genre")
                if (tap("공식")) shot("${p}_16_betray_official")
                if (tap("2주년 팝업")) { shot("${p}_17_betray_series"); compose.onAllNodesWithText("포스터").onFirst().performClick(); settle(); detailOpen("betray"); shot("${p}_18_betray_detail") }
            }
        }
    }

    /** 비공식 굿즈 등록: 시리즈 · 캐릭터 고르기 */
    private fun runFanAdd(q: String, p: String) {
        RuntimeEnvironment.setQualifiers(q)
        compose.setContent { WowaGoodsProjectTheme { MainScreen() } }
        settle()
        tap("비공식")
        if (tapDesc("추가") && tap("굿즈", last = true)) {
            shot("${p}_10_fan_add")
            if (tap("시리즈")) { shot("${p}_11_pick_series"); tapDesc("닫기", last = true) }
            if (tap("카테고리")) { tap("엽서", last = true) }
            if (tapDesc("지우기", last = true)) {
                listOf("5", "0", "0", "0").forEach { tap(it, last = true) }
                shot("${p}_13_price_pad")
                tap("완료", last = true)
            } else { compose.onAllNodes(hasSetTextAction()).onLast().performTextInput("5000"); settle() }
            if (tap("캐릭터")) shot("${p}_12_pick_chara")
        }
    }

    @Test fun 휴대폰_가로() = runAll("w891dp-h411dp-land", "app_phone")
    @Test fun 태블릿_가로() = runAll("w1280dp-h800dp-land", "app_tablet")
    @Test fun 휴대폰_가로_등록() = runFanAdd("w891dp-h411dp-land", "app_phone")
    @Test fun 태블릿_가로_등록() = runFanAdd("w1280dp-h800dp-land", "app_tablet")
}
