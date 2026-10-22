package com.example.wowagoodsproject

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.wowagoodsproject.db.changed.ChangedGoodsEntity
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.screen.mypage.ChangedGoodsScreen
import com.example.wowagoodsproject.screen.mypage.ChangedGoodsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 변경된 굿즈 화면을 실제로 그려 눌러 본다: 처리/미처리 필터, 카드 → 다이얼로그 → 처리 표시, 체크완료 삭제. */
@RunWith(RobolectricTestRunner::class)
// 카드 세 장이 한 화면에 다 그려지도록 화면을 길게 둔다(LazyColumn 은 보이는 것만 그린다).
@Config(application = Application::class, sdk = [35], qualifiers = "w411dp-h2000dp")
class ChangedGoodsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val changedDao get() = App.changedGoodsDatabase.changedGoodsDao()

    @Before
    fun setUp() = runBlocking {
        TestDb.setUp()
        listOf("감심" to 1, "금희" to 2, "장리" to 3).forEach { (chara, uid) ->
            App.database.goodsDao().insert(
                GoodsEntity(goodsUid = uid, goodsSeries = "1.0버전", goodsChara = chara, goodsCategory = "인형",
                    goodsPrice = "69위안", goodsMemo = "단품", goodsStatus = "PENDING")
            )
            changedDao.insert(
                ChangedGoodsEntity(changedTime = uid.toLong(), oldSeries = "1.0버전", oldChara = chara, oldCategory = "인형",
                    oldPrice = "59위안", oldStatus = "PENDING", newSeries = "1.0버전", newChara = chara, newCategory = "인형",
                    newPrice = "69위안", newMemo = "단품", newUid = uid, changedDetail = "가격: 59위안 → 69위안",
                    statusKept = true, isChecked = chara == "장리")
            )
        }
    }

    @After
    fun tearDown() = TestDb.tearDown()

    private fun show() = compose.setContent { ChangedGoodsScreen(viewModel = ChangedGoodsViewModel()) }

    /**
     * DB 쓰기는 다른 스레드에서 끝난 뒤 메인 스레드로 돌아온다. Robolectric 의 메인 스레드는 직접 돌려 줘야 해서
     * waitUntil 만으로는 진행되지 않는다. 메인 스레드를 돌리며 [condition] 이 맞을 때까지 기다린다.
     */
    private fun eventually(condition: () -> Boolean) {
        repeat(100) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("조건이 끝내 맞지 않았다")
    }

    private fun shown(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun 처음엔_미처리만_보이고_필터로_바꿀_수_있다() {
        show()
        eventually { shown("감심") }
        compose.onAllNodesWithText("금희").assertCountEquals(1)
        compose.onAllNodesWithText("장리").assertCountEquals(0) // 처리된 기록

        compose.onNodeWithText("처리").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("장리").assertCountEquals(1)
        compose.onAllNodesWithText("감심").assertCountEquals(0)

        compose.onNodeWithText("전체").performClick()
        compose.waitForIdle()
        listOf("감심", "금희", "장리").forEach { compose.onAllNodesWithText(it).assertCountEquals(1) }
    }

    @Test
    fun 카드를_눌러_상태를_바꾸면_처리로_넘어가고_지금_상태로_다시_그린다() {
        show()
        eventually { shown("감심") }
        compose.onNodeWithText("감심").performClick()
        eventually { shown("보유 상태") }
        compose.onNodeWithText("보유").performClick() // 다이얼로그의 보유 버튼

        eventually { runBlocking { changedDao.getAllFlow().first().single { it.newChara == "감심" }.isChecked } }
        eventually { !shown("감심") } // 미처리 목록에서 빠진다
        assertEquals("GOTTEN", runBlocking { App.database.goodsDao().getAll().single { it.goodsChara == "감심" }.goodsStatus })

        compose.onNodeWithText("처리").performClick()
        eventually { shown("감심") }
        compose.onAllNodesWithText("보유 중").assertCountEquals(1) // 카드가 지금 상태로 그려진다
    }

    @Test
    fun 체크완료_삭제는_처리한_기록만_지운다() {
        show()
        eventually { shown("체크완료 삭제") }
        compose.onNodeWithText("체크완료 삭제").performClick()
        compose.onNodeWithText("삭제").performClick() // 확인 창
        eventually { runBlocking { changedDao.getAllFlow().first().size == 2 } }
        assertEquals(setOf("감심", "금희"), runBlocking { changedDao.getAllFlow().first().map { it.newChara }.toSet() })
    }
}
