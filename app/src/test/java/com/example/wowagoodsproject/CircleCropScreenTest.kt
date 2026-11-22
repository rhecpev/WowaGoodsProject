package com.example.wowagoodsproject

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.example.wowagoodsproject.screen.betray.CircleCropScreen
import com.example.wowagoodsproject.ui.theme.WowaGoodsProjectTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.min

/**
 * 아이콘 원 범위 고르기 화면.
 * 손대지 않으면 사진 가운데의 가장 큰 원, 원 안을 끌면 옮기기, 모서리 손잡이를 끌면 반대쪽 모서리 기준 크기 바꾸기.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class CircleCropScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var result: Triple<Float, Float, Float>? = null

    private fun show(width: Int, height: Int, qualifiers: String) {
        RuntimeEnvironment.setQualifiers(qualifiers)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        compose.setContent {
            WowaGoodsProjectTheme {
                CircleCropScreen(bitmap = bmp, onCancel = {}, onConfirm = { cx, cy, r -> result = Triple(cx, cy, r) })
            }
        }
        compose.waitForIdle()
    }

    private fun confirm(): Triple<Float, Float, Float> {
        compose.onNodeWithText("확인").performClick()
        compose.waitForIdle()
        assertNotNull("확인을 누르면 범위를 돌려준다", result)
        return result!!
    }

    /** 화면에서 사진이 놓인 배율과 왼쪽 위(BetrayGenreEdit 의 fit 과 같은 계산) */
    private fun fit(bw: Float, bh: Float): Triple<Float, Float, Float> {
        val node = compose.onNodeWithTag("circleCropArea").fetchSemanticsNode()
        val w = node.size.width.toFloat()
        val h = node.size.height.toFloat()
        val pad = 16f * RuntimeEnvironment.getApplication().resources.displayMetrics.density
        val s = min((w - 2 * pad) / bw, (h - 2 * pad) / bh)
        return Triple(s, (w - bw * s) / 2f, (h - bh * s) / 2f)
    }

    @Test
    fun 손대지_않으면_가운데_가장_큰_원() {
        show(2000, 1000, "w411dp-h891dp-port")
        val (cx, cy, r) = confirm()
        assertEquals(1000f, cx, 1f)
        assertEquals(500f, cy, 1f)
        assertEquals(500f, r, 1f)
    }

    @Test
    fun 세로_사진_태블릿_가로() {
        show(900, 1600, "w1280dp-h800dp-land")
        val (cx, cy, r) = confirm()
        assertEquals(450f, cx, 1f)
        assertEquals(800f, cy, 1f)
        assertEquals(450f, r, 1f)
    }

    @Test
    fun 모서리를_안쪽으로_끌면_반대쪽_모서리를_두고_작아진다() {
        show(2000, 1000, "w411dp-h891dp-port")
        val (s, left, top) = fit(2000f, 1000f)
        // 오른쪽 아래 모서리: 사진 픽셀 (1500, 1000)
        val corner = Offset(left + 1500f * s, top + 1000f * s)
        compose.onNodeWithTag("circleCropArea").performTouchInput {
            down(corner); moveBy(Offset(-200f * s, -200f * s)); moveBy(Offset(-200f * s, -200f * s)); up()
        }
        compose.waitForIdle()
        val (cx, cy, r) = confirm()
        // 왼쪽 위(500, 0)는 그대로, 한 변 1000 → 600
        assertEquals(300f, r, 15f)
        assertEquals(800f, cx, 15f)
        assertEquals(300f, cy, 15f)
    }

    @Test
    fun 아주_작게_줄인_원도_옮길_수_있다() {
        show(2000, 1000, "w411dp-h891dp-port")
        val (s, left, top) = fit(2000f, 1000f)
        // 오른쪽 아래 모서리를 왼쪽 위 끝까지 끌어 가장 작게 줄인다
        compose.onNodeWithTag("circleCropArea").performTouchInput {
            down(Offset(left + 1500f * s, top + 1000f * s)); moveBy(Offset(-1000f * s, -1000f * s)); up()
        }
        compose.waitForIdle()
        val small = confirm()
        // 가장 작은 원은 화면에서 지름 48dp. 이 화면에서는 사진 픽셀로 반지름 약 127
        assertTrue("가장 작게 줄었다: ${small.third}", small.third in 100f..160f)

        // 작은 원 가운데를 끌면 크기는 그대로, 위치만 옮겨진다
        compose.onNodeWithTag("circleCropArea").performTouchInput {
            down(Offset(left + small.first * s, top + small.second * s)); moveBy(Offset(300f * s, 200f * s)); up()
        }
        compose.waitForIdle()
        val (cx, cy, r) = confirm()
        assertEquals(small.third, r, 0.5f)
        assertEquals(small.first + 300f, cx, 15f)
        assertEquals(small.second + 200f, cy, 15f)
    }

    @Test
    fun 원_안을_끌면_옮겨지고_사진_밖으로는_못_나간다() {
        show(2000, 1000, "w411dp-h891dp-port")
        val (s, left, top) = fit(2000f, 1000f)
        val center = Offset(left + 1000f * s, top + 500f * s)
        compose.onNodeWithTag("circleCropArea").performTouchInput {
            down(center); moveBy(Offset(150f * s, 0f)); moveBy(Offset(150f * s, 300f * s)); up()
        }
        compose.waitForIdle()
        val moved = confirm()
        assertEquals(1300f, moved.first, 15f)
        assertEquals("세로로는 사진 높이에 꽉 차서 못 움직인다", 500f, moved.second, 1f)
        assertEquals(500f, moved.third, 1f)

        // 오른쪽 끝을 넘겨 끌어도 원은 사진 안에 머문다
        compose.onNodeWithTag("circleCropArea").performTouchInput {
            val c = Offset(left + 1300f * s, top + 500f * s)
            down(c); moveBy(Offset(400f * s, 0f)); moveBy(Offset(400f * s, 0f)); up()
        }
        compose.waitForIdle()
        val (cx, _, r) = confirm()
        assertTrue(cx + r <= 2000f + 0.5f)
        assertEquals(1500f, cx, 1f)
    }
}
