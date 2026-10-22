package com.example.wowagoodsproject

import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.screen.search.hasWords
import com.example.wowagoodsproject.screen.search.searchWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 굿즈 검색 탭의 검색창 규칙 */
class SearchWordsTest {

    private val doll = GoodsEntity(
        goodsSeries = "2.3버전", goodsChara = "감심,금희", goodsCategory = "인형",
        goodsPrice = "59위안", goodsMemo = "Halloween 한정"
    )

    @Test
    fun 띄어쓰기로_낱말을_나누고_앞뒤_공백은_버린다() {
        assertEquals(listOf("감심", "인형"), searchWords("  감심   인형 "))
        assertEquals(emptyList<String>(), searchWords("   "))
    }

    @Test
    fun 검색어가_없으면_거르지_않는다() {
        assertTrue(doll.hasWords(emptyList()))
    }

    @Test
    fun 낱말이_모두_어딘가에_들어_있어야_맞는다() {
        assertTrue(doll.hasWords(searchWords("감심 인형")))
        assertTrue(doll.hasWords(searchWords("2.3 59위안")))
        assertTrue(doll.hasWords(searchWords("한정")))
        assertFalse(doll.hasWords(searchWords("감심 아크릴")))
    }

    @Test
    fun 대소문자는_가리지_않는다() {
        assertTrue(doll.hasWords(searchWords("halloween")))
        assertTrue(doll.hasWords(searchWords("HALLOWEEN")))
    }

    @Test
    fun 이차창작_굿즈도_같은_칸으로_찾는다() {
        val fan = FanGoodsEntity(fanGoodsSeries = "c107", fanGoodsChara = "플로로", fanGoodsCategory = "스티커", fanGoodsMemo = "부스 A-12")
        assertTrue(fan.hasWords(searchWords("c107 플로로")))
        assertTrue(fan.hasWords(searchWords("a-12")))
        assertFalse(fan.hasWords(searchWords("감심")))
    }
}
