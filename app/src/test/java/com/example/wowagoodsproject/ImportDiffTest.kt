package com.example.wowagoodsproject

import com.example.wowagoodsproject.screen.mypage.DiffRow
import com.example.wowagoodsproject.screen.mypage.FavRow
import com.example.wowagoodsproject.screen.mypage.countByChara
import com.example.wowagoodsproject.screen.mypage.diffRows
import com.example.wowagoodsproject.screen.mypage.favRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.graphics.luminance
import org.junit.Test

class ImportDiffTest {

    @Test
    fun `여러 캐릭터 굿즈는 캐릭터마다 한 번씩 센다`() {
        val counts = countByChara(listOf("치사, 카르티시아", "치사", "", "치사,치사")) { it }
        assertEquals(mapOf("치사" to 3, "카르티시아" to 1), counts)
    }

    @Test
    fun `달라진 캐릭터만 변화가 큰 순서로 남긴다`() {
        val rows = diffRows(
            before = mapOf("치사" to 2, "카르티시아" to 5, "금희" to 1),
            after = mapOf("치사" to 5, "카르티시아" to 5, "장리" to 1)
        ) { "" }
        assertEquals(
            listOf(DiffRow("치사", "", 2, 5), DiffRow("금희", "", 1, 0), DiffRow("장리", "", 0, 1)),
            rows
        )
        assertEquals(listOf(3, -1, 1), rows.map { it.diff })
    }

    @Test
    fun `수량만 바뀌어도 바뀌는 굿즈, 없어지는 굿즈는 불러온 뒤 상태가 없다`() {
        val gotten = com.example.wowagoodsproject.component.GoodsStatus.GOTTEN
        fun g(name: String, qty: Int) = com.example.wowagoodsproject.screen.mypage.ReviewGoods(
            official = false, series = name, chara = "", category = "", price = "", memo = "", status = gotten, quantity = qty
        )
        // 지금: (이름, 수량)
        val now = listOf("a" to 3, "b" to 2, "c" to 1)
        val rows = com.example.wowagoodsproject.screen.mypage.withChanges(
            file = listOf(g("a", 1) to "a", g("b", 2) to "b"),
            now = now,
            key = { it.first },
            status = { gotten },
            quantity = { it.second },
            gone = { g(it.first, it.second).copy(change = gotten to null) }
        )
        val a = rows.first { it.series == "a" }
        assertEquals(gotten to gotten, a.change)
        assertEquals(3, a.beforeQuantity)
        assertEquals(null, rows.first { it.series == "b" }.change)
        val c = rows.first { it.series == "c" }
        assertEquals(null, c.afterStatus)
    }

    @Test
    fun `선호는 파일 순서대로, 빠지는 캐릭터는 뒤에`() {
        val rows = favRows(before = listOf("금희", "치사"), after = listOf("치사", "장리")) { "" }
        assertEquals(
            listOf(FavRow("치사", "", true, true), FavRow("장리", "", false, true), FavRow("금희", "", true, false)),
            rows
        )
    }

    @Test
    fun `연한 배신 굿즈 색은 바탕에서 읽힐 만큼 진하게 맞춘다`() {
        val white = androidx.compose.ui.graphics.Color.White
        val ink = androidx.compose.ui.graphics.Color(0xFF1A1C1E)
        fun contrast(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Float {
            val la = a.luminance(); val lb = b.luminance()
            return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
        }
        // 밝은 테마의 연한 하늘색(배경용 색)
        val pale = androidx.compose.ui.graphics.Color(0xFFBEE3F0)
        val fixed = com.example.wowagoodsproject.screen.mypage.readableOn(pale, white, ink)
        assertTrue(contrast(fixed, white) >= 4.5f)
        // 이미 진한 색은 그대로
        val deep = androidx.compose.ui.graphics.Color(0xFF7A2E5C)
        assertEquals(deep, com.example.wowagoodsproject.screen.mypage.readableOn(deep, white, ink))
    }
}
