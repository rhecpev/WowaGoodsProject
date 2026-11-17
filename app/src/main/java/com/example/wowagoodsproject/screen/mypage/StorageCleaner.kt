package com.example.wowagoodsproject.screen.mypage

import android.content.Context
import androidx.room.RoomDatabase
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.example.wowagoodsproject.App
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 용량 정리에서 고를 수 있는 항목 */
enum class CleanTarget(val title: String, val description: String) {
    PATCH_NOTES("업데이트 이력", "데이터 업데이트 기록. 굿즈 데이터는 그대로예요"),
    READ_NEWS("읽은 소식", "이미 본 신규·변경 소식. 안 읽은 소식은 남아요"),
    CHECKED_CHANGES("처리한 변경 기록", "변경된 굿즈 중 처리한 기록. 미처리 기록은 남아요"),
    IMAGE_CACHE("이미지 캐시", "받아 둔 굿즈·캐릭터 사진. 필요할 때 다시 받아요"),
    TEMP_FILES("남은 임시 파일", "카메라 임시 사진, 백업 임시 파일, 어느 2차창작 굿즈도 쓰지 않는 사진")
}

/**
 * 한 항목의 지울 거리.
 * @property count 지울 개수(기록 수·파일 수). 이미지 캐시는 개수를 세지 않아 -1.
 * @property bytes 지우면 확보될 크기. DB 기록은 내용 크기로 어림한 값, 파일은 실제 크기.
 * @property estimated [bytes] 가 어림값인지
 */
data class CleanItem(val target: CleanTarget, val count: Int, val bytes: Long, val estimated: Boolean)

/**
 * 용량 정리: 지워도 되는(계속 쌓이거나 다시 받을 수 있는) 데이터를 찾아 크기를 재고 지운다.
 * DB 는 행을 지워도 파일이 바로 줄지 않으므로, 지운 뒤 VACUUM 으로 압축하고 WAL 을 비운다.
 * 실제로 확보한 크기는 정리 전후 파일 크기를 재서 돌려준다.
 */
object StorageCleaner {

    private val gson = Gson()

    /** 내용을 JSON 으로 바꾼 크기로 DB 기록 크기를 어림한다(문자 데이터가 대부분이라 실제와 비슷하다). */
    private fun estimate(rows: List<Any>): Long = if (rows.isEmpty()) 0 else gson.toJson(rows).toByteArray().size.toLong()

    suspend fun scan(context: Context): List<CleanItem> = withContext(Dispatchers.IO) {
        val patch = App.patchNoteDatabase.patchNoteDao().getAll()
        val news = App.newsDatabase.newsDao().getRead()
        val checked = App.changedGoodsDatabase.changedGoodsDao().getChecked()
        val temp = tempFiles(context)
        listOf(
            CleanItem(CleanTarget.PATCH_NOTES, patch.size, estimate(patch), estimated = true),
            CleanItem(CleanTarget.READ_NEWS, news.size, estimate(news), estimated = true),
            CleanItem(CleanTarget.CHECKED_CHANGES, checked.size, estimate(checked), estimated = true),
            CleanItem(CleanTarget.IMAGE_CACHE, -1, imageCacheBytes(context), estimated = false),
            CleanItem(CleanTarget.TEMP_FILES, temp.size, temp.sumOf { sizeOf(it) }, estimated = false)
        )
    }

