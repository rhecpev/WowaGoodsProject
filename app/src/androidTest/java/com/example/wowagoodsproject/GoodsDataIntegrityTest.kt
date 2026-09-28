package com.example.wowagoodsproject

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wowagoodsproject.component.CATEGORY_SET
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.PurchaseInfo
import com.example.wowagoodsproject.component.PurchaseInfoEdit
import com.example.wowagoodsproject.db.changed.ChangedGoodsDatabase
import com.example.wowagoodsproject.db.character.CharaDatabase
import com.example.wowagoodsproject.db.fan.FanGoodsDatabase
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.news.NewsDatabase
import com.example.wowagoodsproject.db.official.GoodsDatabase
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.db.official.applyStatus
import com.example.wowagoodsproject.db.official.setStatus
import com.example.wowagoodsproject.db.official.toggleGotten
import com.example.wowagoodsproject.db.patchnote.PatchNoteDatabase
import com.example.wowagoodsproject.db.series.SeriesDatabase
import com.example.wowagoodsproject.db.series.SeriesEntity
import com.example.wowagoodsproject.screen.fan.FanAddViewModel
import com.example.wowagoodsproject.screen.mypage.MyPageViewModel
import com.example.wowagoodsproject.screen.series.SeriesViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 수량·구매 정보가 추출/입력, 다른 화면의 옛 값, 상태 변경 때문에 사라지거나 되돌아가지 않는지 확인한다.
 * 실제 앱 데이터를 건드리지 않도록 모든 DB 를 메모리 DB 로 바꿔 끼운 뒤 돌린다.
 */
@RunWith(AndroidJUnit4::class)
class GoodsDataIntegrityTest {

    private lateinit var context: Context
    private val goodsDao get() = App.database.goodsDao()
    private val fanDao get() = App.fanDatabase.fanGoodsDao()

    private val series = SeriesEntity(seriesNm = "S1", seriesCountry = "한국", seriesCharas = "A")

    @Before
    fun setUp() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        App.database = Room.inMemoryDatabaseBuilder(context, GoodsDatabase::class.java).build()
        App.fanDatabase = Room.inMemoryDatabaseBuilder(context, FanGoodsDatabase::class.java).build()
        App.charaDatabase = Room.inMemoryDatabaseBuilder(context, CharaDatabase::class.java).build()
        App.seriesDatabase = Room.inMemoryDatabaseBuilder(context, SeriesDatabase::class.java).build()
        App.newsDatabase = Room.inMemoryDatabaseBuilder(context, NewsDatabase::class.java).build()
        App.changedGoodsDatabase = Room.inMemoryDatabaseBuilder(context, ChangedGoodsDatabase::class.java).build()
        App.patchNoteDatabase = Room.inMemoryDatabaseBuilder(context, PatchNoteDatabase::class.java).build()

