package com.example.wowagoodsproject

import android.app.Application
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.db.betray.BetrayFavoriteEntity
import com.example.wowagoodsproject.db.betray.BetrayGenreEntity
import com.example.wowagoodsproject.db.betray.BetrayGoodsEntity
import com.example.wowagoodsproject.db.betray.BetrayKind
import com.example.wowagoodsproject.db.betray.deleteGenreWithGoods
import com.example.wowagoodsproject.db.betray.toggleFavorite
import com.example.wowagoodsproject.db.betray.toggleGotten
import com.example.wowagoodsproject.db.betray.togglePending
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.screen.betray.NO_SERIES
import com.example.wowagoodsproject.screen.betray.betrayCharas
import com.example.wowagoodsproject.screen.betray.betraySeries
import com.example.wowagoodsproject.screen.betray.searchBetray
import com.example.wowagoodsproject.screen.betray.sortForList
import com.example.wowagoodsproject.screen.mypage.BackupZip
import com.example.wowagoodsproject.screen.mypage.CleanTarget
import com.example.wowagoodsproject.screen.mypage.ExportPreview
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 배신 굿즈: 화면 계산, 쓰기, 백업 왕복, 용량 정리 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BetrayTest {

    private val dao get() = App.betrayDatabase.betrayDao()
    private lateinit var filesDir: File
    private lateinit var workDir: File

    @Before
    fun setUp() {
        TestDb.setUp()
        filesDir = File(TestDb.context.filesDir, "restore").apply { deleteRecursively(); mkdirs() }
        workDir = File(TestDb.context.cacheDir, "work").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun tearDown() = TestDb.tearDown()

    private fun g(
        series: String, chara: String, status: GoodsStatus = GoodsStatus.NOT_GOTTEN,
        kind: BetrayKind = BetrayKind.OFFICIAL, date: String = "2026-01-01", genreId: Int = 1,
        img: String = "", id: Int = 0, category: String = "키링", memo: String = ""
    ) = BetrayGoodsEntity(
        goodsId = id, genreId = genreId, goodsKind = kind.name, goodsRegDate = date, goodsSeries = series,
        goodsChara = chara, goodsStatus = status.name, goodsImgPath = img, goodsCategory = category, goodsMemo = memo
    )

    // ── 화면 계산 ──

    @Test
    fun 공식_시리즈는_종류별로_묶고_최근_등록_순이다() {
        val goods = listOf(
            g("팝업", "A", GoodsStatus.GOTTEN, date = "2026-01-01"),
            g("팝업", "B", GoodsStatus.PENDING, date = "2026-01-03"),
            g("카페", "A", date = "2026-01-02", img = "/x/cafe.jpg"),
            g(" ", "A", date = "2026-01-05"),                         // 시리즈 없음
            g("팝업", "A", kind = BetrayKind.FAN, date = "2026-02-01")  // 2차창작은 공식 탭에 안 나온다
        )
        val s = betraySeries(goods, BetrayKind.OFFICIAL, emptyList())
        assertEquals(listOf(NO_SERIES, "팝업", "카페"), s.map { it.name })
        val popup = s.single { it.name == "팝업" }
        assertEquals(listOf(2, 1, 1), listOf(popup.total, popup.gotten, popup.pending))
        assertEquals("/x/cafe.jpg", s.single { it.name == "카페" }.cover)
    }

    @Test
    fun 캐릭터는_굿즈에서_만들고_선호가_우선순위대로_먼저다() {
        val goods = listOf(
            g("s", "A", GoodsStatus.GOTTEN), g("s", "A"), g("s", "A"),
            g("s", "B,C", img = "/x/bc.jpg"), g("s", "C", img = "/x/c.jpg")
        )
        val favorites = listOf(BetrayFavoriteEntity(1, "C", 1), BetrayFavoriteEntity(1, "B", 2))
        val charas = betrayCharas(goods, favorites)
        assertEquals(listOf("C", "B", "A"), charas.map { it.name })
        assertEquals("/x/c.jpg", charas.first().cover) // 혼자 나온 굿즈 사진을 먼저
        assertEquals(1, charas.single { it.name == "A" }.gotten)
        assertEquals(3, charas.single { it.name == "A" }.total)
    }

    @Test
    fun 검색은_모든_낱말이_어딘가에_들어_있어야_한다() {
        val goods = listOf(
            g("2주년 팝업", "푸리나", category = "아크릴"),
            g("2주년 팝업", "나히다", category = "키링", memo = "한정"),
            g("카페", "푸리나", kind = BetrayKind.FAN)
        )
        assertEquals(2, searchBetray(goods, "푸리나").size)
        assertEquals(listOf("나히다"), searchBetray(goods, "팝업  한정").map { it.goodsChara })
        assertEquals(1, searchBetray(goods, "푸리나", BetrayKind.FAN).size)
        assertTrue(searchBetray(goods, "   ").isEmpty())
    }

    @Test
    fun 목록은_시리즈끼리_붙어_있다() {
        val goods = listOf(
            g("A", "x", date = "2026-01-01", id = 1),
            g("B", "x", date = "2026-01-05", id = 2),
            g("A", "x", date = "2026-01-09", id = 3)
        )
        assertEquals(listOf(3, 1, 2), sortForList(goods).map { it.goodsId })
    }

    // ── 쓰기 ──

    @Test
    fun 상태_전환과_선호_토글과_장르_삭제() = runBlocking {
        val genre = dao.insertGenre(BetrayGenreEntity(genreNm = "원신")).toInt()
        val photo = File(TestDb.context.filesDir, "betray_del.jpg").apply { writeBytes(ByteArray(10)) }
        val id = dao.insertGoods(g("s", "A", genreId = genre, img = photo.absolutePath)).toInt()

        dao.togglePending(id)
        assertEquals(GoodsStatus.PENDING, dao.goodsById(id)!!.status)
        dao.toggleGotten(id)
        assertEquals(GoodsStatus.GOTTEN, dao.goodsById(id)!!.status)
        dao.toggleGotten(id)
        assertEquals(GoodsStatus.NOT_GOTTEN, dao.goodsById(id)!!.status)

        dao.toggleFavorite(genre, "A"); dao.toggleFavorite(genre, "B")
        assertEquals(listOf("A" to 1, "B" to 2), dao.favoritesOf(genre).map { it.charaNm to it.favoriteOrder })
        dao.toggleFavorite(genre, "A")
        assertEquals(listOf("B"), dao.favoritesOf(genre).map { it.charaNm })

        dao.deleteGenreWithGoods(genre)
        assertTrue(dao.genres().isEmpty() && dao.allGoods().isEmpty() && dao.allFavorites().isEmpty())
        assertFalse("지운 장르의 사진도 지운다", photo.exists())
    }

    // ── 백업 ──

    private suspend fun seedBetray(): File {
        val photo = File(TestDb.context.filesDir, "betray_a.jpg").apply { writeBytes(ByteArray(777) { it.toByte() }) }
        val genshin = dao.insertGenre(BetrayGenreEntity(genreNm = "원신", genreOrder = 1)).toInt()
        val bluearchive = dao.insertGenre(BetrayGenreEntity(genreNm = "블루아카", genreOrder = 2)).toInt()
        dao.insertGoods(g("팝업", "푸리나", GoodsStatus.GOTTEN, genreId = genshin, img = photo.absolutePath).copy(goodsQuantity = 3))
        dao.insertGoods(g("팝업", "나히다", GoodsStatus.PENDING, genreId = genshin).copy(goodsPurchaseStore = "공식몰"))
        dao.insertGoods(g("코믹", "호시노", kind = BetrayKind.FAN, genreId = bluearchive))
        dao.toggleFavorite(genshin, "푸리나")
        return photo
    }

    private suspend fun betrayState() = Triple(
        dao.genres().map { it.genreNm to it.genreOrder },
        dao.allGoods().map { it.copy(goodsId = 0, goodsImgPath = File(it.goodsImgPath).name.takeIf { n -> it.goodsImgPath.isNotEmpty() }.orEmpty()) }
            .sortedBy { it.goodsChara },
        dao.allFavorites().map { it.charaNm to it.favoriteOrder }
    )

    @Test
    fun 배신_굿즈도_백업에_들어가고_불러오면_그대로_돌아온다() = runBlocking {
        val photo = seedBetray()
        val before = betrayState()
        val zip = File(workDir, "b.zip")
        BackupZip.write(zip)

        // 추출 확인 시트 숫자 = 파일 숫자
        val shown = ExportPreview.load()
        assertEquals(emptyList<String>(), shown.differencesFrom(ExportPreview.fromZip(zip)))
        assertEquals(listOf(2, 3, 1), listOf(shown.betrayGenres, shown.betrayGoods, shown.betrayGotten))
        assertEquals(1, shown.photoCount)

        // 다 지우고 불러오기
        dao.deleteAllGoods(); dao.deleteAllFavorites(); dao.deleteAllGenres()
        val report = BackupZip.restore(zip.inputStream(), filesDir)
        assertEquals(before, betrayState())
        assertEquals(3, report.betrayGoods)
        assertTrue(report.message().contains("배신 굿즈 3개"))
        // 사진은 바이트까지 같고, 되살린 굿즈가 그 사진을 가리킨다
        val restoredPhoto = File(filesDir, "betray_a.jpg")
        assertTrue(restoredPhoto.readBytes().contentEquals(photo.readBytes()))
        assertEquals(restoredPhoto.absolutePath, dao.allGoods().single { it.goodsChara == "푸리나" }.goodsImgPath)
        // 장르 번호가 그대로라 선호 캐릭터가 같은 장르를 가리킨다
        val genshin = dao.genres().single { it.genreNm == "원신" }.genreId
        assertEquals(listOf("푸리나"), dao.favoritesOf(genshin).map { it.charaNm })
    }

    @Test
    fun 배신_굿즈_파일이_없는_옛_백업은_배신_굿즈를_건드리지_않는다() = runBlocking {
        seedBetray()
        val before = betrayState()
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("fan_goods.json")); z.write("""[{"fanGoodsSeries":"c1"}]""".toByteArray()); z.closeEntry()
        }
        BackupZip.restore(out.toByteArray().inputStream(), filesDir)
        assertEquals(before, betrayState())
    }

    @Test
    fun 배신_굿즈만_있는_백업도_불러온다() = runBlocking {
        seedBetray()
        val zip = File(workDir, "only.zip")
        BackupZip.write(zip)
        dao.deleteAllGoods(); dao.deleteAllFavorites(); dao.deleteAllGenres()
        // 명조 쪽은 아무것도 없어도 배신 굿즈가 있으면 빈 백업이 아니다
        assertFalse(ExportPreview.fromZip(zip).isEmpty)
        BackupZip.restore(zip.inputStream(), filesDir)
        assertEquals(3, dao.allGoods().size)
    }

    @Test
    fun 용량_정리는_배신_굿즈_사진을_지우지_않는다() = runBlocking {
        val betrayPhoto = File(TestDb.context.filesDir, "betray_keep.jpg").apply { writeBytes(ByteArray(50)) }
        val orphan = File(TestDb.context.filesDir, "betray_orphan.jpg").apply { writeBytes(ByteArray(60)) }
        val fanPhoto = File(TestDb.context.filesDir, "fan_keep.jpg").apply { writeBytes(ByteArray(70)) }
        val genre = dao.insertGenre(BetrayGenreEntity(genreNm = "원신")).toInt()
        dao.insertGoods(g("s", "A", genreId = genre, img = betrayPhoto.absolutePath))
        App.fanDatabase.fanGoodsDao().insert(FanGoodsEntity(fanGoodsImgPath = fanPhoto.absolutePath))

        StorageCleaner.clean(TestDb.context, setOf(CleanTarget.TEMP_FILES))

        assertTrue(betrayPhoto.exists())
        assertTrue(fanPhoto.exists())
        assertFalse(orphan.exists())
        listOf(betrayPhoto, fanPhoto).forEach { it.delete() }
    }
}
