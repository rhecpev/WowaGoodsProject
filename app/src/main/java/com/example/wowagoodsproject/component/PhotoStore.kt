package com.example.wowagoodsproject.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.util.UUID
import kotlin.math.max

/**
 * 사용자가 고른 사진(2차창작·배신 굿즈·장르 배경/아이콘)을 앱 폴더에 넣는다.
 *
 * 새로 넣는 사진은 WebP 로 바꿔 저장한다. 폰 카메라 원본(수 MB)을 그대로 두면 앱 용량과 백업 zip 이 커지기 때문이다.
 *  - EXIF 방향대로 세워서 저장한다(메타데이터·위치 정보는 남지 않는다).
 *  - 긴 변이 [MAX_SIDE] 보다 크면 줄인다.
 *  - GIF 는 바꾸면 움직임이 멈추므로 원본 그대로 둔다.
 *  - 사진을 풀 수 없는 형식이면(오래된 기기의 HEIC 등) 예전처럼 원본을 그대로 복사한다.
 * 이미 저장된 사진은 건드리지 않는다. 백업은 파일 이름으로 사진을 찾으므로 확장자가 달라도 그대로 오간다.
 */
object PhotoStore {

    const val MAX_SIDE = 2048
    const val QUALITY = 85

    /** [uri] 의 사진을 앱 폴더에 "[prefix]UUID.webp"(GIF 는 .gif) 로 저장하고 절대 경로를 돌려준다. */
    suspend fun save(context: Context, uri: Uri, prefix: String): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val head = ByteArray(4)
        val read = (resolver.openInputStream(uri) ?: throw IllegalStateException("사진을 읽을 수 없습니다"))
            .use { it.read(head) }
        val isGif = read == 4 && String(head, Charsets.US_ASCII) == "GIF8"

        val bitmap = if (isGif) null else runCatching { loadUpright(context, uri) }.getOrNull()
        val file = File(context.filesDir, "$prefix${UUID.randomUUID()}." + when {
            bitmap != null -> "webp"
            isGif -> "gif"
            else -> "jpg"
        })
        try {
            if (bitmap != null) {
                try {
                    file.outputStream().use { writeWebp(bitmap, it) }
                } finally {
                    bitmap.recycle()
                }
            } else {
                val input = resolver.openInputStream(uri) ?: throw IllegalStateException("사진을 읽을 수 없습니다")
                input.use { i -> file.outputStream().use { i.copyTo(it) } }
            }
        } catch (e: Exception) {
            file.delete() // 반쯤 쓴 사진을 남기지 않는다
            throw e
        }
        file.absolutePath
    }

    /** 사진을 읽어 EXIF 방향대로 세운다. 긴 변이 [maxSide] 보다 크면 2의 거듭제곱으로 줄여 읽은 뒤 맞춘다. */
    suspend fun loadUpright(context: Context, uri: Uri, maxSide: Int = MAX_SIDE): Bitmap = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // 크기만 읽을 때 decodeStream 은 늘 null 을 돌려준다. 크기는 bounds 에 담기므로 스트림이 열리는지만 본다.
        val stream = resolver.openInputStream(uri) ?: throw IllegalStateException("사진을 읽을 수 없습니다")
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IllegalStateException("사진 형식을 알 수 없습니다")
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalStateException("사진을 읽을 수 없습니다")

        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        // EXIF 는 저장할 때 버리므로 회전뿐 아니라 뒤집힘(거울상)도 여기서 사진에 굽는다.
        val orient = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> orient.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> orient.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> { orient.setRotate(180f); orient.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSPOSE -> { orient.setRotate(90f); orient.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> orient.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { orient.setRotate(-90f); orient.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> orient.setRotate(-90f)
        }

        // 2의 거듭제곱으로 줄인 뒤에도 남는 만큼은 정확히 맞춰 줄인다.
        val longSide = max(decoded.width, decoded.height)
        val scale = if (longSide > maxSide) maxSide.toFloat() / longSide else 1f
        if (orient.isIdentity && scale == 1f) return@withContext decoded
        val matrix = Matrix().apply {
            if (scale != 1f) postScale(scale, scale)
            postConcat(orient)
        }
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            .also { if (it !== decoded) decoded.recycle() }
    }

    fun writeWebp(bitmap: Bitmap, out: OutputStream, quality: Int = QUALITY) {
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        if (!bitmap.compress(format, quality, out)) throw IllegalStateException("사진을 저장하지 못했습니다")
    }
}
