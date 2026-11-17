package com.example.wowagoodsproject

import android.app.Application
import com.example.wowagoodsproject.db.changed.ChangedGoodsEntity
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.news.NewsEntity
import com.example.wowagoodsproject.db.patchnote.PatchNoteEntity
import com.example.wowagoodsproject.screen.mypage.CleanTarget
import com.example.wowagoodsproject.screen.mypage.StorageCleaner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** 용량 정리: 지워도 되는 것만 지우고, 보유 데이터·쓰는 사진은 남긴다. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class StorageCleanerTest {

    private val ctx get() = TestDb.context
    private lateinit var usedPhoto: File
    private lateinit var orphanPhoto: File
    private lateinit var cameraTemp: File

    @Before
    fun setUp() = runBlocking {
        TestDb.setUp()
        usedPhoto = File(ctx.filesDir, "fan_used.jpg").apply { writeBytes(ByteArray(100)) }
        orphanPhoto = File(ctx.filesDir, "fan_orphan.jpg").apply { writeBytes(ByteArray(300)) }
        cameraTemp = File(ctx.cacheDir, "camera_temp_1.jpg").apply { writeBytes(ByteArray(50)) }
        App.fanDatabase.fanGoodsDao().insert(FanGoodsEntity(fanGoodsChara = "장리", fanGoodsImgPath = usedPhoto.absolutePath))
        App.patchNoteDatabase.patchNoteDao().insert(PatchNoteEntity(patchContent = "[굿즈 추가] 장리 키링"))
        App.newsDatabase.newsDao().insertAll(
            listOf(NewsEntity(newsChara = "읽음", newsIsRead = true), NewsEntity(newsChara = "안읽음", newsIsRead = false))
        )
        App.changedGoodsDatabase.changedGoodsDao().insert(ChangedGoodsEntity(newChara = "처리", isChecked = true))
        App.changedGoodsDatabase.changedGoodsDao().insert(ChangedGoodsEntity(newChara = "미처리", isChecked = false))
        Unit
    }

    @After
    fun tearDown() {
        listOf(usedPhoto, orphanPhoto, cameraTemp).forEach { it.delete() }
        TestDb.tearDown()
    }

    @Test
    fun 지울_거리를_세고_파일은_실제_크기로_잰다() = runBlocking {
        val items = StorageCleaner.scan(ctx).associateBy { it.target }
        assertEquals(1, items.getValue(CleanTarget.PATCH_NOTES).count)
        assertEquals(1, items.getValue(CleanTarget.READ_NEWS).count)        // 안 읽은 소식은 안 셈
        assertEquals(1, items.getValue(CleanTarget.CHECKED_CHANGES).count)  // 미처리는 안 셈
        val temp = items.getValue(CleanTarget.TEMP_FILES)
        assertEquals(2, temp.count)          // 카메라 임시 + 안 쓰는 사진 (쓰는 사진은 아님)
        assertEquals(350L, temp.bytes)
        assertFalse(temp.estimated)
        assertTrue(items.getValue(CleanTarget.PATCH_NOTES).estimated)
    }

    @Test
    fun 고른_것만_지우고_쓰는_사진과_안_읽은_소식_미처리_기록은_남긴다() = runBlocking {
        StorageCleaner.clean(ctx, setOf(CleanTarget.READ_NEWS, CleanTarget.CHECKED_CHANGES, CleanTarget.TEMP_FILES))

        assertTrue("2차창작이 쓰는 사진은 남아야 한다", usedPhoto.exists())
        assertFalse(orphanPhoto.exists())
        assertFalse(cameraTemp.exists())
        assertEquals(listOf("안읽음"), App.newsDatabase.newsDao().getAllFlow().first().map { it.newsChara })
        assertEquals(listOf("미처리"), App.changedGoodsDatabase.changedGoodsDao().getAllFlow().first().map { it.newChara })
        // 고르지 않은 업데이트 이력은 그대로
        assertEquals(1, App.patchNoteDatabase.patchNoteDao().getAll().size)
        // 보유 데이터(2차창작 굿즈)는 건드리지 않는다
        assertEquals(1, App.fanDatabase.fanGoodsDao().getAll().size)
    }
}
