package com.example.wowagoodsproject

import com.example.wowagoodsproject.component.CATEGORY_SET
import com.example.wowagoodsproject.component.categoryCoverImages
import com.example.wowagoodsproject.component.seriesCoverImages
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.official.GoodsEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/** 카테고리 필터 그리드의 대표 사진 고르기 */
class CategoryCoverTest {

    private fun goods(category: String, chara: String, url: String) =
        GoodsEntity(goodsCategory = category, goodsChara = chara, goodsUrl = url)

    @Test
    fun 선호_1순위_캐릭터의_굿즈_사진을_쓴다() {
        val list = listOf(
            goods("키링", "금희", "k-jinhsi"),
            goods("키링", "감심", "k-baizhi"),
            goods("키링", "장리", "k-changli")
        )
        val covers = categoryCoverImages(listOf("키링"), list, listOf("장리", "금희"))
        assertEquals("k-changli", covers["키링"])
    }

    @Test
    fun 선호_1순위_굿즈가_없으면_다음_순위_캐릭터로_넘어간다() {
        val list = listOf(
            goods("인형", "감심", "d-baizhi"),
            goods("인형", "금희", "d-jinhsi")
        )
        // 장리 인형은 없다 → 2순위 금희
        val covers = categoryCoverImages(listOf("인형"), list, listOf("장리", "금희", "감심"))
        assertEquals("d-jinhsi", covers["인형"])
    }

    @Test
    fun 여러_캐릭터가_함께_있는_굿즈도_그_캐릭터_굿즈로_본다() {
        val list = listOf(
            goods("포스터", "감심", "p-baizhi"),
            goods("포스터", "금희,장리", "p-duo")
        )
        val covers = categoryCoverImages(listOf("포스터"), list, listOf("장리"))
        assertEquals("p-duo", covers["포스터"])
    }

    @Test
    fun 글자_일부만_같은_다른_캐릭터는_고르지_않는다() {
        val list = listOf(
            goods("키링", "양양현령", "k-other"),
            goods("키링", "감심", "k-baizhi")
        )
        // '양양' 선호지만 '양양현령' 굿즈는 아니다 → 선호 굿즈 없음 → 아무 굿즈(맨 앞)
        val covers = categoryCoverImages(listOf("키링"), list, listOf("양양"))
        assertEquals("k-other", covers["키링"])
    }

    @Test
    fun 시리즈_대표_사진도_같은_규칙으로_고르고_2차창작_굿즈도_쓴다() {
        val fan = FanGoodsEntity(
            fanGoodsSeries = " 코믹월드 ", fanGoodsChara = "장리", fanGoodsCategory = "키링",
            fanGoodsImgPath = "/files/fan_changli.jpg"
        )
        val list = listOf(
            goods("키링", "감심", "official-baizhi").copy(goodsSeries = "코믹월드"),
            fan
        )
        // 장리가 1순위 → 공식(감심)보다 2차창작 장리 굿즈 사진. 시리즈 이름 앞뒤 공백은 무시한다.
        val covers = seriesCoverImages(listOf("코믹월드"), list, listOf("장리"))
        assertEquals("/files/fan_changli.jpg", covers["코믹월드"])
    }

    @Test
    fun 선호_캐릭터_굿즈가_없으면_그_카테고리의_아무_굿즈_사진을_쓰고_사진_없는_굿즈와_세트는_건너뛴다() {
        val list = listOf(
            goods("아크릴", "감심", ""),          // 사진 없음
            goods(CATEGORY_SET, "감심", "set"),   // 세트는 카테고리 칸이 아니다
            goods("아크릴", "카멜리아", "a-camellya")
        )
        val covers = categoryCoverImages(listOf("아크릴", "뱃지"), list, listOf("금희"))
        assertEquals(mapOf("아크릴" to "a-camellya"), covers) // 뱃지는 굿즈가 없어 사진 없음
    }
}
