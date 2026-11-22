package com.example.wowagoodsproject

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Looper
import com.example.wowagoodsproject.db.betray.BetrayGenreEntity
import com.example.wowagoodsproject.db.betray.BetrayGoodsEntity
import com.example.wowagoodsproject.screen.betray.BetrayCharaEditViewModel
import com.example.wowagoodsproject.screen.betray.BetrayGenreEditViewModel
import com.example.wowagoodsproject.screen.betray.CharaEditTarget
import com.example.wowagoodsproject.screen.betray.GenrePhoto
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 장르·캐릭터 편집 화면의 사진 흐름(뷰모델).
 * 장르: 사진 넣기 → 배경 + 가운데 아이콘 자동, 아이콘이 배경을 따라가는지/따로인지, 위치·크기 변경, 빼기, 저장.
 * 캐릭터: 선호/비선호를 골라야 저장, 사진 넣기 → 가운데 원 자동, 저장하면 WebP 사진과 선호가 DB 에.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BetrayEditViewModelTest {

    private val context get() = TestDb.context
    private val dao get() = App.betrayDatabase.betrayDao()
    private lateinit var src: File

    @Before
    fun setUp() {
        TestDb.setUp()
        src = File(context.cacheDir, "src").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun tearDown() = TestDb.tearDown()

    /** 왼쪽 절반 [left], 오른쪽 절반 [right] 인 사진 */
    private fun photo(name: String, w: Int, h: Int, left: Int, right: Int = left): Uri {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (x in 0 until w) for (y in 0 until h) bmp.setPixel(x, y, if (x < w / 2) left else right)
        val f = File(src, name)
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(f)
    }

    /** 뷰모델 일(메인 + IO)이 끝날 때까지 */
    private fun await(cond: () -> Boolean) {
        repeat(300) {
            shadowOf(Looper.getMainLooper()).idle()
            if (cond()) return
            Thread.sleep(10)
        }
        throw AssertionError("기다리던 상태가 오지 않았다")
    }

    private fun centerColor(p: GenrePhoto): Int {
        val path = when (p) {
            is GenrePhoto.Picked -> p.uri.path!!
            is GenrePhoto.Saved -> p.path
            GenrePhoto.None -> throw AssertionError("사진이 없다")
        }
        val b = BitmapFactory.decodeFile(path)
        return b.getPixel(b.width / 2, b.height / 2)
    }

    private fun isBlueish(c: Int) = Color.blue(c) > 180 && Color.red(c) < 80
    private fun isRedish(c: Int) = Color.red(c) > 180 && Color.blue(c) < 80
    private fun isGreenish(c: Int) = Color.green(c) > 180 && Color.red(c) < 80

    // ── 장르 ──

    @Test
    fun 사진을_넣으면_배경과_가운데_아이콘이_자동으로_정해지고_원_화면은_안_열린다() {
        val vm = BetrayGenreEditViewModel()
        vm.open(null)
        val banner = photo("b.png", 400, 200, Color.RED, Color.BLUE)
        vm.setBanner(context, banner)
        await { vm.icon.value is GenrePhoto.Picked && !vm.isLoading.value }
        assertEquals(GenrePhoto.Picked(banner), vm.banner.value)
        assertNull("원 범위 화면은 자동으로 열지 않는다", vm.cropBitmap.value)
        val icon = BitmapFactory.decodeFile((vm.icon.value as GenrePhoto.Picked).uri.path)
        assertEquals("가운데 가장 큰 원 = 짧은 변 정사각형", icon.width, icon.height)
        assertEquals(200, icon.width)
    }

    @Test
    fun 아이콘이_배경을_따라가면_배경을_바꿀_때_아이콘도_다시_정해진다() {
        val vm = BetrayGenreEditViewModel()
        vm.open(null)
        vm.setBanner(context, photo("r.png", 200, 200, Color.RED))
        await { vm.icon.value is GenrePhoto.Picked && !vm.isLoading.value }
        assertTrue(isRedish(centerColor(vm.icon.value)))
        vm.setBanner(context, photo("g.png", 200, 200, Color.GREEN))
        await { !vm.isLoading.value && isGreenish(centerColor(vm.icon.value)) }
    }

    @Test
    fun 아이콘을_따로_고르면_배경을_바꿔도_아이콘은_그대로다() {
        val vm = BetrayGenreEditViewModel()
        vm.open(null)
        vm.setBanner(context, photo("r.png", 200, 200, Color.RED))
        await { vm.icon.value is GenrePhoto.Picked && !vm.isLoading.value }
        // 아이콘 → 사진 변경: 원 범위 화면이 열리고, 확인하면 그 사진이 아이콘이 된다
        vm.setIconPhoto(context, photo("blue.png", 300, 300, Color.BLUE))
        await { vm.cropBitmap.value != null }
        vm.confirmCrop(context, 150f, 150f, 150f)
        await { vm.cropBitmap.value == null && isBlueish(centerColor(vm.icon.value)) }
        val customIcon = vm.icon.value
        vm.setBanner(context, photo("g.png", 200, 200, Color.GREEN))
        await { !vm.isLoading.value }
        assertEquals("아이콘은 따로 고른 것 그대로", customIcon, vm.icon.value)
        // 위치·크기 변경은 따로 고른 사진(파랑)에서 다시 고른다
        vm.recropIcon(context)
        await { vm.cropBitmap.value != null }
        val crop = vm.cropBitmap.value!!
        assertTrue(isBlueish(crop.getPixel(crop.width / 2, crop.height / 2)))
        vm.cancelCrop()
        // 배경을 빼도 따로 고른 아이콘은 남는다
        vm.removeBanner()
        assertEquals(GenrePhoto.None, vm.banner.value)
        assertEquals(customIcon, vm.icon.value)
    }

    @Test
    fun 아이콘_사진_변경을_취소하면_아이콘과_따라가기가_그대로다() {
        val vm = BetrayGenreEditViewModel()
        vm.open(null)
        vm.setBanner(context, photo("r.png", 200, 200, Color.RED))
        await { vm.icon.value is GenrePhoto.Picked && !vm.isLoading.value }
        val before = vm.icon.value
        vm.setIconPhoto(context, photo("blue.png", 300, 300, Color.BLUE))
        await { vm.cropBitmap.value != null }
        vm.cancelCrop()
        assertEquals(before, vm.icon.value)
        // 취소했으니 아이콘은 여전히 배경을 따라간다
        vm.setBanner(context, photo("g.png", 200, 200, Color.GREEN))
        await { !vm.isLoading.value && isGreenish(centerColor(vm.icon.value)) }
    }

    @Test
    fun 배경_사진에서_위치_크기를_바꿔도_계속_배경을_따라간다() {
        val vm = BetrayGenreEditViewModel()
        vm.open(null)
        vm.setBanner(context, photo("rb.png", 400, 200, Color.RED, Color.BLUE))
        await { vm.icon.value is GenrePhoto.Picked && !vm.isLoading.value }
        vm.recropIcon(context)
        await { vm.cropBitmap.value != null }
        vm.confirmCrop(context, 300f, 100f, 80f) // 오른쪽(파랑)
        await { vm.cropBitmap.value == null && isBlueish(centerColor(vm.icon.value)) }
        vm.removeBanner()
        assertEquals("배경을 따라가던 아이콘은 함께 빠진다", GenrePhoto.None, vm.icon.value)
    }

    @Test
    fun 저장하면_배경과_아이콘이_WebP로_저장되고_임시_파일은_지워진다() = runBlocking {
        val vm = BetrayGenreEditViewModel()
        vm.open(null)
        vm.name.value = "원신"
        vm.setBanner(context, photo("b.png", 400, 200, Color.RED, Color.BLUE))
        await { vm.icon.value is GenrePhoto.Picked && !vm.isLoading.value }
        val temp = File((vm.icon.value as GenrePhoto.Picked).uri.path!!)
        assertTrue(temp.exists())
        var result: Boolean? = null
        vm.save(context) { result = it }
        await { result != null }
        assertEquals(true, result)
        val g = dao.genres().single()
        assertEquals("원신", g.genreNm)
        assertTrue(g.genreImgPath.endsWith(".webp") && File(g.genreImgPath).isFile)
        assertTrue(g.genreIconPath.endsWith(".webp") && File(g.genreIconPath).isFile)
        await { !temp.exists() }
    }

    @Test
    fun 이름이_겹치면_저장하지_않는다() = runBlocking {
        dao.insertGenre(BetrayGenreEntity(genreNm = "원신"))
        val vm = BetrayGenreEditViewModel()
        vm.open(null)
        vm.name.value = " 원신 "
        var result: Boolean? = null
        vm.save(context) { result = it }
        await { result != null }
        assertEquals(false, result)
        assertEquals(1, dao.genres().size)
    }

    @Test
    fun 저장된_사진이_있는_장르를_열면_그대로_보이고_건드리지_않으면_그대로_저장된다() = runBlocking {
        val bg = File(context.filesDir, "betray_genre_bg.webp").apply { writeBytes(ByteArray(10)) }
        val ic = File(context.filesDir, "betray_genre_ic.webp").apply { writeBytes(ByteArray(10)) }
        val id = dao.insertGenre(BetrayGenreEntity(genreNm = "원신", genreImgPath = bg.absolutePath, genreIconPath = ic.absolutePath)).toInt()
        val vm = BetrayGenreEditViewModel()
        vm.open(dao.genre(id))
        assertEquals(GenrePhoto.Saved(bg.absolutePath), vm.banner.value)
        assertEquals(GenrePhoto.Saved(ic.absolutePath), vm.icon.value)
        vm.name.value = "원신2"
        var result: Boolean? = null
        vm.save(context) { result = it }
        await { result != null }
        val g = dao.genre(id)!!
        assertEquals("원신2", g.genreNm)
        assertEquals(bg.absolutePath, g.genreImgPath)
        assertEquals(ic.absolutePath, g.genreIconPath)
        assertTrue(bg.exists() && ic.exists())
    }

    // ── 캐릭터 ──

    @Test
    fun 새_캐릭터는_선호_비선호를_고르기_전에는_저장되지_않는다() = runBlocking {
        val genre = dao.insertGenre(BetrayGenreEntity(genreNm = "원신")).toInt()
        val vm = BetrayCharaEditViewModel()
        vm.open(genre, CharaEditTarget("", "", registered = false))
        assertNull("처음에는 아무것도 안 고른 상태", vm.favorite.value)
        vm.name.value = "나히다"
        var saved: String? = "x"
        vm.save(context, genre, null) { saved = it }
        await { saved != "x" }
        assertNull(saved)
        assertTrue(dao.charasOf(genre).isEmpty())
    }

    @Test
    fun 선호를_고르고_사진을_넣어_저장하면_WebP_사진과_선호가_저장된다() = runBlocking {
        val genre = dao.insertGenre(BetrayGenreEntity(genreNm = "원신")).toInt()
        val vm = BetrayCharaEditViewModel()
        vm.open(genre, CharaEditTarget("", "", registered = false, prefill = "나히다"))
        assertEquals("검색어가 이름 칸에", "나히다", vm.name.value)
        vm.favorite.value = true
        vm.onPicked(context, photo("c.png", 300, 200, Color.GREEN))
        await { vm.photo.value is GenrePhoto.Picked && !vm.isLoading.value }
        val icon = BitmapFactory.decodeFile((vm.photo.value as GenrePhoto.Picked).uri.path)
        assertEquals("가운데 원 자동", icon.width, icon.height)
        var saved: String? = null
        vm.save(context, genre, null) { saved = it }
        await { saved != null }
        assertEquals("나히다", saved)
        val c = dao.chara(genre, "나히다")
        assertNotNull(c)
        assertTrue(c!!.charaImgPath.endsWith(".webp") && File(c.charaImgPath).isFile)
        assertEquals(listOf("나히다"), dao.favoritesOf(genre).map { it.charaNm })
    }

    @Test
    fun 비선호로_저장하면_선호에_들지_않고_고칠_때는_지금_상태로_시작한다() = runBlocking {
        val genre = dao.insertGenre(BetrayGenreEntity(genreNm = "원신")).toInt()
        dao.insertGoods(BetrayGoodsEntity(genreId = genre, goodsChara = "푸리나"))
        val vm = BetrayCharaEditViewModel()
        vm.open(genre, CharaEditTarget("", "", registered = false))
        vm.name.value = "벤티"
        vm.favorite.value = false
        var saved: String? = null
        vm.save(context, genre, null) { saved = it }
        await { saved != null }
        assertTrue(dao.favoritesOf(genre).isEmpty())

        // 굿즈에만 있던 선호 캐릭터를 고치기: 처음 값은 선호
        val vm2 = BetrayCharaEditViewModel()
        vm2.open(genre, CharaEditTarget("푸리나", "", registered = false, favorite = true))
        assertEquals(true, vm2.favorite.value)
        assertNotEquals(null, vm2.favorite.value)
    }

    @Test
    fun 캐릭터_사진_위치_크기_변경은_고른_원본에서_다시_고른다() {
        val genre = runBlocking { dao.insertGenre(BetrayGenreEntity(genreNm = "원신")).toInt() }
        val vm = BetrayCharaEditViewModel()
        vm.open(genre, CharaEditTarget("", "", registered = false))
        vm.onPicked(context, photo("rb.png", 400, 200, Color.RED, Color.BLUE))
        await { vm.photo.value is GenrePhoto.Picked && !vm.isLoading.value }
        vm.recrop(context)
        await { vm.cropBitmap.value != null }
        assertEquals("원본 전체(400x200)에서 고른다", 400, vm.cropBitmap.value!!.width)
        vm.confirmCrop(context, 300f, 100f, 90f)
        await { vm.cropBitmap.value == null && isBlueish(centerColor(vm.photo.value)) }
        vm.removePhoto()
        assertEquals(GenrePhoto.None, vm.photo.value)
    }
}
