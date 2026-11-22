package com.example.wowagoodsproject

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.wowagoodsproject.component.ReleaseNotes
import com.example.wowagoodsproject.component.WhatsNewDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 지금 버전 변경점 팝업을 비포·애프터 사진과 함께 그리고, 끝 페이지까지 넘겨 본다. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class WhatsNewDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private fun runPaging(qualifiers: String) {
        RuntimeEnvironment.setQualifiers(qualifiers)
        val sections = ReleaseNotes.forCurrentVersion()!!
        var dismissed = false
        compose.setContent { WhatsNewDialog(version = ReleaseNotes.currentVersion, sections = sections, onDismiss = { dismissed = true }) }

        // 첫 페이지: 비포·애프터 사진이 있으면 둘 다 보인다
        if (sections.first().beforeImage != null) {
            compose.onAllNodesWithContentDescription("이전 버전 화면")[0].assertIsDisplayed()
            compose.onAllNodesWithContentDescription("이번 버전 화면")[0].assertIsDisplayed()
        }
        repeat(sections.size - 1) {
            compose.onNodeWithText("다음").performClick()
            compose.waitForIdle()
        }
        compose.onNodeWithText("${sections.size} / ${sections.size}").assertIsDisplayed()
        compose.onNodeWithText("확인").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun 세로_휴대폰에서_사진과_함께_끝까지_넘긴다() = runPaging("w384dp-h832dp-port")

    @Test
    fun 낮은_가로_화면에서도_꺼지지_않는다() = runPaging("w891dp-h411dp-land")

    @Test
    fun 사진이_있는_섹션이_있다() {
        // 3.0 변경점의 비포·애프터 사진(변경점 화면에서 지난 버전으로 계속 보인다)
        val sections = ReleaseNotes.allVersions().first { it.first == "3.0" }.second
        assertEquals(5, sections.count { it.beforeImage != null || it.afterImage != null })
    }
}
