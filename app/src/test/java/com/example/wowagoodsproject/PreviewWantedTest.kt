package com.example.wowagoodsproject

import com.example.wowagoodsproject.screen.mypage.BetrayBackup
import com.example.wowagoodsproject.screen.mypage.BetrayBackup.BetrayCharaBackup
import com.example.wowagoodsproject.screen.mypage.BetrayBackup.BetrayGenreBackup
import com.example.wowagoodsproject.screen.mypage.BetrayBackup.BetrayGoodsBackup
import com.example.wowagoodsproject.screen.mypage.BetrayBackup.BetraySeriesBackup
import com.example.wowagoodsproject.screen.mypage.FanGoodsBackup
import com.example.wowagoodsproject.screen.mypage.ImportPreviewer
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 확인 화면에 보이는 사진만 미리보기로 줄인다. */
class PreviewWantedTest {

    private val gson = Gson()
    private val texts = mapOf(
        "fan_goods.json" to gson.toJson(listOf(FanGoodsBackup(fanGoodsImgPath = "/data/x/fan1.jpg"), FanGoodsBackup(fanGoodsImgPath = ""))),
        "betray_goods.json" to gson.toJson(
            BetrayBackup(
                genres = listOf(BetrayGenreBackup(1, "원신", genreImgPath = "/g/bg1.png"), BetrayGenreBackup(2, "붕괴")),
                goods = listOf(BetrayGoodsBackup(genreId = 1, goodsImgPath = "/a/g1.jpg"), BetrayGoodsBackup(genreId = 2, goodsImgPath = "/a/g2.jpg")),
                charas = listOf(BetrayCharaBackup(1, "x", "/c/c1.jpg"), BetrayCharaBackup(2, "y", "/c/c2.jpg")),
                series = listOf(BetraySeriesBackup(1, "s", "/s/s1.jpg"))
            )
        )
    )

    @Test
    fun `전체면 비공식과 모든 장르의 굿즈·캐릭터 사진`() {
        assertEquals(setOf("fan1.jpg", "g1.jpg", "g2.jpg", "c1.jpg", "c2.jpg"), ImportPreviewer.previewWanted(texts, onlyGenre = null))
    }

    @Test
    fun `장르 하나만 불러오면 그 장르 사진만(비공식·다른 장르 제외)`() {
        assertEquals(setOf("g2.jpg", "c2.jpg"), ImportPreviewer.previewWanted(texts, onlyGenre = "붕괴"))
    }

    @Test
    fun `목록을 아직 못 읽었으면 null(모두 줄인다)`() {
        assertNull(ImportPreviewer.previewWanted(emptyMap(), onlyGenre = null))
    }
}
