package com.example.wowagoodsproject

import com.example.wowagoodsproject.component.argbToHsl
import com.example.wowagoodsproject.component.assignDistinctColors
import com.example.wowagoodsproject.component.charaShares
import com.example.wowagoodsproject.component.tooClose
import com.example.wowagoodsproject.db.official.GoodsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** 마이페이지 보유 굿즈의 캐릭터 비율과 캐릭터 색 배정 */
class CharaShareTest {

    private fun g(chara: String) = GoodsEntity(goodsChara = chara)

    @Test
    fun 여러_캐릭터_굿즈는_캐릭터마다_한_번씩_세고_상위만_따로_나머지는_기타() {
        val goods = listOf(g("장리"), g("장리,금희"), g("금희"), g("감심"), g("카멜리아"), g(" 장리 "))
        val shares = charaShares(goods, favoritesInOrder = emptyList(), topCount = 2)
        assertEquals(listOf("장리" to 3, "금희" to 2), shares.top.map { it.name to it.count })
        assertEquals(2, shares.othersCount) // 감심 1 + 카멜리아 1
        assertEquals(7, shares.total)
    }

    @Test
    fun 선호만_따로_두면_선호가_아닌_캐릭터는_모두_기타() {
        val goods = listOf(g("장리"), g("장리,금희"), g("감심"), g("감심"), g("감심"), g("카멜리아"))
        val shares = charaShares(goods, favoritesInOrder = listOf("금희", "장리"), onlyFavorites = true)
        // 선호(장리 2, 금희 1)만 많은 순으로 따로, 감심 3 + 카멜리아 1 = 기타 4
        assertEquals(listOf("장리" to 2, "금희" to 1), shares.top.map { it.name to it.count })
        assertEquals(4, shares.othersCount)
        assertEquals("감심 외 1명", shares.othersLabel) // 가장 많은 감심 + 카멜리아
    }

    @Test
    fun 선호가_하나도_없으면_상위_캐릭터로_돌아간다() {
        val shares = charaShares(listOf(g("감심"), g("감심"), g("장리")), emptyList(), topCount = 1, onlyFavorites = true)
        assertEquals(listOf("감심" to 2), shares.top.map { it.name to it.count })
        assertEquals(1, shares.othersCount)
        assertEquals("장리", shares.othersLabel) // 한 명뿐이면 이름만
    }

    @Test
    fun 수집률은_세트를_빼고_캐릭터마다_보유만_센다() {
        val goods = listOf(
            GoodsEntity(goodsChara = "장리", goodsStatus = "GOTTEN"),
            GoodsEntity(goodsChara = "장리,금희", goodsStatus = "PENDING"),   // 구매예정은 보유 아님
            GoodsEntity(goodsChara = "장리", goodsStatus = "NOT_GOTTEN"),
            GoodsEntity(goodsChara = "장리", goodsCategory = "세트", goodsStatus = "GOTTEN") // 세트는 안 셈
        )
        val c = com.example.wowagoodsproject.component.charaCollection(goods)
        assertEquals(1 to 3, c["장리"])
        assertEquals(0 to 1, c["금희"])
    }

    @Test
    fun 끌어서_바꾼_순서는_보이던_캐릭터_자리끼리만_바뀐다() {
        val full = listOf("장리", "금희", "감심", "카멜리아", "상리요")
        // 검색으로 금희·카멜리아·상리요만 보이다가 상리요를 맨 위로 끌었다
        val merged = com.example.wowagoodsproject.component.mergeFavoriteOrder(full, listOf("상리요", "금희", "카멜리아"))
        // 안 보이던 장리(1)·감심(3)은 제자리, 보이던 자리(2·4·5)에 새 순서
        assertEquals(listOf("장리", "상리요", "감심", "금희", "카멜리아"), merged)
        // 다 보일 때는 새 순서 그대로
        assertEquals(listOf("금희", "장리"), com.example.wowagoodsproject.component.mergeFavoriteOrder(listOf("장리", "금희"), listOf("금희", "장리")))
    }

    @Test
    fun 같은_수면_선호_우선순위가_앞이다() {
        val shares = charaShares(listOf(g("감심"), g("금희")), favoritesInOrder = listOf("금희"), topCount = 1)
        assertEquals("금희", shares.top.single().name)
    }

    @Test
    fun 사진_색이_겹치면_다음_후보로_바꿔_서로_헷갈리지_않는다() {
        val red = 0xFFD03030.toInt()
        val sameRed = 0xFFC82828.toInt()
        val blue = 0xFF3050D0.toInt()
        val colors = assignDistinctColors(
            order = listOf("장리", "카멜리아"),
            candidates = mapOf("장리" to listOf(red), "카멜리아" to listOf(sameRed, blue)),
            darkTheme = false
        )
        val a = argbToHsl(colors.getValue("장리"))
        val b = argbToHsl(colors.getValue("카멜리아"))
        assertFalse(tooClose(a, b))
        // 장리는 첫 후보(빨강), 카멜리아는 빨강이 겹쳐 둘째 후보(파랑) 계열
        assertEquals(true, b.h in 200f..260f)
    }

    @Test
    fun 후보가_모두_겹치거나_없어도_서로_다른_색을_받는다() {
        val red = 0xFFD03030.toInt()
        val order = listOf("가", "나", "다", "라", "마", "바")
        val colors = assignDistinctColors(
            order = order,
            candidates = order.associateWith { listOf(red) } + mapOf("바" to emptyList()),
            darkTheme = true
        )
        val hsl = order.map { argbToHsl(colors.getValue(it)) }
        for (i in hsl.indices) for (j in i + 1 until hsl.size) {
            assertFalse("${order[i]} 와 ${order[j]} 가 가깝다", tooClose(hsl[i], hsl[j]))
        }
    }
}
