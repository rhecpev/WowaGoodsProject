package com.example.wowagoodsproject

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.wowagoodsproject.component.PhotoStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 사진 올리기: 새로 넣는 사진은 WebP 로, 세워서, 줄여서 저장한다. GIF 는 그대로. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoStoreTest {

    private lateinit var context: Context
    private lateinit var srcDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        srcDir = File(context.cacheDir, "src").apply { deleteRecursively(); mkdirs() }
    }

    private fun jpeg(name: String, w: Int, h: Int, color: Int = Color.GREEN): File {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return File(srcDir, name).apply { outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
    }

    private fun size(path: String) = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        .also { BitmapFactory.decodeFile(path, it) }.let { it.outWidth to it.outHeight }

    @Test
    fun 사진을_고르면_WebP로_저장한다() = runBlocking {
        val path = PhotoStore.save(context, Uri.fromFile(jpeg("a.jpg", 1200, 800)), "fan_")
        val file = File(path)
        assertTrue(file.name.startsWith("fan_") && file.name.endsWith(".webp"))
        assertEquals(File(context.filesDir, file.name).absolutePath, path)
        assertEquals("RIFF", String(file.readBytes().copyOfRange(0, 4), Charsets.US_ASCII))
        assertEquals("WEBP", String(file.readBytes().copyOfRange(8, 12), Charsets.US_ASCII))
        assertEquals(1200 to 800, size(path))
        val c = BitmapFactory.decodeFile(path).getPixel(600, 400)
        assertTrue("색이 그대로다", Color.green(c) > 200 && Color.red(c) < 60)
    }

    @Test
    fun 큰_사진은_긴_변을_2048로_줄인다() = runBlocking {
        val path = PhotoStore.save(context, Uri.fromFile(jpeg("big.jpg", 4000, 3000)), "betray_")
        assertEquals(2048 to 1536, size(path))
    }

    @Test
    fun 카메라_사진의_방향대로_세운다() = runBlocking {
        val src = jpeg("rot.jpg", 400, 200)
        ExifInterface(src.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val path = PhotoStore.save(context, Uri.fromFile(src), "betray_genre_")
        assertEquals("가로 사진이 세로로 선다", 200 to 400, size(path))
    }

    @Test
    fun GIF는_움직임이_멈추지_않게_그대로_둔다() = runBlocking {
        val gif = File(srcDir, "a.gif").apply { writeBytes("GIF89a".toByteArray() + ByteArray(30) { it.toByte() }) }
        val path = PhotoStore.save(context, Uri.fromFile(gif), "fan_")
        assertTrue(path.endsWith(".gif"))
        assertTrue(File(path).readBytes().contentEquals(gif.readBytes()))
    }

    @Test
    fun 풀_수_없는_사진은_원본을_그대로_복사한다() = runBlocking {
        val odd = File(srcDir, "odd.bin").apply { writeBytes(ByteArray(64) { 7 }) }
        val path = PhotoStore.save(context, Uri.fromFile(odd), "fan_")
        assertTrue(File(path).readBytes().contentEquals(odd.readBytes()))
    }

    @Test
    fun 없는_사진은_오류로_끝나고_파일을_남기지_않는다() = runBlocking {
        val before = context.filesDir.list()?.size ?: 0
        val result = runCatching { PhotoStore.save(context, Uri.fromFile(File(srcDir, "nope.jpg")), "fan_") }
        assertTrue(result.isFailure)
        assertEquals(before, context.filesDir.list()?.size ?: 0)
    }

    @Test
    fun 아이콘_원_범위용으로_사진을_읽는다() = runBlocking {
        // 아이콘 '사진을 열 수 없습니다' 오류: 크기만 읽는 단계에서 막히던 것
        val bmp = PhotoStore.loadUpright(context, Uri.fromFile(jpeg("icon.jpg", 900, 600)))
        assertEquals(900, bmp.width)
        assertEquals(600, bmp.height)
    }
}
