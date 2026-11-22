package com.example.wowagoodsproject

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.screen.fan.SeriesOption
import com.example.wowagoodsproject.screen.fan.StepGoodsForm
import com.example.wowagoodsproject.ui.theme.WowaGoodsProjectTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 한 칸씩 여는 굿즈 등록: '다음' 단추 없이 고르거나 적으면 다음 칸이 열리는지(고르기 창을 그냥 닫거나 키보드 다음으로 넘길 수도),
 * 마지막에 가로로 꽉 찬 '다음'으로 요약 + 사진이 나오고 저장이 켜지는지, 요약 줄을 눌러 고칠 수 있는지, 입력한 채로 뒤로 가면 묻는지.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class StepGoodsFormTest {

    @get:Rule
    val compose = createComposeRule()

    // 카테고리 고르기 그리드가 대표 사진을 DB 에서 찾는다
    @org.junit.Before fun setUpDb() = TestDb.setUp()
    @org.junit.After fun tearDownDb() = TestDb.tearDown()

    private var series by mutableStateOf("")
    private var category by mutableStateOf("")
    private var price by mutableStateOf("")
    private var memo by mutableStateOf("")
    private var kind by mutableStateOf<Int?>(null)
    private var status by mutableStateOf(GoodsStatus.NOT_GOTTEN)
    private var quantity by mutableIntStateOf(1)
    private var charaOpen by mutableStateOf(false)
    private var charas by mutableStateOf(listOf<String>())
    private var store by mutableStateOf("")
    private var buyDate by mutableStateOf("")
    private var receive by mutableStateOf("")
    private var saved = 0
    private var left = 0
    private var addSeriesWith: String? = null

    private fun show(withKind: Boolean) {
        compose.setContent {
            WowaGoodsProjectTheme {
                StepGoodsForm(
                    title = "굿즈 등록",
                    onLeave = { left++ },
                    kindOptions = if (withKind) listOf("공식", "2차창작") else null,
                    kindIndex = kind,
                    onKind = { kind = it },
                    series = series, onSeries = { series = it },
                    seriesOptions = listOf(SeriesOption("팝업"), SeriesOption("카페")),
                    onAddSeries = { addSeriesWith = it },
                    category = category, categories = listOf("키링", "아크릴"), onCategory = { category = it },
                    price = price, onPrice = { price = it },
                    charas = charas, onRemoveChara = { n -> charas = charas - n }, onPickChara = { charaOpen = true },
                    charaPickerOpen = charaOpen,
                    memo = memo, onMemo = { memo = it },
                    status = status, onStatus = { status = it }, showPending = true,
                    purchaseStore = store, onPurchaseStore = { store = it },
                    purchaseDate = buyDate, onPurchaseDate = { buyDate = it },
                    receiveDate = receive, onReceiveDate = { receive = it },
                    quantity = quantity, onQuantity = { quantity = it },
                    imageUri = null, onCamera = {}, onGallery = {}, onUrl = {}, onRemoveImage = {},
                    isSaving = false, onSave = { saved++ }
                )
            }
        }
        compose.waitForIdle()
    }

    private fun exists(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    /** 화면 밖이면 사람처럼 스크롤해서 보이게 한 뒤 누른다. */
    private fun tapLast(text: String) {
        // 목록(고르기 창) 아래쪽이라 아직 그려지지 않았으면 그 줄까지 스크롤한다.
        if (compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()) {
            compose.onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction()).fetchSemanticsNodes().indices.reversed().forEach { i ->
                runCatching {
                    compose.onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction())[i]
                        .performScrollToNode(androidx.compose.ui.test.hasText(text))
                }
            }
            compose.waitForIdle()
        }
        val node = compose.onAllNodesWithText(text).filter(hasClickAction()).onLast()
        runCatching { node.performScrollTo() }
        node.performClick()
        compose.waitForIdle()
    }

    private fun typeInLast(text: String) {
        compose.onAllNodes(hasSetTextAction()).onLast().performTextInput(text)
        compose.waitForIdle()
    }

    /** 캐릭터 고르기 창이 열렸다 닫힌 것처럼(창은 부르는 쪽 것이다) */
    private fun closeCharaPicker(pick: String? = null) {
        compose.runOnIdle {
            if (pick != null) charas = charas + pick
            charaOpen = false
        }
        compose.waitForIdle()
    }

    @Test
    fun 시리즈가_채워진_채로_열면_바로_카테고리_칸이_열린다() {
        // 시리즈 상세에서 '+' 로 온 경우
        series = "카페"
        show(withKind = false)
        compose.waitForIdle()
        assertTrue("카테고리 칸이 바로 열린다", exists("카테고리"))
    }

    @Test
    fun 고르거나_적으면_바로_다음_칸이_열리고_마지막_다음으로_요약과_사진이_나온다() {
        show(withKind = false)
        assertFalse("요약 전에는 저장 단추가 없다", exists("저장"))
        assertTrue("처음에는 시리즈 칸만", exists("시리즈"))
        assertFalse(exists("카테고리"))
        assertFalse("작은 '다음' 단추는 없다", exists("다음"))

        tapLast("시리즈"); tapLast("팝업")
        assertEquals("팝업", series)
        assertTrue("고르면 카테고리 칸", exists("카테고리"))

        tapLast("카테고리"); tapLast("키링")
        assertEquals("키링", category)
        assertTrue(exists("가격"))
        assertFalse(exists("캐릭터"))

        typeInLast("5000원")
        assertEquals("5000원", price)
        assertTrue("적으면 바로 캐릭터 칸", exists("캐릭터"))

        tapLast("캐릭터")
        assertTrue(charaOpen)
        assertFalse("창을 닫기 전에는 보유 여부 칸이 없다", exists("보유 여부"))
        closeCharaPicker(pick = "장리")
        assertTrue("창을 닫으면 보유 여부와 메모(선택)가 함께", exists("보유 여부") && exists("메모"))
        assertTrue("입력 칸에 고른 캐릭터", exists("장리"))

        typeInLast("한정판")
        assertFalse(exists("저장"))
        // 마지막에만 가로로 꽉 찬 '다음'
        assertEquals(1, compose.onAllNodesWithText("다음").fetchSemanticsNodes().size)
        tapLast("다음")

        assertTrue(exists("팝업") && exists("키링") && exists("5000원") && exists("한정판") && exists("미보유"))
        assertTrue("사진 넣기", exists("굿즈 사진을 추가하세요"))
        // 저장은 보유 여부 아래(화면 밖이면 스크롤)
        tapLast("저장")
        assertEquals(1, saved)
    }

    @Test
    fun 시리즈는_비워_넘길_수_있고_카테고리_가격은_필수다() {
        show(withKind = true)
        assertTrue(exists("종류"))
        assertFalse("종류를 고르기 전에는 시리즈 칸이 없다", exists("시리즈"))
        tapLast("2차창작")
        assertEquals(1, kind)

        tapLast("시리즈")
        compose.onAllNodesWithContentDescription("닫기").onLast().performClick(); compose.waitForIdle()
        assertTrue("시리즈 고르기를 닫으면 카테고리", exists("카테고리"))
        tapLast("카테고리")
        compose.onAllNodesWithContentDescription("닫기").onLast().performClick(); compose.waitForIdle()
        assertFalse("카테고리를 고르지 않고 닫으면 넘어가지 않는다", exists("가격"))
        tapLast("카테고리"); tapLast("아크릴")
        assertTrue(exists("가격"))
        compose.onAllNodes(hasSetTextAction()).onLast().performImeAction(); compose.waitForIdle()
        assertFalse("가격을 비운 채 키보드 다음으로는 넘어가지 않는다", exists("캐릭터"))
        typeInLast("3000")
        assertTrue(exists("캐릭터"))
        tapLast("캐릭터"); closeCharaPicker()
        assertTrue("메모는 비워 둬도 된다", exists("보유 여부"))

        // 구매예정을 고르면 구매 정보 칸
        assertFalse(exists("구입처"))
        tapLast("구매예정")
        assertEquals(GoodsStatus.PENDING, status)
        assertTrue(exists("구입처") && exists("구매일") && exists("수령일"))
        compose.onAllNodesWithContentDescription("구입처").onFirst().performTextInput("트위터 통판"); compose.waitForIdle()
        assertEquals("트위터 통판", store)
        tapLast("다음")
        assertTrue("요약에 구매예정", exists("구매예정 · 1개"))
        assertTrue("구매 정보는 아래 줄로", exists("구입처") && exists("트위터 통판"))
        assertTrue("시리즈를 비워 두면 미분류", exists("미분류"))
        assertEquals("", series + memo)
        assertEquals("아크릴", category)
    }

    @Test
    fun 요약에서_가격을_지우면_저장할_수_없다() {
        series = "팝업"; category = "키링"; price = "5000원"
        compose.setContent {
            WowaGoodsProjectTheme {
                StepGoodsForm(
                    title = "굿즈 수정", onLeave = {}, kindOptions = null, kindIndex = null, onKind = {},
                    series = series, onSeries = { series = it }, seriesOptions = emptyList(), onAddSeries = {},
                    category = category, categories = listOf("키링"), onCategory = { category = it },
                    price = price, onPrice = { price = it },
                    charas = charas, onRemoveChara = {}, onPickChara = {}, charaPickerOpen = false,
                    memo = memo, onMemo = { memo = it },
                    status = status, onStatus = { status = it }, showPending = true,
                    purchaseStore = store, onPurchaseStore = { store = it },
                    purchaseDate = buyDate, onPurchaseDate = { buyDate = it },
                    receiveDate = receive, onReceiveDate = { receive = it },
                    quantity = quantity, onQuantity = { quantity = it },
                    imageUri = null, onCamera = {}, onGallery = {}, onUrl = {}, onRemoveImage = {},
                    isSaving = false, onSave = { saved++ }, startAtSummary = true
                )
            }
        }
        compose.waitForIdle()
        assertFalse("다 채워져 있으면 안내가 없다", exists("가격을 입력해야 저장할 수 있어요"))
        compose.runOnIdle { price = "" }
        compose.waitForIdle()
        assertTrue("가격 줄에 필수 입력", exists("필수 입력"))
        assertTrue(exists("가격을 입력해야 저장할 수 있어요"))
        tapLast("저장")
        assertEquals("비어 있으면 저장되지 않는다", 0, saved)
    }

    @Test
    fun 요약_줄을_누르면_그_칸만_고치고_확인으로_닫는다() {
        show(withKind = true)
        tapLast("공식")
        tapLast("시리즈"); tapLast("카페")
        tapLast("카테고리"); tapLast("아크릴")
        typeInLast("3000원")
        tapLast("캐릭터"); closeCharaPicker()
        typeInLast("메모")
        tapLast("다음")
        compose.onNodeWithText("저장").assertIsEnabled()
        assertTrue("메모 줄", exists("메모"))

        tapLast("가격")
        typeInLast("0")
        // 칸에는 금액만 있어 "0" 을 더 치면 금액이 바뀐다(단위는 그대로)
        assertEquals("30000원", price)
        compose.onNodeWithText("저장").assertIsNotEnabled()
        tapLast("확인")
        compose.onNodeWithText("저장").assertIsEnabled()
        assertTrue(exists("30000원"))

        // 시리즈 줄: 고르면 바로 닫힌다
        tapLast("시리즈"); tapLast("팝업")
        assertEquals("팝업", series)
        compose.onNodeWithText("저장").assertIsEnabled()
    }

    @Test
    fun 시리즈_고르기의_시리즈_추가는_검색어를_넘기고_새_시리즈가_오면_다음_칸이_열린다() {
        show(withKind = false)
        tapLast("시리즈")
        typeInLast("신규 행사")
        tapLast("시리즈 추가 · '신규 행사'")
        assertEquals("신규 행사", addSeriesWith)
        compose.runOnIdle { series = "신규 행사" }
        compose.waitForIdle()
        assertTrue(exists("카테고리"))
    }

    @Test
    fun 입력한_채로_뒤로_가면_묻고_아무것도_없으면_바로_나간다() {
        show(withKind = true)
        compose.onAllNodesWithContentDescription("뒤로").onFirst().performClick()
        compose.waitForIdle()
        assertEquals("입력이 없으면 바로 나간다", 1, left)

        tapLast("공식")
        compose.onAllNodesWithContentDescription("뒤로").onFirst().performClick()
        compose.waitForIdle()
        assertTrue(exists("저장하지 않고 나갈까요?"))
        tapLast("계속 입력")
        assertEquals(1, left)
        compose.onAllNodesWithContentDescription("뒤로").onFirst().performClick()
        compose.waitForIdle()
        tapLast("나가기")
        assertEquals(2, left)
    }

    @Test
    fun 시리즈_고르기의_미분류는_시리즈를_비우고_다음_칸으로() {
        show(withKind = false)
        tapLast("시리즈")
        assertTrue("시리즈 선택", exists("시리즈 선택"))
        tapLast("미분류")
        assertEquals("", series)
        assertTrue("칸에 미분류", exists("미분류"))
        assertTrue(exists("카테고리"))
    }
}
