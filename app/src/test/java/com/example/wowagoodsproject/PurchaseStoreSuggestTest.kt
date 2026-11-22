package com.example.wowagoodsproject

import com.example.wowagoodsproject.component.PendingInfoFilter
import com.example.wowagoodsproject.component.matchStores
import com.example.wowagoodsproject.component.matchRanges
import com.example.wowagoodsproject.component.mergeStores
import com.example.wowagoodsproject.component.rankStores
import org.junit.Assert.assertEquals
import org.junit.Test

class PurchaseStoreSuggestTest {

    @Test
    fun `구입처는 많이 쓴 순, 같으면 이름순으로 겹치지 않게`() {
        val ranked = rankStores(listOf("알리", " 알리 ", "", "홍대 팝업", "타오바오", "타오바오", "타오바오", "  "))
        assertEquals(listOf("타오바오", "알리", "홍대 팝업"), ranked)
    }

    @Test
    fun `적은 글자가 들어간 것만, 똑같이 적었으면 뺀다`() {
        val stores = listOf("타오바오", "알리익스프레스", "알리", "홍대 팝업")
        assertEquals(stores, matchStores(stores, ""))
        assertEquals(listOf("알리익스프레스"), matchStores(stores, "알리"))
        assertEquals(listOf("홍대 팝업"), matchStores(stores, "팝업"))
        assertEquals(emptyList<String>(), matchStores(stores, "없는가게"))
    }

    @Test
    fun `굿즈에서 사라진 구입처도 기억해 둔 것으로 뒤에 남는다`() {
        val merged = mergeStores(listOf("알리", "타오바오", "타오바오"), setOf("홍대 팝업", "알리", " 번개장터 "))
        assertEquals(listOf("타오바오", "알리", "번개장터", "홍대 팝업"), merged)
    }

    @Test
    fun `구매예정 필터는 배송 중 · 배송 전이 앞`() {
        assertEquals(
            listOf("배송 중", "배송 전", "구입처 미정", "구매일 미정", "수령일 미정"),
            PendingInfoFilter.entries.map { it.label }
        )
    }

    @Test
    fun `겹치는 글자 구간을 모두 찾는다(대소문자 무시)`() {
        assertEquals(listOf(0..1), matchRanges("알리익스프레스", "알리"))
        assertEquals(listOf(0..2, 4..6), matchRanges("abc-ABC", "abc"))
        assertEquals(emptyList<IntRange>(), matchRanges("타오바오", " "))
        assertEquals(emptyList<IntRange>(), matchRanges("타오바오", "알리"))
    }
}
