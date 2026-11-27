package com.example.wowagoodsproject.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 베이스라인 프로필을 만든다: 앱을 켜고 자주 쓰는 동작을 해 보며 그때 쓰인 코드를 모은다.
 * 동작: 시작 → 공식 시리즈 하나 열기 → 그리드/리스트 몇 번 전환·스크롤 → 마이페이지 → 보유 굿즈 목록 전환.
 * 처음 켜면 굿즈 데이터를 받으므로(인터넷) 목록이 뜰 때까지 기다린다.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        // 알림 권한 창이 화면을 가리지 않게 미리 허락해 둔다(Android 13+)
        device.executeShellCommand("pm grant $PACKAGE android.permission.POST_NOTIFICATIONS")
        pressHome()
        startActivityAndWait()
        dismissDialogs(device)

        // 공식 탭: 시리즈 목록이 뜰 때까지(처음엔 데이터를 받는다)
        device.findObject(By.text("공식"))?.click()
        device.wait(Until.hasObject(By.scrollable(true)), 90_000)
        dismissDialogs(device)
        // 첫 시리즈 열기
        device.findObject(By.scrollable(true))?.children?.firstOrNull()?.click()
        device.waitForIdle()
        toggleViewMode(device, times = 4)

        // 마이페이지 → 보유 굿즈 목록
        device.pressBack()
        device.findObject(By.text("마이페이지"))?.click()
        device.waitForIdle()
        device.findObject(By.text("목록 보기"))?.click()
        device.waitForIdle()
        toggleViewMode(device, times = 2)
    }

    /** 오른쪽 위 '보기 방식 전환'을 눌러 그리드/리스트를 바꾸고, 바꿀 때마다 조금 스크롤한다 */
    private fun toggleViewMode(device: UiDevice, times: Int) {
        repeat(times) {
            device.findObject(By.desc("보기 방식 전환"))?.click() ?: return
            Thread.sleep(800) // 애니메이션이 끝날 때까지
            device.findObject(By.scrollable(true))?.let {
                it.scroll(Direction.DOWN, 0.6f)
                it.scroll(Direction.UP, 0.6f)
            }
        }
    }

    /** 첫 실행에 뜨는 창(변경점 안내 등)을 닫는다 */
    private fun dismissDialogs(device: UiDevice) {
        repeat(3) {
            val close = device.findObject(By.text("닫기")) ?: device.findObject(By.text("확인")) ?: return
            close.click()
            device.waitForIdle()
        }
    }

    private companion object {
        const val PACKAGE = "com.example.wowagoodsproject"
    }
}
