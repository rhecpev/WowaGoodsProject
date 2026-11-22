package com.example.wowagoodsproject

import android.app.Application
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.component.charaShares
import com.example.wowagoodsproject.db.betray.BetrayCharaEntity
import com.example.wowagoodsproject.db.betray.BetrayGenreEntity
import com.example.wowagoodsproject.db.betray.BetrayGoodsEntity
import com.example.wowagoodsproject.db.betray.BetrayKind
import com.example.wowagoodsproject.db.betray.saveChara
import com.example.wowagoodsproject.db.betray.setFavorite
import com.example.wowagoodsproject.db.betray.toggleFavorite
import com.example.wowagoodsproject.db.betray.deleteCharaWithImage
import com.example.wowagoodsproject.db.betray.reorderFavorites
import com.example.wowagoodsproject.db.betray.saveSeries
import com.example.wowagoodsproject.db.fan.saveSeries
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.screen.betray.charaKindBreakdown
import com.example.wowagoodsproject.screen.mypage.BackupExport
import com.example.wowagoodsproject.screen.mypage.BackupZip
import com.example.wowagoodsproject.screen.mypage.BetrayBackup
import com.example.wowagoodsproject.screen.mypage.CleanTarget
import com.example.wowagoodsproject.screen.mypage.ExportPreview
import com.example.wowagoodsproject.screen.mypage.ExportSelection
import com.example.wowagoodsproject.screen.mypage.StorageCleaner
import com.google.gson.Gson
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
import java.util.zip.ZipFile

