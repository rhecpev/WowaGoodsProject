package com.example.wowagoodsproject

import android.app.Application
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.fan.deleteWithImage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class FanGoodsWritesTest {

    private val dao get() = App.fanDatabase.fanGoodsDao()

    @Before
    fun setUp() = TestDb.setUp()

    @After
    fun tearDown() = TestDb.tearDown()

    @Test
    fun 굿즈를_지우면_사진도_지운다() = runBlocking {
        val photo = File(TestDb.context.filesDir, "fan_1.jpg").apply { writeText("x") }
        dao.insert(FanGoodsEntity(fanGoodsImgPath = photo.absolutePath))
        dao.deleteWithImage(dao.getAll().single())
        assertFalse(photo.exists())
        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun 다른_굿즈가_같은_사진을_쓰면_남긴다() = runBlocking {
        val photo = File(TestDb.context.filesDir, "fan_shared.jpg").apply { writeText("x") }
        dao.insertAll(listOf(FanGoodsEntity(fanGoodsImgPath = photo.absolutePath), FanGoodsEntity(fanGoodsImgPath = photo.absolutePath)))
        dao.deleteWithImage(dao.getAll().first())
        assertTrue(photo.exists())
    }

    @Test
    fun 사진이_없는_굿즈도_지운다() = runBlocking {
        dao.insert(FanGoodsEntity(fanGoodsImgPath = ""))
        dao.deleteWithImage(dao.getAll().single())
        assertTrue(dao.getAll().isEmpty())
    }
}
