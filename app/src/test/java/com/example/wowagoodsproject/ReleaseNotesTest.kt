package com.example.wowagoodsproject

import com.example.wowagoodsproject.component.ReleaseNotes
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesTest {
    @Test
    fun 지금_버전의_변경점이_적혀_있다() {
        // 버전을 올리고 변경점을 빠뜨리면 업데이트 안내와 마이페이지 변경점이 뜨지 않는다.
        val notes = ReleaseNotes.forCurrentVersion()
        assertNotNull("v${BuildConfig.VERSION_NAME} 변경점이 없다", notes)
        assertTrue(notes!!.all { it.items.isNotEmpty() })
    }

    @Test
    fun 변경점_화면은_최신_버전부터_보여_준다() {
        val versions = com.example.wowagoodsproject.component.ReleaseNotes.allVersions().map { it.first }
        org.junit.Assert.assertEquals(BuildConfig.VERSION_NAME, versions.first())
        // 숫자로 견준다(2.10 이 2.9 보다 새 버전)
        org.junit.Assert.assertTrue(com.example.wowagoodsproject.component.ReleaseNotes.compareVersions("2.10", "2.9") > 0)
        org.junit.Assert.assertEquals(versions, versions.sortedWith { a, b -> com.example.wowagoodsproject.component.ReleaseNotes.compareVersions(b, a) })
    }
}
