package com.example.wowagoodsproject

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.example.wowagoodsproject.db.betray.BetrayCharaEntity
import com.example.wowagoodsproject.db.betray.BetrayDatabase
import com.example.wowagoodsproject.db.betray.BetrayGenreEntity
import com.example.wowagoodsproject.db.betray.BetrayGoodsEntity
import com.example.wowagoodsproject.db.betray.deleteCharaWithImage
import com.example.wowagoodsproject.db.betray.deleteGenreWithGoods
import com.example.wowagoodsproject.db.betray.reorderFavorites
import com.example.wowagoodsproject.db.betray.saveChara
import com.example.wowagoodsproject.db.betray.toggleFavorite
import com.example.wowagoodsproject.screen.betray.betrayCharas
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
import java.io.File

/** 배신 굿즈에서 직접 추가한 캐릭터(이름·사진). 명조 캐릭터 DB 는 건드리지 않는다. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BetrayCharaTest {

    private val dao get() = App.betrayDatabase.betrayDao()
    private lateinit var restoreDir: File
    private var genre = 0

    @Before
    fun setUp() = runBlocking {
        TestDb.setUp()
        restoreDir = File(TestDb.context.filesDir, "restore").apply { deleteRecursively(); mkdirs() }
        genre = dao.insertGenre(BetrayGenreEntity(genreNm = "원신")).toInt()
    }

    @After
    fun tearDown() = TestDb.tearDown()

    private fun photo(name: String, size: Int = 40) =
        File(TestDb.context.filesDir, name).apply { writeBytes(ByteArray(size) { (it * 3).toByte() }) }

    private suspend fun goods(chara: String) =
        dao.insertGoods(BetrayGoodsEntity(genreId = genre, goodsChara = chara, goodsRegDate = "2026-11-01")).toInt()

    @Test
    fun DB_2에서_3으로_올리면_캐릭터_표가_생기고_장르는_그대로다() {
        val file = TestDb.context.getDatabasePath("betray_v2_test.db").apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `tb_betray_genre` (`genreId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `genreNm` TEXT NOT NULL, `genreOrder` INTEGER NOT NULL, `genreImgPath` TEXT NOT NULL, `genreIconPath` TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `tb_betray_goods` (`goodsId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `genreId` INTEGER NOT NULL, `goodsKind` TEXT NOT NULL, `goodsRegDate` TEXT NOT NULL, `goodsSeries` TEXT NOT NULL, `goodsPrice` TEXT NOT NULL, `goodsChara` TEXT NOT NULL, `goodsCategory` TEXT NOT NULL, `goodsImgPath` TEXT NOT NULL, `goodsStatus` TEXT NOT NULL, `goodsMemo` TEXT NOT NULL, `goodsPurchaseDate` TEXT NOT NULL, `goodsPurchaseStore` TEXT NOT NULL, `goodsReceiveDate` TEXT NOT NULL, `goodsQuantity` INTEGER NOT NULL, `goodsShippingDate` TEXT NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tb_betray_goods_genreId` ON `tb_betray_goods` (`genreId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `tb_betray_favorite` (`genreId` INTEGER NOT NULL, `charaNm` TEXT NOT NULL, `favoriteOrder` INTEGER NOT NULL, PRIMARY KEY(`genreId`, `charaNm`))")
            db.execSQL("INSERT INTO tb_betray_genre VALUES (7, '블루아카', 1, '/a.webp', '/b.webp')")
            db.version = 2
        }
        val migrated = Room.databaseBuilder(TestDb.context, BetrayDatabase::class.java, file.absolutePath)
            .addMigrations(BetrayDatabase.MIGRATION_1_2, BetrayDatabase.MIGRATION_2_3, BetrayDatabase.MIGRATION_3_4, BetrayDatabase.MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                assertEquals(listOf(BetrayGenreEntity(7, "블루아카", 1, "/a.webp", "/b.webp")), migrated.betrayDao().genres())
                migrated.betrayDao().upsertChara(BetrayCharaEntity(7, "호시노", "/c.webp"))
                assertEquals(listOf(BetrayCharaEntity(7, "호시노", "/c.webp")), migrated.betrayDao().allCharas())
            }
        } finally {
            migrated.close()
            file.delete()
        }
    }

    @Test
    fun 굿즈가_없어도_추가한_캐릭터가_나오고_사진이_먼저다() = runBlocking {
        val goodsPhoto = "/x/goods.webp"
        dao.insertGoods(BetrayGoodsEntity(genreId = genre, goodsChara = "푸리나", goodsImgPath = goodsPhoto))
        assertTrue(dao.saveChara(genre, null, "나히다", "/x/nahida.webp"))
        assertTrue(dao.saveChara(genre, "푸리나", "푸리나", "/x/furina.webp"))

        val charas = betrayCharas(dao.goodsOf(genre), emptyList(), dao.charasOf(genre)).associateBy { it.name }
        assertEquals(0, charas.getValue("나히다").total)
        assertTrue(charas.getValue("나히다").registered)
        assertEquals("/x/nahida.webp", charas.getValue("나히다").cover)
        assertEquals("직접 넣은 사진이 굿즈 사진보다 먼저", "/x/furina.webp", charas.getValue("푸리나").cover)

        // 사진을 안 넣은 캐릭터는 굿즈 사진
        assertTrue(dao.saveChara(genre, "푸리나", "푸리나", ""))
        assertEquals(goodsPhoto, betrayCharas(dao.goodsOf(genre), emptyList(), dao.charasOf(genre)).single { it.name == "푸리나" }.cover)
    }

    @Test
    fun 빈_이름_쉼표_겹치는_이름은_저장하지_않는다() = runBlocking {
        goods("푸리나")
        assertTrue(dao.saveChara(genre, null, "나히다", null))
        assertFalse(dao.saveChara(genre, null, "  ", null))
        assertFalse("굿즈 캐릭터 칸의 구분자", dao.saveChara(genre, null, "A,B", null))
        assertFalse("직접 추가한 캐릭터와 겹침", dao.saveChara(genre, null, "나히다", null))
        assertFalse("굿즈에 나온 캐릭터와 겹침", dao.saveChara(genre, null, "푸리나", null))
        assertFalse("이름을 바꿀 때도 겹치면 안 된다", dao.saveChara(genre, "나히다", "푸리나", null))
        assertEquals(listOf("나히다"), dao.charasOf(genre).map { it.charaNm })
    }

    @Test
    fun 다른_장르의_같은_이름은_따로다() = runBlocking {
        val other = dao.insertGenre(BetrayGenreEntity(genreNm = "블루아카")).toInt()
        assertTrue(dao.saveChara(genre, null, "아리스", null))
        assertTrue(dao.saveChara(other, null, "아리스", null))
        assertEquals(2, dao.allCharas().size)
    }

    @Test
    fun 이름을_바꾸면_굿즈와_선호_캐릭터_이름도_바뀐다() = runBlocking {
        val solo = goods("푸리나")
        val pair = goods("푸리나,나히다")
        val other = goods("나히다")
        dao.toggleFavorite(genre, "나히다")
        dao.toggleFavorite(genre, "푸리나")
        assertFalse("굿즈에 있는 이름은 새로 추가할 수 없다", dao.saveChara(genre, null, "푸리나", null))
        // 굿즈에만 있던 캐릭터를 편집(이름 바꾸기)하면 직접 추가한 캐릭터가 된다
        assertTrue(dao.saveChara(genre, "푸리나", "포칼로스", null))

        assertEquals("포칼로스", dao.goodsById(solo)!!.goodsChara)
        assertEquals("포칼로스,나히다", dao.goodsById(pair)!!.goodsChara)
        assertEquals("다른 캐릭터 굿즈는 그대로", "나히다", dao.goodsById(other)!!.goodsChara)
        assertEquals("선호 순서는 그대로", listOf("나히다" to 1, "포칼로스" to 2), dao.favoritesOf(genre).map { it.charaNm to it.favoriteOrder })
        assertEquals(listOf("포칼로스"), dao.charasOf(genre).map { it.charaNm })
    }

    @Test
    fun 사진을_바꾸거나_지우면_예전_사진_파일을_지운다() = runBlocking {
        val first = photo("betray_chara_1.webp")
        val second = photo("betray_chara_2.webp")
        assertTrue(dao.saveChara(genre, null, "나히다", first.absolutePath))
        assertTrue(dao.saveChara(genre, "나히다", "나히다", second.absolutePath))
        assertFalse(first.exists())
        assertTrue("null 이면 사진 그대로", dao.saveChara(genre, "나히다", "나히다2", null))
        assertEquals(second.absolutePath, dao.chara(genre, "나히다2")!!.charaImgPath)

        goods("나히다2")
        dao.deleteCharaWithImage(genre, "나히다2")
        assertFalse(second.exists())
        assertTrue(dao.charasOf(genre).isEmpty())
        assertEquals("굿즈는 그대로", "나히다2", dao.goodsOf(genre).single().goodsChara)
    }

    @Test
    fun 장르를_지우면_캐릭터와_사진도_지운다() = runBlocking {
        val p = photo("betray_chara_del.webp")
        dao.saveChara(genre, null, "나히다", p.absolutePath)
        dao.deleteGenreWithGoods(genre)
        assertTrue(dao.allCharas().isEmpty())
        assertFalse(p.exists())
    }

    @Test
    fun 캐릭터도_백업에_들어가고_불러오면_그대로_돌아온다() = runBlocking {
        val p = photo("betray_chara_bk.webp", 99)
        dao.saveChara(genre, null, "나히다", p.absolutePath)
        dao.saveChara(genre, null, "사진없음", "")
        val zip = File(TestDb.context.cacheDir, "chara.zip")
        BackupZip.write(zip)

        val shown = ExportPreview.load()
        assertEquals(1, shown.photoCount)
        assertEquals(emptyList<String>(), shown.differencesFrom(ExportPreview.fromZip(zip)))

        dao.deleteAllCharas()
        BackupZip.restore(zip.inputStream(), restoreDir)
        assertEquals(
            listOf(BetrayCharaEntity(genre, "나히다", File(restoreDir, p.name).absolutePath), BetrayCharaEntity(genre, "사진없음", "")),
            dao.allCharas()
        )
        assertTrue(File(restoreDir, p.name).readBytes().contentEquals(p.readBytes()))
    }

    @Test
    fun 캐릭터_칸이_없는_예전_배신_굿즈_백업도_불러온다() = runBlocking {
        val json = """{"genres":[{"genreId":5,"genreNm":"원신","genreOrder":1}],"goods":[],"favorites":[]}"""
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("betray_goods.json")); z.write(json.toByteArray()); z.closeEntry()
        }
        BackupZip.restore(out.toByteArray().inputStream(), restoreDir)
        assertEquals(listOf("원신"), dao.genres().map { it.genreNm })
        assertTrue(dao.allCharas().isEmpty())
    }

    @Test
    fun 용량_정리는_캐릭터_사진을_지우지_않는다() = runBlocking {
        val p = photo("betray_chara_keep.webp")
        val orphan = photo("betray_chara_orphan.webp")
        dao.saveChara(genre, null, "나히다", p.absolutePath)
        StorageCleaner.clean(TestDb.context, setOf(CleanTarget.TEMP_FILES))
        assertTrue(p.exists())
        assertFalse(orphan.exists())
        p.delete()
        Unit
    }

    @Test
    fun 명조_캐릭터_DB는_건드리지_않는다() = runBlocking {
        val before = App.charaDatabase.charaDao().getAll()
        dao.saveChara(genre, null, "나히다", null)
        dao.saveChara(genre, "나히다", "나히다2", null)
        dao.deleteCharaWithImage(genre, "나히다2")
        assertEquals(before, App.charaDatabase.charaDao().getAll())
    }

    @Test
    fun 선호_순서를_끌어서_바꾸면_그대로_저장한다() = runBlocking {
        listOf("A", "B", "C").forEach { dao.toggleFavorite(genre, it) }
        dao.reorderFavorites(genre, listOf("C", "A", "B", "없는캐릭터"))
        assertEquals(listOf("C" to 1, "A" to 2, "B" to 3), dao.favoritesOf(genre).map { it.charaNm to it.favoriteOrder })
        // 화면 계산도 그 순서를 쓴다
        val charas = betrayCharas(emptyList(), dao.favoritesOf(genre), emptyList())
        assertEquals(emptyList<String>(), charas.map { it.name }) // 굿즈·추가 캐릭터가 없으면 목록은 비어 있다
        dao.saveChara(genre, null, "A", null); dao.saveChara(genre, null, "B", null); dao.saveChara(genre, null, "C", null)
        assertEquals(listOf("C", "A", "B"), betrayCharas(emptyList(), dao.favoritesOf(genre), dao.charasOf(genre)).map { it.name })
    }
}
