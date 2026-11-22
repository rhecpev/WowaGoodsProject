package com.example.wowagoodsproject

import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.goodsContentKey
import com.example.wowagoodsproject.component.sameContentAs
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.fan.withStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DuplicateGoodsTest {

    @Test
    fun `공백과 캐릭터 순서는 무시하고 같은 굿즈로 본다`() {
        assertEquals(
            goodsContentKey("코믹", "치사, 금희", "아크릴", "3000원", ""),
            goodsContentKey(" 코믹 ", "금희,치사", "아크릴 ", "3000원", " ")
        )
    }

    @Test
    fun `가격이나 메모가 다르면 다른 굿즈`() {
        val base = goodsContentKey("코믹", "치사", "아크릴", "3000원", "")
        assertNotEquals(base, goodsContentKey("코믹", "치사", "아크릴", "4000원", ""))
        assertNotEquals(base, goodsContentKey("코믹", "치사", "아크릴", "3000원", "B"))
    }

    @Test
    fun `사진과 보유 상태가 달라도 같은 굿즈로 찾는다`() {
        val a = FanGoodsEntity(fanGoodsId = 1, fanGoodsSeries = "코믹", fanGoodsChara = "치사", fanGoodsCategory = "아크릴", fanGoodsPrice = "3000원", fanGoodsImgPath = "/a.jpg")
            .withStatus(GoodsStatus.GOTTEN.name)
        val b = FanGoodsEntity(fanGoodsId = 2, fanGoodsSeries = "코믹", fanGoodsChara = "치사", fanGoodsCategory = "아크릴", fanGoodsPrice = "4000원")
        val key = goodsContentKey("코믹", "치사", "아크릴", "3000원", "")
        assertEquals(listOf(1), listOf(a, b).sameContentAs(key).map { it.fanGoodsId })
    }
}
