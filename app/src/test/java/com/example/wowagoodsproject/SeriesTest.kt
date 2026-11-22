package com.example.wowagoodsproject

import android.app.Application
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.seriesCards
import com.example.wowagoodsproject.db.allUsedImagePaths
import com.example.wowagoodsproject.db.betray.BetrayGenreEntity
import com.example.wowagoodsproject.db.betray.BetrayGoodsEntity
import com.example.wowagoodsproject.db.betray.BetraySeriesEntity
import com.example.wowagoodsproject.db.betray.deleteGenreWithGoods
import com.example.wowagoodsproject.db.betray.deleteSeriesWithImages
import com.example.wowagoodsproject.db.betray.saveSeries
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.fan.FanSeriesEntity
import com.example.wowagoodsproject.db.fan.deleteSeriesWithImages
import com.example.wowagoodsproject.db.fan.saveSeries
import com.example.wowagoodsproject.screen.fan.seriesOptionsOf
import com.example.wowagoodsproject.screen.mypage.BackupZip
import com.example.wowagoodsproject.screen.mypage.CleanTarget
import com.example.wowagoodsproject.screen.mypage.ExportPreview
import com.example.wowagoodsproject.screen.mypage.ExportSelection
import com.example.wowagoodsproject.screen.mypage.StorageCleaner
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
import java.util.zip.ZipFile

