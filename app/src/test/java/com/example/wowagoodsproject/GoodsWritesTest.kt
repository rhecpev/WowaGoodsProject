package com.example.wowagoodsproject

import android.app.Application
import com.example.wowagoodsproject.component.CATEGORY_SET
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.PurchaseInfo
import com.example.wowagoodsproject.component.PurchaseInfoEdit
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.db.official.applyStatus
import com.example.wowagoodsproject.db.official.bulkSetStatus
import com.example.wowagoodsproject.db.official.savePending
import com.example.wowagoodsproject.db.official.setQuantity
import com.example.wowagoodsproject.db.official.setShipping
import com.example.wowagoodsproject.db.official.toggleGotten
import com.example.wowagoodsproject.db.official.togglePending
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 화면에서 누르는 상태 바꾸기를 실제 DB 로 돌려 본다.
 * 세트 굿즈는 같은 시리즈·같은 메모의 구성품 상태를 따라간다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class GoodsWritesTest {

    private val dao get() = App.database.goodsDao()

    @Before
    fun setUp() = runBlocking {
        TestDb.setUp()
        listOf(
            GoodsEntity(goodsUid = 1, goodsSeries = "S", goodsChara = "A", goodsCategory = CATEGORY_SET, goodsMemo = "세트1"),
            GoodsEntity(goodsUid = 2, goodsSeries = "S", goodsChara = "A", goodsCategory = "배지", goodsMemo = "세트1"),
            GoodsEntity(goodsUid = 3, goodsSeries = "S", goodsChara = "B", goodsCategory = "배지", goodsMemo = "세트1"),
            GoodsEntity(goodsUid = 4, goodsSeries = "S", goodsChara = "A", goodsCategory = "인형", goodsMemo = ""),
            GoodsEntity(goodsUid = 5, goodsSeries = "T", goodsChara = "A", goodsCategory = "배지", goodsMemo = "세트1") // 다른 시리즈
        ).forEach { dao.insert(it) }
    }

    @After
    fun tearDown() = TestDb.tearDown()

    private suspend fun g(uid: Int) = dao.getAll().single { it.goodsUid == uid }
    private suspend fun status(uid: Int) = g(uid).goodsStatus

    @Test
    fun 구성품을_모두_보유하면_세트도_보유가_되고_하나라도_빼면_미보유로_돌아간다() = runBlocking {
        dao.toggleGotten(g(2).goodsId)
        assertEquals("NOT_GOTTEN", status(1))
        dao.toggleGotten(g(3).goodsId)
        assertEquals("GOTTEN", status(1))
        assertEquals("NOT_GOTTEN", status(5)) // 다른 시리즈는 상관없다
        dao.toggleGotten(g(2).goodsId)
        assertEquals("NOT_GOTTEN", status(1))
    }

    @Test
    fun 세트를_한_번에_바꾸면_구성품도_같은_상태가_된다() = runBlocking {
        val info = PurchaseInfo(purchaseStore = "샵", purchaseDate = "2026-01-01", receiveDate = "2026-02-01")
        dao.bulkSetStatus(g(1).goodsId, GoodsStatus.PENDING, info)
        assertEquals(listOf("PENDING", "PENDING", "PENDING"), listOf(status(1), status(2), status(3)))
        assertEquals("샵", g(2).goodsPurchaseStore)
        assertEquals("NOT_GOTTEN", status(4))
        assertEquals("NOT_GOTTEN", status(5))
    }

    @Test
    fun 미보유로_바꾸면_수량은_1로_배송_시작일은_지운다() = runBlocking {
        val id = g(4).goodsId
        dao.savePending(id, PurchaseInfo(purchaseStore = "샵"))
        dao.setQuantity(id, 5)
        dao.setShipping(id, "2026-03-01")
        dao.togglePending(id) // 구매예정 → 미보유
        val after = g(4)
        assertEquals(listOf("NOT_GOTTEN", 1, ""), listOf(after.goodsStatus, after.goodsQuantity, after.goodsShippingDate))
    }

    @Test
    fun 보유로_바꾸면_배송_시작일은_지우고_수량은_남긴다() = runBlocking {
        val id = g(4).goodsId
        dao.savePending(id, PurchaseInfo())
        dao.setQuantity(id, 3)
        dao.setShipping(id, "2026-03-01")
        dao.toggleGotten(id)
        val after = g(4)
        assertEquals(listOf("GOTTEN", 3, ""), listOf(after.goodsStatus, after.goodsQuantity, after.goodsShippingDate))
        assertTrue(after.goodsIsGotten)
    }

    @Test
    fun 화면이_들고_있던_옛_값으로_다른_칸을_덮지_않는다() = runBlocking {
        val stale = g(4) // 화면이 들고 있는 값
        dao.setQuantity(stale.goodsId, 7)
        dao.togglePending(stale.goodsId) // 옛 값을 넘겨도 id 로 최신 값을 읽어 고친다
        assertEquals(7, g(4).goodsQuantity)
    }

    @Test
    fun 다중_선택_일괄_변경은_세트와_구성품을_함께_맞추고_구매_정보는_고친_칸만_적는다() = runBlocking {
        dao.savePending(g(2).goodsId, PurchaseInfo(purchaseStore = "원래샵", purchaseDate = "2026-01-01"))
        dao.applyStatus(listOf(g(2).goodsId, g(3).goodsId), GoodsStatus.PENDING, PurchaseInfoEdit(purchaseStore = null, purchaseDate = null, receiveDate = "2026-05-05"))
        assertEquals(listOf("원래샵", "2026-01-01", "2026-05-05"), g(2).let { listOf(it.goodsPurchaseStore, it.goodsPurchaseDate, it.goodsReceiveDate) })
        // 구성품이 모두 구매예정이면 세트는 보유가 아니다
        assertEquals("NOT_GOTTEN", status(1))
        dao.applyStatus(listOf(g(2).goodsId, g(3).goodsId), GoodsStatus.GOTTEN)
        assertEquals("GOTTEN", status(1))
    }

    @Test
    fun 다중_선택에_세트를_넣으면_구성품도_바뀐다() = runBlocking {
        dao.applyStatus(listOf(g(1).goodsId), GoodsStatus.GOTTEN)
        assertEquals(listOf("GOTTEN", "GOTTEN", "GOTTEN"), listOf(status(1), status(2), status(3)))
    }

    @Test
    fun 없어진_굿즈를_바꾸려_해도_오류가_나지_않는다() = runBlocking {
        val gone = g(4)
        dao.delete(gone)
        dao.toggleGotten(gone.goodsId)
        dao.setQuantity(gone.goodsId, 3)
        dao.applyStatus(listOf(gone.goodsId), GoodsStatus.GOTTEN)
        assertEquals(4, dao.getAll().size)
    }
}
