package com.example.wowagoodsproject

import android.app.Application
import com.example.wowagoodsproject.db.changed.ChangedGoodsEntity
import com.example.wowagoodsproject.db.changed.restoreChangedGoods
import com.example.wowagoodsproject.db.official.GoodsEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 변경된 굿즈에서 초기화된 상태를 되돌리는 것. 메모리 DB 로 돌린다. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ChangedGoodsRestoreTest {

    private val changedDao get() = App.changedGoodsDatabase.changedGoodsDao()

    @Before
    fun setUp() = TestDb.setUp()

    @After
    fun tearDown() = TestDb.tearDown()

    @Test
    fun 상태를_되돌리면_처리로_넘어가고_미처리_수에서_빠진다() = runBlocking {
        App.database.goodsDao().insert(GoodsEntity(
            goodsUid = 7, goodsSeries = "1.0버전", goodsChara = "감심", goodsCategory = "인형",
            goodsPrice = "69위안", goodsMemo = "단품"
        ))
        val id = changedDao.insert(ChangedGoodsEntity(
            changedTime = 1L,
            oldSeries = "1.0버전", oldChara = "감심", oldCategory = "인형", oldPrice = "59위안", oldMemo = "단품",
            oldUrl = "", oldStatus = "GOTTEN", oldQuantity = 2,
            newSeries = "1.0버전", newChara = "감심", newCategory = "인형", newPrice = "69위안", newMemo = "단품",
            newUrl = "", newUid = 7, changedDetail = "가격: 59위안 → 69위안"
        )).toInt()
        assertEquals(1, changedDao.uncheckedCountFlow().first())

        assertEquals("상태를 되돌렸습니다", restoreChangedGoods(id))

        val record = changedDao.getById(id)!!
        assertTrue(record.isRestored)
        assertTrue(record.isChecked)
        assertEquals(0, changedDao.uncheckedCountFlow().first())
        val goods = App.database.goodsDao().getByUid(7)!!
        assertEquals("GOTTEN", goods.goodsStatus)
        assertEquals(2, goods.goodsQuantity)
    }
}
