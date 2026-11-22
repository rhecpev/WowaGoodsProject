package com.example.wowagoodsproject

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.wowagoodsproject.screen.mypage.ReleaseNotesScreen
import com.example.wowagoodsproject.ui.theme.WowaGoodsProjectTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 변경점 화면: 지금 버전은 펼쳐져 있고, 지난 버전은 머리 줄을 눌러 펼친다. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ReleaseNotesScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun exists(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun 지금_버전은_펼쳐져_있고_지난_버전은_눌러서_본다() {
        var back = false
        compose.setContent { WowaGoodsProjectTheme { ReleaseNotesScreen(onNavigateBack = { back = true }) } }
        compose.waitForIdle()
        assertTrue("지금 버전 표시", exists("v${BuildConfig.VERSION_NAME}") && exists("지금 버전"))
        assertTrue("지금 버전 내용이 펼쳐져 있다", exists("배신 굿즈 (다른 장르)"))
        // 3.0 내용은 접혀 있다가 누르면 보인다
        assertFalse(exists("둥근 상자를 걷어내고"))
        // 지금 버전 내용이 길어 3.0 줄은 아래에 있다. 목록을 그 줄까지 내린다.
        compose.onNode(androidx.compose.ui.test.hasScrollToNodeAction())
            .performScrollToNode(androidx.compose.ui.test.hasContentDescription("v3.0 펼치기"))
        compose.onNodeWithContentDescription("v3.0 펼치기").performClick()
        compose.waitForIdle()
        compose.onNode(androidx.compose.ui.test.hasScrollToNodeAction())
            .performScrollToNode(androidx.compose.ui.test.hasText("둥근 상자를 걷어내고", substring = true))
        assertTrue("지난 버전 펼치기", exists("둥근 상자를 걷어내고"))
        assertFalse(back)
    }
}
