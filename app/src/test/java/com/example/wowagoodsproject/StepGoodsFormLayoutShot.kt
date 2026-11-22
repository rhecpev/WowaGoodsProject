package com.example.wowagoodsproject

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.getOrNull
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.screen.fan.SeriesOption
import com.example.wowagoodsproject.screen.fan.StepGoodsForm
import com.example.wowagoodsproject.ui.theme.WowaGoodsProjectTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 굿즈 등록 칸 모양 확인: 아이콘 아래 제목, 칸에는 값과 밑줄, 캐릭터는 밑줄 없이 아이콘과 같은 줄.
 * 화면을 build/layout-shots/ 에 그림으로 남긴다(눈으로 확인용).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w360dp-h1400dp-port")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StepGoodsFormLayoutShot {

    @get:Rule
    val compose = androidx.compose.ui.test.junit4.createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @org.junit.Before fun setUp() = TestDb.setUp()
    @org.junit.After fun tearDown() = TestDb.tearDown()

    @Test
    fun 칸을_채운_등록_화면을_그린다() {
        var series by mutableStateOf("")
        var category by mutableStateOf("")
        var price by mutableStateOf("")
        var charas by mutableStateOf(listOf("치사"))
        var status by mutableStateOf(GoodsStatus.NOT_GOTTEN)
        var memo by mutableStateOf("")
        var charaOpen by mutableStateOf(false)
        compose.setContent {
            WowaGoodsProjectTheme {
                StepGoodsForm(
                    title = "비공식 굿즈 등록",
                    onLeave = {},
                    series = series, onSeries = { series = it },
                    seriesOptions = listOf(SeriesOption("123")),
                    onAddSeries = {},
                    category = category, categories = listOf("CD"), onCategory = { category = it },
                    price = price, onPrice = { price = it },
                    charas = charas, onRemoveChara = { n -> charas = charas - n }, onPickChara = {},
                    charaPickerOpen = charaOpen,
                    memo = memo, onMemo = { memo = it },
                    status = status, onStatus = { status = it }, showPending = true,
                    quantity = 1, onQuantity = {},
                    imageUri = null, onCamera = {}, onGallery = {}, onUrl = {}, onRemoveImage = {},
                    isSaving = false, onSave = {}
                )
            }
        }
        compose.waitForIdle()
        // 시리즈 → 카테고리 → 가격 순으로 채운다
        compose.onAllNodesWithText("시리즈")[0].performClick(); compose.waitForIdle()
        compose.onAllNodesWithText("123").onLast().performClick(); compose.waitForIdle()
        compose.onAllNodesWithText("카테고리")[0].performClick(); compose.waitForIdle()
        compose.onAllNodesWithText("CD").onLast().performClick(); compose.waitForIdle()
        compose.onAllNodes(hasSetTextAction()).onLast().performTextInput("123"); compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
        // 캐릭터 고르기 창을 열었다 닫으면 다음 단계(보유 여부)가 열린다
        charaOpen = true; compose.waitForIdle()
        charaOpen = false; compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
        // 구매예정으로 바꿔 구입처·구매일·수령일·메모 칸까지 펼친다
        status = GoodsStatus.PENDING
        compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()

        // 아이콘 아래 제목은 한 줄 안에 다 들어가야 한다(잘리거나 밀리면 실패)
        listOf("시리즈", "카테고리", "가격", "캐릭터", "구입처", "구매일", "수령일", "메모").forEach { label ->
            // 누를 수 있는 칸이 글자를 묶으므로, 묶기 전의 글자 노드를 본다
            val nodes = compose.onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue("$label 칸이 있다", nodes.isNotEmpty())
            nodes.forEach { n ->
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                n.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                // 글자가 한 줄에 필요한 폭이 칸 폭보다 크면 잘리거나 밀린 것이다
                layouts.forEach {
                    val need = it.multiParagraph.intrinsics.maxIntrinsicWidth
                    val room = it.layoutInput.constraints.maxWidth
                    assertTrue("'$label' 이 칸(${room}px)보다 넓다(${need}px)", need <= room + 1 && it.lineCount == 1)
                }
            }
        }
        // 입력 칸 커서 깜빡임이 끝나지 않으므로 시계를 멈추고 찍는다.
        compose.mainClock.autoAdvance = false

        val dir = File("build/layout-shots").apply { mkdirs() }
        val file = File(dir, "step_goods_form.png")
        // 화면 뷰를 그대로 그림으로 그린다(Robolectric 실제 그리기 모드)
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(file.length() > 0)
        // 제목은 아이콘 아래 한 번만(칸 안에 떠오르는 제목이 없다)
        assertTrue(compose.onAllNodesWithText("가격").fetchSemanticsNodes().size == 1)
    }
}
