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
}