    /**
     * [targets] 를 지우고 실제로 확보한 크기를 돌려준다.
     * 정리 전후로 관련 DB 파일·캐시·임시 파일 크기를 재서 그 차이를 확보한 크기로 본다.
     */
    suspend fun clean(context: Context, targets: Set<CleanTarget>): Long = withContext(Dispatchers.IO) {
        val before = measuredBytes(context)
        if (CleanTarget.PATCH_NOTES in targets) App.patchNoteDatabase.patchNoteDao().deleteAll()
        if (CleanTarget.READ_NEWS in targets) App.newsDatabase.newsDao().deleteRead()
        if (CleanTarget.CHECKED_CHANGES in targets) App.changedGoodsDatabase.changedGoodsDao().deleteChecked()
        if (CleanTarget.IMAGE_CACHE in targets) clearImageCache(context)
        if (CleanTarget.TEMP_FILES in targets) tempFiles(context).forEach { it.deleteRecursively() }

        if (CleanTarget.PATCH_NOTES in targets) compact(App.patchNoteDatabase)
        if (CleanTarget.READ_NEWS in targets) compact(App.newsDatabase)
        if (CleanTarget.CHECKED_CHANGES in targets) compact(App.changedGoodsDatabase)
        (before - measuredBytes(context)).coerceAtLeast(0)
    }

    /** 지운 행이 차지하던 자리를 파일에서 실제로 비운다. VACUUM 은 트랜잭션 밖에서만 된다. */
    private fun compact(db: RoomDatabase) {
        runCatching {
            val sql = db.openHelper.writableDatabase
            sql.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
            sql.execSQL("VACUUM")
            sql.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
        }
    }

    /** 정리 대상과 관련된 파일 크기의 합: 세 DB(+wal/shm), 이미지 캐시, 임시 파일 */
    private suspend fun measuredBytes(context: Context): Long {
        val dbFiles = listOf("patch_note_database", "news_database", "changed_goods_database").flatMap { name ->
            val main = context.getDatabasePath(name)
            listOf(main, File(main.path + "-wal"), File(main.path + "-shm"))
        }
        return dbFiles.sumOf { sizeOf(it) } + imageCacheBytes(context) + tempFiles(context).sumOf { sizeOf(it) }
    }

    @OptIn(ExperimentalCoilApi::class)
    private fun imageCacheBytes(context: Context): Long =
        runCatching { context.imageLoader.diskCache?.size ?: 0L }.getOrDefault(0L)

    @OptIn(ExperimentalCoilApi::class)
    private fun clearImageCache(context: Context) {
        runCatching {
            context.imageLoader.diskCache?.clear()
            context.imageLoader.memoryCache?.clear()
        }
    }

    /** 사진 파일로 보는 확장자(2차창작 사진·백업에서 푼 사진) */
    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "heic")

    /**
     * 앱이 더 쓰지 않는 파일:
     * - 카메라로 찍으려다 남은 임시 사진(cacheDir/camera_temp_*)
     * - 불러오기 중간에 남은 임시 폴더(.restore_staging), 예전 방식으로 내보내고 남은 백업 zip
     * - 앱 폴더의 사진 중 어느 2차창작 굿즈도 가리키지 않는 것
     * 2차창작 목록을 못 읽으면 사진은 하나도 고르지 않는다(쓰는 사진을 지우지 않게).
     */
    private suspend fun tempFiles(context: Context): List<File> {
        val result = mutableListOf<File>()
        context.cacheDir.listFiles()?.filter { it.isFile && it.name.startsWith("camera_temp_") }?.let { result += it }
        File(context.filesDir, ".restore_staging").takeIf { it.exists() }?.let { result += it }
        File(context.filesDir, "wowa_backup.zip").takeIf { it.isFile }?.let { result += it }
        val used = runCatching {
            App.fanDatabase.fanGoodsDao().getAll()
                .map { it.fanGoodsImgPath }
                .filter { it.isNotEmpty() }
                .map { File(it).canonicalPath }
                .toSet()
        }.getOrNull()
        if (used != null) {
            context.filesDir.listFiles()
                ?.filter { it.isFile && it.extension.lowercase() in IMAGE_EXT && it.canonicalPath !in used }
                ?.let { result += it }
        }
        return result
    }

    private fun sizeOf(f: File): Long = when {
        !f.exists() -> 0
        f.isFile -> f.length()
        else -> f.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /** 1536 → "1.5KB" */
    fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "%.1fKB".format(bytes / 1024f)
        else -> "%.1fMB".format(bytes / (1024f * 1024f))
    }
}
