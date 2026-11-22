package com.example.wowagoodsproject

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.room.Room
import com.example.wowagoodsproject.db.betray.BetrayDatabase
import com.example.wowagoodsproject.db.betray.BetrayGenreEntity
import com.example.wowagoodsproject.db.betray.BetrayGoodsEntity
import com.example.wowagoodsproject.db.betray.deleteGenreWithGoods
import com.example.wowagoodsproject.db.betray.updateGenreInfo
import com.example.wowagoodsproject.screen.betray.cropToSquareFile
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
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 배신 굿즈 장르 사진(배경·아이콘): DB 이전, 바꾸기, 지우기, 백업, 용량 정리, 원 자르기 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BetrayGenreTest {

    private val dao get() = App.betrayDatabase.betrayDao()
    private lateinit var restoreDir: File

    @Before
    fun setUp() {
        TestDb.setUp()
        restoreDir = File(TestDb.context.filesDir, "restore").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun tearDown() = TestDb.tearDown()

    private fun photo(name: String, size: Int = 40) =
        File(TestDb.context.filesDir, name).apply { writeBytes(ByteArray(size) { (it * 7).toByte() }) }

    @Test
    fun DB_1에서_최신으로_올려도_장르가_남고_사진_칸은_비어_있다() {
        val file = TestDb.context.getDatabasePath("betray_migration_test.db").apply { parentFile?.mkdirs(); delete() }
        // 1.json 의 표 그대로 만든 옛 DB
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `tb_betray_genre` (`genreId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `genreNm` TEXT NOT NULL, `genreOrder` INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `tb_betray_goods` (`goodsId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `genreId` INTEGER NOT NULL, `goodsKind` TEXT NOT NULL, `goodsRegDate` TEXT NOT NULL, `goodsSeries` TEXT NOT NULL, `goodsPrice` TEXT NOT NULL, `goodsChara` TEXT NOT NULL, `goodsCategory` TEXT NOT NULL, `goodsImgPath` TEXT NOT NULL, `goodsStatus` TEXT NOT NULL, `goodsMemo` TEXT NOT NULL, `goodsPurchaseDate` TEXT NOT NULL, `goodsPurchaseStore` TEXT NOT NULL, `goodsReceiveDate` TEXT NOT NULL, `goodsQuantity` INTEGER NOT NULL, `goodsShippingDate` TEXT NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tb_betray_goods_genreId` ON `tb_betray_goods` (`genreId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `tb_betray_favorite` (`genreId` INTEGER NOT NULL, `charaNm` TEXT NOT NULL, `favoriteOrder` INTEGER NOT NULL, PRIMARY KEY(`genreId`, `charaNm`))")
            db.execSQL("INSERT INTO tb_betray_genre (genreId, genreNm, genreOrder) VALUES (3, '원신', 1)")
            db.version = 1
        }
        val migrated = Room.databaseBuilder(TestDb.context, BetrayDatabase::class.java, file.absolutePath)
            .addMigrations(BetrayDatabase.MIGRATION_1_2, BetrayDatabase.MIGRATION_2_3, BetrayDatabase.MIGRATION_3_4, BetrayDatabase.MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        try {
            val genres = runBlocking { migrated.betrayDao().genres() }
            assertEquals(listOf(BetrayGenreEntity(3, "원신", 1, "", "")), genres)
            assertEquals(emptyList<Any>(), runBlocking { migrated.betrayDao().allCharas() })
        } finally {
            migrated.close()
            file.delete()
        }
    }

    @Test
    fun 사진을_바꾸면_예전_사진은_지우고_null은_그대로_빈_값은_뺀다() = runBlocking {
        val oldBanner = photo("betray_genre_old_banner.jpg")
        val oldIcon = photo("betray_genre_old_icon.jpg")
        val newBanner = photo("betray_genre_new_banner.jpg")
        val id = dao.insertGenre(BetrayGenreEntity(genreNm = "원신", genreImgPath = oldBanner.absolutePath, genreIconPath = oldIcon.absolutePath)).toInt()

        dao.updateGenreInfo(id, "원신2", newBanner.absolutePath, null)
        val g = dao.genre(id)!!
        assertEquals("원신2", g.genreNm)
        assertEquals(newBanner.absolutePath, g.genreImgPath)
        assertEquals("아이콘은 null 이라 그대로", oldIcon.absolutePath, g.genreIconPath)
        assertFalse("바뀐 배경의 예전 사진은 지운다", oldBanner.exists())
        assertTrue(oldIcon.exists())

        dao.updateGenreInfo(id, null, null, "")
        assertEquals("", dao.genre(id)!!.genreIconPath)
        assertEquals("이름이 null 이면 그대로", "원신2", dao.genre(id)!!.genreNm)
        assertFalse("뺀 아이콘 사진도 지운다", oldIcon.exists())
        assertTrue(newBanner.exists())
    }

    @Test
    fun 다른_곳에서_쓰는_사진은_바꿔도_남는다() = runBlocking {
        // 사진 하나로 만든 뒤 '사진 두 개'로 바꾸는 등 같은 파일을 다른 장르·굿즈가 가리킬 수 있다.
        val shared = photo("betray_genre_shared.jpg")
        val a = dao.insertGenre(BetrayGenreEntity(genreNm = "A", genreImgPath = shared.absolutePath)).toInt()
        dao.insertGoods(BetrayGoodsEntity(genreId = a, goodsImgPath = shared.absolutePath))
        dao.updateGenreInfo(a, null, "", null)
        assertTrue(shared.exists())
    }

    @Test
    fun 장르를_지우면_배경과_아이콘도_지운다() = runBlocking {
        val banner = photo("betray_genre_del_banner.jpg")
        val icon = photo("betray_genre_del_icon.jpg")
        val id = dao.insertGenre(BetrayGenreEntity(genreNm = "원신", genreImgPath = banner.absolutePath, genreIconPath = icon.absolutePath)).toInt()
        dao.deleteGenreWithGoods(id)
        assertFalse(banner.exists())
        assertFalse(icon.exists())
    }

    @Test
    fun 장르_사진도_백업에_들어가고_불러오면_그대로_돌아온다() = runBlocking {
        val banner = photo("betray_genre_bk_banner.jpg", 300)
        val icon = photo("betray_genre_bk_icon.jpg", 120)
        dao.insertGenre(BetrayGenreEntity(genreNm = "원신", genreOrder = 1, genreImgPath = banner.absolutePath, genreIconPath = icon.absolutePath))
        dao.insertGenre(BetrayGenreEntity(genreNm = "사진 없음", genreOrder = 2))
        val zip = File(TestDb.context.cacheDir, "genre.zip")
        BackupZip.write(zip)

        val shown = ExportPreview.load()
        assertEquals(2, shown.photoCount)
        assertEquals(420L, shown.photoBytes)
        assertEquals(emptyList<String>(), shown.differencesFrom(ExportPreview.fromZip(zip)))

        dao.deleteAllGenres()
        BackupZip.restore(zip.inputStream(), restoreDir)
        val restored = dao.genres().associateBy { it.genreNm }
        assertEquals(File(restoreDir, banner.name).absolutePath, restored.getValue("원신").genreImgPath)
        assertEquals(File(restoreDir, icon.name).absolutePath, restored.getValue("원신").genreIconPath)
        assertTrue(File(restoreDir, banner.name).readBytes().contentEquals(banner.readBytes()))
        assertTrue(File(restoreDir, icon.name).readBytes().contentEquals(icon.readBytes()))
        assertEquals("", restored.getValue("사진 없음").genreImgPath)
        assertEquals("", restored.getValue("사진 없음").genreIconPath)
    }

    @Test
    fun 장르_사진_칸이_없는_처음_배신_굿즈_백업도_불러온다() = runBlocking {
        val json = """{"genres":[{"genreId":5,"genreNm":"원신","genreOrder":1}],"goods":[],"favorites":[]}"""
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("betray_goods.json")); z.write(json.toByteArray()); z.closeEntry()
        }
        BackupZip.restore(out.toByteArray().inputStream(), restoreDir)
        assertEquals(listOf(BetrayGenreEntity(5, "원신", 1, "", "")), dao.genres())
    }

    @Test
    fun 용량_정리는_장르_사진을_지우지_않는다() = runBlocking {
        val banner = photo("betray_genre_keep_banner.jpg")
        val icon = photo("betray_genre_keep_icon.jpg")
        val orphan = photo("betray_genre_orphan.jpg")
        dao.insertGenre(BetrayGenreEntity(genreNm = "원신", genreImgPath = banner.absolutePath, genreIconPath = icon.absolutePath))

        StorageCleaner.clean(TestDb.context, setOf(CleanTarget.TEMP_FILES))

        assertTrue(banner.exists())
        assertTrue(icon.exists())
        assertFalse(orphan.exists())
        listOf(banner, icon).forEach { it.delete() }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun 원_범위대로_정사각형을_잘라_줄인다() = runBlocking {
        // 왼쪽 절반 빨강, 오른쪽 절반 파랑인 2000x1000 사진
        val bmp = Bitmap.createBitmap(2000, 1000, Bitmap.Config.ARGB_8888)
        for (x in 0 until 2000) for (y in 0 until 1000) bmp.setPixel(x, y, if (x < 1000) Color.RED else Color.BLUE)

        // 오른쪽 절반 가운데, 반지름 400 → 800x800 을 잘라 512 로 줄인다
        val file = cropToSquareFile(TestDb.context, bmp, cx = 1500f, cy = 500f, r = 400f)
        val out = BitmapFactory.decodeFile(file.absolutePath)
        assertEquals(512, out.width)
        assertEquals(512, out.height)
        val c = out.getPixel(256, 256)
        assertTrue("파랑 쪽을 잘랐다", Color.blue(c) > 200 && Color.red(c) < 60)
        assertTrue("임시 파일 이름이라 남아도 용량 정리가 지운다", file.name.startsWith("camera_temp_"))

        // 사진 밖으로 넘치는 범위는 사진 안으로 밀어 넣는다(작으면 줄이지 않는다)
        val edge = BitmapFactory.decodeFile(cropToSquareFile(TestDb.context, bmp, cx = 1990f, cy = 10f, r = 100f).absolutePath)
        assertEquals(200, edge.width)
        assertEquals(200, edge.height)
    }
}