        App.seriesDatabase.seriesDao().insert(series)
        goodsDao.insert(goods("K1", "1000"))
        goodsDao.insert(goods("K2", "2000"))
        goodsDao.insert(goods(CATEGORY_SET, "5000", memo = "M"))
        goodsDao.insert(goods("C1", "0", memo = "M"))
        goodsDao.insert(goods("C2", "0", memo = "M"))
    }

    private fun goods(category: String, price: String, memo: String = "") = GoodsEntity(
        goodsSeries = "S1", goodsChara = "A", goodsCategory = category, goodsPrice = price, goodsMemo = memo
    )

    private suspend fun byCategory(category: String) = goodsDao.getAll().first { it.goodsCategory == category }

    /** 조건을 만족할 때까지(최대 5초) 기다렸다가 그 값을 돌려준다. */
    private fun <T> await(get: suspend () -> T, until: (T) -> Boolean): T = runBlocking {
        withTimeout(5_000) {
            var value = get()
            while (!until(value)) {
                delay(50)
                value = get()
            }
            value
        }
    }

    private fun <T> onMain(block: () -> T): T = runBlocking { withContext(Dispatchers.Main) { block() } }

    // ------------------------------------------------------------------ 추출 → 입력

    @Test
    fun backupRoundTrip_keepsQuantityPurchaseInfoAndShipping() {
        runBlocking {
            goodsDao.update(byCategory("K1").copy(goodsStatus = GoodsStatus.GOTTEN.name, goodsQuantity = 3))
            goodsDao.update(
                byCategory("K2").copy(
                    goodsStatus = GoodsStatus.PENDING.name, goodsQuantity = 2,
                    goodsPurchaseStore = "알리", goodsPurchaseDate = "2026-09-01",
                    goodsReceiveDate = "2026-09-10", goodsShippingDate = "2026-09-05"
                )
            )
            fanDao.insert(
                FanGoodsEntity(
                    fanGoodsSeries = "F1", fanGoodsStatus = GoodsStatus.PENDING.name, fanGoodsQuantity = 4,
                    fanGoodsPurchaseStore = "팝업", fanGoodsReceiveDate = "2026-10-01", fanGoodsShippingDate = "2026-09-20"
                )
            )
        }
        val vm = onMain { MyPageViewModel() }
        val zip = File(context.cacheDir, "roundtrip.zip")
        runBlocking { vm.writeBackupZip(zip) }

        // 입력 전에 다 지워 둔다(입력이 실제로 값을 되살리는지 보려고).
        runBlocking {
            goodsDao.resetAllGotten()
            fanDao.deleteAll()
        }
        onMain { vm.importData(context, Uri.fromFile(zip)) }

        val k1 = await({ byCategory("K1") }) { it.goodsQuantity == 3 }
        assertEquals(GoodsStatus.GOTTEN.name, k1.goodsStatus)

        val k2 = await({ byCategory("K2") }) { it.goodsStatus == GoodsStatus.PENDING.name }
        assertEquals(2, k2.goodsQuantity)
        assertEquals("알리", k2.goodsPurchaseStore)
        assertEquals("2026-09-01", k2.goodsPurchaseDate)
        assertEquals("2026-09-10", k2.goodsReceiveDate)
        assertEquals("2026-09-05", k2.goodsShippingDate)

        val fan = await({ fanDao.getAll() }) { it.size == 1 }.single()
        assertEquals(GoodsStatus.PENDING.name, fan.fanGoodsStatus)
        assertEquals(4, fan.fanGoodsQuantity)
        assertEquals("팝업", fan.fanGoodsPurchaseStore)
        assertEquals("2026-10-01", fan.fanGoodsReceiveDate)
        assertEquals("2026-09-20", fan.fanGoodsShippingDate)
    }

    @Test
    fun oldBackupWithoutNewFields_fillsDefaults() {
        runBlocking { goodsDao.update(byCategory("K1").copy(goodsQuantity = 5, goodsReceiveDate = "2026-01-01")) }
        // 이번 업데이트 전 앱이 만든 백업: 수량·수령예정일·배송 칸이 없고, 2차창작은 상태 칸도 없다.
        val zip = File(context.cacheDir, "old.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("official_gotten.json"))
            out.write(
                """[{"goodsSeries":"S1","goodsChara":"A","goodsCategory":"K1","goodsStatus":"GOTTEN","goodsMemo":"","goodsPrice":"1000"}]"""
                    .toByteArray()
            )
            out.closeEntry()
            out.putNextEntry(ZipEntry("fan_goods.json"))
            out.write("""[{"fanGoodsSeries":"F1","fanGoodsIsGotten":true}]""".toByteArray())
            out.closeEntry()
        }
        val vm = onMain { MyPageViewModel() }
        onMain { vm.importData(context, Uri.fromFile(zip)) }

        val k1 = await({ byCategory("K1") }) { it.goodsStatus == GoodsStatus.GOTTEN.name }
        assertEquals(1, k1.goodsQuantity)
        assertEquals("", k1.goodsReceiveDate)
        assertEquals("", k1.goodsShippingDate)

        val fan = await({ fanDao.getAll() }) { it.size == 1 }.single()
        assertEquals(GoodsStatus.GOTTEN.name, fan.fanGoodsStatus)
        assertEquals(1, fan.fanGoodsQuantity)
        assertEquals("", fan.fanGoodsReceiveDate)
    }

    // ------------------------------------------------------------------ 다른 화면이 들고 있던 옛 값

    @Test
    fun staleSeriesScreen_seesImportedValues_andDoesNotOverwriteQuantity() {
        val vm = onMain { SeriesViewModel() }
        onMain { vm.selectSeries(series) }
        val stale = await({ vm.seriesGoods.value }) { list -> list.any { it.goodsCategory == "K1" } }
            .first { it.goodsCategory == "K1" }
        assertEquals(1, stale.goodsQuantity)

        // 데이터 입력(또는 다른 탭)이 DB 를 바꾼 상황
        runBlocking { goodsDao.update(byCategory("K1").copy(goodsStatus = GoodsStatus.GOTTEN.name, goodsQuantity = 3)) }

        // 열려 있는 시리즈 화면도 새 값을 보여야 한다.
        await({ vm.seriesGoods.value.first { it.goodsCategory == "K1" } }) { it.goodsQuantity == 3 }

        // 옛 값(수량 1)을 들고 상태를 바꿔도 수량 3 이 유지되어야 한다.
        onMain { vm.setPending(stale) }
        val afterPending = await({ byCategory("K1") }) { it.goodsStatus == GoodsStatus.PENDING.name }
        assertEquals(3, afterPending.goodsQuantity)

        onMain { vm.savePendingInfo(stale, PurchaseInfo(purchaseStore = "스토어", receiveDate = "2026-12-01")) }
        val afterInfo = await({ byCategory("K1") }) { it.goodsPurchaseStore == "스토어" }
        assertEquals(3, afterInfo.goodsQuantity)
        assertEquals("2026-12-01", afterInfo.goodsReceiveDate)
    }

    @Test
    fun staleMyPageSnapshot_doesNotRevertQuantity() {
        runBlocking { goodsDao.update(byCategory("K2").copy(goodsStatus = GoodsStatus.PENDING.name, goodsPurchaseDate = "2026-09-01")) }
        val vm = onMain { MyPageViewModel() }
        val k2Id = await({ vm.pendingOfficialGoods.value }) { it.isNotEmpty() }.single().goodsId

        // 다른 탭에서 수량을 4 로 바꾼 상황
        runBlocking { goodsDao.update(byCategory("K2").copy(goodsQuantity = 4)) }

        onMain { vm.setShipping(setOf(k2Id), emptySet(), "2026-09-20") }
        val shipped = await({ byCategory("K2") }) { it.goodsShippingDate == "2026-09-20" }
        assertEquals(4, shipped.goodsQuantity)

        onMain { vm.applyPurchaseInfo(setOf(k2Id), emptySet(), PurchaseInfoEdit("스토어", null, null)) }
        val edited = await({ byCategory("K2") }) { it.goodsPurchaseStore == "스토어" }
        assertEquals(4, edited.goodsQuantity)
        assertEquals("2026-09-01", edited.goodsPurchaseDate) // 손대지 않은 칸은 그대로
    }

    // ------------------------------------------------------------------ 상태 변경 규칙

    @Test
    fun notGotten_resetsQuantity_andLeavingPendingClearsShipping() = runBlocking {
        val id = byCategory("K1").goodsId
        goodsDao.update(byCategory("K1").copy(goodsStatus = GoodsStatus.PENDING.name, goodsQuantity = 3, goodsShippingDate = "2026-09-01"))

        goodsDao.setStatus(id, GoodsStatus.GOTTEN)
        val gotten = byCategory("K1")
        assertEquals(3, gotten.goodsQuantity)
        assertEquals("", gotten.goodsShippingDate)
        assertEquals(true, gotten.goodsIsGotten)

        goodsDao.toggleGotten(id)
        val notGotten = byCategory("K1")
        assertEquals(GoodsStatus.NOT_GOTTEN.name, notGotten.goodsStatus)
        assertEquals(1, notGotten.goodsQuantity)
        assertEquals(false, notGotten.goodsIsGotten)
    }

    @Test
    fun setGoods_followsComponents() = runBlocking {
        val c1 = byCategory("C1").goodsId
        val c2 = byCategory("C2").goodsId
        goodsDao.applyStatus(listOf(c1, c2), GoodsStatus.GOTTEN)
        assertEquals(GoodsStatus.GOTTEN.name, byCategory(CATEGORY_SET).goodsStatus)

        goodsDao.toggleGotten(c1)
        assertEquals(GoodsStatus.NOT_GOTTEN.name, byCategory(CATEGORY_SET).goodsStatus)
    }

    // ------------------------------------------------------------------ 2차창작 등록

    @Test
    fun fanAdd_withGottenChecked_isSavedAsGotten() {
        val vm = onMain { FanAddViewModel() }
        onMain {
            vm.onSeriesChange("새 굿즈")
            vm.onIsGottenChange(true)
            vm.insert(context) {}
        }
        val fan = await({ fanDao.getAll() }) { it.isNotEmpty() }.single()
        assertEquals(GoodsStatus.GOTTEN.name, fan.fanGoodsStatus)
        assertEquals(true, fan.isGotten)
    }
}
