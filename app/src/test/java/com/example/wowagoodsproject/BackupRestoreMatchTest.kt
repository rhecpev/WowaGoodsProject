package com.example.wowagoodsproject

import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.screen.mypage.FanGoodsBackup
import com.example.wowagoodsproject.screen.mypage.OfficialGoodsBackup
import com.example.wowagoodsproject.screen.mypage.matchOfficialBackups
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 백업 줄이 폰의 어느 굿즈로 되살아나는지, 버전마다 다른 백업 형식을 어떻게 읽는지 확인한다. */
class BackupRestoreMatchTest {

    private var nextId = 1
    private fun goods(
        category: String,
        price: String = "59위안",
        memo: String = "단품",
        uid: Int = 0,
        chara: String = "감심",
        series: String = "1.0"
    ) = GoodsEntity(
        goodsId = nextId++, goodsUid = uid, goodsSeries = series, goodsChara = chara,
        goodsCategory = category, goodsPrice = price, goodsMemo = memo
    )

    private fun backup(
        category: String,
        price: String? = "59위안",
        memo: String? = "단품",
        uid: Int? = null,
        status: String? = "GOTTEN",
        isGotten: Boolean? = null,
        chara: String = "감심",
        series: String = "1.0"
    ) = OfficialGoodsBackup(
        goodsSeries = series, goodsChara = chara, goodsCategory = category,
        goodsPrice = price, goodsMemo = memo, goodsUid = uid, goodsStatus = status, goodsIsGotten = isGotten
    )

    private fun parse(json: String): List<OfficialGoodsBackup> =
        Gson().fromJson(json, object : TypeToken<List<OfficialGoodsBackup>>() {}.type)

    private fun match(backups: List<OfficialGoodsBackup>, local: List<GoodsEntity>) =
        matchOfficialBackups(backups, local).associate { (b, g) -> b to g.goodsId }

    // ── 짝 찾기 ──────────────────────────────────────────────────────────

    @Test
    fun 번호가_같으면_가격이_바뀌었어도_찾는다() {
        val l = goods("인형", price = "69위안", uid = 1)
        val b = backup("인형", price = "59위안", uid = 1)
        assertEquals(l.goodsId, match(listOf(b), listOf(l))[b])
    }

    @Test
    fun 내용이_모두_같으면_찾는다() {
        val l = goods("인형")
        val b = backup("인형")
        assertEquals(l.goodsId, match(listOf(b), listOf(l))[b])
    }

    @Test
    fun 폰에_번호가_아직_없으면_내용으로_찾는다() {
        val l = goods("인형", uid = 0)
        val b = backup("인형", uid = 7)
        assertEquals(l.goodsId, match(listOf(b), listOf(l))[b])
    }

    @Test
    fun 번호가_데이터에서_사라졌어도_내용이_같으면_찾는다() {
        val l = goods("인형", uid = 50)
        val b = backup("인형", uid = 7)
        assertEquals(l.goodsId, match(listOf(b), listOf(l))[b])
    }

    @Test
    fun 번호_없는_백업_뒤에_가격이_바뀐_굿즈도_메모가_같으면_찾는다() {
        // 실제 백업에서 나온 경우: 메모는 같고 가격 표기만 바뀌었다
        val l = goods("인형", price = "중국판 99위안")
        val b = backup("인형", price = "한국판 99위안")
        assertEquals(l.goodsId, match(listOf(b), listOf(l))[b])
    }

    @Test
    fun 번호_없는_백업_뒤에_메모가_바뀐_굿즈도_가격이_같으면_찾는다() {
        val l = goods("인형", memo = "A세트")
        val b = backup("인형", memo = "단품")
        assertEquals(l.goodsId, match(listOf(b), listOf(l))[b])
    }

    @Test
    fun 가격과_메모가_둘_다_다르면_다른_굿즈일_수_있어_짝짓지_않는다() {
        val l = goods("인형", price = "99위안", memo = "세트구성")
        val b = backup("인형", price = "59위안", memo = "단품")
        assertTrue(match(listOf(b), listOf(l)).isEmpty())
    }

    @Test
    fun 후보가_여럿이라_어느_것인지_모르면_짝짓지_않는다() {
        // 메모가 같은 후보가 둘, 가격이 같은 후보는 없다
        val a = goods("인형", price = "69위안")
        val c = goods("인형", price = "79위안")
        val b = backup("인형", price = "59위안")
        assertTrue(match(listOf(b), listOf(a, c)).isEmpty())
    }

