package com.example.wowagoodsproject

import com.example.wowagoodsproject.component.CATEGORY_SET
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.screen.mypage.ExportPreview
import com.example.wowagoodsproject.screen.mypage.ExportPreview.CharaCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportPreviewTest {

    private fun goods(chara: String, status: String, category: String = "키링") =
        GoodsEntity(goodsChara = chara, goodsCategory = category, goodsStatus = status)

    private val manyGoods = List(ExportPreview.MIN_GOODS_DATA) { goods("기타", "NOT_GOTTEN") }

    @Test
    fun 캐릭터별로_세트를_빼고_여러_캐릭터는_각자_센다() {
        val p = ExportPreview.of(
            official = manyGoods + listOf(
                goods("치사", "GOTTEN"),
                goods("치사", "PENDING"),
                goods("치사,데니아", "GOTTEN"),
                goods("치사,데니아", "GOTTEN", category = CATEGORY_SET), // 세트 줄은 세지 않는다
            ),
            fan = listOf(
                FanGoodsEntity(fanGoodsChara = "데니아", fanGoodsStatus = "GOTTEN", fanGoodsImgPath = "/x/a.jpg"),
                FanGoodsEntity(fanGoodsChara = "데니아", fanGoodsStatus = "NOT_GOTTEN", fanGoodsImgPath = "/y/a.jpg"), // 같은 이름 사진
                FanGoodsEntity(fanGoodsChara = "플로로", fanGoodsStatus = "PENDING", fanGoodsImgPath = "/x/missing.jpg"),
            ),
            charas = listOf(
                CharaEntity(charaId = 1, charaNm = "데니아", charaIsFavorite = true, charaFavoriteOrder = 2),
                CharaEntity(charaId = 2, charaNm = "치사", charaIsFavorite = true, charaFavoriteOrder = 1),
                CharaEntity(charaId = 3, charaNm = "수수")
            ),
            photoSize = { if (it.name == "missing.jpg") null else 100L }
        )
        assertEquals(2, p.officialGotten)
        assertEquals(1, p.officialPending)
        assertEquals(3, p.fanTotal)
        assertEquals(1, p.fanGotten)
        assertEquals(1, p.fanPending)
        assertEquals(1, p.photoCount) // a.jpg 한 번, 없는 사진은 빠진다
        assertEquals(100L, p.photoBytes)
        assertEquals(listOf("치사", "데니아"), p.favorites)
        assertEquals(
            listOf(CharaCount("치사", 2, 1), CharaCount("데니아", 2, 0), CharaCount("플로로", 0, 1)),
            p.byChara
        )
        assertFalse(p.isEmpty)
        assertFalse(p.goodsDataMissing)
    }

    @Test
    fun 굿즈_데이터가_거의_없고_보유도_없으면_둘_다_알린다() {
        // 친구 백업(wowa_backup.zip)과 같은 상태: 굿즈 2줄, 모두 미보유, 2차창작·선호 없음
        val p = ExportPreview.of(
            official = listOf(goods("카를로타", "NOT_GOTTEN"), goods("앙코", "NOT_GOTTEN")),
            fan = emptyList(),
            charas = emptyList()
        )
        assertTrue(p.isEmpty)
        assertTrue(p.goodsDataMissing)
        assertEquals(emptyList<CharaCount>(), p.byChara)
    }
}
