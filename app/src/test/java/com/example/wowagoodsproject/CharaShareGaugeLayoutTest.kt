package com.example.wowagoodsproject

import android.app.Application
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import com.example.wowagoodsproject.component.CharaShare
import com.example.wowagoodsproject.component.CharaShareGauge
import com.example.wowagoodsproject.component.CharaShares
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 태블릿·가로 화면 마이페이지는 두 칸을 같은 높이로 맞추려고 IntrinsicSize.Min 으로 미리 잰다.
 * 그 안에 캐릭터 비율 게이지를 넣어도 꺼지지 않아야 한다(BoxWithConstraints 를 쓰던 때는 여기서 꺼졌다).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w1280dp-h800dp-land")
class CharaShareGaugeLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun 높이를_미리_재는_줄_안에서도_게이지가_그려진다() {
        val shares = CharaShares(
            top = listOf(CharaShare("장리", 3), CharaShare("금희", 2)),
            othersCount = 4,
            total = 9,
            othersNames = listOf("감심", "카멜리아")
        )
        compose.setContent {
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                CharaShareGauge(
                    shares = shares,
                    colors = mapOf("장리" to Color.Red, "금희" to Color.Blue),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        compose.onNodeWithText("장리").assertIsDisplayed()
        // '누구 외 N명' 은 이름과 '외 N명' 을 나눠 그려, 칸이 좁아도 '외 N명' 은 잘리지 않는다.
        compose.onNodeWithText("감심").assertIsDisplayed()
        compose.onNodeWithText("외 1명", substring = true).assertIsDisplayed()
    }
}