    @Test
    fun 미보유_줄은_보지_않고_다른_줄의_짝을_가로채지_않는다() {
        val l = goods("인형", price = "69위안")
        val notOwned = backup("인형", price = "69위안", status = "NOT_GOTTEN")
        val owned = backup("인형", price = "59위안")
        val result = match(listOf(notOwned, owned), listOf(l))
        assertEquals(l.goodsId, result[owned])
        assertNull(result[notOwned])
    }

    @Test
    fun 한_굿즈는_한_줄에만_짝지어진다() {
        val l = goods("인형", uid = 1)
        val first = backup("인형", uid = 1, status = "PENDING")
        val second = backup("인형", uid = 1)
        val pairs = matchOfficialBackups(listOf(first, second), listOf(l))
        assertEquals(1, pairs.size)
        assertEquals(first, pairs.single().first)
    }

    @Test
    fun 번호로_짝지은_굿즈는_내용_비교에서_다시_쓰지_않는다() {
        val moved = goods("인형", price = "69위안", uid = 1)
        val byUid = backup("인형", price = "59위안", uid = 1)
        val oldRow = backup("인형", price = "69위안") // 번호 없는 줄이 같은 굿즈를 가리킨다
        val result = match(listOf(oldRow, byUid), listOf(moved))
        assertEquals(moved.goodsId, result[byUid])
        assertNull(result[oldRow])
    }

    @Test
    fun 시리즈나_캐릭터가_다르면_찾지_않는다() {
        val l = goods("인형", series = "2.0")
        assertTrue(match(listOf(backup("인형")), listOf(l)).isEmpty())
        val other = goods("인형", chara = "금희")
        assertTrue(match(listOf(backup("인형")), listOf(other)).isEmpty())
    }

    // ── 버전별 백업 형식 ─────────────────────────────────────────────────

    @Test
    fun v1_0_형식_가격과_상태가_없고_goodsIsGotten_만_있다() {
        val rows = parse("""[{"goodsSeries":"1.0","goodsChara":"감심","goodsCategory":"인형","goodsIsGotten":true,"goodsMemo":"단품"},
            {"goodsSeries":"1.0","goodsChara":"감심","goodsCategory":"키링","goodsIsGotten":false,"goodsMemo":""}]""")
        assertEquals(GoodsStatus.GOTTEN, rows[0].restoredStatus)
        assertFalse(rows[1].isOwned)
        val l = goods("인형", price = "59위안")
        assertEquals(l.goodsId, match(rows, listOf(l, goods("키링")))[rows[0]])
    }

    @Test
    fun v1_1_형식_메모도_없으면_시리즈_캐릭터_카테고리로_찾는다() {
        val rows = parse("""[{"goodsSeries":"1.0","goodsChara":"감심","goodsCategory":"인형","goodsIsGotten":true}]""")
        val l = goods("인형", memo = "")
        assertEquals(l.goodsId, match(rows, listOf(l))[rows[0]])
    }

    @Test
    fun v1_1_형식에서_후보가_여럿이면_메모가_빈_굿즈를_고른다() {
        val rows = parse("""[{"goodsSeries":"1.0","goodsChara":"감심","goodsCategory":"인형","goodsIsGotten":true}]""")
        val plain = goods("인형", memo = "")
        val special = goods("인형", memo = "한정판")
        assertEquals(plain.goodsId, match(rows, listOf(special, plain))[rows[0]])
    }

    @Test
    fun v1_1_형식에서_메모가_빈_후보도_없으면_짝짓지_않는다() {
        val rows = parse("""[{"goodsSeries":"1.0","goodsChara":"감심","goodsCategory":"인형","goodsIsGotten":true}]""")
        assertTrue(match(rows, listOf(goods("인형", memo = "A"), goods("인형", memo = "B"))).isEmpty())
    }

    @Test
    fun v2_1_형식_상태와_가격과_구매정보() {
        val rows = parse("""[{"goodsSeries":"1.0","goodsChara":"감심","goodsCategory":"인형","goodsStatus":"PENDING","goodsMemo":"단품","goodsPrice":"59위안","goodsPurchaseDate":"2026-01-02","goodsPurchaseStore":"공식몰"}]""")
        val l = goods("인형")
        val restored = rows[0].applyTo(l)
        assertEquals(GoodsStatus.PENDING.name, restored.goodsStatus)
        assertEquals("공식몰", restored.goodsPurchaseStore)
        assertEquals(1, restored.goodsQuantity) // 수량 칸이 없으면 1
    }

