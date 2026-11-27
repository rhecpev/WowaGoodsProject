package com.example.wowagoodsproject

import android.app.Application
import com.example.wowagoodsproject.component.GoodsStatus
import com.example.wowagoodsproject.db.betray.BetrayGenreEntity
import com.example.wowagoodsproject.db.betray.BetrayGoodsEntity
import com.example.wowagoodsproject.db.betray.BetrayKind
import com.example.wowagoodsproject.db.betray.saveChara
import com.example.wowagoodsproject.db.betray.saveSeries
import com.example.wowagoodsproject.db.betray.toggleFavorite
import com.example.wowagoodsproject.db.character.CharaEntity
import com.example.wowagoodsproject.db.fan.FanGoodsEntity
import com.example.wowagoodsproject.db.fan.saveSeries
import com.example.wowagoodsproject.db.official.GoodsEntity
import com.example.wowagoodsproject.screen.mypage.BackupZip
import com.example.wowagoodsproject.screen.mypage.ExportSelection
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 웹(wuwa-goods-web)에 넣어 볼 백업 파일을 **앱의 추출 코드(BackupZip.write)** 로 만든다. 기기·에뮬레이터는 쓰지 않는다.
 * 굿즈 데이터는 실제 wuwa-goods-data 를 넣어 웹이 받는 데이터와 굿즈 번호가 맞게 한다.
 * 환경 변수가 없으면 건너뛴다(평소 테스트에는 끼지 않는다).
 * - WUWA_DATA_DIR: wuwa-goods-data 폴더
 * - WUWA_WEB_FIXTURE_PHOTOS: 사진(png) 폴더
 * - WUWA_WEB_FIXTURE_OUT: 만든 zip 을 둘 폴더
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class WebHandoffFixture {

    @Before
    fun setUp() = TestDb.setUp()

    @After
    fun tearDown() = TestDb.tearDown()

    @Test
    fun 웹에_넣을_백업을_앱_추출_코드로_만든다() = runBlocking {
        val dataDir = System.getenv("WUWA_DATA_DIR")?.let(::File)
        val photoDir = System.getenv("WUWA_WEB_FIXTURE_PHOTOS")?.let(::File)
        val outDir = System.getenv("WUWA_WEB_FIXTURE_OUT")?.let(::File)
        assumeTrue(dataDir != null && photoDir != null && outDir != null && File(dataDir, "goods.json").isFile)
        outDir!!.mkdirs()

        val gson = Gson()
        val goods: List<GoodsEntity> = gson.fromJson(File(dataDir, "goods.json").readText(), object : TypeToken<List<GoodsEntity>>() {}.type)
        val charas: List<CharaEntity> = gson.fromJson(File(dataDir, "characters.json").readText(), object : TypeToken<List<CharaEntity>>() {}.type)
        goods.forEach { App.database.goodsDao().insert(it.copy(goodsId = 0)) }
        charas.forEach { App.charaDatabase.charaDao().insert(it.copy(charaId = 0)) }

        // 사진은 앱 폴더(filesDir)에 앱과 같은 이름 모양으로 둔다.
        fun photo(name: String): String {
            val src = File(photoDir, "$name.png")
            val dst = File(TestDb.context.filesDir, "$name.png")
            src.copyTo(dst, overwrite = true)
            return dst.absolutePath
        }

        // ── 명조: 공식 보유 5개 · 구매예정 3개(구매 정보 포함) ──
        val gdao = App.database.goodsDao()
        val all = gdao.getAll().filter { it.goodsCategory != "세트" }.sortedBy { it.goodsUid }
        all.take(5).forEach { gdao.update(it.copy(goodsStatus = GoodsStatus.GOTTEN.name, goodsIsGotten = true, goodsQuantity = 2)) }
        all.drop(5).take(3).forEach {
            gdao.update(it.copy(goodsStatus = GoodsStatus.PENDING.name, goodsPurchaseStore = "알리익스프레스", goodsPurchaseDate = "2026-11-20", goodsReceiveDate = "2026-12-10"))
        }
        // 선호 캐릭터 2명(순서 있음)
        val cdao = App.charaDatabase.charaDao()
        cdao.getAll().take(2).forEachIndexed { i, c -> cdao.update(c.copy(charaIsFavorite = true, charaFavoriteOrder = i + 1)) }
        val favName = cdao.getAll().first { it.charaIsFavorite && it.charaFavoriteOrder == 1 }.charaNm

        // 비공식(2차창작) 2개 + 비공식 시리즈 1개
        val fdao = App.fanDatabase.fanGoodsDao()
        fdao.insert(FanGoodsEntity(fanGoodsReleaseDate = "2026-11-01", fanGoodsSeries = "앱테스트 코믹", fanGoodsChara = favName, fanGoodsCategory = "아크릴 스탠드",
            fanGoodsPrice = "12,000원", fanGoodsImgPath = photo("fan_a"), fanGoodsStatus = "GOTTEN", fanGoodsIsGotten = true, fanGoodsMemo = "앱에서 등록"))
        fdao.insert(FanGoodsEntity(fanGoodsReleaseDate = "2026-11-02", fanGoodsSeries = "앱테스트 코믹", fanGoodsChara = favName, fanGoodsCategory = "키링",
            fanGoodsPrice = "8,000원", fanGoodsImgPath = photo("fan_b"), fanGoodsStatus = "PENDING", fanGoodsPurchaseStore = "트위터 통판", fanGoodsPurchaseDate = "2026-11-25"))
        fdao.saveSeries(null, "앱테스트 코믹", photo("fan_series_bg"), "", "2026-11-01")

        // ── 배신 굿즈: 원신(배경·아이콘·캐릭터·시리즈·선호), 블루아카 ──
        val bdao = App.betrayDatabase.betrayDao()
        val gs = bdao.insertGenre(BetrayGenreEntity(genreNm = "원신", genreOrder = 1, genreImgPath = photo("betray_genre_gs_bg"), genreIconPath = photo("betray_genre_gs_icon"))).toInt()
        val ba = bdao.insertGenre(BetrayGenreEntity(genreNm = "블루아카", genreOrder = 2)).toInt()
        fun bg(genre: Int, chara: String, kind: BetrayKind, status: GoodsStatus, series: String, img: String = "", reg: String = "2026-11-10") = BetrayGoodsEntity(
            genreId = genre, goodsKind = kind.name, goodsRegDate = reg, goodsSeries = series, goodsChara = chara, goodsCategory = "아크릴 스탠드",
            goodsPrice = "3,000엔", goodsStatus = status.name, goodsImgPath = img
        )
        // 직접 추가한 캐릭터는 굿즈에 이름이 나오기 전에 만든다(이미 굿즈에 있는 이름은 앱이 막는다).
        check(bdao.saveChara(gs, null, "느비예트", photo("betray_chara_nv")))
        bdao.insertGoods(bg(gs, "푸리나", BetrayKind.OFFICIAL, GoodsStatus.GOTTEN, "폰타인 팝업", photo("betray_gs1")))
        bdao.insertGoods(bg(gs, "푸리나,나히다", BetrayKind.FAN, GoodsStatus.GOTTEN, "동인 행사", photo("betray_gs2")))
        bdao.insertGoods(bg(gs, "나히다", BetrayKind.OFFICIAL, GoodsStatus.PENDING, "폰타인 팝업").copy(goodsPurchaseStore = "미호요 스토어", goodsPurchaseDate = "2026-11-21"))
        bdao.insertGoods(bg(gs, "느비예트", BetrayKind.OFFICIAL, GoodsStatus.NOT_GOTTEN, "폰타인 팝업"))
        bdao.insertGoods(bg(ba, "호시노", BetrayKind.OFFICIAL, GoodsStatus.GOTTEN, "", photo("betray_ba1")))
        bdao.saveSeries(gs, null, "폰타인 팝업", photo("betray_series_pop"), "", "2026-10-30")
        bdao.toggleFavorite(gs, "나히다")
        bdao.toggleFavorite(gs, "푸리나")
        bdao.toggleFavorite(ba, "호시노")

        // 앱의 추출 그대로: 전체 / 원신만(장르 화면의 '이 장르만 추출')
        val full = File(outDir, "app_export_full.zip")
        val genre = File(outDir, "app_export_genshin.zip")
        BackupZip.write(full)
        BackupZip.write(genre, selection = ExportSelection.genreOnly(gs))
        println("FIXTURE ${full.absolutePath} ${full.length()} / ${genre.absolutePath} ${genre.length()}")
    }
}
