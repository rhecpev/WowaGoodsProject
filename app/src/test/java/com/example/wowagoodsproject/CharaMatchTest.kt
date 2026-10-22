package com.example.wowagoodsproject

import com.example.wowagoodsproject.component.filterGoodsList
import com.example.wowagoodsproject.component.hasChara
import com.example.wowagoodsproject.db.official.GoodsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 캐릭터 이름은 한 명 단위로 비교한다. 실제 데이터에 "양양"/"양양현령", "아브"/"아브대마왕" 이 있다. */
class CharaMatchTest {

    @Test
    fun 이름의_일부만_같은_다른_캐릭터는_아니다() {
        assertFalse(hasChara("양양현령", "양양"))
        assertFalse(hasChara("아브대마왕,감심", "아브"))
    }

    @Test
    fun 여러_명_중_한_명이면_맞다() {
        assertTrue(hasChara("감심,양양", "양양"))
        assertTrue(hasChara("감심, 양양 ,금희", "양양"))
        assertTrue(hasChara("양양", " 양양 "))
    }

    @Test
    fun 빈_이름은_맞지_않는다() {
        assertFalse(hasChara("감심", ""))
        assertFalse(hasChara("", ""))
        assertFalse(hasChara(",,", " "))
    }

    @Test
    fun 목록_필터도_한_명_단위로_거른다() {
        val list = listOf(
            GoodsEntity(goodsId = 1, goodsChara = "양양", goodsCategory = "인형"),
            GoodsEntity(goodsId = 2, goodsChara = "양양현령", goodsCategory = "인형"),
            GoodsEntity(goodsId = 3, goodsChara = "감심,양양", goodsCategory = "키링")
        )
        assertEquals(listOf(1, 3), filterGoodsList(list, charaFilter = "양양").map { it.goodsId })
    }
}