    @Test
    fun v2_2_형식_수량과_배송() {
        val rows = parse("""[{"goodsSeries":"1.0","goodsChara":"감심","goodsCategory":"인형","goodsStatus":"PENDING","goodsMemo":"단품","goodsPrice":"59위안","goodsReceiveDate":"2026-03-01","goodsQuantity":3,"goodsShippingDate":"2026-02-01"}]""")
        val restored = rows[0].applyTo(goods("인형"))
        assertEquals(3, restored.goodsQuantity)
        assertEquals("2026-02-01", restored.goodsShippingDate)
        assertEquals("2026-03-01", restored.goodsReceiveDate)
    }

    @Test
    fun v3_0_형식은_번호와_예전_보유_칸을_같이_적는다() {
        val l = goods("인형", uid = 9).copy(goodsStatus = "GOTTEN", goodsQuantity = 2)
        val json = Gson().toJson(listOf(OfficialGoodsBackup.of(l)))
        val row = parse(json).single()
        assertEquals(9, row.goodsUid)
        assertEquals(true, row.goodsIsGotten)
        assertEquals(2, row.applyTo(goods("인형")).goodsQuantity)
    }

    // ── 되살릴 값 ────────────────────────────────────────────────────────

    @Test
    fun 모르는_상태_값은_미보유로_보고_되살리지_않는다() {
        val row = backup("인형", status = "SOLD_OUT")
        assertEquals(GoodsStatus.NOT_GOTTEN, row.restoredStatus)
        assertFalse(row.isOwned)
    }

    @Test
    fun 상태_칸이_있으면_예전_보유_칸보다_우선한다() {
        assertEquals(GoodsStatus.PENDING, backup("인형", status = "PENDING", isGotten = false).restoredStatus)
    }

    @Test
    fun 보유로_되살리면_배송_시작일은_지우고_예전_칸도_맞춘다() {
        val row = OfficialGoodsBackup(goodsStatus = "GOTTEN", goodsShippingDate = "2026-01-01", goodsQuantity = 0)
        val restored = row.applyTo(goods("인형"))
        assertEquals("", restored.goodsShippingDate)
        assertTrue(restored.goodsIsGotten)
        assertEquals(1, restored.goodsQuantity) // 0 이하 수량은 1로
    }

    // ── 2차창작 ──────────────────────────────────────────────────────────

    private fun parseFan(json: String): FanGoodsBackup =
        Gson().fromJson<List<FanGoodsBackup>>(json, object : TypeToken<List<FanGoodsBackup>>() {}.type).single()

    @Test
    fun v1_1부터_v2_0까지_숫자_가격을_읽는다() {
        assertEquals("15000", parseFan("""[{"fanGoodsPrice":15000,"fanGoodsIsGotten":true}]""").toEntity("").fanGoodsPrice)
        assertEquals("", parseFan("""[{"fanGoodsPrice":0}]""").toEntity("").fanGoodsPrice)
    }

    @Test
    fun 상태_칸이_없는_2차창작은_예전_보유_칸으로_되살린다() {
        val e = parseFan("""[{"fanGoodsSeries":"c107","fanGoodsIsGotten":true}]""").toEntity("")
        assertEquals(GoodsStatus.GOTTEN.name, e.fanGoodsStatus)
    }

    @Test
    fun 실제_백업처럼_예전_칸과_상태가_어긋나도_상태를_따르고_예전_칸을_맞춘다() {
        // 실제 2.x 백업: fanGoodsIsGotten 은 false 인데 상태는 GOTTEN
        val e = parseFan("""[{"fanGoodsIsGotten":false,"fanGoodsStatus":"GOTTEN","fanGoodsQuantity":2}]""").toEntity("")
        assertEquals(GoodsStatus.GOTTEN.name, e.fanGoodsStatus)
        assertTrue(e.fanGoodsIsGotten)
        assertEquals(2, e.fanGoodsQuantity)
    }

    @Test
    fun 미보유_2차창작은_수량_1로_받는다() {
        val e = parseFan("""[{"fanGoodsStatus":"NOT_GOTTEN","fanGoodsQuantity":5,"fanGoodsShippingDate":"2026-01-01"}]""").toEntity("")
        assertEquals(1, e.fanGoodsQuantity)
        assertEquals("", e.fanGoodsShippingDate)
    }
}
