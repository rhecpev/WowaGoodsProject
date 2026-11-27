package com.example.wowagoodsproject

import android.app.Application
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.db.betray.BetrayGenreEntity
import com.example.wowagoodsproject.db.betray.BetrayGoodsEntity
import com.example.wowagoodsproject.db.betray.saveSeries
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.fan.saveSeries
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.screen.mypage.BackupZip
import com.example.wowagoodsproject.screen.mypage.ExportSelection
import com.example.wowagoodsproject.screen.mypage.ImportPart
import com.example.wowagoodsproject.screen.mypage.ImportPreviewer
import com.example.wowagoodsproject.screen.mypage.RestoreParts
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** 불러오기 전 항목별 비교와, 고른 항목만 불러오기 · 장르 복구(합치기) */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ImportPreviewTest {

    private val goods get() = App.database.goodsDao()
    private val fan get() = App.fanDatabase.fanGoodsDao()
    private val chara get() = App.charaDatabase.charaDao()
    private val betray get() = App.betrayDatabase.betrayDao()
    private lateinit var zip: File
    private var genshin = 0

    @Before
    fun setUp() = runBlocking {
        TestDb.setUp()
        goods.insert(GoodsEntity(goodsUid = 1, goodsSeries = "1.0", goodsChara = "장리", goodsCategory = "키링", goodsStatus = "GOTTEN", goodsIsGotten = true))
        goods.insert(GoodsEntity(goodsUid = 2, goodsSeries = "1.0", goodsChara = "금희", goodsCategory = "키링", goodsStatus = "NOT_GOTTEN"))
        fan.insert(FanGoodsEntity(fanGoodsSeries = "코믹", fanGoodsChara = "장리", fanGoodsImgPath = photo("fan_p.webp").absolutePath))
        fan.saveSeries(null, "코믹", photo("fan_series_p.webp").absolutePath, "")
        chara.insert(CharaEntity(charaNm = "장리", charaIsFavorite = true, charaFavoriteOrder = 1))
        chara.insert(CharaEntity(charaNm = "금희"))
        genshin = betray.insertGenre(BetrayGenreEntity(genreNm = "원신", genreOrder = 1)).toInt()
        betray.insertGoods(BetrayGoodsEntity(genreId = genshin, goodsChara = "푸리나", goodsStatus = GoodsStatus.GOTTEN.name, goodsImgPath = photo("betray_p.webp").absolutePath))
        betray.saveSeries(genshin, null, "팝업", "", "")
        zip = File(TestDb.context.cacheDir, "all.zip")
        BackupZip.write(zip)
        Unit
    }

    @After
    fun tearDown() = TestDb.tearDown()

    private fun photo(name: String) = File(TestDb.context.filesDir, name).apply { writeBytes(ByteArray(20) { it.toByte() }) }
    private suspend fun plan() = zip.inputStream().use { ImportPreviewer.inspect(it) }

    @Test
    fun 같은_기기의_백업은_모든_항목이_같음이고_배신_굿즈는_처음에_꺼져_있다() = runBlocking {
        val p = plan()
        assertEquals(ImportPart.entries.toSet(), p.sections.map { it.part }.toSet())
        p.sections.forEach { assertFalse("${it.part} 같음", it.changed) }
        assertTrue(p.defaultSelection.isEmpty())
    }

    @Test
    fun 바뀐_항목만_변경_있음이고_지금과_파일_숫자가_보인다() = runBlocking {
        // 파일을 만든 뒤 기기에서 바꾼다
        goods.getAll().forEach { goods.update(it.copy(goodsStatus = "NOT_GOTTEN", goodsIsGotten = false)) }
        fan.insert(FanGoodsEntity(fanGoodsSeries = "새 행사"))
        chara.getAll().forEach { chara.update(it.copy(charaIsFavorite = false, charaFavoriteOrder = 0)) }
        betray.insertGoods(BetrayGoodsEntity(genreId = genshin, goodsChara = "나히다"))
        betray.insertGenre(BetrayGenreEntity(genreNm = "블루아카", genreOrder = 2))
        val p = plan()
        val official = p.section(ImportPart.OFFICIAL)!!
        assertTrue(official.changed)
        assertEquals("보유 0 · 구매예정 0", official.now)
        assertEquals("보유 1 · 구매예정 0", official.file)
        assertTrue(official.details.any { it.contains("상태가 바뀌는 굿즈 1개") })
        val fanSec = p.section(ImportPart.FAN)!!
        assertEquals("2개" to "1개", fanSec.now to fanSec.file)
        assertTrue(fanSec.changed)
        assertTrue(p.section(ImportPart.FAVORITES)!!.changed)
        assertFalse("시리즈는 그대로", p.section(ImportPart.FAN_SERIES)!!.changed)
        val b = p.section(ImportPart.BETRAY)!!
        assertTrue(b.changed)
        assertTrue(b.details.any { it.contains("원신: 굿즈 2개 → 1개") })
        assertTrue("전체 백업이라 파일에 없는 장르는 지워진다고 알린다", b.details.any { it.contains("블루아카: 파일에 없어 지워집니다") })
        assertEquals(setOf(ImportPart.OFFICIAL, ImportPart.FAN, ImportPart.FAVORITES), p.defaultSelection)
    }

    @Test
    fun 고른_항목만_불러오고_나머지는_그대로다() = runBlocking {
        goods.getAll().forEach { goods.update(it.copy(goodsStatus = "NOT_GOTTEN", goodsIsGotten = false)) }
        fan.insert(FanGoodsEntity(fanGoodsSeries = "새 행사"))
        betray.insertGenre(BetrayGenreEntity(genreNm = "블루아카", genreOrder = 2))
        // 공식 보유 현황만
        BackupZip.restore(zip.inputStream(), TestDb.context.filesDir, RestoreParts.of(setOf(ImportPart.OFFICIAL)))
        assertEquals("공식은 파일 것", "GOTTEN", goods.getAll().single { it.goodsUid == 1 }.goodsStatus)
        assertEquals("2차창작은 그대로", 2, fan.getAll().size)
        assertTrue("배신 굿즈는 그대로", betray.genres().any { it.genreNm == "블루아카" })
    }

    @Test
    fun 고르지_않은_항목의_사진은_남지_않는다() = runBlocking {
        val restoreDir = File(TestDb.context.filesDir, "restore").apply { deleteRecursively(); mkdirs() }
        // 다른 기기처럼: 파일의 사진을 이 기기에서는 아무도 쓰지 않는다
        fan.deleteAll(); fan.deleteAllSeries(); betray.deleteAllGoods(); betray.deleteAllSeries()
        BackupZip.restore(zip.inputStream(), restoreDir, RestoreParts.of(setOf(ImportPart.FAVORITES)))
        assertTrue("선호 캐릭터만 불러오면 사진은 하나도 풀리지 않는다", restoreDir.listFiles().orEmpty().none { it.isFile })
    }

    @Test
    fun 장르_복구는_전체_백업이라도_합치기로_배신_굿즈만_불러온다() = runBlocking {
        val blue = betray.insertGenre(BetrayGenreEntity(genreNm = "블루아카", genreOrder = 2)).toInt()
        goods.getAll().forEach { goods.update(it.copy(goodsStatus = "NOT_GOTTEN", goodsIsGotten = false)) }
        betray.deleteGoodsOf(genshin)

        val section = zip.inputStream().use { ImportPreviewer.inspectGenres(it) }!!
        assertTrue(section.details.first().contains("다른 장르는 그대로"))
        assertFalse("합치기라 지워진다는 말이 없다", section.details.any { it.contains("지워집니다") })

        BackupZip.restore(zip.inputStream(), TestDb.context.filesDir, RestoreParts.BETRAY_MERGE)
        assertEquals("원신 굿즈가 돌아온다", listOf("푸리나"), betray.goodsOf(genshin).map { it.goodsChara })
        assertTrue("파일에 없는 블루아카는 그대로", betray.genre(blue) != null)
        assertEquals("명조는 그대로", "NOT_GOTTEN", goods.getAll().single { it.goodsUid == 1 }.goodsStatus)
    }

    @Test
    fun 배신_굿즈가_없는_파일은_장르_복구_대상이_아니다() = runBlocking {
        val noBetray = File(TestDb.context.cacheDir, "nob.zip")
        BackupZip.write(noBetray, selection = ExportSelection(includeWuwa = true, genreIds = emptySet()))
        assertNull(noBetray.inputStream().use { ImportPreviewer.inspectGenres(it) })
        assertNull("배신 굿즈 항목이 없다", noBetray.inputStream().use { ImportPreviewer.inspect(it) }.section(ImportPart.BETRAY))
    }

    @Test
    fun 장르만_추출한_파일은_명조_항목_없이_배신_굿즈만_보인다() = runBlocking {
        val gs = File(TestDb.context.cacheDir, "gs.zip")
        BackupZip.write(gs, selection = ExportSelection.genreOnly(genshin))
        val p = gs.inputStream().use { ImportPreviewer.inspect(it) }
        assertEquals(listOf(ImportPart.BETRAY), p.sections.map { it.part })
        assertTrue(p.section(ImportPart.BETRAY)!!.details.first().contains("다른 장르는 그대로"))
    }

    @Test
    fun 백업이_아닌_파일은_알려준다() = runBlocking {
        val bad = File(TestDb.context.cacheDir, "bad.zip")
        java.util.zip.ZipOutputStream(bad.outputStream()).use { z -> z.putNextEntry(java.util.zip.ZipEntry("hello.txt")); z.write(1); z.closeEntry() }
        val r = runCatching { bad.inputStream().use { ImportPreviewer.inspect(it) } }
        assertEquals("백업 파일이 아닙니다", r.exceptionOrNull()?.message)
    }

    @Test
    fun 장르_복구에_명조_전체_추출_파일이면_명조_항목도_보이고_배신_굿즈는_합치기로_비교한다() = runBlocking {
        betray.insertGenre(BetrayGenreEntity(genreNm = "블루아카", genreOrder = 2))
        val p = zip.inputStream().use { ImportPreviewer.inspect(it, betrayMerge = true) }
        assertTrue("명조 항목도 있다", p.section(ImportPart.OFFICIAL) != null && p.section(ImportPart.FAN) != null)
        val b = p.section(ImportPart.BETRAY)!!
        assertTrue(b.details.first().contains("다른 장르는 그대로"))
        assertFalse("합치기라 블루아카가 지워진다는 말이 없다", b.details.any { it.contains("블루아카") })
        // 배신 굿즈만 골라 합치기
        BackupZip.restore(zip.inputStream(), TestDb.context.filesDir, RestoreParts.of(setOf(ImportPart.BETRAY), betrayMerge = true))
        assertTrue(betray.genres().any { it.genreNm == "블루아카" })
    }

    @Test
    fun 보고서에_공식_2차창작_선호_캐릭터별_배신_장르가_정리되고_읽는_게이지가_끝까지_간다() = runBlocking {
        val progress = mutableListOf<Float>()
        val p = zip.inputStream().use { ImportPreviewer.inspect(it, totalBytes = zip.length()) { f -> progress += f } }
        val r = p.report
        assertEquals(1, r.officialGotten)
        assertEquals(0, r.officialPending)
        assertEquals(1, r.fanTotal)
        assertEquals(1, r.fanSeries)
        assertEquals(listOf("장리"), r.favorites.map { it.name })
        // 장리: 공식 1(보유) + 2차창작 1. 금희는 미보유라 보고서에 없다
        assertEquals(listOf("장리"), r.charas.map { it.name })
        assertEquals(1, r.charas.first().official)
        assertEquals(1, r.charas.first().fan)
        assertEquals("굿즈 단계 목록: 공식 1 + 2차창작 1", listOf(true, false), r.goods.map { it.official })
        assertEquals(listOf("원신" to 1), r.betrayGenres)
        assertTrue("게이지가 오른다", progress.size >= 2 && progress.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(1f, progress.last())
    }

    @Test
    fun 캐릭터별_굿즈_확인에_지금_보유인데_파일에_없는_굿즈가_보유에서_미보유로_나온다() = runBlocking {
        // 파일을 만든 뒤 기기에서 금희 굿즈를 보유로, 비공식 굿즈 하나를 더한다(파일에는 둘 다 없다)
        goods.getAll().first { it.goodsChara == "금희" }.let { goods.update(it.copy(goodsStatus = "GOTTEN", goodsIsGotten = true)) }
        fan.insert(FanGoodsEntity(fanGoodsSeries = "새 행사", fanGoodsChara = "금희", fanGoodsStatus = "GOTTEN", fanGoodsIsGotten = true))
        val rows = plan().report.goods
        val lost = rows.single { it.official && it.chara == "금희" }
        assertEquals(GoodsStatus.GOTTEN to GoodsStatus.NOT_GOTTEN, lost.change)
        assertEquals(GoodsStatus.NOT_GOTTEN, lost.status)
        val gone = rows.single { !it.official && it.series == "새 행사" }
        assertEquals("비공식은 없어진다", GoodsStatus.GOTTEN to null, gone.change)
        // 그대로인 굿즈는 바뀜 표시가 없다
        assertNull(rows.single { it.official && it.chara == "장리" }.change)
        assertNull(rows.single { !it.official && it.series == "코믹" }.change)
        // 배신 굿즈: 파일 장르와 같은 이름 장르에 파일에 없는 굿즈를 더하면 '없어짐'
        betray.insertGoods(BetrayGoodsEntity(genreId = genshin, goodsChara = "나히다", goodsStatus = GoodsStatus.PENDING.name))
        val genre = plan().report.betrayReview.single { it.genreName == "원신" }
        assertEquals(GoodsStatus.PENDING to null, genre.goods.single { it.chara == "나히다" }.change)
        assertNull(genre.goods.single { it.chara == "푸리나" }.change)
    }
}