/**
 * 추출 범위(전체 / 장르 골라서 / 장르 하나만)와 그 파일 불러오기, 공식·2차창작 범례 수, 캐릭터 선호 정하기.
 * 경우마다: 파일에 무엇이 들어가는지, 추출 확인 숫자 = 파일 숫자인지, 불러오면 무엇이 바뀌고 무엇이 그대로인지.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BetrayExportTest {

    private val dao get() = App.betrayDatabase.betrayDao()
    private lateinit var restoreDir: File
    private lateinit var workDir: File
    private var genshin = 0
    private var blue = 0

    @Before
    fun setUp() = runBlocking {
        TestDb.setUp()
        restoreDir = File(TestDb.context.filesDir, "restore").apply { deleteRecursively(); mkdirs() }
        workDir = File(TestDb.context.cacheDir, "work").apply { deleteRecursively(); mkdirs() }
        // 명조 데이터
        App.database.goodsDao().insert(GoodsEntity(goodsUid = 1, goodsSeries = "1.0", goodsChara = "장리", goodsCategory = "키링", goodsStatus = "GOTTEN", goodsIsGotten = true))
        App.database.goodsDao().insert(GoodsEntity(goodsUid = 2, goodsSeries = "1.0", goodsChara = "금희", goodsCategory = "키링", goodsStatus = "PENDING"))
        App.fanDatabase.fanGoodsDao().insert(FanGoodsEntity(fanGoodsSeries = "코믹", fanGoodsChara = "장리", fanGoodsImgPath = photo("fan_a.webp", 30).absolutePath))
        App.charaDatabase.charaDao().insert(CharaEntity(charaNm = "장리", charaIsFavorite = true, charaFavoriteOrder = 1))
        // 배신 굿즈: 원신(사진·캐릭터·선호 있음), 블루아카
        genshin = dao.insertGenre(BetrayGenreEntity(genreNm = "원신", genreOrder = 1,
            genreImgPath = photo("betray_genre_gs_bg.webp", 50).absolutePath, genreIconPath = photo("betray_genre_gs_icon.webp", 20).absolutePath)).toInt()
        blue = dao.insertGenre(BetrayGenreEntity(genreNm = "블루아카", genreOrder = 2)).toInt()
        dao.insertGoods(goods(genshin, "푸리나", BetrayKind.OFFICIAL, GoodsStatus.GOTTEN, photo("betray_gs1.webp", 70).absolutePath))
        dao.insertGoods(goods(genshin, "푸리나,나히다", BetrayKind.FAN, GoodsStatus.GOTTEN))
        dao.insertGoods(goods(genshin, "나히다", BetrayKind.OFFICIAL, GoodsStatus.PENDING))
        dao.insertGoods(goods(blue, "호시노", BetrayKind.OFFICIAL, GoodsStatus.GOTTEN, photo("betray_ba1.webp", 90).absolutePath))
        dao.saveChara(genshin, null, "느비예트", photo("betray_chara_nv.webp", 40).absolutePath)
        dao.toggleFavorite(genshin, "나히다")
        dao.toggleFavorite(blue, "호시노")
        Unit
    }

    @After
    fun tearDown() = TestDb.tearDown()

    private fun photo(name: String, size: Int) =
        File(TestDb.context.filesDir, name).apply { writeBytes(ByteArray(size) { (it * 5 + size).toByte() }) }

    private fun goods(genre: Int, chara: String, kind: BetrayKind, status: GoodsStatus, img: String = "") = BetrayGoodsEntity(
        genreId = genre, goodsKind = kind.name, goodsRegDate = "2026-11-01", goodsSeries = "팝업", goodsChara = chara,
        goodsStatus = status.name, goodsImgPath = img
    )

    private fun entries(zip: File): Set<String> = ZipFile(zip).use { z -> z.entries().asSequence().map { it.name }.toSet() }

    private fun betrayJson(zip: File): BetrayBackup? = ZipFile(zip).use { z ->
        z.getEntry("betray_goods.json")?.let { e -> Gson().fromJson(z.getInputStream(e).bufferedReader().readText(), BetrayBackup::class.java) }
    }

    private suspend fun write(selection: ExportSelection, name: String): File {
        val zip = File(workDir, name)
        BackupZip.write(zip, selection = selection)
        // 추출 확인에 보인 숫자 = 만든 파일 숫자(모든 범위에서)
        assertEquals("추출 확인 = 파일 ($name)", emptyList<String>(), ExportPreview.load(selection).differencesFrom(ExportPreview.fromZip(zip)))
        return zip
    }

    private suspend fun wuwaState() = Triple(
        App.database.goodsDao().getAll().map { it.goodsUid to it.goodsStatus },
        App.fanDatabase.fanGoodsDao().getAll().map { it.fanGoodsSeries },
        App.charaDatabase.charaDao().getAll().filter { it.charaIsFavorite }.map { it.charaNm }
    )

    private suspend fun genreSnapshot(id: Int) = listOf(
        dao.genre(id)?.let { listOf(it.genreNm, File(it.genreImgPath).name, File(it.genreIconPath).name) },
        dao.goodsOf(id).map { listOf(it.goodsChara, it.goodsKind, it.goodsStatus, File(it.goodsImgPath).name) }.sortedBy { it.toString() },
        dao.favoritesOf(id).map { it.charaNm to it.favoriteOrder },
        dao.charasOf(id).map { it.charaNm to File(it.charaImgPath).name }
    )

    // ── 파일에 들어가는 것 ──

    @Test
    fun 전체_추출은_명조와_모든_장르를_담고_전체_백업이다() = runBlocking {
        val zip = write(ExportSelection.ALL, "all.zip")
        val names = entries(zip)
        assertTrue(names.containsAll(listOf("official_gotten.json", "fan_goods.json", "favorite_charas.json", "betray_goods.json")))
        assertTrue(names.containsAll(listOf("images/fan_a.webp", "images/betray_gs1.webp", "images/betray_ba1.webp",
            "images/betray_genre_gs_bg.webp", "images/betray_genre_gs_icon.webp", "images/betray_chara_nv.webp")))
        val json = betrayJson(zip)!!
        assertNull("전체 백업", json.partial)
        assertEquals(setOf("원신", "블루아카"), json.genres!!.map { it!!.genreNm }.toSet())
    }

    @Test
    fun 장르만_추출은_그_장르와_사진만_담고_명조_파일은_없다() = runBlocking {
        val zip = write(ExportSelection.genreOnly(genshin), "gs.zip")
        val names = entries(zip)
        assertFalse(names.any { it in setOf("official_gotten.json", "fan_goods.json", "favorite_charas.json") })
        assertEquals(
            setOf("betray_goods.json", "images/betray_gs1.webp", "images/betray_genre_gs_bg.webp", "images/betray_genre_gs_icon.webp", "images/betray_chara_nv.webp"),
            names
        )
        val json = betrayJson(zip)!!
        assertEquals(true, json.partial)
        assertEquals(listOf("원신"), json.genres!!.map { it!!.genreNm })
        assertEquals(3, json.goods!!.size)
        assertEquals(listOf("나히다"), json.favorites!!.map { it!!.charaNm })
        assertEquals(listOf("느비예트"), json.charas!!.map { it!!.charaNm })
        val preview = ExportPreview.load(ExportSelection.genreOnly(genshin))
        assertEquals(listOf(1, 3, 2, 4), listOf(preview.betrayGenres, preview.betrayGoods, preview.betrayGotten, preview.photoCount))
        assertEquals(0, preview.officialTotal)
    }

    @Test
    fun 명조_추출에서_장르를_골라_담을_수_있다() = runBlocking {
        val zip = write(ExportSelection(includeWuwa = true, genreIds = setOf(blue)), "pick.zip")
        val names = entries(zip)
        assertTrue(names.containsAll(listOf("official_gotten.json", "fan_goods.json", "favorite_charas.json", "images/fan_a.webp", "images/betray_ba1.webp")))
        assertFalse("고르지 않은 장르 사진은 없다", names.contains("images/betray_gs1.webp"))
        val json = betrayJson(zip)!!
        assertEquals(true, json.partial)
        assertEquals(listOf("블루아카"), json.genres!!.map { it!!.genreNm })
    }

    @Test
    fun 장르를_하나도_안_고르면_배신_굿즈_파일을_넣지_않는다() = runBlocking {
        val zip = write(ExportSelection(includeWuwa = true, genreIds = emptySet()), "none.zip")
        val names = entries(zip)
        assertFalse(names.contains("betray_goods.json"))
        assertFalse(names.any { it.startsWith("images/betray") })
        assertTrue(names.contains("images/fan_a.webp"))
        assertEquals(0, ExportPreview.load(ExportSelection(true, emptySet())).betrayGenres)
    }

    // ── 불러오기 ──

    @Test
    fun 장르만_추출한_파일을_다른_기기에서_불러오면_새_장르로_더해지고_나머지는_그대로다() = runBlocking {
        val zip = write(ExportSelection.genreOnly(genshin), "gs.zip")
        val before = genreSnapshot(genshin)
        // 다른 기기: 원신은 없고 블루아카와 명조 데이터만 있다
        dao.deleteGoodsOf(genshin); dao.deleteFavoritesOf(genshin); dao.deleteCharasOf(genshin); dao.deleteGenre(genshin)
        val wuwa = wuwaState()
        val blueBefore = genreSnapshot(blue)

        val report = BackupZip.restore(zip.inputStream(), restoreDir)

        assertEquals("명조 데이터는 그대로", wuwa, wuwaState())
        assertEquals("다른 장르는 그대로", blueBefore, genreSnapshot(blue))
        val added = dao.genres().single { it.genreNm == "원신" }
        assertTrue("새 번호", added.genreId != blue)
        assertEquals("맨 뒤 순서", 3, added.genreOrder)
        assertEquals(before, genreSnapshot(added.genreId))
        assertTrue(File(added.genreImgPath).isFile && File(added.genreIconPath).isFile)
        assertEquals(3, report.betrayGoods)
        assertEquals("입력 완료! 배신 굿즈 3개", report.message())
    }

    @Test
    fun 같은_이름_장르가_있으면_그_장르만_파일_것으로_바뀐다() = runBlocking {
        val zip = write(ExportSelection.genreOnly(genshin), "gs.zip")
        val exported = genreSnapshot(genshin)
        val oldPhoto = photo("betray_gs_local_only.webp", 33)
        // 이 기기에서 원신을 바꿔 둔다(굿즈 추가, 선호 바꾸기, 캐릭터 추가). 이름은 대소문자·공백만 다르게.
        dao.updateGenre(dao.genre(genshin)!!.copy(genreNm = " 원신 "))
        dao.insertGoods(goods(genshin, "카즈하", BetrayKind.FAN, GoodsStatus.NOT_GOTTEN, oldPhoto.absolutePath))
        dao.toggleFavorite(genshin, "카즈하")
        dao.saveChara(genshin, null, "벤티", null)
        val blueBefore = genreSnapshot(blue)

        BackupZip.restore(zip.inputStream(), restoreDir)

        assertEquals("장르가 늘지 않는다", 2, dao.genres().size)
        val after = genreSnapshot(genshin)
        assertEquals("이 기기 장르 이름은 그대로", " 원신 ", (after[0] as List<*>)[0])
        assertEquals("내용은 파일 것", exported.drop(1), after.drop(1))
        assertFalse("바뀌어 아무도 안 쓰는 사진은 지운다", oldPhoto.exists())
        assertEquals("다른 장르는 그대로", blueBefore, genreSnapshot(blue))
    }

    @Test
    fun 같은_기기에서_자기_장르_추출을_다시_불러와도_사진이_남는다() = runBlocking {
        val zip = write(ExportSelection.genreOnly(genshin), "gs.zip")
        val before = genreSnapshot(genshin)
        // 사진은 filesDir 에 같은 이름으로 다시 풀린다(restoreDir 를 앱 폴더로 쓴다)
        BackupZip.restore(zip.inputStream(), TestDb.context.filesDir)
        assertEquals(before, genreSnapshot(genshin))
        dao.goodsOf(genshin).map { it.goodsImgPath }.filter { it.isNotEmpty() }.forEach { assertTrue(it, File(it).isFile) }
        val g = dao.genre(genshin)!!
        assertTrue(File(g.genreImgPath).isFile && File(g.genreIconPath).isFile)
        assertTrue(File(dao.chara(genshin, "느비예트")!!.charaImgPath).isFile)
    }

    @Test
    fun 장르를_골라_담은_명조_백업을_불러오면_고르지_않은_장르는_그대로다() = runBlocking {
        val zip = write(ExportSelection(includeWuwa = true, genreIds = setOf(blue)), "pick.zip")
        val gsBefore = genreSnapshot(genshin)
        dao.insertGoods(goods(blue, "아리스", BetrayKind.OFFICIAL, GoodsStatus.NOT_GOTTEN)) // 파일 뒤에 바뀐 것
        App.database.goodsDao().getAll().forEach { App.database.goodsDao().update(it.copy(goodsStatus = "NOT_GOTTEN", goodsIsGotten = false)) }

        BackupZip.restore(zip.inputStream(), restoreDir)

        assertEquals("원신은 그대로", gsBefore, genreSnapshot(genshin))
        assertEquals("블루아카는 파일 것", listOf("호시노"), dao.goodsOf(blue).map { it.goodsChara })
        assertEquals("명조 보유 현황은 파일 것", listOf(1 to "GOTTEN", 2 to "PENDING"), App.database.goodsDao().getAll().sortedBy { it.goodsUid }.map { it.goodsUid to it.goodsStatus })
    }

    @Test
    fun 전체_백업을_불러오면_파일에_없는_장르는_지워진다() = runBlocking {
        val zip = write(ExportSelection.ALL, "all.zip")
        val extra = dao.insertGenre(BetrayGenreEntity(genreNm = "파일 뒤에 만든 장르", genreOrder = 3)).toInt()
        BackupZip.restore(zip.inputStream(), restoreDir)
        assertNull(dao.genre(extra))
        assertEquals(setOf("원신", "블루아카"), dao.genres().map { it.genreNm }.toSet())
    }

    @Test
    fun 배신_굿즈_없는_명조_백업을_불러오면_배신_굿즈는_그대로다() = runBlocking {
        val zip = write(ExportSelection(true, emptySet()), "none.zip")
        val gs = genreSnapshot(genshin); val ba = genreSnapshot(blue)
        BackupZip.restore(zip.inputStream(), restoreDir)
        assertEquals(gs, genreSnapshot(genshin))
        assertEquals(ba, genreSnapshot(blue))
    }

    @Test
    fun 빈_장르만_추출해도_불러올_수_있다() = runBlocking {
        val empty = dao.insertGenre(BetrayGenreEntity(genreNm = "빈 장르", genreOrder = 3)).toInt()
        val zip = write(ExportSelection.genreOnly(empty), "empty.zip")
        dao.deleteGenre(empty)
        val report = BackupZip.restore(zip.inputStream(), restoreDir)
        assertTrue(dao.genres().any { it.genreNm == "빈 장르" })
        assertEquals(0, report.betrayGoods)
    }

    // ── 파일 이름 · 용량 정리 ──

    @Test
    fun 추출_파일_이름은_버전_날짜_항목이고_못_쓰는_글자를_바꾼다() {
        val day = java.util.GregorianCalendar(2026, java.util.Calendar.NOVEMBER, 20).time
        assertEquals("wuwaGoods_3.0_20261120_전체.zip", BackupExport.fileName(null, "3.0", day))
        assertEquals("wuwaGoods_3.0_20261120_원신.zip", BackupExport.fileName(listOf("원신"), "3.0", day))
        assertEquals("wuwaGoods_3.0_20261120_명조_블루_아카_이브.zip", BackupExport.fileName(listOf("명조", "블루 아카/이브?"), "3.0", day))
        // 너무 길면 뒤는 '외N'
        val many = (1..30).map { "장르이름$it" }
        assertTrue(BackupExport.fileName(many, "3.0", day).let { it.contains("_외") && it.length < 100 })
    }

    @Test
    fun 명조와_모든_장르를_고르면_전체_아니면_고른_항목() = runBlocking {
        val genres = dao.genres()
        assertTrue("장르가 둘 이상 있어야 확인할 수 있다", genres.size >= 2)
        val (a, b) = genres
        val v = BuildConfig.VERSION_NAME
        fun clean(n: String) = n.trim().replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").trim('_')
        assertTrue(BackupExport.fileNameFor(ExportSelection.ALL).endsWith("_전체.zip"))
        assertTrue(BackupExport.fileNameFor(ExportSelection(includeWuwa = true, genreIds = genres.map { it.genreId }.toSet())).endsWith("_전체.zip"))
        assertTrue(BackupExport.fileNameFor(ExportSelection(includeWuwa = true, genreIds = setOf(b.genreId))).endsWith("_명조_${clean(b.genreNm)}.zip"))
        assertTrue(BackupExport.fileNameFor(ExportSelection(includeWuwa = true, genreIds = emptySet())).endsWith("_명조.zip"))
        val only = BackupExport.fileNameFor(ExportSelection.genreOnly(a.genreId))
        assertTrue(only, only.startsWith("wuwaGoods_${v}_") && only.endsWith("_${clean(a.genreNm)}.zip"))
    }

    @Test
    fun 남은_장르별_추출_파일도_용량_정리가_지운다() = runBlocking {
        val left = File(TestDb.context.filesDir, "wowa_backup_원신.zip").apply { writeBytes(ByteArray(10)) }
        val keep = File(TestDb.context.filesDir, "other.zip").apply { writeBytes(ByteArray(10)) }
        StorageCleaner.clean(TestDb.context, setOf(CleanTarget.TEMP_FILES))
        assertFalse(left.exists())
        assertTrue(keep.exists())
        keep.delete()
        Unit
    }

    // ── 게이지 범례의 공식/2차창작 ──

    @Test
    fun 범례의_공식과_2차창작을_더하면_게이지_수와_같다() = runBlocking {
        val gotten = dao.goodsOf(genshin).filter { it.status == GoodsStatus.GOTTEN }
        val b = charaKindBreakdown(gotten)
        assertEquals(mapOf("푸리나" to (1 to 1), "나히다" to (0 to 1)), b)
        val shares = charaShares(gotten, listOf("나히다"))
        shares.top.forEach { s -> assertEquals(s.name, s.count, b.getValue(s.name).let { it.first + it.second }) }
    }

    // ── 캐릭터 선호 정하기 ──

    @Test
    fun 선호로_정하면_맨_뒤에_붙고_풀면_빠지고_같은_상태면_그대로다() = runBlocking {
        dao.setFavorite(genshin, "느비예트", true)
        assertEquals(listOf("나히다" to 1, "느비예트" to 2), dao.favoritesOf(genshin).map { it.charaNm to it.favoriteOrder })
        dao.setFavorite(genshin, "느비예트", true)
        assertEquals(2, dao.favoritesOf(genshin).size)
        dao.setFavorite(genshin, "나히다", false)
        assertEquals(listOf("느비예트"), dao.favoritesOf(genshin).map { it.charaNm })
        dao.setFavorite(genshin, "없는캐릭터", false)
        assertEquals(1, dao.favoritesOf(genshin).size)
        assertEquals("다른 장르 선호는 그대로", listOf("호시노"), dao.favoritesOf(blue).map { it.charaNm })
    }

    @Test
    fun 이름을_바꾸면서_선호를_풀어도_새_이름으로_반영된다() = runBlocking {
        // 편집 화면 저장 순서: saveChara(이름 바꾸기, 선호 이름도 바뀜) → setFavorite(새 이름, 스위치 값)
        assertTrue(dao.saveChara(genshin, "나히다", "쿠사나리", null))
        assertEquals(listOf("쿠사나리"), dao.favoritesOf(genshin).map { it.charaNm })
        dao.setFavorite(genshin, "쿠사나리", false)
        assertTrue(dao.favoritesOf(genshin).isEmpty())
        // 새 캐릭터를 선호로 추가
        assertTrue(dao.saveChara(genshin, null, "벤티", null))
        dao.setFavorite(genshin, "벤티", true)
        assertEquals(listOf("벤티"), dao.favoritesOf(genshin).map { it.charaNm })
        assertEquals(listOf(BetrayCharaEntity(genshin, "느비예트", dao.chara(genshin, "느비예트")!!.charaImgPath)), dao.charasOf(genshin).filter { it.charaNm == "느비예트" })
    }

    // ── 검증: 개수로 못 거르는 배신 굿즈 내용도 파일과 기기를 견준다 ──

    @Test
    fun 사진_없는_캐릭터_시리즈_선호_순서가_파일과_다르면_검증에_걸린다() = runBlocking {
        dao.toggleFavorite(genshin, "푸리나") // 선호: 나히다(1) · 푸리나(2)
        val zip = write(ExportSelection.genreOnly(genshin), "detail.zip")
        val written = ExportPreview.fromZip(zip)
        assertEquals(listOf("원신|나히다", "원신|푸리나"), written.betrayDetail.favorites)
        assertEquals(listOf("원신|느비예트"), written.betrayDetail.charas)
        assertEquals(listOf("원신|팝업").size, 1)

        // 파일을 만든 뒤 기기에서 바꾸면(= 파일에 그 내용이 없으면) 어느 칸인지 알려 준다
        dao.saveChara(genshin, null, "사진없는캐릭터", "")
        assertTrue(ExportPreview.load(ExportSelection.genreOnly(genshin)).differencesFrom(written).contains("배신 굿즈 캐릭터"))
        dao.deleteCharaWithImage(genshin, "사진없는캐릭터")

        dao.reorderFavorites(genshin, listOf("푸리나", "나히다"))
        assertTrue("선호 순서까지 본다", ExportPreview.load(ExportSelection.genreOnly(genshin)).differencesFrom(written).contains("배신 굿즈 선호 캐릭터"))
        dao.reorderFavorites(genshin, listOf("나히다", "푸리나"))

        dao.saveSeries(genshin, null, "사진없는시리즈", "", "")
        assertTrue(ExportPreview.load(ExportSelection.genreOnly(genshin)).differencesFrom(written).contains("배신 굿즈 시리즈"))
    }

    @Test
    fun 이차창작_시리즈는_개수가_같아도_이름이_다르면_검증에_걸린다() = runBlocking {
        App.fanDatabase.fanGoodsDao().saveSeries(null, "코믹월드", "", "")
        val zip = write(ExportSelection.ALL, "fs.zip")
        val written = ExportPreview.fromZip(zip)
        assertEquals(listOf("코믹월드"), written.fanSeriesNames)
        App.fanDatabase.fanGoodsDao().saveSeries("코믹월드", "일러스타", null, null)
        assertTrue(ExportPreview.load().differencesFrom(written).contains("2차창작 시리즈"))
    }
}
