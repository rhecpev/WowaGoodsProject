package com.example.wowagoodsproject

import android.app.Application
import com.example.wowagoodsproject.component.CATEGORY_SET
import com.example.wowagoodsproject.db.changed.restoreChangedGoods
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.db.news.NewsType
import com.example.wowagoodsproject.db.official.GoodsEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 굿즈 번호(goodsUid)로 동기화할 때 생길 수 있는 경우를 실제 DB 로 돌려 본다.
 * 원본 목록을 직접 넘겨 [UpdateManager.applyGoodsSync] 를 부르므로 네트워크는 쓰지 않는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class GoodsSyncIntegrationTest {

    private val goodsDao get() = App.database.goodsDao()
    private val newsDao get() = App.newsDatabase.newsDao()
    private val changedDao get() = App.changedGoodsDatabase.changedGoodsDao()

    @Before
    fun setUp() = runBlocking {
        TestDb.setUp()
        App.charaDatabase.charaDao().insert(CharaEntity(charaNm = "감심", charaIsFavorite = true))
    }

    @After
    fun tearDown() = TestDb.tearDown()

    private fun remote(uid: Int, category: String = "인형$uid", price: String = "59위안", memo: String = "단품", chara: String = "감심") =
        GoodsEntity(goodsUid = uid, goodsSeries = "1.0버전", goodsChara = chara, goodsCategory = category,
            goodsPrice = price, goodsMemo = memo, goodsReleaseDate = "2024-07-04", goodsUrl = "https://x/$uid.jpg")

    private fun sync(vararg goods: GoodsEntity) = runBlocking { UpdateManager.applyGoodsSync(goods.toList()) }

    private suspend fun allNews() = newsDao.getAllFlow().first()
    private suspend fun allChanged() = changedDao.getAllFlow().first()

    private suspend fun byUid(uid: Int) = goodsDao.getAll().single { it.goodsUid == uid }

    private suspend fun own(uid: Int, status: String = "GOTTEN", quantity: Int = 2) {
        val g = byUid(uid)
        goodsDao.update(g.copy(goodsStatus = status, goodsQuantity = quantity, goodsPurchaseStore = "매장"))
    }

    @Test
    fun 처음_설치하면_모두_넣고_소식은_만들지_않는다() = runBlocking {
        assertEquals(Triple(3, 0, 0), sync(remote(1), remote(2), remote(3)))
        assertEquals(listOf(1, 2, 3), goodsDao.getAll().map { it.goodsUid }.sorted())
        assertTrue(allNews().isEmpty())
    }

    @Test
    fun 업데이트_뒤_첫_동기화는_번호만_채우고_보유_기록과_변경_수는_그대로다() = runBlocking {
        // 예전 버전처럼 번호 없이 들어 있던 굿즈
        listOf(remote(1), remote(2)).forEach { goodsDao.insert(it.copy(goodsUid = 0)) }
        goodsDao.getAll().forEach { goodsDao.update(it.copy(goodsStatus = "GOTTEN", goodsQuantity = 5)) }
        val ids = goodsDao.getAll().map { it.goodsId }.toSet()

        assertEquals(Triple(0, 0, 0), sync(remote(1), remote(2)))

        val after = goodsDao.getAll()
        assertEquals(ids, after.map { it.goodsId }.toSet()) // 같은 줄을 그대로 쓴다(지우고 새로 넣지 않는다)
        assertEquals(setOf(1, 2), after.map { it.goodsUid }.toSet())
        assertTrue(after.all { it.goodsStatus == "GOTTEN" && it.goodsQuantity == 5 })
        assertTrue(allNews().isEmpty())
        assertTrue(allChanged().isEmpty())
    }

    @Test
    fun 가격과_메모가_바뀌어도_보유_기록이_남고_변경된_굿즈에_기록된다() = runBlocking {
        sync(remote(1), remote(2))
        own(1)
        val id = byUid(1).goodsId

        assertEquals(Triple(0, 1, 0), sync(remote(1, price = "69위안", memo = "재판"), remote(2)))

        val g = byUid(1)
        assertEquals(id, g.goodsId)
        assertEquals(listOf("69위안", "재판", "GOTTEN", 2, "매장"), listOf(g.goodsPrice, g.goodsMemo, g.goodsStatus, g.goodsQuantity, g.goodsPurchaseStore))
        val record = allChanged().single()
        assertTrue(record.statusKept)
        assertFalse(record.canRestore)
        assertEquals(1, record.newUid)
        val news = allNews().single()
        assertEquals(NewsType.CHANGED_GOODS.name, news.newsType)
        assertEquals(record.changedId, news.newsChangedId)
        assertTrue(news.newsDetail.contains("가격: 59위안 → 69위안"))
    }

    @Test
    fun 보유하지_않은_굿즈가_바뀌면_기록과_소식은_만들지_않는다() = runBlocking {
        sync(remote(1))
        assertEquals(Triple(0, 1, 0), sync(remote(1, price = "69위안")))
        assertTrue(allChanged().isEmpty())
        assertTrue(allNews().isEmpty())
    }

    @Test
    fun 세트_굿즈는_바뀌어도_소식을_만들지_않는다() = runBlocking {
        sync(remote(1, category = CATEGORY_SET))
        own(1)
        sync(remote(1, category = CATEGORY_SET, price = "999위안"))
        assertTrue(allNews().isEmpty())
        assertEquals("GOTTEN", byUid(1).goodsStatus)
    }

    @Test
    fun 사라진_보유_굿즈는_지우고_대체_굿즈가_있으면_되돌릴_수_있다() = runBlocking {
        sync(remote(1, category = "인형"))
        own(1, status = "PENDING", quantity = 3)
        // 탐색기에서 이름을 바꿔 새 번호를 받은 경우: 1번이 사라지고 같은 시리즈·캐릭터·카테고리의 2번이 생겼다
        assertEquals(Triple(1, 0, 1), sync(remote(2, category = "인형", price = "69위안")))

        assertEquals("NOT_GOTTEN", byUid(2).goodsStatus)
        val record = allChanged().single()
        assertTrue(record.canRestore)
        assertEquals(2, record.newUid)
        // 신규 소식은 만들지 않는다(정보가 바뀐 것이므로)
        assertTrue(allNews().none { it.newsType == NewsType.NEW_GOODS.name })

        assertEquals("상태를 되돌렸습니다", restoreChangedGoods(record.changedId))
        val g = byUid(2)
        assertEquals(listOf("PENDING", 3, "매장"), listOf(g.goodsStatus, g.goodsQuantity, g.goodsPurchaseStore))
        assertEquals("되돌릴 기록을 찾을 수 없습니다", restoreChangedGoods(record.changedId)) // 두 번은 안 된다
    }

    @Test
    fun 되돌리기는_대체_굿즈의_가격이_또_바뀌었어도_번호로_찾는다() = runBlocking {
        sync(remote(1, category = "인형"))
        own(1)
        sync(remote(2, category = "인형", price = "69위안"))
        sync(remote(2, category = "인형", price = "79위안")) // 대체 굿즈가 또 바뀌었다
        val record = allChanged().single { !it.statusKept }
        assertEquals("상태를 되돌렸습니다", restoreChangedGoods(record.changedId))
        assertEquals("GOTTEN", byUid(2).goodsStatus)
    }

    @Test
    fun 대체_굿즈_없이_사라지면_되돌릴_수_없는_삭제_기록만_남긴다() = runBlocking {
        sync(remote(1, category = "인형"), remote(2, category = "키링"))
        own(1)
        sync(remote(2, category = "키링"))
        val record = allChanged().single()
        assertTrue(record.isDeleted)
        assertFalse(record.canRestore)
    }

    @Test
    fun 번호를_전부_새로_매긴_데이터가_와도_내용이_같으면_보유_기록이_남는다() = runBlocking {
        sync(remote(1), remote(2))
        own(1); own(2, status = "PENDING")
        // 번호 대장을 잃어 100번대로 다시 매겨진 경우. 사진 주소는 그대로다
        assertEquals(Triple(0, 0, 0), sync(
            remote(101, category = "인형1").copy(goodsUrl = "https://x/1.jpg"),
            remote(102, category = "인형2").copy(goodsUrl = "https://x/2.jpg")
        ))
        assertEquals("GOTTEN", byUid(101).goodsStatus)
        assertEquals("PENDING", byUid(102).goodsStatus)
        assertTrue(allChanged().isEmpty())
    }

    @Test
    fun 번호가_없거나_겹치는_데이터는_아무것도_바꾸지_않는다() = runBlocking {
        sync(remote(1), remote(2))
        own(1)
        val before = goodsDao.getAll()
        for (bad in listOf(listOf(remote(1), remote(0, category = "x")), listOf(remote(1), remote(1, category = "y")))) {
            try {
                UpdateManager.applyGoodsSync(bad)
                fail("동기화를 멈춰야 한다")
            } catch (e: IllegalStateException) {
                // 기대한 대로 멈춤
            }
        }
        assertEquals(before, goodsDao.getAll())
    }

    @Test
    fun 선호_캐릭터의_새_굿즈만_신규_소식을_만든다() = runBlocking {
        sync(remote(1))
        sync(remote(1), remote(2), remote(3, chara = "금희"))
        val news = allNews()
        assertEquals(listOf("인형2"), news.filter { it.newsType == NewsType.NEW_GOODS.name }.map { it.newsCategory })
    }

    @Test
    fun 선호_캐릭터와_이름_일부만_같은_캐릭터의_새_굿즈는_소식을_만들지_않는다() = runBlocking {
        sync(remote(1))
        sync(remote(1), remote(2, chara = "감심현령"), remote(3, chara = "금희,감심"))
        assertEquals(listOf("인형3"), allNews().filter { it.newsType == NewsType.NEW_GOODS.name }.map { it.newsCategory })
    }

    @Test
    fun 같은_데이터를_두_번_받아도_아무것도_바뀌지_않는다() = runBlocking {
        sync(remote(1), remote(2))
        own(1)
        val before = goodsDao.getAll()
        assertEquals(Triple(0, 0, 0), sync(remote(1), remote(2)))
        assertEquals(before, goodsDao.getAll())
        assertTrue(App.patchNoteDatabase.patchNoteDao().getAll().size == 1) // 처음 넣을 때 한 번만
    }

    @Test
    fun 동기화는_사용자가_적은_칸을_건드리지_않는다() = runBlocking {
        sync(remote(1))
        val g = byUid(1)
        goodsDao.update(g.copy(goodsStatus = "PENDING", goodsQuantity = 7, goodsPurchaseDate = "2026-01-01",
            goodsPurchaseStore = "샵", goodsReceiveDate = "2026-02-02", goodsShippingDate = "2026-01-15"))
        sync(remote(1, price = "1위안", memo = "", chara = "감심,금희", category = "새카테고리"))
        val after = byUid(1)
        assertEquals(
            listOf("PENDING", 7, "2026-01-01", "샵", "2026-02-02", "2026-01-15"),
            listOf(after.goodsStatus, after.goodsQuantity, after.goodsPurchaseDate, after.goodsPurchaseStore, after.goodsReceiveDate, after.goodsShippingDate)
        )
        assertEquals(listOf("1위안", "", "감심,금희", "새카테고리"), listOf(after.goodsPrice, after.goodsMemo, after.goodsChara, after.goodsCategory))
    }
}