/** 시리즈(명조 2차창작·배신 굿즈): 등록·고치기·지우기, 카드 묶기, 백업, 용량 정리 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SeriesTest {

    private val betray get() = App.betrayDatabase.betrayDao()
    private val fan get() = App.fanDatabase.fanGoodsDao()
    private lateinit var restoreDir: File
    private var genre = 0

    @Before
    fun setUp() = runBlocking {
        TestDb.setUp()
        restoreDir = File(TestDb.context.filesDir, "restore").apply { deleteRecursively(); mkdirs() }
        genre = betray.insertGenre(BetrayGenreEntity(genreNm = "원신")).toInt()
    }

    @After
    fun tearDown() = TestDb.tearDown()

    private fun photo(name: String, size: Int = 30) =
        File(TestDb.context.filesDir, name).apply { writeBytes(ByteArray(size) { (it * 11 + size).toByte() }) }

    // ── 배신 굿즈 시리즈 ──

    @Test
    fun 배신_시리즈를_등록하고_이름을_바꾸면_굿즈_시리즈도_바뀐다() = runBlocking {
        betray.insertGoods(BetrayGoodsEntity(genreId = genre, goodsSeries = " 팝업 "))
        betray.insertGoods(BetrayGoodsEntity(genreId = genre, goodsSeries = "카페"))
        assertTrue("굿즈에만 있던 시리즈도 등록할 수 있다", betray.saveSeries(genre, null, "팝업", "/x/bg.webp", "/x/ic.webp"))
        assertTrue(betray.saveSeries(genre, "팝업", "2주년 팝업", null, null))
        assertEquals(listOf(BetraySeriesEntity(genre, "2주년 팝업", "/x/bg.webp", "/x/ic.webp")), betray.seriesOf(genre))
        assertEquals(setOf("2주년 팝업", "카페"), betray.goodsOf(genre).map { it.goodsSeries }.toSet())
    }

    @Test
    fun 배신_시리즈_이름이_비었거나_다른_등록_시리즈와_겹치면_저장하지_않는다() = runBlocking {
        assertTrue(betray.saveSeries(genre, null, "팝업", null, null))
        assertTrue(betray.saveSeries(genre, null, "카페", null, null))
        assertFalse(betray.saveSeries(genre, null, "  ", null, null))
        assertFalse(betray.saveSeries(genre, null, "팝업", null, null))
        assertFalse("대소문자만 달라도 겹침", betray.saveSeries(genre, "카페", "팝업", null, null))
        assertTrue("자기 이름 그대로 저장은 된다", betray.saveSeries(genre, "카페", "카페", null, null))
        val other = betray.insertGenre(BetrayGenreEntity(genreNm = "블루아카")).toInt()
        assertTrue("다른 장르는 같은 이름 가능", betray.saveSeries(other, null, "팝업", null, null))
    }

    @Test
    fun 배신_시리즈_사진을_바꾸거나_지우면_예전_사진을_지우고_굿즈는_남는다() = runBlocking {
        val bg1 = photo("betray_series_bg1.webp"); val bg2 = photo("betray_series_bg2.webp"); val ic = photo("betray_series_ic.webp")
        betray.insertGoods(BetrayGoodsEntity(genreId = genre, goodsSeries = "팝업"))
        betray.saveSeries(genre, null, "팝업", bg1.absolutePath, ic.absolutePath)
        betray.saveSeries(genre, "팝업", "팝업", bg2.absolutePath, null)
        assertFalse(bg1.exists())
        assertTrue(bg2.exists() && ic.exists())
        betray.deleteSeriesWithImages(genre, "팝업")
        assertFalse(bg2.exists() || ic.exists())
        assertTrue(betray.seriesOf(genre).isEmpty())
        assertEquals("굿즈는 그대로", listOf("팝업"), betray.goodsOf(genre).map { it.goodsSeries })
    }

    @Test
    fun 장르를_지우면_그_장르_시리즈와_사진도_지운다() = runBlocking {
        val bg = photo("betray_series_del.webp")
        betray.saveSeries(genre, null, "팝업", bg.absolutePath, "")
        betray.deleteGenreWithGoods(genre)
        assertTrue(betray.allSeries().isEmpty())
        assertFalse(bg.exists())
    }

    // ── 명조 2차창작 시리즈 ──

    @Test
    fun 이차창작_시리즈를_등록하고_이름을_바꾸면_굿즈_시리즈도_바뀐다() = runBlocking {
        fan.insert(FanGoodsEntity(fanGoodsSeries = "코믹월드"))
        fan.insert(FanGoodsEntity(fanGoodsSeries = "일러스타"))
        assertTrue(fan.saveSeries(null, "코믹월드", "/x/a.webp", "/x/b.webp"))
        assertTrue(fan.saveSeries("코믹월드", "코믹월드 2026", null, null))
        assertEquals(listOf(FanSeriesEntity("코믹월드 2026", "/x/a.webp", "/x/b.webp")), fan.allSeries())
        assertEquals(setOf("코믹월드 2026", "일러스타"), fan.getAll().map { it.fanGoodsSeries }.toSet())
        assertFalse(fan.saveSeries(null, "코믹월드 2026", null, null))
    }

    @Test
    fun 이차창작_시리즈를_지우면_사진만_지우고_굿즈는_남는다() = runBlocking {
        val bg = photo("fan_series_bg.webp")
        fan.insert(FanGoodsEntity(fanGoodsSeries = "코믹월드"))
        fan.saveSeries(null, "코믹월드", bg.absolutePath, "")
        fan.deleteSeriesWithImages("코믹월드")
        assertFalse(bg.exists())
        assertEquals(listOf("코믹월드"), fan.getAll().map { it.fanGoodsSeries })
    }

    // ── 카드·고르기 목록 ──

    @Test
    fun 시리즈_카드는_등록한_사진이_먼저고_굿즈_없는_등록_시리즈도_나온다() {
        data class G(val series: String, val status: GoodsStatus, val img: String, val date: String, val chara: String = "")
        val goods = listOf(
            G("팝업", GoodsStatus.GOTTEN, "/g/1.webp", "2026-01-02", "장리,금희"),
            G(" 팝업", GoodsStatus.PENDING, "", "2026-01-05", "금희"),
            G("", GoodsStatus.NOT_GOTTEN, "/g/2.webp", "2026-01-01")
        )
        val cards = seriesCards(goods, { it.series }, { it.status }, { it.img }, { it.date }, charaOf = { it.chara },
            registered = mapOf("팝업" to "/reg/bg.webp", "빈 시리즈" to ""),
            charaInfo = { n -> if (n == "장리") "/a/jangli.png" to 1 else "" to null },
            noSeriesName = "시리즈 없음")
        assertEquals("미분류는 맨 아래", listOf("팝업", "빈 시리즈", "시리즈 없음"), cards.map { it.name })
        val popup = cards[0]
        assertEquals(listOf(2, 1, 1), listOf(popup.total, popup.gotten, popup.pending))
        assertEquals("/reg/bg.webp", popup.cover)
        // 공식 시리즈 카드처럼 등장 캐릭터 칩: 선호 먼저, 보유/전체
        assertEquals(listOf("장리", "금희"), popup.charas.map { it.name })
        assertEquals(listOf(1 to 1, 1 to 2), popup.charas.map { it.gotten to it.total })
        assertTrue(popup.charas[0].favorite)
        assertEquals("/a/jangli.png", popup.charas[0].avatar)
        assertEquals("등록 사진이 없으면 굿즈 사진", "/g/2.webp", cards[2].cover)
        assertEquals(0, cards[1].total)
        assertTrue(cards[1].registered)
    }

    @Test
    fun 시리즈_고르기는_등록한_것이_먼저고_굿즈에만_있는_이름도_나온다() {
        val opts = seriesOptionsOf(listOf("팝업" to "/ic.webp", "가나" to ""), listOf(" 카페", "팝업", "", "카페"))
        assertEquals(listOf("가나", "팝업", "카페"), opts.map { it.name })
        assertEquals("/ic.webp", opts.single { it.name == "팝업" }.image)
    }

    // ── 백업 ──

    @Test
    fun 시리즈도_백업에_들어가고_불러오면_그대로_돌아온다() = runBlocking {
        val fbg = photo("fan_series_bk_bg.webp", 41); val fic = photo("fan_series_bk_ic.webp", 42)
        val bbg = photo("betray_series_bk_bg.webp", 43)
        fan.insert(FanGoodsEntity(fanGoodsSeries = "코믹월드"))
        fan.saveSeries(null, "코믹월드", fbg.absolutePath, fic.absolutePath)
        betray.saveSeries(genre, null, "팝업", bbg.absolutePath, "")
        val zip = File(TestDb.context.cacheDir, "series.zip")
        BackupZip.write(zip)
        val names = ZipFile(zip).use { z -> z.entries().asSequence().map { it.name }.toSet() }
        assertTrue(names.containsAll(listOf("fan_series.json", "images/fan_series_bk_bg.webp", "images/fan_series_bk_ic.webp", "images/betray_series_bk_bg.webp")))
        val shown = ExportPreview.load()
        assertEquals(1, shown.fanSeries)
        assertEquals(emptyList<String>(), shown.differencesFrom(ExportPreview.fromZip(zip)))

        fan.deleteAllSeries(); betray.deleteAllSeries()
        BackupZip.restore(zip.inputStream(), restoreDir)
        assertEquals(
            listOf(FanSeriesEntity("코믹월드", File(restoreDir, fbg.name).absolutePath, File(restoreDir, fic.name).absolutePath)),
            fan.allSeries()
        )
        assertEquals(listOf(BetraySeriesEntity(genre, "팝업", File(restoreDir, bbg.name).absolutePath, "")), betray.allSeries())
        assertTrue(File(restoreDir, fbg.name).readBytes().contentEquals(fbg.readBytes()))
    }

    @Test
    fun 시리즈_파일이_없는_예전_백업은_2차창작_시리즈를_건드리지_않는다() = runBlocking {
        fan.saveSeries(null, "코믹월드", "", "")
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("fan_goods.json")); z.write("""[{"fanGoodsSeries":"c1"}]""".toByteArray()); z.closeEntry()
        }
        BackupZip.restore(out.toByteArray().inputStream(), restoreDir)
        assertEquals(listOf("코믹월드"), fan.allSeries().map { it.seriesNm })
    }

    @Test
    fun 장르만_추출해도_그_장르_시리즈가_들어가고_다른_기기에_더해진다() = runBlocking {
        val bg = photo("betray_series_gs.webp")
        betray.insertGoods(BetrayGoodsEntity(genreId = genre, goodsSeries = "팝업"))
        betray.saveSeries(genre, null, "팝업", bg.absolutePath, "")
        fan.saveSeries(null, "명조쪽", "", "")
        val zip = File(TestDb.context.cacheDir, "gs.zip")
        BackupZip.write(zip, selection = ExportSelection.genreOnly(genre))
        val names = ZipFile(zip).use { z -> z.entries().asSequence().map { it.name }.toSet() }
        assertFalse("장르만 추출에는 명조 시리즈 파일이 없다", names.contains("fan_series.json"))
        assertTrue(names.contains("images/betray_series_gs.webp"))
        assertEquals(emptyList<String>(), ExportPreview.load(ExportSelection.genreOnly(genre)).differencesFrom(ExportPreview.fromZip(zip)))
        // 다른 기기(원신 없음)
        betray.deleteGenreWithGoods(genre)
        BackupZip.restore(zip.inputStream(), restoreDir)
        val g = betray.genres().single { it.genreNm == "원신" }
        assertEquals(listOf("팝업"), betray.seriesOf(g.genreId).map { it.seriesNm })
        assertEquals("명조 시리즈는 그대로", listOf("명조쪽"), fan.allSeries().map { it.seriesNm })
    }

    // ── 사진 사용처 · 용량 정리 ──

    @Test
    fun 시리즈_사진은_쓰는_사진으로_세서_용량_정리가_지우지_않는다() = runBlocking {
        val f = photo("fan_series_keep.webp"); val b = photo("betray_series_keep.webp"); val orphan = photo("fan_series_orphan.webp")
        fan.saveSeries(null, "코믹월드", f.absolutePath, "")
        betray.saveSeries(genre, null, "팝업", "", b.absolutePath)
        assertTrue(allUsedImagePaths().containsAll(listOf(f.absolutePath, b.absolutePath)))
        StorageCleaner.clean(TestDb.context, setOf(CleanTarget.TEMP_FILES))
        assertTrue(f.exists() && b.exists())
        assertFalse(orphan.exists())
        listOf(f, b).forEach { it.delete() }
        Unit
    }

    @Test
    fun 미분류는_가장_최근에_등록해도_맨_아래() {
        data class G(val s: String, val d: String)
        val cards = com.example.wowagoodsproject.component.seriesCards(
            goods = listOf(G("", "2026-11-19"), G("코믹", "2026-01-01"), G("팝업", "2026-05-01")),
            seriesOf = { it.s }, statusOf = { com.example.wowagoodsproject.component.GoodsStatus.GOTTEN },
            imageOf = { "" }, dateOf = { it.d },
            registered = mapOf("등록만" to ""), noSeriesName = "미분류"
        )
        assertEquals(listOf("팝업", "코믹", "등록만", "미분류"), cards.map { it.name })
    }
}
