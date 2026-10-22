package com.example.wowagoodsproject

import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.db.official.GoodsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GoodsSyncTest {

    private fun remote(uid: Int, price: String = "59위안", memo: String = "단품", url: String = "u$uid") = GoodsEntity(
        goodsUid = uid, goodsSeries = "1.0", goodsChara = "감심", goodsCategory = "인형$uid",
        goodsPrice = price, goodsMemo = memo, goodsUrl = url
    )

    /** 폰에 저장된 굿즈. 사용자가 보유로 바꾸고 수량을 적어 둔 상태 */
    private fun local(id: Int, from: GoodsEntity, uid: Int = from.goodsUid) = from.copy(
        goodsId = id, goodsUid = uid, goodsStatus = GoodsStatus.GOTTEN.name, goodsQuantity = 3
    )

    @Test
    fun 번호가_같으면_가격이_바뀌어도_같은_굿즈로_고친다() {
        val before = remote(1)
        val after = remote(1, price = "69위안")
        val plan = planGoodsSync(listOf(local(10, before)), listOf(after))

        assertEquals(0, plan.inserts.size)
        assertEquals(0, plan.deletes.size)
        val (l, r) = plan.updates.single()
        val updated = l.withSourceOf(r)
        assertEquals("69위안", updated.goodsPrice)
        // 사용자 기록은 그대로
        assertEquals(10, updated.goodsId)
        assertEquals(GoodsStatus.GOTTEN.name, updated.goodsStatus)
        assertEquals(3, updated.goodsQuantity)
    }

    @Test
    fun 번호가_없는_기존_굿즈는_내용으로_짝을_찾아_번호를_받는다() {
        val r = remote(7)
        val plan = planGoodsSync(listOf(local(10, r, uid = 0)), listOf(r))

        assertEquals(0, plan.inserts.size)
        assertEquals(0, plan.deletes.size)
        assertEquals(7, plan.updates.single().let { (l, r) -> l.withSourceOf(r) }.goodsUid)
    }

    @Test
    fun 바뀐_것이_없으면_고치지_않는다() {
        val r = remote(1)
        val plan = planGoodsSync(listOf(local(10, r)), listOf(r))
        assertTrue(plan.updates.isEmpty() && plan.inserts.isEmpty() && plan.deletes.isEmpty())
    }

    @Test
    fun 새_번호는_추가하고_사라진_번호는_지운다() {
        val plan = planGoodsSync(listOf(local(10, remote(1))), listOf(remote(2)))
        assertEquals(listOf(2), plan.inserts.map { it.goodsUid })
        assertEquals(listOf(10), plan.deletes.map { it.goodsId })
    }

    @Test
    fun 번호가_바뀌었어도_내용이_같으면_같은_굿즈로_본다() {
        // 원본의 번호 대장이 다시 만들어진 경우
        val plan = planGoodsSync(listOf(local(10, remote(1))), listOf(remote(1).copy(goodsUid = 500)))
        assertEquals(0, plan.inserts.size)
        assertEquals(0, plan.deletes.size)
        assertEquals(500, plan.updates.single().second.goodsUid)
    }

    @Test
    fun 번호로_짝지은_폰_굿즈는_내용_비교에_다시_쓰지_않는다() {
        // 1번은 가격이 바뀌고, 같은 내용의 옛 굿즈가 새 번호 2로 들어온 경우
        val old = remote(1)
        val plan = planGoodsSync(
            listOf(local(10, old)),
            listOf(remote(1, price = "69위안"), old.copy(goodsUid = 2))
        )
        assertEquals(10, plan.updates.single().first.goodsId)
        assertEquals(listOf(2), plan.inserts.map { it.goodsUid })
    }

    // ── 번호가 없는 굿즈의 첫 동기화에서 데이터가 오래된 경우 ─────────────

    private fun g(id: Int, uid: Int, price: String, memo: String, category: String = "인형") = GoodsEntity(
        goodsId = id, goodsUid = uid, goodsSeries = "1.0", goodsChara = "감심", goodsCategory = category,
        goodsPrice = price, goodsMemo = memo, goodsStatus = GoodsStatus.GOTTEN.name
    )

    @Test
    fun 번호가_없는_굿즈는_그사이_가격이_바뀌었어도_메모가_같은_하나뿐인_짝을_찾는다() {
        val plan = planGoodsSync(listOf(g(10, 0, "59위안", "단품")), listOf(g(0, 5, "69위안", "단품")))
        assertEquals(10, plan.updates.single().first.goodsId)
        assertTrue(plan.inserts.isEmpty() && plan.deletes.isEmpty())
    }

    @Test
    fun 가격이_숫자이던_옛_데이터도_메모가_같으면_찾는다() {
        val plan = planGoodsSync(listOf(g(10, 0, "59", "단품")), listOf(g(0, 5, "59위안", "단품")))
        assertEquals(10, plan.updates.single().first.goodsId)
    }

    @Test
    fun 번호가_없는_굿즈라도_후보가_여럿이면_짝짓지_않는다() {
        // 메모가 같은 원본이 둘
        val plan = planGoodsSync(listOf(g(10, 0, "59위안", "단품")), listOf(g(0, 5, "69위안", "단품"), g(0, 6, "79위안", "단품")))
        assertEquals(1, plan.deletes.size)
        assertEquals(2, plan.inserts.size)
    }

    @Test
    fun 거꾸로_봐도_하나뿐이어야_한다() {
        // 폰에 메모가 같은 굿즈가 둘이고 원본은 하나 → 어느 쪽인지 모른다
        val plan = planGoodsSync(listOf(g(10, 0, "59위안", "단품"), g(11, 0, "99위안", "단품")), listOf(g(0, 5, "69위안", "단품")))
        assertTrue(plan.updates.isEmpty())
        assertEquals(2, plan.deletes.size)
    }

    @Test
    fun 가격과_메모가_둘_다_다르면_번호가_없어도_짝짓지_않는다() {
        val plan = planGoodsSync(listOf(g(10, 0, "59위안", "단품")), listOf(g(0, 5, "69위안", "세트")))
        assertTrue(plan.updates.isEmpty())
    }

    @Test
    fun 번호가_있는_굿즈는_느슨하게_짝짓지_않는다() {
        // 3번이 원본에서 사라지고 비슷한 5번이 새로 생겼다 → 다른 굿즈로 본다
        val plan = planGoodsSync(listOf(g(10, 3, "59위안", "단품")), listOf(g(0, 5, "69위안", "단품")))
        assertTrue(plan.updates.isEmpty())
        assertEquals(listOf(10), plan.deletes.map { it.goodsId })
    }

    @Test
    fun 정확히_같은_짝이_있으면_느슨한_짝보다_먼저다() {
        val plan = planGoodsSync(
            listOf(g(10, 0, "59위안", "단품"), g(11, 0, "99위안", "단품")),
            listOf(g(0, 5, "59위안", "단품"), g(0, 6, "109위안", "단품"))
        )
        // 10 ↔ 5 는 내용이 같아 먼저 짝지어지고, 남은 11 ↔ 6 은 메모가 같은 하나뿐인 짝이다
        assertEquals(mapOf(10 to 5, 11 to 6), plan.updates.associate { (l, r) -> l.goodsId to r.goodsUid })
    }

    @Test
    fun 번호가_없거나_겹치면_동기화를_멈춘다() {
        assertThrows(IllegalStateException::class.java) { requireGoodsUids(listOf(remote(1), remote(0))) }
        assertThrows(IllegalStateException::class.java) { requireGoodsUids(listOf(remote(1), remote(1, price = "1"))) }
        requireGoodsUids(listOf(remote(1), remote(2)))
    }
}
