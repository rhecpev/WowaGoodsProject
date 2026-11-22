package com.example.wowagoodsproject

import com.example.wowagoodsproject.screen.mypage.MAX_KEPT_GOODS_ID
import com.example.wowagoodsproject.screen.mypage.withKeptIds
import org.junit.Assert.assertEquals
import org.junit.Test

/** 백업을 불러올 때 굿즈 번호(PK)를 되살리는 규칙 */
class KeptIdsTest {
    private data class Row(val name: String, val id: Int)

    private fun kept(rows: List<Row>, taken: Set<Int> = emptySet()) =
        withKeptIds(rows, taken, { it.id }, { r, id -> r.copy(id = id) })

    @Test
    fun 번호가_맞으면_그대로_쓴다() {
        assertEquals(listOf(Row("a", 3), Row("b", 7)), kept(listOf(Row("a", 3), Row("b", 7))))
    }

    @Test
    fun 없거나_범위를_넘거나_겹치면_새_번호를_받고_뒤로_간다() {
        val out = kept(
            listOf(Row("없음", 0), Row("a", 5), Row("겹침", 5), Row("음수", -1), Row("큼", MAX_KEPT_GOODS_ID + 1), Row("b", 9))
        )
        assertEquals(listOf(Row("a", 5), Row("b", 9), Row("없음", 0), Row("겹침", 0), Row("음수", 0), Row("큼", 0)), out)
    }

    @Test
    fun 기기에_남은_굿즈와_겹치면_새_번호를_받는다() {
        assertEquals(listOf(Row("b", 4), Row("a", 0)), kept(listOf(Row("a", 2), Row("b", 4)), taken = setOf(2)))
    }
}
