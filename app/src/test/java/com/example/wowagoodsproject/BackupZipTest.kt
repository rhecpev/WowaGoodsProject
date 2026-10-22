package com.example.wowagoodsproject

import android.app.Application
import com.example.wowagoodsproject.component.CATEGORY_SET
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.screen.mypage.BackupZip
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

/**
 * 백업 zip 을 실제로 만들고 불러와 본다. DB 는 메모리 DB 다.
 * 확인하는 것: 모든 칸이 그대로 돌아오는지, 버전마다 다른 옛 백업, 깨진 파일, 이상한 경로, 큰 사진, 되풀이.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BackupZipTest {

    private val goodsDao get() = App.database.goodsDao()
    private val fanDao get() = App.fanDatabase.fanGoodsDao()
    private val charaDao get() = App.charaDatabase.charaDao()
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

    // ── 준비 ─────────────────────────────────────────────────────────────

    private fun official(
        uid: Int, category: String, price: String = "59위안", memo: String = "단품",
        series: String = "1.0버전", chara: String = "감심"
    ) = GoodsEntity(
        goodsUid = uid, goodsSeries = series, goodsChara = chara, goodsCategory = category,
        goodsPrice = price, goodsMemo = memo, goodsReleaseDate = "2024-07-04", goodsUrl = "https://x/$uid.jpg"
    )

    /** 상태·수량·구매 정보·배송 시작일의 경우를 모두 담은 공식 굿즈 */
    private suspend fun seedOfficial() {
        goodsDao.insert(official(1, "인형"))                                       // 미보유
        goodsDao.insert(official(2, "키링").copy(goodsStatus = "GOTTEN", goodsIsGotten = true, goodsQuantity = 3))
        goodsDao.insert(official(3, "아크릴").copy(                               // 구매예정 + 모든 정보
            goodsStatus = "PENDING", goodsQuantity = 2, goodsPurchaseStore = "공식몰 \"특가\"",
            goodsPurchaseDate = "2026-01-02", goodsReceiveDate = "2026-03-04", goodsShippingDate = "2026-02-03"
        ))
        goodsDao.insert(official(4, "엽서", memo = "").copy(goodsStatus = "PENDING")) // 메모 없음, 정보 없음
        goodsDao.insert(official(5, CATEGORY_SET, price = "199위안", memo = "A세트").copy(goodsStatus = "GOTTEN", goodsIsGotten = true))
        goodsDao.insert(official(6, "배지", memo = "A세트").copy(goodsStatus = "GOTTEN", goodsIsGotten = true))
        goodsDao.insert(official(7, "인형", chara = "금희,장리", price = "₩29,000", memo = "특전_포함/한정 ✨").copy(goodsStatus = "GOTTEN", goodsIsGotten = true))
    }

    private fun image(name: String, size: Int = 2048): File =
        File(TestDb.context.filesDir, name).apply { writeBytes(Random(name.hashCode()).nextBytes(size)) }

    private suspend fun seedFan() {
        fanDao.insertAll(listOf(
            FanGoodsEntity(fanGoodsSeries = "c107", fanGoodsChara = "플로로", fanGoodsCategory = "스티커", fanGoodsPrice = "5000원",
                fanGoodsImgPath = image("fan_a.jpg").absolutePath, fanGoodsStatus = "GOTTEN", fanGoodsIsGotten = true, fanGoodsQuantity = 4),
            FanGoodsEntity(fanGoodsSeries = "c107", fanGoodsChara = "카멜리아", fanGoodsCategory = "아크릴",
                fanGoodsImgPath = image("fan_b.png").absolutePath, fanGoodsStatus = "PENDING", fanGoodsPurchaseStore = "부스 A-12",
                fanGoodsShippingDate = "2026-05-01", fanGoodsMemo = "메모"),
            FanGoodsEntity(fanGoodsSeries = "c108", fanGoodsChara = "치사", fanGoodsCategory = "엽서", fanGoodsImgPath = ""), // 사진 없음
            FanGoodsEntity(fanGoodsSeries = "c108", fanGoodsChara = "치사", fanGoodsCategory = "엽서2",
                fanGoodsImgPath = File(TestDb.context.filesDir, "missing.jpg").absolutePath) // 사진 파일이 지워진 굿즈
        ))
    }

    private suspend fun seedCharas(vararg favorites: String) {
        listOf("감심", "금희", "장리", "플로로").forEach {
            charaDao.insert(CharaEntity(charaNm = it, charaIsFavorite = it in favorites))
        }
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun restore(bytes: ByteArray) = runBlocking { BackupZip.restore(ByteArrayInputStream(bytes), filesDir) }

    private fun restoreFails(bytes: ByteArray): String {
        try {
            restore(bytes)
        } catch (e: IllegalStateException) {
            return e.message.orEmpty()
        }
        fail("불러오기가 실패해야 한다")
        error("unreachable")
    }

    /** 사용자 칸만 모은 비교용 값 */
    private fun userState(g: GoodsEntity) = listOf(
        g.goodsStatus, g.goodsQuantity, g.goodsPurchaseStore, g.goodsPurchaseDate, g.goodsReceiveDate, g.goodsShippingDate, g.goodsIsGotten
    )

    private suspend fun stateByUid() = goodsDao.getAll().associate { it.goodsUid to userState(it) }

    private suspend fun clearUserData() {
        goodsDao.resetAllGotten()
        fanDao.deleteAll()
        charaDao.getAll().forEach { charaDao.update(it.copy(charaIsFavorite = false)) }
    }

    // ── 왕복 ─────────────────────────────────────────────────────────────

    @Test
    fun 추출한_백업을_불러오면_모든_칸이_그대로_돌아온다() = runBlocking {
        seedOfficial(); seedFan(); seedCharas("감심", "플로로")
        val before = stateByUid()
        val fansBefore = fanDao.getAll().map { it.copy(fanGoodsId = 0, fanGoodsImgPath = File(it.fanGoodsImgPath).name) }
        val photoBefore = File(TestDb.context.filesDir, "fan_a.jpg").readBytes()
        val zip = File(workDir, "b.zip")
        BackupZip.write(zip)

        clearUserData()
        val report = BackupZip.restore(zip.inputStream(), filesDir)

        assertEquals(before, stateByUid())
        val fansAfter = fanDao.getAll().map { it.copy(fanGoodsId = 0, fanGoodsImgPath = File(it.fanGoodsImgPath).name) }
        assertEquals(fansBefore.sortedBy { it.fanGoodsCategory }, fansAfter.sortedBy { it.fanGoodsCategory })
        assertEquals(setOf("감심", "플로로"), charaDao.getAll().filter { it.charaIsFavorite }.map { it.charaNm }.toSet())
        // 사진은 바이트까지 같다
        assertArrayEquals(photoBefore, File(filesDir, "fan_a.jpg").readBytes())
        assertTrue(File(filesDir, "fan_b.png").isFile)
        assertEquals(6, report.ownedInBackup)
        assertEquals(6, report.restored)
        assertEquals(4, report.fanGoods)
        assertEquals(2, report.favorites)
    }

    @Test
    fun 불러오기는_지금_상태를_백업_상태로_바꾼다() = runBlocking {
        seedOfficial()
        val zip = File(workDir, "b.zip")
        BackupZip.write(zip)
        // 백업 뒤에 바뀐 것: 미보유를 보유로, 보유를 미보유로
        goodsDao.getAll().forEach {
            goodsDao.update(if (it.goodsUid == 1) it.copy(goodsStatus = "GOTTEN") else it.copy(goodsStatus = "NOT_GOTTEN"))
        }
        BackupZip.restore(zip.inputStream(), filesDir)
        assertEquals("NOT_GOTTEN", goodsDao.getAll().single { it.goodsUid == 1 }.goodsStatus)
        assertEquals("GOTTEN", goodsDao.getAll().single { it.goodsUid == 2 }.goodsStatus)
    }

    @Test
    fun 두_번_불러와도_결과가_같다() = runBlocking {
        seedOfficial(); seedFan(); seedCharas("금희")
        val zip = File(workDir, "b.zip")
        BackupZip.write(zip)
        BackupZip.restore(zip.inputStream(), filesDir)
        val once = stateByUid() to fanDao.getAll().size
        BackupZip.restore(zip.inputStream(), filesDir)
        assertEquals(once, stateByUid() to fanDao.getAll().size) // 2차창작이 두 배로 늘지 않는다
    }

    @Test
    fun 백업_뒤에_원본_데이터의_가격이_바뀌어도_번호로_되살린다() = runBlocking {
        seedOfficial()
        val zip = File(workDir, "b.zip")
        BackupZip.write(zip)
        val g = goodsDao.getAll().single { it.goodsUid == 2 }
        goodsDao.update(g.copy(goodsPrice = "69위안", goodsMemo = "재판", goodsStatus = "NOT_GOTTEN"))
        BackupZip.restore(zip.inputStream(), filesDir)
        assertEquals("GOTTEN", goodsDao.getAll().single { it.goodsUid == 2 }.goodsStatus)
    }

    @Test
    fun 같은_사진을_두_굿즈가_써도_추출이_실패하지_않는다() = runBlocking {
        val shared = image("fan_shared.jpg").absolutePath
        fanDao.insertAll(listOf(FanGoodsEntity(fanGoodsImgPath = shared), FanGoodsEntity(fanGoodsImgPath = shared)))
        val zip = File(workDir, "b.zip")
        BackupZip.write(zip)
        fanDao.deleteAll()
        BackupZip.restore(zip.inputStream(), filesDir)
        assertEquals(2, fanDao.getAll().count { it.fanGoodsImgPath.endsWith("fan_shared.jpg") })
    }

    @Test
    fun 큰_사진도_불러온다() = runBlocking {
        val big = image("fan_big.jpg", size = 48 * 1024 * 1024)
        fanDao.insert(FanGoodsEntity(fanGoodsImgPath = big.absolutePath))
        val size = big.length()
        val zip = File(workDir, "b.zip")
        BackupZip.write(zip)
        BackupZip.restore(zip.inputStream(), filesDir)
        assertEquals(size, File(filesDir, "fan_big.jpg").length())
    }

    // ── 버전별 옛 백업 ───────────────────────────────────────────────────

    @Test
    fun v1_0_백업을_불러온다_가격과_상태_칸이_없다() = runBlocking {
        seedOfficial()
        val json = """[
            {"goodsSeries":"1.0버전","goodsChara":"감심","goodsCategory":"인형","goodsIsGotten":true,"goodsMemo":"단품"},
            {"goodsSeries":"1.0버전","goodsChara":"감심","goodsCategory":"키링","goodsIsGotten":false,"goodsMemo":"단품"}]"""
        val fan = """[{"fanGoodsId":3,"fanGoodsReleaseDate":"","fanGoodsSeries":"c1","fanGoodsPrice":"3000","fanGoodsChara":"감심","fanGoodsCategory":"키링","fanGoodsImgPath":"/data/user/0/com.example.wowagoodsproject/files/fan_old.jpg","fanGoodsIsGotten":true,"fanGoodsMemo":""}]"""
        val report = restore(zipOf(
            "fan_goods.json" to fan.toByteArray(), "official_gotten.json" to json.toByteArray(),
            "images/fan_old.jpg" to byteArrayOf(1, 2, 3)
        ))
        val all = goodsDao.getAll()
        assertEquals("GOTTEN", all.single { it.goodsUid == 1 }.goodsStatus)
        assertEquals("NOT_GOTTEN", all.single { it.goodsUid == 2 }.goodsStatus) // 백업에서 미보유
        assertEquals(1, report.restored)
        val f = fanDao.getAll().single()
        assertEquals(GoodsStatus.GOTTEN.name, f.fanGoodsStatus)
        assertEquals(File(filesDir, "fan_old.jpg").absolutePath, f.fanGoodsImgPath)
        assertNull(report.favorites) // 2.0까지는 선호 캐릭터가 없다
    }

    @Test
    fun v1_1_백업을_불러온다_메모도_없고_2차창작_가격이_숫자다() = runBlocking {
        goodsDao.insert(official(1, "인형", memo = ""))
        val report = restore(zipOf(
            "official_gotten.json" to """[{"goodsSeries":"1.0버전","goodsChara":"감심","goodsCategory":"인형","goodsIsGotten":true}]""".toByteArray(),
            "fan_goods.json" to """[{"fanGoodsPrice":15000,"fanGoodsIsGotten":false},{"fanGoodsPrice":0}]""".toByteArray()
        ))
        assertEquals("GOTTEN", goodsDao.getAll().single().goodsStatus)
        assertEquals(1, report.restored)
        assertEquals(setOf("15000", ""), fanDao.getAll().map { it.fanGoodsPrice }.toSet())
    }

    @Test
    fun v2_1_백업을_불러온다() = runBlocking {
        seedOfficial()
        restore(zipOf("official_gotten.json" to """[{"goodsSeries":"1.0버전","goodsChara":"감심","goodsCategory":"아크릴","goodsStatus":"PENDING","goodsMemo":"단품","goodsPrice":"59위안","goodsPurchaseDate":"2025-12-01","goodsPurchaseStore":"매장"}]""".toByteArray()))
        val g = goodsDao.getAll().single { it.goodsUid == 3 }
        assertEquals(listOf("PENDING", 1, "매장", "2025-12-01", "", ""), userState(g).take(6))
    }

    @Test
    fun 실제_2_1_이전_형식처럼_구매_정보_칸이_전혀_없는_백업() = runBlocking {
        seedOfficial()
        restore(zipOf(
            "official_gotten.json" to """[{"goodsCategory":"키링","goodsChara":"감심","goodsMemo":"단품","goodsPrice":"59위안","goodsSeries":"1.0버전","goodsStatus":"GOTTEN"}]""".toByteArray(),
            "fan_goods.json" to """[{"fanGoodsId":15,"fanGoodsIsGotten":false,"fanGoodsStatus":"GOTTEN","fanGoodsSeries":"c107"}]""".toByteArray(),
            "favorite_charas.json" to """["감심"]""".toByteArray()
        ))
        assertEquals("GOTTEN", goodsDao.getAll().single { it.goodsUid == 2 }.goodsStatus)
        assertTrue(fanDao.getAll().single().fanGoodsIsGotten) // 예전 칸을 상태에 맞춘다
    }

    // ── 깨진 파일·이상한 파일 ────────────────────────────────────────────

    @Test
    fun zip이_아니면_아무것도_바꾸지_않는다() = runBlocking {
        seedOfficial(); seedFan()
        val before = stateByUid() to fanDao.getAll()
        assertEquals("백업 파일이 아닙니다", restoreFails(Random.nextBytes(4096)))
        assertEquals("백업 파일이 아닙니다", restoreFails(ByteArray(0)))
        assertEquals("백업 파일이 아닙니다", restoreFails(zipOf("readme.txt" to "hi".toByteArray())))
        assertEquals(before, stateByUid() to fanDao.getAll())
    }

    @Test
    fun 공식_굿즈_JSON이_깨져_있으면_2차창작도_바꾸지_않는다() = runBlocking {
        seedOfficial(); seedFan()
        val before = stateByUid() to fanDao.getAll()
        for (broken in listOf("""[{"goodsSeries":""", "null", "", "{}", """{"a":1}""")) {
            val message = restoreFails(zipOf(
                "fan_goods.json" to "[]".toByteArray(),
                "official_gotten.json" to broken.toByteArray()
            ))
            assertTrue(message, message.startsWith("백업 파일이 손상되었습니다"))
        }
        assertEquals(before, stateByUid() to fanDao.getAll())
    }

    @Test
    fun 빈_항목이_섞여_있으면_그_항목만_버린다() = runBlocking {
        seedOfficial()
        restore(zipOf(
            "official_gotten.json" to """[null,{"goodsSeries":"1.0버전","goodsChara":"감심","goodsCategory":"인형","goodsStatus":"GOTTEN","goodsMemo":"단품","goodsPrice":"59위안"}]""".toByteArray(),
            "favorite_charas.json" to """[null,"감심"]""".toByteArray()
        ))
        assertEquals("GOTTEN", goodsDao.getAll().single { it.goodsUid == 1 }.goodsStatus)
    }

    @Test
    fun 굿즈_데이터를_받기_전에는_불러오지_않는다() = runBlocking {
        seedFan()
        val fansBefore = fanDao.getAll()
        val message = restoreFails(zipOf(
            "fan_goods.json" to "[]".toByteArray(),
            "official_gotten.json" to """[{"goodsSeries":"1.0버전","goodsChara":"감심","goodsCategory":"인형","goodsStatus":"GOTTEN"}]""".toByteArray()
        ))
        assertTrue(message.startsWith("굿즈 데이터가 아직 없습니다"))
        assertEquals(fansBefore, fanDao.getAll())
    }

    @Test
    fun 보유_굿즈가_없는_백업은_데이터를_받기_전이어도_불러온다() = runBlocking {
        restore(zipOf("official_gotten.json" to "[]".toByteArray(), "fan_goods.json" to """[{"fanGoodsSeries":"c1"}]""".toByteArray()))
        assertEquals(1, fanDao.getAll().size)
    }

    @Test
    fun zip_안의_경로로_앱_폴더_밖에_쓰지_않는다() = runBlocking {
        val outside = File(filesDir.parentFile, "evil.txt").apply { delete() }
        restore(zipOf(
            "fan_goods.json" to "[]".toByteArray(),
            "images/../evil.txt" to "x".toByteArray(),
            "images/../../evil.txt" to "x".toByteArray(),
            "../images/ok.jpg" to "y".toByteArray(),
            "images/.." to "z".toByteArray(),
            "images/" to ByteArray(0)
        ))
        assertFalse(outside.exists())
        assertFalse(File(filesDir.parentFile?.parentFile, "evil.txt").exists())
        assertEquals("y", File(filesDir, "ok.jpg").readText()) // 이름만 써서 앱 폴더 안에 둔다
        assertEquals(setOf("ok.jpg"), filesDir.list()!!.toSet())
    }

    @Test
    fun PC에서_풀었다_다시_묶어_폴더가_하나_더_붙은_백업도_알아본다() = runBlocking {
        seedOfficial()
        restore(zipOf(
            "wowa_backup/" to ByteArray(0),
            "wowa_backup/official_gotten.json" to """[{"goodsUid":2,"goodsStatus":"PENDING"}]""".toByteArray(),
            "wowa_backup\\images\\fan_x.jpg" to "img".toByteArray()
        ))
        assertEquals("PENDING", goodsDao.getAll().single { it.goodsUid == 2 }.goodsStatus)
        assertEquals("img", File(filesDir, "fan_x.jpg").readText())
    }

    @Test
    fun 선호_캐릭터는_백업대로_켜고_끈다() = runBlocking {
        seedCharas("장리")
        restore(zipOf("favorite_charas.json" to """[" 감심 ","없는캐릭터"]""".toByteArray()))
        assertEquals(setOf("감심"), charaDao.getAll().filter { it.charaIsFavorite }.map { it.charaNm }.toSet())
    }

    @Test
    fun 공식_굿즈_파일이_없는_백업은_지금_보유_기록을_건드리지_않는다() = runBlocking {
        seedOfficial()
        val before = stateByUid()
        restore(zipOf("fan_goods.json" to "[]".toByteArray()))
        assertEquals(before, stateByUid())
    }

    @Test
    fun 불러오면_바뀌기_전_2차창작의_사진_중_안_쓰는_것은_지우고_다시_푼_사진은_남긴다() = runBlocking {
        // 앱처럼 사진을 불러오기 폴더에 둔다
        val keep = File(filesDir, "fan_keep.jpg").apply { writeText("old") }
        val gone = File(filesDir, "fan_gone.jpg").apply { writeText("old") }
        val unrelated = File(filesDir, "other.dat").apply { writeText("x") } // 2차창작이 쓰지 않던 파일은 건드리지 않는다
        fanDao.insertAll(listOf(FanGoodsEntity(fanGoodsImgPath = keep.absolutePath), FanGoodsEntity(fanGoodsImgPath = gone.absolutePath)))
        restore(zipOf(
            "fan_goods.json" to """[{"fanGoodsImgPath":"/old/fan_keep.jpg"}]""".toByteArray(),
            "images/fan_keep.jpg" to "new".toByteArray()
        ))
        assertEquals("new", keep.readText())
        assertFalse(gone.exists())
        assertTrue(unrelated.exists())
    }

    @Test
    fun 결과_문구는_백업에_있던_것만_알린다() = runBlocking {
        seedOfficial(); seedCharas()
        assertEquals("입력 완료! 2차창작 1개", restore(zipOf("fan_goods.json" to """[{"fanGoodsSeries":"c1"}]""".toByteArray())).message())
        assertEquals("입력 완료! 선호 캐릭터 1명", restore(zipOf("favorite_charas.json" to """["감심"]""".toByteArray())).message())
        val both = restore(zipOf(
            "official_gotten.json" to """[{"goodsUid":2,"goodsStatus":"GOTTEN"},{"goodsUid":999,"goodsStatus":"GOTTEN"}]""".toByteArray(),
            "fan_goods.json" to "[]".toByteArray()
        ))
        assertEquals("입력 완료! 보유·구매예정 2개 중 1개 (1개는 지금 데이터에서 찾지 못함), 2차창작 0개", both.message())
    }

    @Test
    fun 진행률은_끝까지_간다() = runBlocking {
        seedOfficial()
        val zip = File(workDir, "b.zip")
        BackupZip.write(zip)
        val seen = mutableListOf<Float>()
        BackupZip.restore(zip.inputStream(), filesDir) { seen += it }
        assertEquals(1f, seen.last())
        assertEquals(seen, seen.sorted()) // 뒤로 가지 않는다
    }
}
