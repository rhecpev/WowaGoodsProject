package com.example.wowagoodsproject

import com.example.wowagoodsproject.component.joinPrice
import com.example.wowagoodsproject.component.splitPrice
import org.junit.Assert.assertEquals
import org.junit.Test

class PriceUnitTest {

    @Test
    fun `저장된 가격을 금액과 단위로 나눈다`() {
        assertEquals("59" to "위안", splitPrice("59위안"))
        assertEquals("3,000" to "원", splitPrice(" 3,000 원 "))
        assertEquals("1500" to "엔", splitPrice("1500엔"))
        // 세 단위가 아니면 적힌 그대로, 단위 없음
        assertEquals("\$5" to null, splitPrice("\$5"))
        assertEquals("무료" to null, splitPrice("무료"))
        // 단위 글자만 있으면 금액이 아니다
        assertEquals("원" to null, splitPrice("원"))
        assertEquals("" to null, splitPrice(""))
    }

    @Test
    fun `금액과 단위를 합친다(금액이 비면 빈 값)`() {
        assertEquals("59위안", joinPrice("59", "위안"))
        assertEquals("3000원", joinPrice(" 3000 ", "원"))
        assertEquals("", joinPrice("  ", "엔"))
        assertEquals("\$5", joinPrice("\$5", null))
    }

    @Test
    fun `나눴다 합치면 원래 값`() {
        listOf("59위안", "3000원", "1500엔", "\$5").forEach { p ->
            val (a, u) = splitPrice(p)
            assertEquals(p, joinPrice(a, u))
        }
    }
}
