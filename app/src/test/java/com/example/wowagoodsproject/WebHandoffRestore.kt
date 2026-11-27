package com.example.wowagoodsproject

import android.app.Application
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.screen.mypage.BackupZip
import com.example.wowagoodsproject.screen.mypage.ImportPart
import com.example.wowagoodsproject.screen.mypage.ImportPreviewer
import com.example.wowagoodsproject.screen.mypage.RestoreParts
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 웹(wuwa-goods-web)에서 추출한 백업을 앱의 불러오기 코드로 읽는다(웹 → 앱). 기기·에뮬레이터는 쓰지 않는다.
 * 환경 변수가 없으면 건너뛴다.
 * - WUWA_DATA_DIR: wuwa-goods-data 폴더
 * - WUWA_WEB_EXPORT_DIR: 웹에서 내려받은 zip 폴더(wuwaGoods_web_*_전체.zip, wuwaGoods_web_*_테스트장르.zip)
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class WebHandoffRestore {

    @Before
    fun setUp() = TestDb.setUp()

    @After
    fun tearDown() = TestDb.tearDown()

    private fun files(): Pair<File, File>? {
        val dataDir = System.getenv("WUWA_DATA_DIR")?.let(::File) ?: return null
        val dir = System.getenv("WUWA_WEB_EXPORT_DIR")?.let(::File) ?: return null
        val full = dir.listFiles()?.firstOrNull { it.name.startsWith("wuwaGoods_web_") && it.name.endsWith("_전체.zip") } ?: return null
        val genre = dir.listFiles()?.firstOrNull { it.name.endsWith("_테스트장르.zip") } ?: return null
        val gson = Gson()
        runBlocking {
            gson.fromJson<List<GoodsEntity>>(File(dataDir, "goods.json").readText(), object : TypeToken<List<GoodsEntity>>() {}.type)
                .forEach { App.database.goodsDao().insert(it.copy(goodsId = 0)) }
            gson.fromJson<List<CharaEntity>>(File(dataDir, "characters.json").readText(), object : TypeToken<List<CharaEntity>>() {}.type)
                .forEach { App.charaDatabase.charaDao().insert(it.copy(charaId = 0)) }
        }
        return full to genre
    }

    @Test
    fun 웹_전체_추출을_앱이_불러온다() = runBlocking {
        val (full, _) = files() ?: return@runBlocking assumeTrue(false)
        val dir = File(TestDb.context.filesDir, "web_full").apply { deleteRecursively(); mkdirs() }

        // 앱의 불러오기 전 확인도 깨지지 않고 읽는다
        val plan = full.inputStream().use { ImportPreviewer.inspect(it) }
        println("APP PLAN " + plan.sections.joinToString(" | ") { "${it.part}: ${it.now} -> ${it.file}" })
        assertTrue(plan.section(ImportPart.BETRAY) != null && plan.section(ImportPart.FAN_SERIES) != null)

        val report = BackupZip.restore(full.inputStream(), dir)
        println("APP REPORT " + report.message())
        val goods = App.database.goodsDao().getAll()
        assertEquals(5, goods.count { it.goodsStatus == "GOTTEN" })
        assertEquals(3, goods.count { it.goodsStatus == "PENDING" && it.goodsPurchaseStore == "알리익스프레스" })
        assertEquals(listOf("릴리", "레베카"), App.charaDatabase.charaDao().getAll().filter { it.charaIsFavorite }.sortedBy { it.charaFavoriteOrder }.map { it.charaNm })

        val fan = App.fanDatabase.fanGoodsDao().getAll()
        assertEquals(3, fan.size)
        val webFan = fan.single { it.fanGoodsSeries == "웹 비공식" }
        assertEquals("웹에서 등록한 비공식 굿즈 번호 유지", 3, webFan.fanGoodsId)
        assertEquals("GOTTEN", webFan.fanGoodsStatus)
        fan.forEach { assertTrue("비공식 사진 파일 ${it.fanGoodsImgPath}", File(it.fanGoodsImgPath).isFile) }
        val fanSeries = App.fanDatabase.fanGoodsDao().allSeries()
        assertEquals(setOf("앱테스트 코믹", "웹에서 만든 시리즈"), fanSeries.map { it.seriesNm }.toSet())

        val b = App.betrayDatabase.betrayDao()
        val genres = b.genres()
        assertEquals(listOf("원신", "블루아카", "테스트장르"), genres.map { it.genreNm })
        val test = genres.single { it.genreNm == "테스트장르" }
        assertTrue("웹에서 만든 장르 배경", File(test.genreImgPath).isFile)
        assertTrue("웹에서 만든 장르 아이콘", File(test.genreIconPath).isFile)
        val webGoods = b.goodsOf(test.genreId).single()
        assertEquals("웹 팝업 2026", webGoods.goodsSeries)
        assertEquals("웹캐릭", webGoods.goodsChara)
        assertEquals("PENDING", webGoods.goodsStatus)
        assertEquals("웹 스토어", webGoods.goodsPurchaseStore)
        assertTrue("웹에서 올린 굿즈 사진(WebP)", File(webGoods.goodsImgPath).isFile && webGoods.goodsImgPath.endsWith(".webp"))
        assertEquals(listOf("웹캐릭"), b.charasOf(test.genreId).map { it.charaNm })
        // 원신은 웹에서 장르만 입력으로 되돌린 상태, 블루아카 호시노는 웹에서 구매예정으로 바꾼 상태
        val gs = genres.single { it.genreNm == "원신" }
        assertEquals("NOT_GOTTEN", b.goodsOf(gs.genreId).single { it.goodsChara == "느비예트" }.goodsStatus)
        assertEquals("PENDING", b.goodsOf(genres.single { it.genreNm == "블루아카" }.genreId).single().goodsStatus)
        assertEquals(listOf("폰타인 팝업"), b.seriesOf(gs.genreId).map { it.seriesNm })
        assertTrue(File(b.charasOf(gs.genreId).single().charaImgPath).isFile)
    }

    @Test
    fun 웹_장르만_추출을_앱_장르_입력으로_불러온다() = runBlocking {
        val (_, genre) = files() ?: return@runBlocking assumeTrue(false)
        val dir = File(TestDb.context.filesDir, "web_genre").apply { deleteRecursively(); mkdirs() }
        // 장르 목록의 '복구'처럼 배신 굿즈만 합치기로
        val plan = genre.inputStream().use { ImportPreviewer.inspect(it, betrayMerge = true) }
        println("APP GENRE PLAN " + plan.sections.joinToString(" | ") { "${it.part}: ${it.details}" })
        BackupZip.restore(genre.inputStream(), dir, RestoreParts.of(setOf(ImportPart.BETRAY), betrayMerge = true))
        val b = App.betrayDatabase.betrayDao()
        val g = b.genres().single()
        assertEquals("테스트장르", g.genreNm)
        val goods = b.goodsOf(g.genreId).single()
        assertEquals("웹캐릭2", goods.goodsChara)
        assertEquals("웹 팝업 2026", goods.goodsSeries)
        assertEquals("5,500원", goods.goodsPrice)
        assertEquals("GOTTEN", goods.goodsStatus)
        assertEquals(listOf("새캐릭", "웹캐릭2"), b.favoritesOf(g.genreId).map { it.charaNm })
        assertEquals(setOf("새캐릭", "웹캐릭2"), b.charasOf(g.genreId).map { it.charaNm }.toSet())
        assertEquals(listOf("웹 팝업 2026"), b.seriesOf(g.genreId).map { it.seriesNm })
        assertTrue(File(goods.goodsImgPath).isFile)
        // 명조는 건드리지 않는다
        assertEquals(0, App.database.goodsDao().getAll().count { it.goodsStatus != "NOT_GOTTEN" })
        assertEquals(0, App.fanDatabase.fanGoodsDao().getAll().size)
    }
}
