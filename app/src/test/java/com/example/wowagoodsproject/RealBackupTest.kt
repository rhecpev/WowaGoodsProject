package com.example.wowagoodsproject

import android.app.Application
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.screen.mypage.BackupZip
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

/**
 * 실제로 쓰던 백업 파일과 지금의 원본 데이터로 불러오기를 돌려 본다.
 * 개인 파일이라 프로젝트에 넣지 않는다. 아래 환경 변수가 없으면 건너뛴다.
 * - WUWA_REAL_BACKUPS: 백업 zip 들이 있는 폴더
 * - WUWA_DATA_DIR: wuwa-goods-data 폴더 (goods.json, characters.json)
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class RealBackupTest {

    @Before
    fun setUp() = TestDb.setUp()

    @After
    fun tearDown() = TestDb.tearDown()

    @Test
    fun 실제_백업을_지금_데이터로_불러온다() = runBlocking {
        val backups = System.getenv("WUWA_REAL_BACKUPS")?.let(::File)?.listFiles { f -> f.extension == "zip" }.orEmpty()
        val dataDir = System.getenv("WUWA_DATA_DIR")?.let(::File)
        assumeTrue(backups.isNotEmpty() && dataDir != null && File(dataDir, "goods.json").isFile)

        val gson = Gson()
        val goods: List<GoodsEntity> = gson.fromJson(File(dataDir, "goods.json").readText(), object : TypeToken<List<GoodsEntity>>() {}.type)
        val charas: List<CharaEntity> = gson.fromJson(File(dataDir, "characters.json").readText(), object : TypeToken<List<CharaEntity>>() {}.type)

        for (zip in backups.sortedBy { it.name }) {
            TestDb.tearDown(); TestDb.setUp()
            goods.forEach { App.database.goodsDao().insert(it.copy(goodsId = 0)) }
            charas.forEach { App.charaDatabase.charaDao().insert(it.copy(charaId = 0)) }
            val filesDir = File(TestDb.context.filesDir, "real_${zip.nameWithoutExtension}").apply { deleteRecursively(); mkdirs() }

            val report = BackupZip.restore(zip.inputStream(), filesDir)

            val entries = ZipFile(zip).use { z -> z.entries().toList().map { it.name } }
            val images = entries.count { it.startsWith("images/") }
            val owned = App.database.goodsDao().getAll().count { it.goodsStatus != "NOT_GOTTEN" }
            val favorites = App.charaDatabase.charaDao().getAll().count { it.charaIsFavorite }
            println("REAL ${zip.name}: ${report.message()} | 보유·구매예정 반영 $owned | 사진 ${filesDir.list()!!.size}/$images | 선호 $favorites/${report.favorites}")

            assertEquals("${zip.name}: 찾은 수만큼 반영", report.restored, owned)
            assertEquals("${zip.name}: 사진을 모두 푼다", images, filesDir.list()!!.size)
            App.fanDatabase.fanGoodsDao().getAll().forEach {
                if (it.fanGoodsImgPath.isNotEmpty()) assertEquals("${zip.name}: 사진 경로", true, File(it.fanGoodsImgPath).isFile)
            }
        }
    }
}
