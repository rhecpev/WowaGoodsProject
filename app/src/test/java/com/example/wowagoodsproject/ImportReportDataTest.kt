package com.example.wowagoodsproject

import com.example.wowagoodsproject.screen.mypage.CharaChange
import com.example.wowagoodsproject.screen.mypage.ImportSnapshot
import com.example.wowagoodsproject.screen.mypage.ReportStat
import com.example.wowagoodsproject.screen.mypage.RestoreReport
import com.example.wowagoodsproject.screen.mypage.reportDataOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportReportDataTest {

    private val before = ImportSnapshot(
        officialGotten = 10, officialPending = 2, fanTotal = 3, fanGotten = 1, favorites = 2,
        betrayGoods = 5, betrayGotten = 1, betrayByGenre = mapOf("원신" to 5, "붕괴" to 0),
        gottenByChara = mapOf("치사" to 4, "금희" to 3, "장리" to 2)
    )
    private val after = before.copy(
        officialGotten = 14, fanTotal = 5, fanGotten = 2, favorites = 3,
        betrayGoods = 8, betrayGotten = 3, betrayByGenre = mapOf("원신" to 6, "붕괴" to 2),
        gottenByChara = mapOf("치사" to 8, "금희" to 3, "장리" to 1, "카르티시아" to 1),
        avatars = mapOf("치사" to "url")
    )

    @Test
    fun `불러온 항목만 Before → After 로, 캐릭터는 변화가 큰 순`() {
        val report = RestoreReport(12, 12, 5, 3, betrayGoods = 8, betrayGenres = listOf("원신", "붕괴"), before = before, after = after)
        val d = reportDataOf(report)!!
        assertEquals(ReportStat("보유 굿즈", 12, 19, "개"), d.summary[0])
        assertEquals(
            listOf("명조 공식 보유", "명조 공식 구매예정", "명조 비공식 굿즈", "명조 비공식 보유", "명조 선호 캐릭터", "배신 굿즈", "배신 굿즈 보유"),
            d.parts.map { it.label }
        )
        // 금희는 그대로라 빠진다. |증감| 큰 순, 같으면 After 큰 순, 그것도 같으면 이름순
        assertEquals(
            listOf(CharaChange("치사", "url", 4, 8), CharaChange("장리", "", 2, 1), CharaChange("카르티시아", "", 0, 1)),
            d.charas
        )
        assertEquals(listOf(5 to 6, 0 to 2), d.genres.map { it.before to it.after })
        assertTrue(d.genres.all { it.betray })
    }

    @Test
    fun `배신 굿즈만 불러오면 명조 항목과 캐릭터는 없다`() {
        val report = RestoreReport(null, 0, null, null, betrayGoods = 8, betrayGenres = listOf("원신"), before = before, after = after)
        val d = reportDataOf(report)!!
        assertEquals(listOf("배신 굿즈", "배신 굿즈 보유"), d.parts.map { it.label })
        assertTrue(d.charas.isEmpty())
        assertEquals(listOf("보유 굿즈", "구매예정"), d.summary.map { it.label })
    }

    @Test
    fun `앞뒤 상태가 없으면 null`() {
        assertNull(reportDataOf(RestoreReport(1, 1, null, null)))
    }
}
