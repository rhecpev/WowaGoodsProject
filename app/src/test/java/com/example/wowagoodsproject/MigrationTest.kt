package com.example.wowagoodsproject

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.wowagoodsproject.db.changed.ChangedGoodsDatabase
import com.example.wowagoodsproject.db.fan.FanGoodsDatabase
import com.example.wowagoodsproject.db.official.GoodsDatabase
import kotlinx.coroutines.flow.first
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 예전 버전에서 쓰던 DB 를 지금 버전으로 올려 본다. 마이그레이션이 틀리면 앱을 켜자마자 꺼진다.
 * - 스키마 파일이 있는 버전(2.1 이후)은 그 파일대로 DB 를 만들어 올린다. 스키마가 맞는지는 Room 이 열 때 확인한다.
 * - 스키마 파일이 없는 버전(1.x~2.0, DB 버전 1~2)은 그때 Room 이 만들던 표를 그대로 만들어 올린다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val goodsMigrations = arrayOf(
        GoodsDatabase.MIGRATION_1_2, GoodsDatabase.MIGRATION_2_3, GoodsDatabase.MIGRATION_3_4, GoodsDatabase.MIGRATION_4_5,
        GoodsDatabase.MIGRATION_5_6, GoodsDatabase.MIGRATION_6_7, GoodsDatabase.MIGRATION_7_8
    )
    private val fanMigrations = arrayOf(
        FanGoodsDatabase.MIGRATION_1_2, FanGoodsDatabase.MIGRATION_2_3, FanGoodsDatabase.MIGRATION_3_4, FanGoodsDatabase.MIGRATION_4_5,
        FanGoodsDatabase.MIGRATION_5_6, FanGoodsDatabase.MIGRATION_6_7
    )

    /** 내보낸 스키마 파일(schemas/)대로 그 버전의 DB 파일을 만든다. 스키마는 Room 이 열 때 다시 확인한다. */
    private fun schemaDb(name: String, dbClass: Class<*>, version: Int, vararg inserts: String) {
        val json = JSONObject(File("schemas/${dbClass.name}/$version.json").readText()).getJSONObject("database")
        val entities = json.getJSONArray("entities")
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        file.delete()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = json.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            inserts.forEach { db.execSQL(it) }
            db.version = version
        }
    }

    @Test
    fun 공식_굿즈_DB_3에서_8까지_올리면_스키마가_맞고_보유_기록이_남는다() = runBlocking {
        schemaDb("g3", GoodsDatabase::class.java, 3,
            "INSERT INTO tb_goods (goodsReleaseDate, goodsSeries, goodsPrice, goodsChara, goodsCategory, goodsIsGotten, goodsStatus, goodsUrl, goodsMemo) VALUES ('2024-07-04','1.0','59위안','감심','인형',1,'GOTTEN','u','단품')")
        val db = Room.databaseBuilder(context, GoodsDatabase::class.java, "g3").addMigrations(*goodsMigrations).build()
        val g = db.goodsDao().getAll().single()
        assertEquals(listOf("GOTTEN", 1, 0, "", "59위안"), listOf(g.goodsStatus, g.goodsQuantity, g.goodsUid, g.goodsShippingDate, g.goodsPrice))
        db.close()
    }

    @Test
    fun 공식_굿즈_DB_4에서_7까지_어느_버전에서든_8로_올린다() = runBlocking {
        for (v in 4..7) {
            schemaDb("gv$v", GoodsDatabase::class.java, v)
            val db = Room.databaseBuilder(context, GoodsDatabase::class.java, "gv$v").addMigrations(*goodsMigrations).build()
            assertEquals(0, db.goodsDao().count())
            db.close()
        }
    }

    @Test
    fun 이차창작_DB_3에서_7까지() = runBlocking {
        schemaDb("f3", FanGoodsDatabase::class.java, 3,
            "INSERT INTO tb_fan_goods (fanGoodsReleaseDate, fanGoodsSeries, fanGoodsPrice, fanGoodsChara, fanGoodsCategory, fanGoodsImgPath, fanGoodsIsGotten, fanGoodsStatus, fanGoodsMemo) VALUES ('','c1','5000','감심','키링','/x.jpg',1,'GOTTEN','')")
        val db = Room.databaseBuilder(context, FanGoodsDatabase::class.java, "f3").addMigrations(*fanMigrations).build()
        val f = db.fanGoodsDao().getAllFlow().first().single()
        assertEquals(listOf("GOTTEN", 1, "5000"), listOf(f.fanGoodsStatus, f.fanGoodsQuantity, f.fanGoodsPrice))
        db.close()
    }

    @Test
    fun 변경_기록_DB_1에서_4까지() = runBlocking {
        schemaDb("c1", ChangedGoodsDatabase::class.java, 1,
            "INSERT INTO tb_changed_goods (changedTime, oldSeries, oldChara, oldCategory, oldPrice, oldMemo, oldUrl, oldStatus, newSeries, newChara, newCategory, newPrice, newMemo, newUrl, changedDetail, isRestored) VALUES (1,'s','c','k','p','m','u','GOTTEN','s','c','k','p2','m','u','d',0)")
        val db = Room.databaseBuilder(context, ChangedGoodsDatabase::class.java, "c1")
            .addMigrations(ChangedGoodsDatabase.MIGRATION_1_2, ChangedGoodsDatabase.MIGRATION_2_3, ChangedGoodsDatabase.MIGRATION_3_4).build()
        val r = db.changedGoodsDao().getAllFlow().first().single()
        assertEquals(listOf(1, false, 0, false, true), listOf(r.oldQuantity, r.statusKept, r.newUid, r.isChecked, r.canRestore))
        db.close()
    }

    @Test
    fun 다른_DB들도_지금_스키마_그대로_열린다() = runBlocking {
        // 캐릭터·시리즈·소식·업데이트 이력은 스키마 파일이 하나뿐이다. 앱의 빌더와 같은 방식으로 열리는지 본다.
        for ((name, cls, v) in listOf(
            Triple("chara", com.example.wowagoodsproject.db.character.CharaDatabase::class.java, 2),
            Triple("series", com.example.wowagoodsproject.db.series.SeriesDatabase::class.java, 2),
            Triple("news", com.example.wowagoodsproject.db.news.NewsDatabase::class.java, 1),
            Triple("patch", com.example.wowagoodsproject.db.patchnote.PatchNoteDatabase::class.java, 1)
        )) {
            schemaDb(name, cls, v)
            @Suppress("UNCHECKED_CAST")
            val db = Room.databaseBuilder(context, cls as Class<androidx.room.RoomDatabase>, name).build()
            db.openHelper.writableDatabase // 스키마가 다르면 여기서 실패한다
            db.close()
        }
    }

    // ── 스키마 파일이 없는 옛 DB ─────────────────────────────────────────

    /** 그 버전의 Room 이 만들던 모양 그대로 DB 파일을 만든다. */
    private fun legacyDb(name: String, version: Int, createSql: String, vararg inserts: String) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        file.delete()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(createSql)
            inserts.forEach { db.execSQL(it) }
            db.version = version
        }
    }

    @Test
    fun 가격이_숫자이던_1_3부터_2_0까지의_공식_굿즈_DB를_올린다() = runBlocking {
        legacyDb(
            "legacy_goods", 2,
            "CREATE TABLE IF NOT EXISTS `tb_goods` (`goodsId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `goodsReleaseDate` TEXT NOT NULL, `goodsSeries` TEXT NOT NULL, `goodsPrice` INTEGER NOT NULL, `goodsChara` TEXT NOT NULL, `goodsCategory` TEXT NOT NULL, `goodsIsGotten` INTEGER NOT NULL, `goodsUrl` TEXT NOT NULL, `goodsMemo` TEXT NOT NULL)",
            "INSERT INTO tb_goods (goodsReleaseDate, goodsSeries, goodsPrice, goodsChara, goodsCategory, goodsIsGotten, goodsUrl, goodsMemo) VALUES ('2024-07-04','1.0',59,'감심','인형',1,'u','단품')",
            "INSERT INTO tb_goods (goodsReleaseDate, goodsSeries, goodsPrice, goodsChara, goodsCategory, goodsIsGotten, goodsUrl, goodsMemo) VALUES ('2024-07-04','1.0',0,'감심','키링',0,'u','')"
        )
        val db = Room.databaseBuilder(context, GoodsDatabase::class.java, "legacy_goods").addMigrations(*goodsMigrations).build()
        val all = db.goodsDao().getAll().sortedBy { it.goodsCategory }
        assertEquals(mapOf("인형" to "59", "키링" to ""), all.associate { it.goodsCategory to it.goodsPrice }) // 0 → 빈 값
        assertEquals("GOTTEN", all.single { it.goodsCategory == "인형" }.goodsStatus)
        db.close()
    }

    @Test
    fun 가격이_숫자이던_1_1_1_2의_메모_없는_공식_굿즈_DB_1을_올린다() = runBlocking {
        legacyDb(
            "legacy_goods_v1", 1,
            "CREATE TABLE IF NOT EXISTS `tb_goods` (`goodsId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `goodsReleaseDate` TEXT NOT NULL, `goodsSeries` TEXT NOT NULL, `goodsPrice` INTEGER NOT NULL, `goodsChara` TEXT NOT NULL, `goodsCategory` TEXT NOT NULL, `goodsIsGotten` INTEGER NOT NULL, `goodsUrl` TEXT NOT NULL)",
            "INSERT INTO tb_goods (goodsReleaseDate, goodsSeries, goodsPrice, goodsChara, goodsCategory, goodsIsGotten, goodsUrl) VALUES ('','1.0',100,'감심','인형',1,'u')"
        )
        val db = Room.databaseBuilder(context, GoodsDatabase::class.java, "legacy_goods_v1").addMigrations(*goodsMigrations).build()
        val g = db.goodsDao().getAll().single()
        assertEquals(listOf("100", "", "GOTTEN"), listOf(g.goodsPrice, g.goodsMemo, g.goodsStatus))
        db.close()
    }

    @Test
    fun 가격이_글자이던_1_0의_공식_굿즈_DB_2도_그대로_올린다() = runBlocking {
        legacyDb(
            "legacy_goods_text", 2,
            "CREATE TABLE IF NOT EXISTS `tb_goods` (`goodsId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `goodsReleaseDate` TEXT NOT NULL, `goodsSeries` TEXT NOT NULL, `goodsPrice` TEXT NOT NULL, `goodsChara` TEXT NOT NULL, `goodsCategory` TEXT NOT NULL, `goodsIsGotten` INTEGER NOT NULL, `goodsUrl` TEXT NOT NULL, `goodsMemo` TEXT NOT NULL)",
            "INSERT INTO tb_goods (goodsReleaseDate, goodsSeries, goodsPrice, goodsChara, goodsCategory, goodsIsGotten, goodsUrl, goodsMemo) VALUES ('','1.0','59위안','감심','인형',1,'u','')"
        )
        val db = Room.databaseBuilder(context, GoodsDatabase::class.java, "legacy_goods_text").addMigrations(*goodsMigrations).build()
        assertEquals("59위안", db.goodsDao().getAll().single().goodsPrice)
        db.close()
    }

    @Test
    fun 가격이_숫자이던_2차창작_DB_2를_올린다() = runBlocking {
        legacyDb(
            "legacy_fan", 2,
            "CREATE TABLE IF NOT EXISTS `tb_fan_goods` (`fanGoodsId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fanGoodsReleaseDate` TEXT NOT NULL, `fanGoodsSeries` TEXT NOT NULL, `fanGoodsPrice` INTEGER NOT NULL, `fanGoodsChara` TEXT NOT NULL, `fanGoodsCategory` TEXT NOT NULL, `fanGoodsImgPath` TEXT NOT NULL, `fanGoodsIsGotten` INTEGER NOT NULL, `fanGoodsMemo` TEXT NOT NULL)",
            "INSERT INTO tb_fan_goods (fanGoodsReleaseDate, fanGoodsSeries, fanGoodsPrice, fanGoodsChara, fanGoodsCategory, fanGoodsImgPath, fanGoodsIsGotten, fanGoodsMemo) VALUES ('','c1',15000,'감심','키링','/x.jpg',1,'m')"
        )
        val db = Room.databaseBuilder(context, FanGoodsDatabase::class.java, "legacy_fan").addMigrations(*fanMigrations).build()
        val f = db.fanGoodsDao().getAllFlow().first().single()
        assertEquals(listOf("15000", "GOTTEN", "m"), listOf(f.fanGoodsPrice, f.fanGoodsStatus, f.fanGoodsMemo))
        db.close()
    }
}
