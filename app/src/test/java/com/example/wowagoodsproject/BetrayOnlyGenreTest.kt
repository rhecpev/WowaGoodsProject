package com.example.wowagoodsproject

import com.example.wowagoodsproject.screen.mypage.BetrayBackup
import com.example.wowagoodsproject.screen.mypage.BetrayBackup.BetrayFavoriteBackup
import com.example.wowagoodsproject.screen.mypage.BetrayBackup.BetrayGenreBackup
import com.example.wowagoodsproject.screen.mypage.BetrayBackup.BetrayGoodsBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BetrayOnlyGenreTest {

    private val backup = BetrayBackup(
        genres = listOf(BetrayGenreBackup(1, "원신"), BetrayGenreBackup(2, " 붕괴 "), null),
        goods = listOf(
            BetrayGoodsBackup(genreId = 1, goodsSeries = "a"),
            BetrayGoodsBackup(genreId = 2, goodsSeries = "b"),
            BetrayGoodsBackup(genreId = 2, goodsSeries = "c"),
            null
        ),
        favorites = listOf(BetrayFavoriteBackup(1, "x"), BetrayFavoriteBackup(2, "y"))
    )

    @Test
    fun `이름이 같은 장르만 남긴다(공백·대소문자 무시)`() {
        val only = backup.onlyGenre("붕괴")!!
        assertEquals(listOf(2), only.genres!!.map { it!!.genreId })
        assertEquals(listOf("b", "c"), only.goods!!.map { it!!.goodsSeries })
        assertEquals(listOf("y"), only.favorites!!.map { it!!.charaNm })
        assertTrue(only.partial == true)
    }

    @Test
    fun `파일에 없는 장르면 null`() {
        assertNull(backup.onlyGenre("명조"))
    }
}
