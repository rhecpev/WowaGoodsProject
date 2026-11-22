package com.example.wowagoodsproject

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.wowagoodsproject.screen.mypage.ImportPart
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.screen.mypage.ExportPreviewSheet
import com.example.wowagoodsproject.screen.mypage.ExportPreview
import com.example.wowagoodsproject.screen.mypage.ImportReport
import com.example.wowagoodsproject.screen.mypage.ReviewGoods
import com.example.wowagoodsproject.screen.mypage.ImportPlan
import com.example.wowagoodsproject.screen.mypage.ImportPreviewSheet
import com.example.wowagoodsproject.screen.mypage.ImportSection
import com.example.wowagoodsproject.ui.theme.WowaGoodsProjectTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** 앱 업데이트 창(새 버전 · 받는 중 · 설치 허용 · 실패)과 받은 APK 정리, 불러오기 확인 시트 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AppUpdateAndImportUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() = TestDb.setUp()

    @After
    fun tearDown() {
        AppUpdater.setState(AppUpdater.State.Idle)
        TestDb.tearDown()
    }

    private fun exists(text: String, desc: Boolean = false) =
        (if (desc) compose.onAllNodesWithContentDescription(text) else compose.onAllNodesWithText(text)).fetchSemanticsNodes().isNotEmpty()
    private fun next() {
        compose.onNodeWithText("다음").performClick()
        compose.waitForIdle()
    }

    private val release = AppUpdater.Release("v99.0", "새 기능", "https://example.invalid/app.apk", "app.apk", 10)

    private fun showDialogs() {
        compose.setContent { WowaGoodsProjectTheme { AppUpdateDialogs() } }
        compose.waitForIdle()
    }

    @Test
    fun 새_버전이면_업데이트를_묻고_나중에로_닫는다() {
        showDialogs()
        compose.runOnIdle { AppUpdater.setState(AppUpdater.State.Available(release)) }
        compose.waitForIdle()
        assertTrue(exists("업데이트 가능"))
        assertTrue(exists("v${BuildConfig.VERSION_NAME} → v99.0"))
        assertTrue("릴리즈 노트", exists("새 기능"))
        compose.onNodeWithText("나중에").performClick()
        compose.waitForIdle()
        assertEquals(AppUpdater.State.Idle, AppUpdater.state.value)
        assertFalse(exists("업데이트 가능"))
    }

    @Test
    fun APK가_없는_릴리즈는_릴리즈_페이지로_안내한다() {
        showDialogs()
        compose.runOnIdle { AppUpdater.setState(AppUpdater.State.Available(release.copy(apkUrl = null))) }
        compose.waitForIdle()
        assertTrue(exists("릴리즈 페이지"))
        assertFalse(exists("업데이트"))
    }

    @Test
    fun 받는_중_설치_허용_실패_창이_그려진다() {
        showDialogs()
        compose.runOnIdle { AppUpdater.setState(AppUpdater.State.Downloading(release, 0.42f)) }
        compose.waitForIdle()
        assertTrue(exists("v99.0 받는 중") && exists("42%"))
        compose.runOnIdle { AppUpdater.setState(AppUpdater.State.NeedPermission(release, File("x.apk"))) }
        compose.waitForIdle()
        assertTrue(exists("설치 허용이 필요해요") && exists("설정 열기"))
        compose.runOnIdle { AppUpdater.setState(AppUpdater.State.Failed(release, "받은 파일 크기가 맞지 않습니다")) }
        compose.waitForIdle()
        assertTrue(exists("업데이트하지 못했어요") && exists("다시 시도"))
    }

    @Test
    fun 받아_둔_APK는_버전마다_따로고_지금_이하_버전과_덜_받은_파일은_정리한다() {
        val ctx = TestDb.context
        assertEquals("wowa_99.0.apk", AppUpdater.apkFile(ctx, release).name)
        val dir = File(ctx.cacheDir, "update").apply { mkdirs() }
        val old = File(dir, "wowa_${BuildConfig.VERSION_NAME}.apk").apply { writeBytes(ByteArray(3)) }
        val newer = File(dir, "wowa_99.0.apk").apply { writeBytes(ByteArray(3)) }
        val part = File(dir, "wowa_99.1.apk.part").apply { writeBytes(ByteArray(3)) }
        AppUpdater.cleanup(ctx)
        assertFalse("설치한(지금) 버전 APK 는 지운다", old.exists())
        assertFalse("덜 받은 파일은 지운다", part.exists())
        assertTrue("더 새 버전은 남긴다(다시 받지 않게)", newer.exists())
    }

    @Test
    fun 불러오기_확인은_바뀐_항목만_켜져_있고_배신_굿즈는_따로_묻는다() {
        val plan = ImportPlan(listOf(
            ImportSection(ImportPart.OFFICIAL, "보유 1", "보유 2", changed = true, details = listOf("상태가 바뀌는 굿즈 1개")),
            ImportSection(ImportPart.FAVORITES, "1명", "1명", changed = false),
            ImportSection(ImportPart.BETRAY, "장르 1개", "장르 2개", changed = true, details = listOf("원신: 새 장르"))
        ))
        var imported: Set<ImportPart>? = null
        compose.setContent { WowaGoodsProjectTheme { ImportPreviewSheet(plan, onImport = { imported = it }, onDismiss = {}) } }
        compose.waitForIdle()
        fun scrollTo(text: String) {
            runCatching {
                compose.onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction()).onFirst()
                    .performScrollToNode(androidx.compose.ui.test.hasText(text))
            }
            compose.waitForIdle()
        }
        // 단계: 파일 내용 → 선호 캐릭터 → 불러올 항목 확인
        assertTrue(exists("1 / 3"))
        next()
        assertTrue("선호 캐릭터 단계", exists("이 선호 캐릭터 목록이 맞아요"))
        next()
        // 지금 / 파일 요약은 Before / After 두 칸으로 나뉜다.
        assertTrue(exists("보유 1"))
        assertTrue(exists("보유 2"))
        scrollTo("배신 굿즈도 함께 불러오기")
        assertTrue(exists("배신 굿즈도 함께 불러오기"))
        compose.onNodeWithText("선택한 항목 불러오기 (1)").assertIsEnabled()
        // 배신 굿즈도 켜고 공식은 끈다
        compose.onAllNodesWithText("배신 굿즈도 함께 불러오기").filter(hasClickAction()).onFirst().performClick()
        scrollTo("공식 굿즈 보유 현황")
        compose.onAllNodesWithText("공식 굿즈 보유 현황").filter(hasClickAction()).onFirst().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("선택한 항목 불러오기 (1)").performClick()
        assertEquals(setOf(ImportPart.BETRAY), imported)
        // 다 끄면 누를 수 없다
        scrollTo("배신 굿즈도 함께 불러오기")
        compose.onAllNodesWithText("배신 굿즈도 함께 불러오기").filter(hasClickAction()).onFirst().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("불러올 항목을 고르세요").assertIsNotEnabled()
    }

    @Test
    fun 파일_내용_칸을_누르면_초록_체크로_그_항목을_고른다() {
        val plan = ImportPlan(
            listOf(
                ImportSection(ImportPart.OFFICIAL, "보유 1", "보유 2", changed = true),
                ImportSection(ImportPart.FAN, "0개", "3개", changed = true)
            ),
            ImportReport(officialGotten = 2, fanTotal = 3, photos = 4)
        )
        var imported: Set<ImportPart>? = null
        compose.setContent { WowaGoodsProjectTheme { ImportPreviewSheet(plan, onImport = { imported = it }, onDismiss = {}, initialSelection = emptySet()) } }
        compose.waitForIdle()
        assertTrue("처음엔 꺼짐", exists("비공식 불러오기 꺼짐", desc = true))
        compose.onNodeWithContentDescription("비공식 불러오기 꺼짐").performClick()
        compose.waitForIdle()
        assertTrue("누르면 켜짐", exists("비공식 불러오기 켜짐", desc = true))
        assertFalse("사진 칸은 체크가 없다", exists("사진 불러오기 꺼짐", desc = true))
        next()
        compose.onNodeWithText("선택한 항목 불러오기 (1)").performClick()
        assertEquals(setOf(ImportPart.FAN), imported)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp") // 실제 폰 크기(목록이 보일 높이)
    fun 굿즈_단계는_캐릭터를_누르면_그_굿즈가_나오고_모두_확인해야_굿즈를_불러온다() {
        val goods = listOf(
            ReviewGoods(true, "1.0", "장리", "키링", "", "", GoodsStatus.GOTTEN, 1),
            ReviewGoods(true, "1.1", "장리", "키링", "", "", GoodsStatus.PENDING, 1),
            ReviewGoods(false, "코믹", "장리, 금희", "아크릴", "", "메모A", GoodsStatus.PENDING, 1),
            ReviewGoods(false, "코믹", "금희", "포카", "", "", GoodsStatus.GOTTEN, 1)
        )
        val plan = ImportPlan(
            listOf(ImportSection(ImportPart.OFFICIAL, "보유 0", "보유 1", changed = true), ImportSection(ImportPart.FAN, "0개", "2개", changed = true)),
            ImportReport(officialGotten = 1, fanTotal = 2, goods = goods)
        )
        var imported: Set<ImportPart>? = null
        compose.setContent { WowaGoodsProjectTheme { ImportPreviewSheet(plan, onImport = { imported = it }, onDismiss = {}, initialSelection = emptySet()) } }
        compose.waitForIdle()
        next()
        assertTrue("굿즈 단계", exists("캐릭터 2명 중 0명 확인"))
        // 처음엔 굿즈가 많은 장리. 금희를 누르면 금희 굿즈
        assertTrue(exists("장리 굿즈 확인했어요"))
        compose.onAllNodesWithText("금희").onFirst().performClick()
        compose.waitForIdle()
        assertTrue(exists("금희 굿즈 확인했어요"))
        // 목록 아래쪽 굿즈는 스크롤해야 보인다(스크롤 가능한 것 중 목록을 찾아 내린다)
        val scrollables = compose.onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction())
        repeat(scrollables.fetchSemanticsNodes().size) { i ->
            runCatching { scrollables[i].performScrollToNode(androidx.compose.ui.test.hasText("포카")) }
        }
        compose.waitForIdle()
        assertTrue("금희 포카", exists("포카"))
        assertFalse("장리만 나온 키링은 없다", exists("키링"))
        // 보유만 거르기: 필터 칩이 있는 맨 위로 올린 뒤 누른다
        repeat(scrollables.fetchSemanticsNodes().size) { i ->
            runCatching { scrollables[i].performScrollToNode(androidx.compose.ui.test.hasText("모든 상태")) }
        }
        compose.waitForIdle()
        assertTrue(exists("아크릴 / 메모A"))
        compose.onAllNodesWithText("보유").filter(androidx.compose.ui.test.isSelectable() or androidx.compose.ui.test.hasClickAction()).onFirst().performClick()
        compose.waitForIdle()
        assertFalse("구매예정 아크릴은 빠진다", exists("아크릴 / 메모A"))
        compose.onNodeWithText("금희 굿즈 확인했어요").performClick()
        compose.waitForIdle()
        assertTrue(exists("금희 확인함", desc = true))
        assertTrue(exists("캐릭터 2명 중 1명 확인"))
        next()
        assertTrue("한 명만 확인하면 굿즈는 안 불러온다", exists("불러올 항목을 고르세요"))
        compose.onNodeWithText("이전").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("모두 확인").performClick()
        compose.waitForIdle()
        next()
        compose.onNodeWithText("선택한 항목 불러오기 (2)").performClick()
        assertEquals(setOf(ImportPart.OFFICIAL, ImportPart.FAN), imported)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp") // 실제 폰 크기(목록이 보일 높이)
    fun 추출도_선호_캐릭터와_굿즈를_체크해야_다음으로_간다() {
        val preview = ExportPreview.of(
            official = listOf(GoodsEntity(goodsSeries = "1.0", goodsChara = "장리", goodsCategory = "키링", goodsStatus = "GOTTEN", goodsIsGotten = true)),
            fan = emptyList(),
            charas = listOf(CharaEntity(charaNm = "장리", charaIsFavorite = true, charaFavoriteOrder = 1))
        )
        var exported = false
        compose.setContent { WowaGoodsProjectTheme { ExportPreviewSheet(preview, onExport = { exported = true }, onDismiss = {}) } }
        compose.waitForIdle()
        next()
        compose.onNodeWithText("다음").assertIsNotEnabled()
        compose.onNodeWithText("이 선호 캐릭터 목록이 맞아요").performClick()
        compose.waitForIdle()
        next()
        // 굿즈 데이터가 적은 테스트라 경고 단추 이름
        compose.onNodeWithText("그래도 추출하기").assertIsNotEnabled()
        compose.onNodeWithText("장리 굿즈 확인했어요").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("그래도 추출하기").performClick()
        assertTrue(exported)
    }
}
