package com.example.wowagoodsproject

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun 높은_버전만_새_버전이다() {
        assertTrue(isNewerVersion("2.6", "2.5"))
        assertFalse(isNewerVersion("2.5", "2.5"))
        // 개발 중인 버전이 배포된 버전보다 높으면 알리지 않는다
        assertFalse(isNewerVersion("2.5", "2.6"))
    }

    @Test
    fun 자리수는_숫자로_비교한다() {
        assertTrue(isNewerVersion("2.10", "2.9"))
        assertFalse(isNewerVersion("2.9", "2.10"))
        assertTrue(isNewerVersion("3.0", "2.99"))
    }

    @Test
    fun 빈_자리는_0으로_본다() {
        assertFalse(isNewerVersion("2.5.0", "2.5"))
        assertTrue(isNewerVersion("2.5.1", "2.5"))
    }

    @Test
    fun 앞의_v는_떼고_본다() {
        assertFalse(isNewerVersion("v2.5", "2.5"))
        assertTrue(isNewerVersion("v2.6", "2.5"))
    }

    @Test
    fun 숫자가_아니면_다르기만_해도_새_버전이다() {
        assertTrue(isNewerVersion("2.6-beta", "2.5"))
        assertFalse(isNewerVersion("2.5-beta", "2.5-beta"))
    }
}
